import logging
from fastapi import FastAPI, Query
from fastapi.middleware.cors import CORSMiddleware

from lampa_bridge import list_sources, search_sources

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("arnix.mediacenter")

app = FastAPI(
    title="ARNIX MediaCenter API",
    description="Native media API with an explicit Lampa-compatible JSON adapter bridge.",
    version="5.1.0",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)

@app.get("/health")
async def health():
    sources = await list_sources()
    return {
        "ok": True,
        "engine": "ARNIX MediaCenter",
        "bridge": "lampa-compatible-json-adapters",
        "configured_sources": len(sources["sources"]),
        "playback": "native Media3 player for direct supported streams",
    }

@app.get("/api/lampa/sources")
async def lampa_sources():
    """List server-configured source adapters available to the native UI."""
    return await list_sources()

@app.get("/api/lampa/search")
async def lampa_search(q: str = Query(min_length=1, max_length=200)):
    """Search all configured source adapters and return normalized catalog items."""
    return await search_sources(q)
