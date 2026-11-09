import asyncio
import logging
import os
import re
from difflib import SequenceMatcher
from urllib.parse import quote

import httpx
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("arnix.streams")

ARCHIVE_SEARCH = "https://archive.org/advancedsearch.php"
ARCHIVE_METADATA = "https://archive.org/metadata/{}"

app = FastAPI(
    title="ARNIX Universal Stream Engine",
    description="Search for openly licensed media with direct playback URLs.",
    version="3.4.0",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)


@app.get("/health")
async def health():
    return {
        "ok": True,
        "engine": "ARNIX Universal Stream Engine",
        "providers": ["Internet Archive", "Alloha (metadata only)"],
        "supported_media": ["movie"],
    }


def _clean_title(value: str) -> str:
    value = re.sub(r"\([^)]*\)|\[[^]]*\]", " ", value.lower())
    value = re.sub(r"[^a-zа-яё0-9]+", " ", value)
    return re.sub(r"\s+", " ", value).strip()


def _is_relevant_title(requested: str, candidate: str) -> bool:
    a, b = _clean_title(requested), _clean_title(candidate)
    if not a or not b:
        return False
    # Require a strong match; avoid unrelated videos returned by broad catalog search.
    return a == b or a in b or SequenceMatcher(None, a, b).ratio() >= 0.86


def _is_openly_licensed(metadata: dict) -> bool:
    license_url = str(metadata.get("licenseurl") or "").lower()
    rights = str(metadata.get("rights") or "").lower()
    return (
        "creativecommons.org" in license_url
        or "creativecommons" in rights
        or "public domain" in rights
        or "publicdomain" in rights
    )


def _video_files(metadata: dict) -> list[dict]:
    files = []
    for item in metadata.get("files") or []:
        name = str(item.get("name") or "")
        fmt = str(item.get("format") or "").lower()
        if not name or name.startswith("__") or str(item.get("private", "")).lower() == "true":
            continue
        if fmt in {"h.264", "h.264 ia", "mpeg4", "mpeg4 (access)", "mpeg4 512kb", "mp4"} or name.lower().endswith((".mp4", ".m4v")):
            files.append(item)
    files.sort(key=lambda f: int(f.get("size") or 0), reverse=True)
    return files


def _mp4_boxes(data: bytes, start: int = 0, end: int | None = None):
    """Iterate ISO-BMFF boxes safely within a byte buffer."""
    end = len(data) if end is None else min(end, len(data))
    pos = start
    while pos + 8 <= end:
        size = int.from_bytes(data[pos:pos + 4], "big")
        kind = data[pos + 4:pos + 8]
        header = 8
        if size == 1 and pos + 16 <= end:
            size = int.from_bytes(data[pos + 8:pos + 16], "big")
            header = 16
        elif size == 0:
            size = end - pos
        if size < header or pos + size > end:
            break
        yield kind, pos + header, pos + size
        pos += size


def _mp4_audio_languages(data: bytes) -> list[str]:
    """Read ISO-639 language codes from mdhd boxes belonging to audio tracks."""
    found = []
    for kind, box_start, box_end in _mp4_boxes(data):
        if kind != b"moov":
            continue
        for trak_kind, trak_start, trak_end in _mp4_boxes(data, box_start, box_end):
            if trak_kind != b"trak":
                continue
            mdia = next(((s, e) for k, s, e in _mp4_boxes(data, trak_start, trak_end) if k == b"mdia"), None)
            if not mdia:
                continue
            mdia_start, mdia_end = mdia
            children = list(_mp4_boxes(data, mdia_start, mdia_end))
            handler = next(((s, e) for k, s, e in children if k == b"hdlr"), None)
            mdhd = next(((s, e) for k, s, e in children if k == b"mdhd"), None)
            if not handler or not mdhd:
                continue
            hs, he = handler
            if he - hs < 12 or data[hs + 8:hs + 12] != b"soun":
                continue
            ms, me = mdhd
            if me - ms < 24:
                continue
            version = data[ms]
            language_offset = ms + (32 if version == 1 else 20)
            if language_offset + 2 > me:
                continue
            packed = int.from_bytes(data[language_offset:language_offset + 2], "big")
            if packed == 0:
                continue
            code = "".join(chr(((packed >> shift) & 0x1F) + 0x60) for shift in (10, 5, 0))
            if re.fullmatch(r"[a-z]{3}", code) and code not in {"und", "xxx"}:
                found.append(code)
    return list(dict.fromkeys(found))


