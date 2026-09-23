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
- **`data/local`** — Room database `foxcontrol_db` (`AppDatabase`, **version 4** — the `version` in the `@Database` annotation is the source of truth; the `AppDatabase.DATABASE_VERSION = 3` constant is stale and unused). DAOs in `data/local/dao`, entities in `data/local/entity`. Schema changes require bumping `@Database(version = …)` **and** adding a `Migration` in `Migrations.kt` (currently `MIGRATION_2_3`, `MIGRATION_3_4`), registered via `.addMigrations(...)` in `di/AppModule.kt`. `.fallbackToDestructiveMigration()` is a safety net only — don't rely on it silently wiping user data. Note: `MIGRATION_2_3` does not match the current entities (see `_docs/bugs_plan.md`, B-3) — intentionally left as is: all devices in use are already on schema v3+ (DB v3 has shipped since `1.2.9`, the first commit in git), so the 2→3 path is never exercised. Don't rely on it; any new migration must be written against the exported/current schema. `exportSchema = true` but no `room.schemaLocation` is configured.
- **`data/repository`**:
  - `UsageStatsRepositoryImpl` — central repository: session/heartbeat writes, daily/weekly aggregation, category resolution, hourly buckets, limits, alert logs, debug info. Bound to `domain.repository.UsageStatsRepository` via `di/RepositoryModule.kt`; all consumers use the interface (only `setGlobalLimit` is still Impl-only). `getDailyUsage` groups sessions by package (name from the latest session) and returns full data; apps with < 60 s in the period are hidden only in UI/email lists via `domain/usecase/UsageListFilter` — totals and limit checks always use all sessions.
  - `EmailRepository` — email recipients, email settings (Room key/value table `email_settings`, keys in `EmailSettingsKeys` in `EmailSettingsEntity.kt`, though callers currently use string literals) and the report send log.
  - `UserRepository` — single user row in Room (name, avatar) + DataStore `settings` (password hash, default `"12345".hashCode()`).
  - `EmailSettingsRepository.kt` is **dead code** (DataStore-based, never instantiated, declared in package `ui.settings`).
- **`domain`** — plain models (`UsageStats`, `DailyUsageStats`, `WeeklyUsageStats`, `DebugInfo`), the repository interface and `domain/usecase/LimitCalculator` (limit rules: alerts compare milliseconds, Home/email compare floored minutes — both kept as they were).
- **`ui`** — one package per screen (`home`, `onboarding`, `settings` [public, private, email settings, email recipients], `appdetail`, `vendor`, `debug`), each with a Compose `Screen` + `ViewModel` (except `vendor`). Shared composables/helpers in `ui/common` (`AppIcon`, `AppUsageHourlyChart`, `formatDuration`). Navigation is centralized in `ui/navigation/AppNavGraph.kt` / `Screen.kt`; start destination is always onboarding, which auto-navigates to Home when all permissions are granted.
- **`core`** — the app's actual background/business logic, split by concern:
  - `core/tracking` — `TrackingForegroundService` (foreground service `dataSync`, `START_STICKY`) owns a `TrackingJob`, which runs four `java.util.Timer`s: heartbeat (60 s), usage-stats polling (60 s), email-report check (60 s), data cleanup (12 h). Polling calls `UsageStatsManager.queryUsageStats(INTERVAL_YEARLY, …)` (always has been; see bugs_plan.md B-7) and computes per-package foreground-time deltas since the last poll, persisting sessions via the repository, then triggers `AlertManager`. `DowntimeCalculator` / `AppUsageHourCalculator` are pure functions producing hourly buckets for the 06:00–22:00 window (always relative to *today*). `CategoryResolver` maps `ApplicationInfo.category` to Russian labels stored as data. `TrackingLogStorage` is a file-backed logger (`filesDir/foxcontrol_tracking.log`, trimmed to 1000 lines, with in-memory fallback) surfaced in `ui/debug/DebugScreen`. `BootReceiver` restarts the service on `BOOT_COMPLETED`; `MainActivity` also starts it if critical permissions are granted.
  - `core/alerts` — `AlertManager` checks global/app limits after each poll and triggers `OverlayAlertService` (system-alert-window overlay, auto-hides after 8 s) plus a notification via `NotificationHelper`. Each alert is shown **once per day per package** — deduplicated via the `alert_logs` table (type `"global"` / `"app"`). The global limit is effectively non-functional (nothing persists it; `getGlobalLimitMinutes()` is a stub returning 120).
  - `core/email` — no WorkManager/AlarmManager: `EmailReportSender.checkAndSendIfDue()` is called every minute from `TrackingJob`'s timer, sends when current `HH:mm` equals the configured send time (5-min in-memory dedup), builds a plain-text report and sends it through `EmailSender` (Jakarta Mail/Angus, STARTTLS, one message per recipient). SMTP settings live in Room `email_settings`; defaults are in `core/email/EmailDefaults.kt`: host/port/send time in code, login / sender / app password from `local.properties` (`SMTP_LOGIN`, `SMTP_FROM`, `SMTP_APP_PASSWORD`) → `BuildConfig`, see `_docs/build_secrets.md`. The report text is built by `EmailReportBuilder` (pure, golden-tested); `EmailSender` sends one message per recipient over a single SMTP connection. Results are written to `report_send_log`.
  - `core/maintenance` — `DataCleanupManager.purgeOldData()` deletes sessions, heartbeats, alert logs and report logs older than 7 days (run by `TrackingJob` every 12 h and manually from the Debug screen). It is not `@Singleton`, so the "last cleanup" shown in Debug is per-instance.
  - `core/permissions` — `PermissionHelper`/`PermissionMonitor`/`PermissionRepository` check and drive the required permissions (usage-stats access, overlay, notifications, battery-optimization exemption); `TrackingForegroundService` polls `PermissionMonitor` every minute and stops/restarts `TrackingJob` if a critical permission (usage stats or overlay) is lost. Onboarding completes only when **all four** are granted.
  - `core/security` — `SecretCipher` / `KeystoreSecretCipher` (AES-GCM, key in Android Keystore). `EmailRepository` stores the SMTP app password as `enc:<iv>:<ciphertext>` and migrates legacy plain values on first read; an undecryptable value (key lost) is treated as not set.
  - `core/util` — `AvatarImageUtil` (resize/save avatar to `filesDir/avatars`), `DrawableExt` (`toBitmap`, in-memory `IconCache`).
  - `core/vendor` — `VendorHelper` maps `Build.BRAND`/`Build.MANUFACTURER` to OEM-specific "allow autostart / disable battery optimization" instructions (Xiaomi, Samsung, Huawei, Honor, Oppo, Vivo, OnePlus, Unknown), shown in `ui/vendor/VendorInstructionsScreen`. `Build.BRAND` is unavailable in plain JVM unit tests — `VendorHelperTest` only tests the enum/data-structure shape, not brand detection, for that reason.
