import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("arnix.mediacenter")

app = FastAPI(
    title="ARNIX MediaCenter API",
    description="Minimal service health endpoint. Media providers are managed by client-side plugins.",
    version="5.0.0",
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
        "engine": "ARNIX MediaCenter",
        "providers": ["Lampa-compatible plugins"],
        "playback": "native Media3 player for direct supported streams",
    }
