import asyncio
import logging
import re
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
    description="Provider-based search for openly licensed, directly playable media.",
    version="3.0.0",
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
        "providers": ["Internet Archive (openly licensed media only)"],
    }


def _is_openly_licensed(metadata: dict) -> bool:
    """Only return media whose metadata explicitly declares an open license."""
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
        if not name or name.startswith("__") or item.get("private") == "true":
            continue
        if fmt in {"h.264", "h.264 ia", "mpeg4", "mpeg4 (access)", "mpeg4 512kb", "mp4"} or name.lower().endswith(".mp4"):
            files.append(item)
    # Prefer MP4/H.264 files with known sizes, larger files first.
    files.sort(key=lambda f: int(f.get("size") or 0), reverse=True)
    return files


async def _archive_search(client: httpx.AsyncClient, title: str, media_type: str):
    query = f'title:({title}) AND mediatype:movies'
    params = {
        "q": query,
        "fl[]": ["identifier", "title", "year", "description"],
        "rows": 12,
        "page": 1,
        "output": "json",
        "sort[]": "downloads desc",
    }
    response = await client.get(ARCHIVE_SEARCH, params=params)
    response.raise_for_status()
    docs = response.json().get("response", {}).get("docs", [])
    return docs


async def _get_archive_item(client: httpx.AsyncClient, doc: dict):
    identifier = str(doc.get("identifier") or "")
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,120}", identifier):
        return None
    response = await client.get(ARCHIVE_METADATA.format(quote(identifier, safe="")))
    response.raise_for_status()
    metadata = response.json().get("metadata") or {}
    if not _is_openly_licensed(metadata):
        return None
    files = _video_files(response.json())
    if not files:
        return None

    language_value = metadata.get("language") or metadata.get("languages") or ""
    if isinstance(language_value, list):
        language_value = " ".join(str(x) for x in language_value)
    language = str(language_value).lower()
    if any(x in language for x in ("russian", "рус")):
        voice_name = "Русская аудиодорожка (если указана в файле)"
        language_code = "ru"
    elif any(x in language for x in ("english", "англ")):
        voice_name = "English audio (if present in file)"
        language_code = "en"
    else:
        voice_name = "Язык не указан в каталоге"
        language_code = "und"

    file = files[0]
    filename = str(file.get("name") or "")
    stream_url = "https://archive.org/download/{}/{}".format(
        quote(identifier, safe=""),
        quote(filename, safe="/"),
    )
    title = str(metadata.get("title") or doc.get("title") or identifier)
    return {
        "id": identifier,
        "title": title,
        "year": metadata.get("year") or doc.get("year"),
        "language": language_code,
        "voice_name": voice_name,
        "url": stream_url,
        "source": "Internet Archive",
        "license": metadata.get("licenseurl") or metadata.get("rights"),
    }


@app.get("/api/playback")
async def playback(
    title: str = Query(min_length=1, max_length=180),
    media_type: str = Query(default="movie", alias="type", pattern="^(movie|tv)$"),
):
    """Search supported providers and return direct stream URLs grouped by language."""
    try:
        timeout = httpx.Timeout(18.0, connect=8.0)
        limits = httpx.Limits(max_connections=12, max_keepalive_connections=6)
        async with httpx.AsyncClient(
            timeout=timeout,
            limits=limits,
            follow_redirects=True,
            headers={"User-Agent": "ARNIX-MediaCenter/3.0 (open-media search)"},
        ) as client:
            docs = await _archive_search(client, title, media_type)
            results = await asyncio.gather(
                *[_get_archive_item(client, doc) for doc in docs[:12]],
                return_exceptions=True,
            )

        items = [x for x in results if isinstance(x, dict)]
        if not items:
            return {
                "title": title,
                "translations": [],
                "sources": [],
                "message": "В подключённом открытом каталоге не найдено видео с явно указанной свободной лицензией. Другие источники можно подключить отдельными адаптерами.",
            }

        groups = {}
        for item in items:
            code = item["language"]
            group = groups.setdefault(code, {
                "id": code,
                "name": item["voice_name"],
                "episodes": [],
            })
            group["episodes"].append({
                "number": len(group["episodes"]) + 1,
                "title": item["title"] + (f" ({item['year']})" if item.get("year") else ""),
                "url": item["url"],
                "source": item["source"],
                "license": item["license"],
            })

        return {
            "title": title,
            "translations": list(groups.values()),
            "sources": sorted({item["source"] for item in items}),
            "results_count": len(items),
        }
    except httpx.HTTPError as exc:
        logger.warning("Provider request failed: %s", exc)
        raise HTTPException(status_code=502, detail="Открытый видеокаталог временно недоступен") from exc
    except Exception as exc:
        logger.exception("Universal search failed")
        raise HTTPException(status_code=500, detail=f"Ошибка поиска: {exc.__class__.__name__}") from exc
