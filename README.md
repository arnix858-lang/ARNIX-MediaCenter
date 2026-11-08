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
