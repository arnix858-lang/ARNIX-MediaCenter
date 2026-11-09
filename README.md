# Медиацентр

Стартовый Android-проект на Kotlin + Jetpack Compose для смартфонов, планшетов и Android TV.

## Сборка APK через GitHub

1. Откройте вкладку **Actions**.
2. Выберите **Build MediaCenter APK** и запустите workflow.
3. Скачайте `MediaCenter-debug` из Artifacts.

APK: `app-debug.apk`.

## Локальная сборка
Требуются JDK 17, Android SDK и Gradle 8.9:

```bash
gradle :app:assembleDebug
```

Приложение регистрируется как Android launcher и Android TV launcher. Главный экран адаптирован под сенсорное управление и D-pad. Переходы в разделы добавим следующим этапом.

## HDRezka playback connector (optional backend)

[Deploy HDRezka connector to Render](https://render.com/deploy?repo=https://github.com/arnix858-lang/ARNIX-MediaCenter)

The Android client calls `/api/playback` to show available translations and episodes, then opens the selected stream in the built-in ARNIX Media3 player. The connector is an **unofficial** third-party integration and is not an HDRezka-supported public API. Use it only where HDRezka's current terms and applicable law permit third-party clients and playback.

### Deploy the connector

1. Open the repository in Render and create a new **Blueprint** from this GitHub repository.
2. Render reads the root `render.yaml` and creates the `arnix-hdrezka-api` web service.
3. Wait until the service is live, then open `https://arnix-hdrezka-api.onrender.com/health`.
4. The Android app is configured for that hostname. If Render assigns a different hostname, update `PLAYBACK_API_BASE` in `PlaybackScreens.kt` and rebuild the APK.

The connector uses the ordinary HTTP client mode (`impersonate=False)); it does not implement Cloudflare challenge bypasses, geo-block circumvention, account sharing, or premium restrictions. HDRezka may deny third-party requests or change its site at any time. A Render free service may sleep when idle, so the first request can take longer.


## Native Lampa-compatible source bridge

The backend exposes a normalized JSON contract for the native Android UI:

- `GET /api/lampa/sources` — configured adapters.
- `GET /api/lampa/search?q=...` — normalized search results grouped by source.
- `GET /health` — service status and configured source count.

Configure adapters on the backend using the `ARNIX_LAMPA_ADAPTERS` environment variable. It is a JSON array; each entry has `id`, `name`, and an HTTPS `search_url` containing the literal `{query}` placeholder. The remote endpoint must return either a JSON array or an object with a `results` array. Each item should contain `id`, `title`, and optional `original_title`, `year`, `poster`, and `description`.

Example (replace the host with an adapter endpoint you operate):
```json
[
  {
    "id": "my-source",
    "name": "My source",
    "search_url": "https://media.example/api/search?q={query}"
  }
]
```

**Compatibility boundary:** this bridge does not execute arbitrary Lampa JavaScript plugins and does not claim full compatibility with every Lampa plugin. Lampa UI plugins require the original Lampa runtime; native ARNIX screens consume only normalized source data. Add adapters only for sources you are authorized to use. Direct HLS/DASH/MP4 links can be handed to the native Media3 player; DRM-protected streams are not bypassed.
