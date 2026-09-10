# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Fox Control (`com.andrew.foxcontrol`) is a private Android parental-control app that tracks per-app screen time on a child's phone, enforces daily limits (global and per-app), shows alerts when limits are exceeded, and can email usage reports to a parent. Not published to Google Play; distributed as a debug APK directly to users (see `INSTALL_GUIDE.md`).

- Min SDK 26, target/compile SDK 35, Kotlin, Jetpack Compose + Material 3, MVVM + Clean Architecture (`data` / `domain` / `ui` / `core` / `di`).

## Common commands

```bash
# Build debug APK (output: app/build/outputs/apk/debug/app-debug_<versionName>.apk)
./gradlew :app:assembleDebug

# Build release APK (minified/shrunk, uses app/proguard-rules.pro)
./gradlew :app:assembleRelease

# Unit tests (JVM, app/src/test)
./gradlew :app:testDebugUnitTest

# Single test class
./gradlew :app:testDebugUnitTest --tests "com.andrew.foxcontrol.core.vendor.VendorHelperTest"

# Single test method
./gradlew :app:testDebugUnitTest --tests "com.andrew.foxcontrol.core.vendor.VendorHelperTest.vendorEnumHasAllValues"

# Instrumented tests (app/src/androidTest, needs connected device/emulator)
./gradlew :app:connectedDebugAndroidTest

# Install debug build to a connected device
./gradlew :app:installDebug
```

Bump `versionCode`/`versionName` in `app/build.gradle.kts` before cutting a build meant for distribution — `INSTALL_GUIDE.md` and the debug APK filename both key off `versionName`.

## Architecture

### Layering
- **`data/local`** — Room database (`AppDatabase`, version 3) with DAOs (`data/local/dao`) and entities (`data/local/entity`). Schema changes require bumping `AppDatabase.DATABASE_VERSION` **and** adding a `Migration` object in `Migrations.kt`, registered via `.addMigrations(...)` in `di/AppModule.kt`. The DB also has `.fallbackToDestructiveMigration()` as a safety net — don't rely on it silently wiping user data when a migration is actually needed.
- **`data/repository`** — `UsageStatsRepositoryImpl` is the central repository: session/heartbeat writes, daily/weekly usage aggregation, limit checks, category resolution. It's bound to the `domain.repository.UsageStatsRepository` interface via `di/RepositoryModule.kt` (only a subset of its methods are on the interface; tracking/heartbeat/limit-setting methods are called through the concrete `UsageStatsRepositoryImpl` type directly, e.g. from `TrackingJob`/`AlertManager`).
- **`domain`** — plain models (`UsageStats`, `DailyUsageStats`, `WeeklyUsageStats`) and the repository interface.
- **`ui`** — one package per screen (`home`, `onboarding`, `settings`, `appdetail`, `vendor`, `debug`), each with a Compose `Screen` + `ViewModel`. Navigation is centralized in `ui/navigation/AppNavGraph.kt` / `Screen.kt`.
- **`core`** — the app's actual background/business logic, split by concern:
  - `core/tracking` — `TrackingForegroundService` (foreground service, `START_STICKY`) owns a `TrackingJob`, which runs two `java.util.Timer`s (heartbeat + usage-stats polling, both every 60s) and polls `UsageStatsManager.queryUsageStats` to compute per-package foreground-time deltas since the last poll, persisting sessions via the repository. `TrackingLogStorage` is an in-memory ring-buffer logger (surfaced in `ui/debug/DebugScreen`) used throughout tracking/repo code instead of (or alongside) `Log`. `BootReceiver` restarts the service on `BOOT_COMPLETED`.
  - `core/alerts` — `AlertManager` checks global/app limits after each poll and triggers `OverlayAlertService` (system-alert-window overlay) plus a fallback notification via `NotificationHelper`, with a 1-minute per-package alert cooldown.
  - `core/email` — `SendReportWorker` (WorkManager) + `ReportAlarmReceiver` (AlarmManager, scheduled via `di/EmailScheduler.kt`) send daily usage reports through `EmailSender` (Jakarta Mail/Angus, SMTP credentials stored per `EmailSettingsEntity`).
  - `core/permissions` — `PermissionHelper`/`PermissionMonitor`/`PermissionRepository` check and drive the required runtime permissions (usage-stats access, overlay, notifications, battery-optimization exemption); `TrackingForegroundService` polls `PermissionMonitor` every minute and restarts itself if a critical permission (usage stats or overlay) is lost.
  - `core/vendor` — `VendorHelper` maps `Build.BRAND` to OEM-specific "allow autostart / disable battery optimization" instructions (Xiaomi, Samsung, Huawei, Honor, Oppo, Vivo, OnePlus, Unknown), shown in `ui/vendor/VendorInstructionsScreen`. `VendorHelper.getVendor()` reads `Build.BRAND`, which is unavailable in plain JVM unit tests — `VendorHelperTest` only tests the enum/data-structure shape, not brand detection, for that reason.
- **`di`** — Hilt modules. `AppModule` provides the Room DB and all DAOs; `RepositoryModule` binds the one repository interface that exists.

### Notable patterns to preserve when touching this code
- Background timer/coroutine work in `TrackingJob`/`TrackingForegroundService` uses `GlobalScope.launch` (`@OptIn(DelicateCoroutinesApi::class)`) rather than a service-scoped `CoroutineScope` — intentional given the service's lifecycle, but be aware when adding new async work here.
- `TrackingLogStorage.add(tag, message)` is the de facto diagnostic log for the tracking/repo pipeline (visible in-app via the Debug screen) — prefer it over plain `Log.d` when adding to that pipeline so issues are diagnosable from a shipped debug build without logcat access.
- Private settings (limits, SMTP credentials, password) sit behind a password gate (`ui/settings/PrivateSettingsScreen`) separate from public settings (`ui/settings/SettingsScreen`); default password is `12345` per `INSTALL_GUIDE.md` and is expected to be changed on first setup.

## Native language
- **Russian**
- Общение с пользователем на русском, даже если задача ставиться на английском.

## Важно:
- Сборка apk данного проекта будет на другом ПК
- В основном - данном проекте твоя роль консультанта.
- Вноси изиенение в код только по запросу пользователя.
