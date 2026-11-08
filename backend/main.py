import os
import logging
import traceback
from difflib import SequenceMatcher
from urllib.parse import urlencode, urlparse

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import RedirectResponse
from hdrezka import HDRezkaClient

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("arnix.hdrezka")
HDREZKA_HOST = os.getenv("HDREZKA_HOST", "https://rezka.ag/").rstrip("/") + "/"

app = FastAPI(title="ARNIX MediaCenter · HDRezka connector", version="1.0.1")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)


@app.get("/health")
async def health():
    return {"ok": True, "provider": "HDRezka", "mode": "unofficial-client"}


def score_title(query: str, candidate: str) -> float:
    return SequenceMatcher(None, query.casefold().strip(), candidate.casefold().strip()).ratio()


@app.get("/api/playback")
async def playback(
    title: str = Query(min_length=1, max_length=180),
    media_type: str = Query(default="movie", alias="type", pattern="^(movie|tv)$"),
):
    """Find a title and expose its available translation/episode choices."""
    try:
        async with HDRezkaClient(host=HDREZKA_HOST, impersonate=False) as client:
            search = client.search(title)
            items = await search.get_page(1)
            if not items:
                raise HTTPException(status_code=404, detail="На HDRezka ничего не найдено")
            ranked = sorted(items, key=lambda item: score_title(title, item.name), reverse=True)
            player = None
            item = None
            wanted_type = "tv_series" if media_type == "tv" else "movie"
            for candidate in ranked:
                candidate_player = await client.player(candidate.url)
                if candidate_player.post.type == wanted_type:
                    item, player = candidate, candidate_player
                    break
            if player is None or item is None:
                raise HTTPException(status_code=404, detail="Подходящий фильм или сериал не найден")

            translators = player.post.translators.name_id
            if not translators:
                raise HTTPException(status_code=404, detail="У этого материала нет доступных озвучек")

            result = []
            for name, translator_id in translators.items():
                episodes = []
                if player.post.type == "movie":
                    params = urlencode({
                        "post_url": item.url,
                        "translator_id": translator_id,
                        "kind": "movie",
                    })
                    episodes.append({"number": 1, "title": "Фильм", "url": f"/api/stream?{params}"})
                else:
                    seasons = await player.get_episodes(translator_id)
                    for season_number, episode_numbers in seasons.items():
                        for episode_number in episode_numbers:
                            params = urlencode({
                                "post_url": item.url,
                                "translator_id": translator_id,
                                "kind": "series",
                                "season": season_number,
                                "episode": episode_number,
                            })
                            episodes.append({
                                "number": episode_number,
                                "title": f"Сезон {season_number} · Серия {episode_number}",
                                "url": f"/api/stream?{params}",
                            })
                if episodes:
                    result.append({"id": str(translator_id), "name": name, "episodes": episodes})

            if not result:
                raise HTTPException(status_code=404, detail="Нет доступных серий или озвучек")
            return {"title": player.post.name, "translations": result}
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("HDRezka playback lookup failed")
        trace = traceback.format_exc().strip().splitlines()
        location = trace[-2] if len(trace) > 1 else "unknown location"
        raise HTTPException(
            status_code=502,
            detail=f"HDRezka connector error: {exc.__class__.__name__}: {exc}; at {location}",
        ) from exc


@app.get("/api/stream")
async def stream(
    post_url: str = Query(min_length=8, max_length=1000),
    translator_id: int = Query(ge=0),
    kind: str = Query(pattern="^(movie|series)$"),
    season: int = Query(default=1, ge=1, le=100),
    episode: int = Query(default=1, ge=1, le=1000),
):
    """Resolve a selected voice/episode and redirect the player to its HLS URL."""
    parsed = urlparse(post_url)
    allowed_host = urlparse(HDREZKA_HOST).hostname
    if parsed.scheme != "https" or parsed.hostname != allowed_host:
        raise HTTPException(status_code=400, detail="Недопустимый адрес материала")
    try:
        async with HDRezkaClient(host=HDREZKA_HOST, impersonate=False) as client:
            player = await client.player(post_url)
            if kind == "movie":
                media = await player.get_stream(translator_id)
            else:
                media = await player.get_stream(season, episode, translator_id)
            sources = media.video.last_url
            if not sources:
                raise HTTPException(status_code=404, detail="Видеопоток недоступен")
            resolved = str(await sources[-1])
            if not resolved.startswith("https://"):
                raise HTTPException(status_code=502, detail="Источник вернул неподдерживаемую ссылку")
            return RedirectResponse(resolved, status_code=307)
    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("HDRezka stream resolution failed")
        raise HTTPException(
            status_code=502,
            detail=f"Не удалось получить поток: {exc.__class__.__name__}: {exc}",
        ) from exc
