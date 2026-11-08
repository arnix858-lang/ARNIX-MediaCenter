import asyncio
import logging
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
    version="3.1.0",
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
        "providers": ["Internet Archive"],
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

    # The catalog's "language" field describes the item, not necessarily its audio track.
    # Never claim a Russian or English voice unless the source explicitly labels audio language.
    audio_language = str(
        metadata.get("audio_language")
        or metadata.get("audio languages")
        or ""
    ).lower()
    if audio_language in {"russian", "ru", "русский", "русская"}:
        language_code = "ru"
        language_label = "Русская аудиодорожка (указана источником)"
    elif audio_language in {"english", "en", "английский", "английская"}:
        language_code = "en"
        language_label = "English audio (declared by source)"
    else:
        language_code = "und"
        language_label = "Язык аудио не подтверждён"

    file = files[0]
    filename = str(file.get("name") or "")
    stream_url = "https://archive.org/download/{}/{}".format(
        quote(identifier, safe=""),
        quote(filename, safe="/"),
    )
    return {
        "id": identifier,
        "title": title,
        "year": metadata.get("year") or doc.get("year"),
        "language": language_code,
        "language_label": language_label,
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
            headers={"User-Agent": "ARNIX-MediaCenter/3.1 (open-media search)"},
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
                "language_verified": best["language"] in {"ru", "en"},
            }],
        }
        return {
            "title": title,
            "translations": [group],
            "sources": [best["source"]],
            "results_count": 1,
            "alternatives_found": max(0, len(items) - 1),
            "note": "Показан один лучший совпадающий файл. Язык аудио указан только при наличии явного поля audio_language в метаданных источника; иначе он помечен как неподтверждённый.",
        }
    except httpx.HTTPError as exc:
        logger.warning("Provider request failed: %s", exc)
        raise HTTPException(status_code=502, detail="Открытый видеокаталог временно недоступен") from exc
    except Exception as exc:
        logger.exception("Universal search failed")
        raise HTTPException(status_code=500, detail=f"Ошибка поиска: {exc.__class__.__name__}") from exc
