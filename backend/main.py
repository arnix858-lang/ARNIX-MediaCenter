import asyncio
import json
import logging
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware

from lampa_bridge import list_sources, search_sources

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("arnix.mediacenter")

app = FastAPI(
    title="ARNIX MediaCenter API",
    description="ARNIX catalog with openly licensed Internet Archive video search.",
    version="5.2.0",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)

IA_LICENSE_MARKERS = (
    "creativecommons.org/publicdomain",
    "creativecommons.org/licenses/by/",
    "creativecommons.org/licenses/by-sa/",
)

def ia_json(url: str):
    request = Request(url, headers={"Accept": "application/json", "User-Agent": "ARNIX-MediaCenter/1.0"})
    with urlopen(request, timeout=18) as response:
        return json.loads(response.read(4 * 1024 * 1024).decode("utf-8"))

def archive_search_sync(query: str):
    safe = query.replace('"', ' ').strip()
    params = urlencode({
        "q": f'mediatype:movies AND title:("{safe}")',
        "fl[]": ["identifier", "title", "year", "description"],
        "rows": 12,
        "page": 1,
        "output": "json",
    }, doseq=True)
    search = ia_json("https://archive.org/advancedsearch.php?" + params)
    docs = search.get("response", {}).get("docs", [])
    results = []
    for doc in docs:
        identifier = doc.get("identifier")
        if not identifier:
            continue
        try:
            metadata = ia_json("https://archive.org/metadata/" + quote(identifier, safe=""))
            meta = metadata.get("metadata", {})
            license_url = str(meta.get("licenseurl") or "").lower()
            if not any(marker in license_url for marker in IA_LICENSE_MARKERS):
                continue
            files = metadata.get("files", [])
            video = next((f for f in files if str(f.get("name", "")).lower().endswith(".mp4")
                          and not f.get("private") and int(f.get("size") or 0) > 0), None)
            if not video:
                continue
            filename = video["name"]
            torrent_file = next((item for item in files
                if str(item.get("name", "")).lower().endswith(".torrent")
                and not item.get("private") and int(item.get("size") or 0) > 0), None)
            torrent_url = (
                f"https://archive.org/download/{quote(identifier, safe='')}/{quote(torrent_file['name'], safe='/')}"
                if torrent_file else None
            )
            results.append({
                "id": identifier,
                "title": meta.get("title") or doc.get("title") or identifier,
                "year": meta.get("year") or doc.get("year") or "",
                "description": str(meta.get("description") or "")[:1200],
                "poster": f"https://archive.org/services/img/{quote(identifier, safe='')}",
                "stream_url": f"https://archive.org/download/{quote(identifier, safe='')}/{quote(filename, safe='/')}",
                "stream_type": "mp4",
                "torrent_url": torrent_url,
                "license": license_url,
                "details": f"https://archive.org/details/{quote(identifier, safe='')}",
            })
        except Exception as exc:
            logger.info("Skipping Internet Archive item %s: %s", identifier, exc)
    return results

@app.get("/health")
async def health():
    sources = await list_sources()
    return {
        "ok": True,
        "engine": "ARNIX MediaCenter",
        "configured_sources": len(sources["sources"]) + 1,
        "built_in_sources": ["Internet Archive (openly licensed MP4 + available torrent files)"],
        "playback": "native Media3 player for direct supported streams",
    }

@app.get("/api/lampa/sources")
async def lampa_sources():
    configured = await list_sources()
    return {"sources": configured["sources"] + [{"id": "internet-archive", "name": "Internet Archive — открытая лицензия / торренты"}]}

@app.get("/api/lampa/search")
async def lampa_search(q: str = Query(min_length=1, max_length=200)):
    custom, archive = await asyncio.gather(
        search_sources(q),
        asyncio.to_thread(archive_search_sync, q),
        return_exceptions=True,
    )
    groups = []
    errors = []
    if isinstance(custom, Exception):
        errors.append({"source_id": "custom", "message": "Дополнительные источники временно недоступны"})
    else:
        groups.extend(custom.get("results", []))
        errors.extend(custom.get("errors", []))
    if isinstance(archive, Exception):
        logger.warning("Internet Archive search failed: %s", archive)
        errors.append({"source_id": "internet-archive", "message": "Internet Archive временно недоступен"})
    else:
        groups.append({"source_id": "internet-archive", "source_name": "Internet Archive — открытая лицензия", "results": archive})
    return {"query": q, "results": groups, "errors": errors}
