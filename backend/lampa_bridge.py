"""ARNIX native bridge for explicitly configured Lampa-compatible JSON adapters.

This is an adapter protocol, not an embedded copy of the Lampa JavaScript runtime.
Only HTTPS JSON endpoints configured by the server are queried.
"""
import asyncio
import json
import logging
import os
from typing import Any
from urllib.parse import urlparse

from urllib.request import Request, urlopen
from urllib.error import URLError, HTTPError

log = logging.getLogger("arnix.lampa")

TIMEOUT_SECONDS = 12
MAX_RESPONSE_BYTES = 2 * 1024 * 1024


def _load_adapters() -> list[dict[str, Any]]:
    raw = os.getenv("ARNIX_LAMPA_ADAPTERS", "[]")
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        log.exception("ARNIX_LAMPA_ADAPTERS is not valid JSON")
        return []
    if not isinstance(data, list):
        return []
    result = []
    for entry in data:
        if not isinstance(entry, dict):
            continue
        source_id = str(entry.get("id", "")).strip()
        name = str(entry.get("name", source_id)).strip()
        search_url = str(entry.get("search_url", "")).strip()
        if not source_id or not search_url:
            continue
        parsed = urlparse(search_url)
        if parsed.scheme != "https" or not parsed.hostname:
            log.warning("Ignoring adapter %s: search_url must be HTTPS", source_id)
            continue
        result.append({"id": source_id, "name": name, "search_url": search_url})
    return result


def _request_json(url: str) -> Any:
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.hostname:
        raise ValueError("Only HTTPS adapter URLs are allowed")
    req = Request(url, headers={"Accept": "application/json", "User-Agent": "ARNIX-MediaCenter/1.0"})
    with urlopen(req, timeout=TIMEOUT_SECONDS) as response:
        if response.status != 200:
            raise RuntimeError(f"Adapter returned HTTP {response.status}")
        body = response.read(MAX_RESPONSE_BYTES + 1)
    if len(body) > MAX_RESPONSE_BYTES:
        raise ValueError("Adapter response is too large")
    return json.loads(body.decode("utf-8"))


def _search_sync(adapter: dict[str, Any], query: str) -> dict[str, Any]:
    from urllib.parse import quote
    url = adapter["search_url"].replace("{query}", quote(query, safe=""))
    payload = _request_json(url)
    # Adapter contract: either {results:[...]} or a top-level array.
    rows = payload.get("results", []) if isinstance(payload, dict) else payload
    if not isinstance(rows, list):
        rows = []
    normalized = []
    for row in rows[:50]:
        if not isinstance(row, dict):
            continue
        stream_url = str(row.get("stream_url") or row.get("play_url") or "").strip()
        parsed_stream = urlparse(stream_url) if stream_url else None
        # Only explicit direct HTTPS media URLs are exposed to the native player.
        if parsed_stream and (parsed_stream.scheme != "https" or not parsed_stream.hostname):
            stream_url = ""
        normalized.append({
            "id": str(row.get("id") or row.get("url") or ""),
            "title": str(row.get("title") or row.get("name") or "Без названия"),
            "original_title": row.get("original_title") or row.get("original_name"),
            "year": row.get("year"),
            "poster": row.get("poster") or row.get("poster_url"),
            "description": row.get("description") or row.get("overview"),
            "stream_url": stream_url or None,
            "stream_type": str(row.get("stream_type") or "").lower() or None,
            "details": row,
        })
    return {"source_id": adapter["id"], "source_name": adapter["name"], "results": normalized}


async def list_sources() -> dict[str, Any]:
    adapters = _load_adapters()
    return {"sources": [{"id": a["id"], "name": a["name"]} for a in adapters]}


async def search_sources(query: str) -> dict[str, Any]:
    if not query.strip():
        return {"query": query, "results": [], "errors": []}
    adapters = _load_adapters()
    tasks = [asyncio.to_thread(_search_sync, adapter, query.strip()) for adapter in adapters]
    settled = await asyncio.gather(*tasks, return_exceptions=True)
    results, errors = [], []
    for adapter, item in zip(adapters, settled):
        if isinstance(item, Exception):
            log.warning("Adapter %s failed: %s", adapter["id"], item)
            errors.append({"source_id": adapter["id"], "message": "Источник временно недоступен"})
        else:
            results.append(item)
    return {"query": query, "results": results, "errors": errors}