async def _detect_mp4_audio_language(client: httpx.AsyncClient, url: str) -> tuple[str | None, str | None]:
    """Inspect a bounded MP4 header range; no PyAV/FFmpeg dependency or full download."""
    try:
        async with client.stream(
            "GET", url, headers={"Range": "bytes=0-16777215"}, follow_redirects=True
        ) as response:
            if response.status_code not in {200, 206}:
                return None, None
            content_type = response.headers.get("content-type", "").lower()
            if content_type and not any(x in content_type for x in ("video/", "octet-stream", "application/mp4")):
                return None, None
            chunks = []
            total = 0
            async for chunk in response.aiter_bytes():
                remaining = 16 * 1024 * 1024 - total
                if remaining <= 0:
                    break
                chunk = chunk[:remaining]
                chunks.append(chunk)
                total += len(chunk)
                if total >= 16 * 1024 * 1024:
                    break
            codes = _mp4_audio_languages(b"".join(chunks))
            for code in codes:
                if code in {"rus", "ru"}:
                    return "ru", "mp4_mdhd_audio_track"
                if code in {"eng", "en"}:
                    return "en", "mp4_mdhd_audio_track"
            if codes:
                return codes[0], "mp4_mdhd_audio_track"
    except (httpx.HTTPError, ValueError, IndexError):
        pass
    return None, None


async def _archive_search(client: httpx.AsyncClient, title: str):
    params = {
        "q": f'title:({title}) AND mediatype:movies',
        "fl[]": ["identifier", "title", "year", "downloads"],
        "rows": 20,
        "page": 1,
        "output": "json",
        "sort[]": "downloads desc",
    }
    response = await client.get(ARCHIVE_SEARCH, params=params)
    response.raise_for_status()
    return response.json().get("response", {}).get("docs", [])