- **`di`** — Hilt modules. `AppModule` provides the Room DB, all DAOs and `PackageManager`; `RepositoryModule` binds the one repository interface that exists.

### Tests
- `app/src/test` — JVM tests. Only `DowntimeCalculatorTest`, `FormatUtilsTest` and the state/data-class tests exercise production code; `AlertManagerLogicTest`, `UsageStatsRepositoryLogicTest` and parts of `OnboardingPermissionsTest` test local copies of logic declared inside the test files (and `AlertManagerLogicTest` tests a 60 s cooldown that does not exist in production).
- `app/src/androidTest/.../HomeScreenUiTest` uses `HiltAndroidRule`/`runTest`, but `hilt-android-testing` and `kotlinx-coroutines-test` are not declared in `app/build.gradle.kts`, so `connectedDebugAndroidTest` does not compile as-is.

### Docs
- `_docs/` — product plan, roadmap, user stories, DB table docs, `refactoring_plan.md` (behavior-preserving refactoring: dead code, performance, duplication, staged plan), `bugs_plan.md` (bugs/stubs B-1…B-18 with fix options and the owner's decision per item — check it before changing behavior in the affected code). `_docs/archive/` — per-feature design notes.

### Notable patterns to preserve when touching this code
- Background timer/coroutine work in `TrackingJob`/`TrackingForegroundService` uses `GlobalScope.launch` (`@OptIn(DelicateCoroutinesApi::class)`) rather than a service-scoped `CoroutineScope` — intentional given the service's lifecycle, but be aware when adding new async work here.
- `TrackingLogStorage.add(tag, message)` is the de facto diagnostic log for the tracking/repo pipeline (visible in-app via the Debug screen) — prefer it over plain `Log.d` when adding to that pipeline so issues are diagnosable from a shipped debug build without logcat access.
- `TrackingLogStorage` tags and message texts are read by the Debug screen (e.g. the `[EmailReport]` filter) — keep them stable when refactoring.
- Private settings (limits, excluded apps, email settings/recipients, vendor instructions, password, clearing tracking data) sit behind a password gate (`ui/settings/PrivateSettingsScreen`) separate from public settings (`ui/settings/SettingsScreen`: name, avatar, Debug screen — the Debug screen is **not** password-protected); default password is `12345` per `INSTALL_GUIDE.md` and is expected to be changed on first setup.
- Date keys in the DB (`usage_sessions.date`, `report_send_log.date`) are `yyyy-MM-dd` formatted with `Locale.getDefault()` — keep the same pattern and locale.

## Native language
- **Russian**
- Общение с пользователем на русском, даже если задача ставиться на английском.

## Важно:
- Сборка apk данного проекта будет на другом ПК
- В основном - данном проекте твоя роль консультанта.
- Вноси изиенение в код только по запросу пользователя.
