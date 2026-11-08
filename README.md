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