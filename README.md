# Fox Control - Parental Control App
> Приватное Android-приложение для мониторинга времени использования смартфона ребёнком.

## О проекте
- **Пакет**: `com.andrew.foxcontrol`
- **Минимальный SDK**: 26 (Android 8.0)
- **Целевой SDK**: 35 (Android 15)
- **Архитектура**: MVVM + Clean Architecture
- **Стек**: Kotlin, Jetpack Compose, Material 3, Hilt, Room, Navigation Compose

## Структура проекта
```
foxControl/
├── app/
│   ├── src/main/java/com/andrew/foxcontrol/
│   │   ├── data/          # Data layer (Room, repositories)
│   │   ├── domain/        # Domain layer (use cases, models)
│   │   ├── ui/            # Presentation layer (Compose screens)
│   │   │   ├── home/      # Главный экран
│   │   │   ├── onboarding/ # Онбординг и разрешения
│   │   │   ├── settings/  # Настройки
│   │   │   └── theme/     # Тема приложения
│   │   ├── di/            # Hilt dependency injection
│   │   └── core/          # Ядро (сервисы, утилиты)
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## Сборка
```bash
./gradlew :app:assembleDebug
```

APK будет доступен в `app/build/outputs/apk/debug/app-debug_0.1.0.apk`

## Текущий статус
- [x] Базовая структура проекта (Milestone 0)
- [ ] Онбординг и разрешения (Milestone 1)
- [ ] Ядро трекинга (Milestone 2)
- [ ] Главный экран (Milestone 3)
- [ ] Лимиты и оповещения (Milestone 4)
- [ ] Настройки (Milestone 5)
- [ ] Email-отчёты (Milestone 6)

## Лицензия
Приватное приложение, не публикуется в Google Play.