async def _get_archive_item(client: httpx.AsyncClient, doc: dict, requested_title: str):
    identifier = str(doc.get("identifier") or "")
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,120}", identifier):
        return None

    response = await client.get(ARCHIVE_METADATA.format(quote(identifier, safe="")))
    response.raise_for_status()
    payload = response.json()
    metadata = payload.get("metadata") or {}
    title = str(metadata.get("title") or doc.get("title") or identifier)
    if not _is_relevant_title(requested_title, title):
        return None
    if not _is_openly_licensed(metadata):
        return None

    files = _video_files(payload)
    if not files:
        return None

    file = files[0]
    filename = str(file.get("name") or "")
    stream_url = "https://archive.org/download/{}/{}".format(
        quote(identifier, safe=""),
        quote(filename, safe="/"),
    )

    # Prefer container-level audio track tags, then explicit Archive metadata.
    detected_language, detected_evidence = await _detect_mp4_audio_language(client, stream_url)
    audio_language = str(
        metadata.get("audio_language")
        or metadata.get("audio languages")
        or ""
    ).strip().lower()
    filename_lower = filename.lower()

    if detected_language in {"ru", "rus"}:
        language_code = "ru"
        language_label = "Русская аудиодорожка (тег MP4)"
        language_verified = True
        language_evidence = detected_evidence
    elif detected_language in {"en", "eng"}:
        language_code = "en"
        language_label = "English audio (MP4 track tag)"
        language_verified = True
        language_evidence = detected_evidence
    elif audio_language in {"russian", "ru", "русский", "русская", "rus"}:
        language_code = "ru"
        language_label = "Русская аудиодорожка (указана источником)"
        language_verified = True
        language_evidence = "source_audio_metadata"
    elif audio_language in {"english", "en", "английский", "английская", "eng"}:
        language_code = "en"
        language_label = "English audio (указана источником)"
        language_verified = True
        language_evidence = "source_audio_metadata"
    elif re.search(r"(^|[._\\s-])(english|eng)([._\\s-]|$)", filename_lower):
        language_code = "en"
        language_label = "English (по названию файла; аудиодорожка не проверена)"
        language_verified = False
        language_evidence = "filename_hint"
    elif re.search(r"(^|[._\\s-])(russian|rus)([._\\s-]|$)", filename_lower):
        language_code = "ru"
        language_label = "Русский (по названию файла; аудиодорожка не проверена)"
        language_verified = False
        language_evidence = "filename_hint"
    else:
        language_code = "und"
        language_label = "Язык аудио не подтверждён"
        language_verified = False
        language_evidence = "unknown"
    # Probe only the first byte so unavailable or non-video links are filtered
    # without downloading the full movie.
    try:
        async with client.stream(
            "GET",
            stream_url,
            headers={"Range": "bytes=0-0"},
            follow_redirects=True,
        ) as probe:
            if probe.status_code not in {200, 206}:
                return None
            content_type = probe.headers.get("content-type", "").lower()
            if content_type and not any(
                marker in content_type
                for marker in ("video/", "application/octet-stream", "application/mp4")
            ):
                return None
            async for _ in probe.aiter_bytes():
                break
    except httpx.HTTPError:
        return None

    return {
        "id": identifier,
        "title": title,
        "year": metadata.get("year") or doc.get("year"),
        "language": language_code,
        "language_label": language_label,
        "language_verified": language_verified,
        "language_evidence": language_evidence,
        "url": stream_url,
        "source": "Internet Archive",
        "license": metadata.get("licenseurl") or metadata.get("rights"),
        "size": int(file.get("size") or 0),
        "downloads": int(doc.get("downloads") or 0),
    }


@app.get("/api/playback")
async def playback(
    title: str = Query(min_length=1, max_length=180),
    media_type: str = Query(default="movie", alias="type", pattern="^(movie|tv)$"),
):
    if media_type == "tv":
        return {
            "title": title,
            "translations": [],
            "sources": [],
            "results_count": 0,
            "message": "В текущем адаптере Internet Archive поиск сериалов и разбиение на сезоны/серии не поддерживаются.",
        }

    try:
        timeout = httpx.Timeout(18.0, connect=8.0)
        limits = httpx.Limits(max_connections=12, max_keepalive_connections=6)
        async with httpx.AsyncClient(
            timeout=timeout,
            limits=limits,
            follow_redirects=True,
            headers={"User-Agent": "ARNIX-MediaCenter/3.4.0 (open-media search)"},
        ) as client:
            docs = await _archive_search(client, title)
            results = await asyncio.gather(
                *[_get_archive_item(client, doc, title) for doc in docs[:20]],
                return_exceptions=True,
            )

        items = [item for item in results if isinstance(item, dict)]
        if not items:
            return {
                "title": title,
                "translations": [],
                "sources": [],
                "results_count": 0,
                "message": "Подходящее видео со свободной лицензией не найдено. Язык дорожки не будет угадываться.",
            }

        # Collapse multiple uploads/restorations of the same requested film into one best match.
        items.sort(
            key=lambda item: (
                item["language"] in {"ru", "en"},
                item["size"],
                item["downloads"],
            ),
            reverse=True,
        )
        best = items[0]
        group = {
            "id": best["language"],
            "name": best["language_label"],
            "episodes": [{
                "number": 1,
                "title": best["title"] + (f" ({best['year']})" if best.get("year") else ""),
                "url": best["url"],
                "source": best["source"],
                "license": best["license"],
                "language_verified": best["language_verified"],
                "language_evidence": best["language_evidence"],
            }],
        }
        return {
            "title": title,
            "translations": [group],
            "sources": [best["source"]],
            "results_count": 1,
            "alternatives_found": max(0, len(items) - 1),
            "note": "Доступность видеопотока проверяется перед выдачей. Язык определяется по тегу аудиодорожки MP4, если он доступен в заголовке контейнера; иначе используются метаданные источника или неподтверждённая подсказка из имени файла.",
        }
    except httpx.HTTPError as exc:
        logger.warning("Provider request failed: %s", exc)
        raise HTTPException(status_code=502, detail="Открытый видеокаталог временно недоступен") from exc
    except Exception as exc:
        logger.exception("Universal search failed")
        raise HTTPException(status_code=500, detail=f"Ошибка поиска: {exc.__class__.__name__}") from exc


