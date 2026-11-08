import os
import logging
from urllib.parse import urlparse

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import RedirectResponse
from HdRezkaApi import HdRezkaApi
from HdRezkaApi.search import HdRezkaSearch

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("arnix.hdrezka")
HDREZKA_HOST = os.getenv("HDREZKA_HOST", "https://rezka.ag/").rstrip("/") + "/"
ALLOWED_HOST = urlparse(HDREZKA_HOST).hostname

app = FastAPI(title="ARNIX MediaCenter · HDRezka connector", version="2.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
    allow_headers=["*"],
)


@app.get("/health")
async def health():
    return {"ok": True, "provider": "HDRezka", "client": "HdRezkaApi"}


def checked_title(url: str):
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname != ALLOWED_HOST:
        raise HTTPException(status_code=400, detail="Недопустимый адрес материала")
    title = HdRezkaApi(url)
    if not title.ok:
        reason = str(title.exception) if title.exception else "страница не распознана"
        raise HTTPException(status_code=502, detail=f"HDRezka: {reason}")
    return title


def get_translators(title):
    # Library format: {translator_id: {name, premium}}
    return title.translators or {}


@app.get("/api/playback")
async def playback(
    title: str = Query(min_length=1, max_length=180),
    media_type: str = Query(default="movie", alias="type", pattern="^(movie|tv)$"),
):
    """Search HDRezka and return voice/episode choices."""
    try:
        search = HdRezkaSearch(HDREZKA_HOST)
        found = search(title)
        if not found:
            raise HTTPException(status_code=404, detail="На HDRezka ничего не найдено")

        wanted_type = "tv_series" if media_type == "tv" else "movie"
        selected = None
        parsed_errors = []
        for entry in found[:10]:
            try:
                candidate = checked_title(entry["url"])
                if str(candidate.type) == wanted_type:
                    selected = (entry, candidate)
                    break
            except HTTPException as exc:
                parsed_errors.append(str(exc.detail))
                continue
            except Exception as exc:
                parsed_errors.append(f"{exc.__class__.__name__}: {exc}")
                logger.warning("HDRezka parse failed for %s: %s", entry.get("url"), exc)
                continue

        if selected is None:
            if parsed_errors:
                raise HTTPException(
                    status_code=502,
                    detail="Найденные страницы не удалось открыть: " + " | ".join(parsed_errors[:2]),
                )
            raise HTTPException(status_code=404, detail="Подходящий фильм или сериал не найден")

        entry, item = selected
        translators = get_translators(item)
        if not translators:
            raise HTTPException(status_code=404, detail="У материала нет доступных озвучек")

        translations = []
        for translator_id, translator in translators.items():
            translator_name = translator.get("name", str(translator_id)) if isinstance(translator, dict) else str(translator)
            episodes = []
            if wanted_type == "movie":
                episodes.append({
                    "number": 1,
                    "title": "Фильм",
                    "url": "/api/stream",
                })
            else:
                info = (item.seriesInfo or {}).get(translator_id)
                if info is None:
                    info = (item.seriesInfo or {}).get(str(translator_id))
                if not info:
                    continue
                for season_number, episode_numbers in (info.get("episodes") or {}).items():
                    for episode_number in episode_numbers:
                        episodes.append({
                            "number": int(episode_number),
                            "season": int(season_number),
                            "title": f"Сезон {season_number} · Серия {episode_number}",
                            "url": "/api/stream",
                        })
            if episodes:
                translations.append({
                    "id": str(translator_id),
                    "name": translator_name,
                    "episodes": episodes,
                    "post_url": entry["url"],
                })

        if not translations:
            raise HTTPException(status_code=404, detail="Не удалось получить серии или озвучки")
        return {"title": item.name, "translations": translations, "post_url": entry["url"]}

    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("HDRezka search failed")
        raise HTTPException(
            status_code=502,
            detail=f"HdRezkaApi search error: {exc.__class__.__name__}: {exc}",
        ) from exc


@app.get("/api/stream")
async def stream(
    post_url: str = Query(min_length=8, max_length=1000),
    translator_id: int = Query(ge=0),
    kind: str = Query(pattern="^(movie|series)$"),
    season: int = Query(default=1, ge=1, le=100),
    episode: int = Query(default=1, ge=1, le=1000),
):
    """Resolve a stream using HdRezkaApi and redirect to its media URL."""
    try:
        item = checked_title(post_url)
        if kind == "movie":
            media = item.getStream(translation=str(translator_id))
        else:
            media = item.getStream(str(season), str(episode), translation=str(translator_id))

        videos = getattr(media, "videos", None) or {}
        if not videos:
            raise HTTPException(status_code=404, detail="HDRezka не вернула видеопотоки")

        quality_order = ["1080p Ultra", "1080p", "720p", "480p", "360p"]
        selected_urls = None
        for quality in quality_order:
            for key, urls in videos.items():
                if quality.lower() in str(key).lower() and urls:
                    selected_urls = urls
                    break
            if selected_urls:
                break
        if not selected_urls:
            selected_urls = next((urls for urls in videos.values() if urls), None)
        if not selected_urls:
            raise HTTPException(status_code=404, detail="Ссылки на видео отсутствуют")

        resolved = selected_urls[0] if isinstance(selected_urls, (list, tuple)) else selected_urls
        parsed = urlparse(str(resolved))
        if parsed.scheme not in ("https", "http"):
            raise HTTPException(status_code=502, detail="Источник вернул некорректную ссылку")
        return RedirectResponse(str(resolved), status_code=307)

    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("HDRezka stream resolution failed")
        raise HTTPException(
            status_code=502,
            detail=f"HdRezkaApi stream error: {exc.__class__.__name__}: {exc}",
        ) from exc