ALLOHA_API = "https://api.alloha.tv/"


@app.get("/api/alloha")
async def alloha_lookup(kp: int = Query(gt=0)):
    """Return Alloha catalogue metadata; iframe embeds are not direct ExoPlayer streams."""
    token = os.getenv("ALLOHA_API_TOKEN", "").strip()
    if not token:
        raise HTTPException(status_code=503, detail="ALLOHA_API_TOKEN не настроен в Render")
    try:
        async with httpx.AsyncClient(
            timeout=httpx.Timeout(15.0, connect=7.0),
            follow_redirects=True,
            headers={"User-Agent": "ARNIX-MediaCenter/3.5.0"},
        ) as client:
            response = await client.get(ALLOHA_API, params={"token": token, "kp": kp})
            response.raise_for_status()
            payload = response.json()
        data = payload.get("data") if isinstance(payload, dict) else None
        if isinstance(data, list):
            data = data[0] if data else None
        if not isinstance(data, dict):
            return {"kp": kp, "found": False, "provider": "Alloha", "message": "Alloha не вернула запись для этого ID."}

        translation_items = data.get("translation_iframe") or {}
        translations = []
        if isinstance(translation_items, dict):
            for key, item in translation_items.items():
                if isinstance(item, dict):
                    translations.append({
                        "id": str(key),
                        "name": str(item.get("name") or key),
                        "quality": item.get("quality"),
                        "available": bool(item.get("iframe")),
                    })
        elif isinstance(translation_items, list):
            for index, item in enumerate(translation_items):
                if isinstance(item, dict):
                    translations.append({
                        "id": str(item.get("id") or index),
                        "name": str(item.get("name") or f"Озвучка {index + 1}"),
                        "quality": item.get("quality"),
                        "available": bool(item.get("iframe")),
                    })

        seasons = data.get("seasons") or {}
        season_summary = []
        if isinstance(seasons, dict):
            for season_number, season_data in seasons.items():
                episodes = season_data.get("episodes", season_data) if isinstance(season_data, dict) else season_data
                count = len(episodes) if isinstance(episodes, (list, dict)) else 0
                season_summary.append({"season": str(season_number), "episodes_count": count})

        return {
            "kp": kp,
            "found": True,
            "provider": "Alloha",
            "title": data.get("name") or data.get("title") or data.get("title_ru"),
            "year": data.get("year"),
            "category": data.get("category"),
            "quality": data.get("quality"),
            "translation": data.get("translation"),
            "translations": translations,
            "seasons": season_summary,
            "iframe_available": bool(data.get("iframe") or translations),
            "direct_stream_available": False,
            "playback_note": "Alloha API возвращает iframe-плеер, а не прямой MP4/M3U8 URL. Встроенный веб-плеер и извлечение защищённого потока не используются.",
        }
    except httpx.HTTPStatusError as exc:
        logger.warning("Alloha returned HTTP %s", exc.response.status_code)
        raise HTTPException(status_code=502, detail="Alloha API вернул ошибку HTTP") from exc
    except (httpx.HTTPError, ValueError) as exc:
        logger.warning("Alloha request failed: %s", exc)
        raise HTTPException(status_code=502, detail="Не удалось получить ответ Alloha API") from exc
