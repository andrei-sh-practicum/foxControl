# Fox Control — план рефакторинга

> Дата анализа: 2026-09-23 · версия кода: `1.5.0` (versionCode 44), коммит `bf44269`
> Объём: ~8.4 тыс. строк Kotlin в `app/src/main` (54 файла) и ~1.3 тыс. строк тестов.

## 0. Принципы

1. **Логика и функциональность не меняются.** Найденные баги и заглушки ([B-1](bugs_plan.md#b-1-глобальный-лимит--нерабочая-функция) … [B-18](bugs_plan.md#b-18-побочные-эффекты-фильтра--1-мин-в-getdailyusage)) вынесены в отдельный документ [`bugs_plan.md`](bugs_plan.md): их исправление меняет поведение, поэтому в рефакторинг они **не входят**. Там же — варианты решения и рекомендуемый порядок относительно этапов рефакторинга.
2. Каждый этап из раздела [8](#8-пошаговый-план-этапы) оформляется **отдельным коммитом** (или PR), чтобы его можно было откатить независимо от остальных.
3. Сборка идёт на другом ПК. Поэтому после каждого этапа: `./gradlew :app:assembleDebug` → `./gradlew :app:testDebugUnitTest` → ручной smoke-тест по чек-листу из раздела [9](#9-чек-лист-регрессионной-проверки-после-каждого-этапа).
4. Схема Room (таблицы, колонки, ключи), ключи настроек (`email_settings.key`, DataStore), формат дат `yyyy-MM-dd`, тексты UI и тексты email-отчёта **не меняются**.
5. Пометки о риске: 🟢 механическая правка, 🟡 затрагивает поток выполнения, но поведение сохраняется, 🔴 требует внимательной ручной проверки.

---

## 1. Сводка анализа

> Баги и заглушки (18 шт.) — в [`bugs_plan.md`](bugs_plan.md).

| Категория | Кол-во находок | Главное |
|---|---|---|
| Мёртвый код | 25+ символов / 1 файл | `EmailSettingsRepository.kt` полностью мёртвый; неиспользуемые DAO-методы, события, константы, зависимость `security-crypto` |
| Неоптимальный код | 12 | `AlertManager` загружает все сессии дня `2N+2` раз в минуту; `runBlocking` внутри `suspend`; файловый I/O на main-потоке в Debug-экране |
| Дублирование | 14 | 3 копии SMTP-дефолтов, 2 копии `EmailSettingsKeys`, 3 копии расчёта превышенных лимитов, 3 копии резолва имени приложения, 6 блоков UI скопированы |
| Потокобезопасность | 5 | общий `SimpleDateFormat` в синглтоне, который читают из нескольких потоков; не-volatile `isRunning` |
| Архитектура и слои | 9 | файл в чужом пакете, ViewModel зависят от `Impl`, UI-модели в интерфейсе domain |
| Build, тесты, документация | 8 | `androidTest` не компилируется; unit-тесты проверяют копии логики, а не продакшн-код; `CLAUDE.md` устарел (уже обновлён, 7.4) |

---

## 2. Мёртвый код (удаление безопасно)

Все символы проверены через grep по `app/src/main`. Использований нет или они только транзитивные внутри мёртвого кода.

| ID | Файл / символ | Комментарий |
|---|---|---|
| D-1 | **`data/repository/EmailSettingsRepository.kt` целиком** (`EmailSettingsRepository`, DataStore `email_settings`, `EmailDefaults`, DataStore-версия `EmailSettingsKeys`) | Класс нигде не создаётся. Файл лежит в `data/repository`, но объявлен в пакете `ui.settings`. Настройки email на деле хранятся в Room. DataStore-файл `email_settings` никто не пишет, поэтому удаление ничего не ломает. |
| D-2 | `AppDatabase.DATABASE_VERSION = 3` и его импорт в `AppModule` | Константа не используется и **не совпадает** с реальной версией `4`. |
| D-3 | `AppModule`: `TAG`, импорт `Log` | Не используются. |
| D-4 | `UsageStatsRepositoryImpl`: `getTopApps`, `getUsageForPackage` (к тому же игнорирует `startDate`), `getTopEntertainmentApps`, `upsertTrackedApp`, `upsertTrackedApps`, `getLastHeartbeat` | Вместе с объявлениями в интерфейсе `UsageStatsRepository`. `setGlobalLimit` **не удалять**: нужен для [B-1](bugs_plan.md#b-1-глобальный-лимит--нерабочая-функция) (решение 2026-09-23 — чинить). |
| D-5 | DAO: `AlertLogDao.getAlertLogs/getAlertLogsSync`, `AppLimitDao.getEnabledLimits (Flow)`, `EmailRecipientDao.getRecipientById`, `EmailSettingsDao.deleteSetting`, `ReportSendLogDao.getLastLog/getLogByDate/getRecentLogs` (для B-10 будет новый метод `hasSuccessfulReport`), `ServiceHeartbeatDao.getLastHeartbeatFlow`, `TrackedAppDao.getAllTrackedApps (Flow)/getTopEntertainmentApps/setEntertainment`, `UsageSessionDao.getSessionsByDate (Flow)/getSessionsByPackageSince`, `UserDao.updatePassword` | `GlobalLimitDao.insertGlobalLimit` **не удалять** — понадобится для [B-1](bugs_plan.md#b-1-глобальный-лимит--нерабочая-функция). |
| D-6 | `EmailRepository`: `getLastLog`, `getRecentLogs`, `deleteSetting` | Нет вызовов. |
| D-7 | `UserRepository.onboardingCompleted` (Flow) | Не используется. `isOnboardingCompleted()` и `completeOnboarding()` **больше не мёртвые**: с исправлением [B-6](bugs_plan.md#b-6-онбординг-может-зависнуть) они запоминают выбор «Продолжить без них». |
| D-8 | `PermissionMonitor`: `Callback`, `setCallback`, `callback`, `areCriticalPermissionsGranted`, `areAllPermissionsGranted` | Колбэк никогда не устанавливается. `lastStatus` используется только для `Log.d` при смене статуса, его сохранить. |
| D-9 | `PermissionHelper.areAllPermissionsGranted` | Используется только мёртвым `PermissionMonitor.areAllPermissionsGranted`. |
| D-10 | `TrackingLogStorage`: `contextRef`, перегрузка `add(context, tag, message)` | Перегрузка не вызывается. `contextRef` пишется, но не читается. |
| D-11 | `TrackingJob`: `lastPollEndTime`, мёртвая ветка `lastKnownForeground` / `if (previousPollEnd != null) 0 else 0` | Обе ветки дают `deltaMs = 0` и `continue`. Упрощается до одной строки без изменения результата. |
| D-12 | `DowntimeCalculator.HEARTBEAT_INTERVAL_MS`, `AppUsageHourCalculator.windowStartMs/windowEndMs` | Не используются. |
| D-13 | `AvatarImageUtil.getPreviousAvatarFile()` (всегда `null`) и вызов `oldFile?.delete()`; пустой `.also { … return@also }` в `centerCropToSquare` | Заглушка и no-op блок. |
| D-14 | `OverlayAlertService.ACTION_HIDE` + ветка в `onStartCommand`; параметр `packageName` в `showAlert` | Никто не шлёт `ACTION_HIDE`. `packageName` не используется. Ветку можно оставить как публичный контракт: решить при реализации. |
| D-15 | `SettingsEvent` / `SettingsViewModel.onEvent` / параметр `onEvent` в `SettingsContent` | Единственное событие обрабатывается пустым блоком `// Handled in UI`. |
| D-16 | `PrivateSettingsEvent.OnAppLimitChanged` (+ обработчик) | ⛔ **Не удалять**: по [B-13](bugs_plan.md#b-13-лимит-приложения-нельзя-изменить-или-удалить) (решение — вариант А) событие переиспользуется для редактирования лимита. |
| D-17 | `EmailRecipientsViewModel.toggleActive`: неиспользуемая локальная `entity` | Создаётся и выбрасывается. |
| D-18 | `EmailSettingsViewModel.OnToggleEnabled`: `if/else` с одинаковыми ветками | Заменить на `copy(isEnabled = event.enabled, lastTestResult = null)`. |
| D-19 | `DebugViewModel.triggerCleanup`: лишний `@OptIn(DelicateCoroutinesApi::class)` | GlobalScope там не используется. |
| D-20 | `CategoryResolver`: импорт `javax.inject.Inject` | Не используется. |
| D-21 | Неиспользуемые импорты в UI | `HomeScreen` (`Divider`, `offset`, `background`, `fillMaxHeight`, `Box`), `DebugScreen` (`Color`, `FontWeight`), `PrivateSettingsScreen` (`R`, `Lock`, `Spacer`, `Divider` — используются по FQN), `AppIcon` (`hiltViewModel`), `AppDetailScreen` (`java.util.*`), `OnboardingScreen` (`Toast`, `CheckCircle`, неиспользуемая переменная `context`), `EmailRecipientsScreen` (`Checkbox`, `Button`, `Person`). Список ориентировочный: удалять через *Optimize Imports* в IDE. |
| D-22 | `Color.kt`: `md_theme_*_tertiary`, `md_theme_*_error` | Не передаются в `ColorScheme`. |
| D-23 | `Theme.kt`: `WindowCompat.setDecorFitsSystemWindows(…, false)` | Дублирует `enableEdgeToEdge()` в `MainActivity`. 🟡 проверить визуально. |
| D-24 | Зависимости `androidx.security:security-crypto` (не используется), `navigation-runtime-ktx` (транзитивно есть в `navigation-compose`) | Удалить обе: по [B-15](bugs_plan.md#b-15-smtp-пароль-хранится-открытым-текстом) выбрано шифрование ключом Android Keystore напрямую, без `security-crypto`. |
| D-25 | `RepositoryModule` / `TrackingForegroundService`: `Log.d` дублирует `TrackingLogStorage.add` | Оставить на усмотрение: `Log` нужен для logcat. Не удалять, только унифицировать (см. R-5.4). |

---

## 3. Неоптимальный код

| ID | Где | Проблема | Решение (без изменения поведения) |
|---|---|---|---|
| P-1 | `AlertManager.checkAndShowAlerts` | Раз в минуту: `checkGlobalLimit()` → `getDailyUsage()`, затем ещё раз `getDailyUsage()`; на каждый лимит `checkAppLimit()` → `getDailyUsage()` и снова `getDailyUsage()`. Итого **2N+2** выборок всех сессий дня плюс N+1 выборок всех `tracked_apps`. | Один раз загрузить `DailyUsageStats` и лимиты, дальше считать в памяти. Сравнения `> limitMs` и деление на минуты оставить как есть. 🟡 |
| P-2 | `UsageStatsRepositoryImpl.getDailyUsage/getWeeklyUsage` | Для категорий грузится **вся** таблица `tracked_apps`. | Допустимо (таблица маленькая), но вынести в приватный `loadCategoryMap()` (дубль, см. DUP-5). По желанию: `SELECT packageName, category`. 🟢 |
| P-3 | `UsageStatsRepositoryImpl.getAppLimits/getAppLimitsSync` | `runBlocking { … }` внутри `suspend fun`. При вызове из `viewModelScope` (Main) UI-поток блокируется на время запроса Room, это риск ANR. | Убрать `runBlocking`, вызывать DAO напрямую. `getAppLimitsSync` слить с `getAppLimits`. 🟢 |
| P-4 | `UsageStatsRepositoryImpl.trackUsageSession` | `CategoryResolver.resolve()` (IPC в PackageManager) вызывается на **каждую** запись сессии, а результат нужен только при `existingApp == null`. | Вызывать резолв внутри ветки `if (existingApp == null)`. 🟢 |
| P-5 | `TrackingJob.getAppName` | `getApplicationInfo(pkg, GET_META_DATA)`: флаг лишний (метаданные грузятся зря), вызов делается каждую минуту для каждого активного пакета. | Флаг `0`. Опционально — кэш `ConcurrentHashMap<String,String>` на время жизни джоба. 🟢 |
| P-6 | `EmailReportSender.checkAndSendIfDue` | До 10 отдельных `runBlocking { getSetting(...) }` подряд. | Один `runBlocking { emailRepository.getAllSettings() }` и дальше чтение из `Map`. Порядок проверок и тексты логов сохранить. 🟡 |
| P-7 | `EmailSender.sendBulkEmail` | На каждого получателя новый `Session` и новое SMTP-соединение (`Transport.send`). | Один `Session`, один `Transport.connect()`, `sendMessage` на каждого получателя. Письма по-прежнему уходят отдельно каждому, результат для каждого отдельный. 🟡 проверить на реальном SMTP. |
| P-8 | `DebugScreen` (`TabLog`, `TabPermissions`) | `getLogContent()`, `getEmailSchedulerLogs()`, `getPermissionInfo()` вызываются **прямо в composition**, то есть файловый I/O (до 1 МБ) и системные вызовы идут на main-потоке при каждой рекомпозиции. Лог читается дважды. | Перенести в `DebugViewModel` (StateFlow, `Dispatchers.IO`), грузить при открытии вкладки. 🟡 |
| P-9 | `TrackingLogStorage.addInternal` | `SimpleDateFormat` создаётся на каждую строку. `file.appendText` открывает и закрывает файл на каждую запись. | Форматтер `DateTimeFormatter` (потокобезопасный, создаётся один раз). Append оставить (надёжнее при падениях). 🟢 |
| P-10 | `TrackingLogStorage.getLogStats` | Повторно читает весь файл через `getAllLogs()`. | Допустимо, вызывается редко. Можно отдавать уже прочитанные строки. 🟢 |
| P-11 | `DrawableExt.IconCache` | Неограниченный `ConcurrentHashMap<String, Bitmap>`. | `android.util.LruCache` с лимитом по байтам. 🟢 |
| P-12 | `HomeScreen.AppUsageCard`, `AppDetailScreen` | Для каждой карточки при каждом входе в экран идёт `PackageManager.getApplicationInfo` + `getApplicationLabel` на IO. `items(apps)` без `key`. | Общий резолвер с кэшем (см. DUP-3); `items(apps, key = { it.packageName })`. Отображаемое имя не меняется. 🟢 |
| P-13 | `AppUsageHourCalculator` / `DowntimeCalculator` | `Calendar.getInstance()` на каждый час; `toMutableList().sortedBy/sorted()` — лишняя копия. В `AppUsageHourCalculator` сортировка вообще не нужна (сумма пересечений от порядка не зависит). | Вычислить `dayStart` один раз, `hourStart = dayStart + h * 3_600_000` — только если DST-поведение подтверждено тестом (🔴 в дни перевода часов результат может отличаться). Безопасная часть — убрать лишние копии. |

---

## 4. Дублирование

| ID | Что дублируется | Где | Решение |
|---|---|---|---|
| DUP-1 | SMTP-дефолты (Brevo host/port/login/from) + чтение `BuildConfig.SMTP_APP_PASSWORD_DEFAULT` **через reflection** | `BrevoDefaults` (`EmailSettingsViewModel.kt`), `EmailDefaults` (мёртвый), `EmailSettingsScreen` (плейсхолдеры) | Один `object EmailDefaults` в `core/email`. Reflection заменить прямым `BuildConfig.SMTP_APP_PASSWORD_DEFAULT` (значение то же). |
| DUP-2 | Ключи настроек email | `EmailSettingsKeys` в `EmailSettingsEntity.kt` **не используется**; вместо него строковые литералы `"smtp_host"`… в `EmailSettingsViewModel` и `EmailReportSender` (≈20 мест) | Использовать `EmailSettingsKeys.*`. Значения ключей совпадают, данные не мигрируют. |
| DUP-3 | Резолв метки приложения через PackageManager | `TrackingJob.getAppName`, `HomeScreen.AppUsageCard`, `AppDetailScreen` | `core/util/AppLabelResolver` с одинаковым fallback на `packageName`. |
| DUP-4 | Расчёт «превышенных лимитов» (минуты, сравнение `>`, `over = total - limit`, сортировка) | `HomeViewModel.computeExceededApps`, `EmailReportSender` (локальный `data class ExceededApp`), частично `AlertManager` | Чистая функция `LimitCalculator.exceededApps(stats, limits)` в `domain`, покрыть unit-тестом **до** переноса. |
| DUP-5 | Заполнение категорий из `tracked_apps` | `getDailyUsage`, `getWeeklyUsage` | Приватная функция `withCategories(apps)`. |
| DUP-6 | Формат даты `yyyy-MM-dd` (`SimpleDateFormat(... Locale.getDefault())`) | 10 мест: репозиторий (×2), `AlertManager` (×2), `EmailReportSender`, `DataCleanupManager`, `HomeViewModel` (×4), `AppDetailViewModel`, `DebugViewModel` | `core/util/DateUtils`: `today()`, `format(date)`, `dayStartMs(date)` на `DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())`. Локаль оставить той же, иначе на локалях с нелатинскими цифрами изменится формат ключа (см. раздел 10). |
| DUP-7 | Начало дня через `Calendar` + 4× `set(...)` | `UsageStatsRepositoryImpl.getServiceDowntimeBuckets`, `AlertManager.wasAlertShownToday` (ещё и `parse(format(Date()))`), `hourStartMs()` в обоих калькуляторах | `DateUtils.dayStartMs()` / `hourStartMs()`. |
| DUP-8 | Fallback «пустые бакеты» `(6..21).map { … }` и окно `6..22` | репозиторий (×3), константы в двух калькуляторах | Общие константы `ChartWindow.START_HOUR/END_HOUR`. |
| DUP-9 | Проверка usage-stats / battery / permission info | `PermissionHelper` и `TrackingLogStorage.getPermissionInfo/isUsageStatsPermissionGranted` | Перенести `getPermissionInfo` в `core/permissions/PermissionDiagnostics`, логгер оставить только логгером. **Логику проверок не менять** (разные методы дают разный результат, см. раздел 11). |
| DUP-10 | `PermissionMonitor.PermissionStatus` ≈ `OnboardingPermissions` + `missingCount` | `PermissionMonitor`, `PermissionHelper` | `PermissionStatus` сделать обёрткой или extension над `OnboardingPermissions`. |
| DUP-11 | Маппинг `EmailRecipientState ↔ EmailRecipientEntity` | `EmailRecipientsViewModel` ×4 | Extension `toEntity()`. |
| DUP-12 | UI: блок «Итого за день/вчера/неделю» ×3, empty-state «Нет данных» ×3 | `HomeScreen.kt:141-351` | Композаблы `UsageSummary(title, totalMs, appCount)` и `EmptyState()`. ~200 строк → ~50. |
| DUP-13 | UI: поле пароля с глазом ×4, dropdown выбора приложения ×2 | `PrivateSettingsScreen.kt` | `PasswordTextField`, `AppPickerDropdown`. |
| DUP-14 | UI: «сколько времени назад» (сек/мин/ч/дн) ×2 | `DebugViewModel` | `formatAgo(ms)`. Тексты сохранить побайтно, включая то, что у heartbeat нет ветки «дн». |

---

## 5. Потокобезопасность и корутины

| ID | Где | Проблема | Решение |
|---|---|---|---|
| T-1 | `UsageStatsRepositoryImpl.dateFormat` (синглтон) | `SimpleDateFormat` не потокобезопасен. Его одновременно используют корутины `GlobalScope` из `TrackingJob` (запись сессий, алерты) и `viewModelScope`. Возможна порча строки даты: сессия запишется с неверным `date`. | `DateTimeFormatter` (immutable), см. DUP-6. Формат тот же. 🟢 |
| T-2 | `DataCleanupManager.dateFormat` (static) | То же. | То же. 🟢 |
| T-3 | `TrackingJob.isRunning` | Пишется из main-потока (`onCreate`/`onDestroy`) и из `GlobalScope` (`restartService`) без `@Volatile`. | `@Volatile` или `AtomicBoolean` с `compareAndSet` в `start()`. 🟢 |
| T-4 | `TrackingJob` | `GlobalScope.launch` внутри `TimerTask`. | Согласно `CLAUDE.md` это осознанное решение, **не меняем**. Допустимо только вынести повторяющийся шаблон `launch { try { … } catch { log(e) } }` в приватный `launchLogged(tag) { }`. 🟢 |
| T-5 | `TrackingForegroundService.startPermissionMonitoring` | `CoroutineScope(Dispatchers.IO)` создаётся «на лету», при этом job отменяется. Работает, но смешано с `GlobalScope` в `restartService`. | Оставить как есть, чтобы не трогать жизненный цикл. Вынести `delay(60_000)` в константу. 🟢 |

---

## 6. Архитектура и структура

| ID | Проблема | Решение |
|---|---|---|
| A-1 | ViewModel зависят от **конкретного** `UsageStatsRepositoryImpl` (`AppDetailViewModel`, `DebugViewModel`), хотя нужные методы есть в интерфейсе (у `AppDetailViewModel` все). | `AppDetailViewModel` → интерфейс `UsageStatsRepository`. Для Debug/Tracking/Alert см. A-2. |
| A-2 | Интерфейс `UsageStatsRepository` покрывает только часть методов; трекинг, heartbeat и алерт-логи вызываются через `Impl`. | Вариант (рекомендуемый, минимальный): добавить в интерфейс недостающие методы (`trackUsageSession`, `recordHeartbeat`, `checkAppLimit`, `getDebugInfo`, `wasAlertShownToday`, `recordAlertLog`) и перевести всех потребителей на интерфейс. Разбиение на 3 репозитория (Usage / Tracking / Limits) — отдельное решение. |
| A-3 | `domain.repository.UsageStatsRepository` импортирует Room-сущности (`TrackedAppEntity`, `AppLimitEntity`, `UsageSessionEntity`) и модели из `core.tracking` (`AppUsageHourBucket`, `DowntimeHourBucket`), местами по FQN. | Минимум — нормальные импорты вместо FQN. Перенос бакетов в `domain.model` — 🟢 (простые data-классы). Отвязку domain от entity пока не делать: объём большой, выгода малая. |
| A-4 | `EmailSettingsRepository.kt` в `data/repository`, но с `package ui.settings`. | Удаляется в D-1. |
| A-5 | `core/email/EmailReportSender` импортирует `ui.common.formatDuration`: core зависит от ui. | Перенести `formatDuration` в `core/util` (или `domain`), в `ui.common` оставить реэкспорт или обновить импорты. |
| A-6 | `NotificationHelper` помечен `@Singleton @Inject`, но `AlertManager` создаёт его через `NotificationHelper(context)` на каждый алерт. | Инжектить в `AlertManager`. 🟢 |
| A-7 | `DebugInfo` (модель) объявлен в файле репозитория, `DateRange`/`UsageStatsSummary` — в файле DAO. | Вынести в `data/local/model` / `domain/model`. 🟢 |
| A-8 | `UsageStatsRepository.kt` (в `data/repository`) содержит класс `UsageStatsRepositoryImpl`, а одноимённый файл есть в `domain/repository`. | Переименовать файл в `UsageStatsRepositoryImpl.kt`. 🟢 |
| A-9 | `CategoryResolver` возвращает русские строки, и они сохраняются в БД как данные. | Не трогать: смена ведёт к миграции данных. Зафиксировать как известное ограничение. |

---

## 7. UI / Compose

| ID | Где | Проблема | Решение |
|---|---|---|---|
| U-1 | `DebugScreen.kt:59` | `var selectedTab by mutableIntStateOf(0)` **без `remember`** (lint `UnrememberedMutableState`). Вкладка сбрасывается на первую при любой рекомпозиции тела `DebugScreen`. | `remember { mutableIntStateOf(0) }`. Задуманное поведение сохраняется. 🟢 |
| U-2 | `DebugScreen.kt:62` | `DisposableEffect(Unit) { load; onDispose {} }`. | `LaunchedEffect(Unit)`. 🟢 |
| U-3 | `PrivateSettingsScreen` (контент), `VendorInstructionsScreen` | Нет `verticalScroll`: на маленьких экранах нижние кнопки («Опасная зона», последние инструкции) уходят за край и недоступны. | Добавить `verticalScroll(rememberScrollState())`. Формально меняет UX, но только в сторону доступности, логика та же. 🟡 **подтвердить с владельцем**. |
| U-4 | `PrivateSettingsScreen.kt` (738 строк) | Один файл: гейт, контент, 4 диалога. | Разнести: `PrivateSettingsGate.kt`, `PrivateSettingsDialogs.kt`; общие `PasswordTextField`, `AppPickerDropdown` (DUP-13). |
| U-5 | `HomeScreen.kt` | Дубли (DUP-12); `HomePeriod.values()` вызывается при каждой рекомпозиции. | `HomePeriod.entries`; `items(key = …)`. |
| U-6 | Везде | FQN внутри кода (`androidx.compose.material3.Button`, `androidx.compose.ui.text.font.FontFamily.Monospace`, `android.util.Log`…) вместо импортов. | Нормальные импорты. 🟢 |
| U-7 | `HomeScreen`, `PrivateSettingsScreen`, `DebugScreen` | Устаревший `Divider` → `HorizontalDivider`. | 🟢 |
| U-8 | `SettingsScreen.kt:210` | Цвет сообщения выбирается через `message.contains("Ошибка")` и `Color.Red`. | Флаг `isError` в `SettingsState`, `colorScheme.error`. Цвет изменится с `Color.Red` на `colorScheme.error`: 🟡 визуально почти тот же, **подтвердить**. |
| U-9 | Все экраны | Все строки захардкожены, в `strings.xml` одна строка. | Перенос в `strings.xml` — отдельная большая, но механическая фаза (опционально, низкий приоритет). Тексты не меняются. |
| U-10 | `AppNavGraph` | Смешаны `Screen.X.route` и `Screen.X` (строковые константы); `navArgument` по FQN при наличии импорта. | Единообразно `Screen.X.route`; хелпер `Screen.AppDetail.createRoute(pkg)`. 🟢 |
| U-11 | `AppDetailViewModel` | `packageName` приходит через `loadAppDetail()` из `LaunchedEffect`. | Можно брать из `SavedStateHandle`. Опционально. |

---

## 8. Пошаговый план (этапы)

Порядок выбран так, чтобы сначала шли самые безопасные изменения, а каждый следующий этап опирался на предыдущий.

### Этап 0 — подготовка (без изменений кода приложения)
- [x] 0.1 ~~Создать ветку~~ — по решению владельца работа идёт прямо в `master`, по коммиту на пункт.
- [ ] 0.2 Починить сборку `androidTest`. `HomeScreenUiTest` использует `HiltAndroidRule` и `runTest`, но в зависимостях нет `hilt-android-testing` и `kotlinx-coroutines-test`, runner не Hilt-овский. Варианты: добавить зависимости и `HiltTestRunner` или удалить нерабочий тест (он всё равно создаёт свои моки, а не проверяет `HomeScreen`). Решение за владельцем.
- [x] 0.3 **Характеризационные тесты перед переносом логики** (JVM, `app/src/test`):
  - *(перенесено в 4.4 — пишется вместе с выносом `LimitCalculator`, т.к. `computeExceededApps` приватный)* `LimitCalculator` / текущий `computeExceededApps`: границы `==limit`, `limit+1`, отсутствие лимита.
  - ✔️ `UsageStatsRepositoryImplTest`: `getDailyUsage` (с моком DAO): порог `59 999 / 60 000` мс, `totalUsageMs` = сумма **отфильтрованных** приложений (текущее поведение с `bf44269`, см. [B-18](bugs_plan.md#b-18-побочные-эффекты-фильтра--1-мин-в-getdailyusage)). При выносе `withCategories()` (DUP-5) и `LimitCalculator` фильтр должен остаться **до** заполнения категорий и расчёта итога; порог вынести в константу `MIN_APP_USAGE_MS = 60_000L`. ⚠️ После исправления B-18 (вариант А) тест обновить: итог и `apps` из репозитория станут полными, фильтр переедет в отображение.
  - `formatDuration`: уже покрыт `FormatUtilsTest` (0, 1 с, 59 мин, часы) — дополнять не нужно.
  - ✔️ `AppUsageHourCalculatorTest`: пересечение сессии с границей часа, будущие часы.
  - *(перенесено в 3.4)* Текст email-отчёта: golden-строка фиксируется при выносе `EmailReportBuilder`.
- [x] 0.4 Переписать или удалить тесты, которые проверяют **копии** логики, а не продакшн-код:
  - `AlertManagerLogicTest` проверяет «cooldown 60 с», которого в коде нет (там раз в сутки через `alert_logs`).
  - `UsageStatsRepositoryLogicTest` проверяет локальные `calculateDailyUsage`/`UsageStatsSummary`, объявленные в самом тесте.
  - `OnboardingPermissionsTest` дублирует `getMissingPermissionCount` приватной копией вместо вызова `PermissionHelper.getMissingPermissionCount`.
  - ✔️ Сделано: `AlertManagerLogicTest` и `UsageStatsRepositoryLogicTest` удалены (заменены `UsageStatsRepositoryImplTest`), `OnboardingPermissionsTest` вызывает `PermissionHelper` + тесты `criticalGranted`. Заодно исправлен устаревший `HomePeriodTest` (в `HomePeriod` три значения, а тест ждал два).
- [x] 0.5 Локальная сборка: Gradle 8.11.1 скачивается во временную папку (в репозитории нет `gradle-wrapper.jar`), `:app:compileDebugKotlin` + `:app:testDebugUnitTest` проходят (101 тест).

### Этап 1 — мёртвый код 🟢
- [x] 1.1 Удалить `EmailSettingsRepository.kt` (D-1).
- [x] 1.2 Удалить неиспользуемые методы репозиториев, интерфейса и DAO (D-4…D-7). `setGlobalLimit` и `insertGlobalLimit` **оставить** ([B-1](bugs_plan.md#b-1-глобальный-лимит--нерабочая-функция)), `OnAppLimitChanged` — тоже (D-16).
- [x] 1.3 `PermissionMonitor`/`PermissionHelper`: удалить мёртвый API (D-8, D-9).
- [x] 1.4 `TrackingLogStorage`: удалить `contextRef` и перегрузку `add(context,…)` (D-10).
- [x] 1.5 `TrackingJob`: схлопнуть мёртвую ветку, удалить `lastPollEndTime` (D-11). **Проверить эквивалентность вручную:** во всех ветках, где `currentForeground < previousTime`, итог — `lastForegroundTime[pkg] = currentForeground` и `continue`.
- [x] 1.6 Мелочи: D-2, D-3, D-12…D-20, D-22. D-14 (`ACTION_HIDE`) оставлен как публичный контракт сервиса, D-16 сохранён для B-13. Дополнительно удалён неиспользуемый `UsageSessionDao.insertSessions` и `ServiceHeartbeatDao.getLastHeartbeat`.
- [x] 1.7 Неиспользуемые импорты удалены по всему `app/src/main` (38 шт., D-21). Замена FQN на импорты (U-6) перенесена в этап 6 — делается вместе с правками этих экранов.
- [x] 1.8 Убрать неиспользуемые зависимости (D-24), включая `security-crypto`.

**Проверка:** сборка, unit-тесты, smoke-тест.

### Этап 2 — общие утилиты и константы 🟢
- [x] 2.1 `core/util/DateUtils` (DUP-6, DUP-7). Заменены все места. **Реализовано на `SimpleDateFormat`, новый экземпляр на вызов, а не на `java.time`:** `DateTimeFormatter` не эквивалентен на экзотических локалях (игнорирует буддийский календарь th_TH и нелатинские цифры), а ключ даты хранится в БД. Гонка T-1/T-2 закрыта, т.к. общих экземпляров больше нет. Покрыто `DateUtilsTest`.
- [x] 2.2 `ChartWindow` — константы окна 6..22 и пустые бакеты (DUP-8).
- [x] 2.3 Перенести `formatDuration` в `core/util` (A-5).
- [x] 2.4 `core/util/AppLabelResolver` (DUP-3, P-5) с тем же fallback. Без кэша: `TrackingJob` должен видеть смену метки приложения, как и раньше. `items(key = …)` из P-12 — в этапе 6.
- [x] 2.5 `IconCache` → `LruCache` (P-11).

### Этап 3 — Email-подсистема 🟡
- [ ] 3.1 `core/email/EmailDefaults`: единый источник дефолтов, прямой `BuildConfig` без reflection (DUP-1).
- [ ] 3.2 Везде `EmailSettingsKeys.*` вместо литералов (DUP-2).
- [ ] 3.3 `EmailReportSender`: одна выборка `getAllSettings()` вместо цепочки `runBlocking` (P-6). **Сохранить порядок проверок и тексты `TrackingLogStorage`**: Debug-экран фильтрует лог по `[EmailReport]`.
- [ ] 3.4 Вынести построение subject/body в чистую функцию `EmailReportBuilder.build(...)`, покрыть golden-тестом из 0.3.
- [ ] 3.5 `EmailSender`: одно SMTP-соединение на рассылку (P-7). 🔴 Ручная проверка: тестовое письмо + отчёт на 2 адреса, один из них заведомо невалидный. Результат по каждому адресу должен совпасть с текущим.
- [ ] 3.6 `EmailRecipientsViewModel`: `toEntity()` (DUP-11), убрать D-17.

### Этап 4 — репозиторий и алерты 🟡
- [ ] 4.1 Убрать `runBlocking` в `getAppLimits` и объединить с `getAppLimitsSync` (P-3).
- [ ] 4.2 `trackUsageSession`: резолв категории только для новых приложений (P-4).
- [ ] 4.3 `withCategories()` (DUP-5, P-2).
- [ ] 4.4 `LimitCalculator` (DUP-4) и перевод на него `HomeViewModel` и `EmailReportSender`.
- [ ] 4.5 `AlertManager`: одна загрузка дня на тик (P-1), инжект `NotificationHelper` (A-6), `dayStartMs` из `DateUtils`. **Сохранить:** порядок «сначала global, потом app-лимиты», типы `"global"`/`"app"` в `alert_logs`, условие `>`, формулу `usedMinutes`. Заглушку `getGlobalLimitMinutes()` в рамках рефакторинга не трогать: её удаляет [B-2](bugs_plan.md#b-2-заглушка-getgloballimitminutes--120) сразу после этапа 4.
- [ ] 4.6 Константы вместо строк `"global"` / `"app"` (`AlertType`).
- [ ] 4.7 Переименовать файл в `UsageStatsRepositoryImpl.kt`, вынести `DebugInfo`/`DateRange`/`UsageStatsSummary` (A-7, A-8).
- [ ] 4.8 Расширить интерфейс и перевести потребителей на него (A-1, A-2). Проверить Hilt-граф: `RepositoryModule` биндит интерфейс, а `Impl` остаётся `@Singleton` → экземпляр один.

### Этап 5 — трекинг и сервис 🟡/🔴
- [ ] 5.1 `TrackingJob.isRunning` → `AtomicBoolean` (T-3).
- [ ] 5.2 `TrackingJob`: приватный `launchLogged(tag) {}` вместо четырёх копий try/catch (T-4). GlobalScope **сохраняется**.
- [ ] 5.3 `queryUsageStats(3, …)` → `UsageStatsManager.INTERVAL_YEARLY` с исправленным комментарием. **Значение не менять** (см. [B-7](bugs_plan.md#b-7-queryusagestats-с-interval_yearly)).
- [ ] 5.4 Унифицировать логирование: хелпер `logE(tag, msg, e)`, который пишет и в `Log`, и в `TrackingLogStorage`. Набор тегов и тексты строк сохранить: от них зависят `getLogStats()` и Debug-экран.
- [ ] 5.5 `TrackingLogStorage.getPermissionInfo` → `core/permissions/PermissionDiagnostics` (DUP-9). Тело перенести **1:1**.
- [ ] 5.6 `PermissionStatus` на базе `OnboardingPermissions` (DUP-10).

**Проверка 🔴:** 30+ минут работы на устройстве. В Debug-экране heartbeat идёт без дыр, сессии пишутся, лимит на приложение срабатывает (overlay + уведомление, один раз за день).

### Этап 6 — UI 🟢/🟡
- [ ] 6.1 `DebugScreen`: `remember` (U-1), `LaunchedEffect` (U-2), вынос I/O в ViewModel (P-8), `formatAgo` (DUP-14).
- [ ] 6.2 `HomeScreen`: `UsageSummary`/`EmptyState` (DUP-12), `entries`, `key` (U-5).
- [ ] 6.3 `PrivateSettingsScreen`: разбиение на файлы, `PasswordTextField`, `AppPickerDropdown` (U-4, DUP-13).
- [ ] 6.4 `Divider` → `HorizontalDivider` (U-7), навигация (U-10).
- [ ] 6.5 *(после подтверждения)* `verticalScroll` (U-3), цвет сообщения (U-8).
- [ ] 6.6 *(опционально)* перенос строк в `strings.xml` (U-9).

### Этап 7 — Build и документация 🟢
- [ ] 7.1 Room: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` при `exportSchema = true`; закоммитить `schemas/…/4.json`. Это база для будущих миграций (например, если B-1 или B-15 потребуют изменений схемы).
- [ ] 7.2 Version catalog (`gradle/libs.versions.toml`) вместо разбросанных версий; выровнять compose-артефакты через BOM (сейчас `ui 1.7.8`, `material-icons-extended 1.7.6`, BOM `2024.09.00` только в `androidTest`). 🟡 не поднимать версии, только перенос.
- [ ] 7.3 `proguard-rules.pro`: правило `-keep class * implements androidx.room.Entity` бессмысленно (`@Entity` — аннотация, а не интерфейс). Добавить keep для Jakarta Mail/Angus (`jakarta.mail.**`, `org.eclipse.angus.**`, SPI-провайдеры в `META-INF`). 🔴 проверить release-сборку отправкой тестового письма, если release вообще используется.
- [x] 7.4 ~~Обновить `CLAUDE.md`~~ (сделано 2026-09-23): БД версии **4**, а не 3; `SendReportWorker`, `ReportAlarmReceiver`, `EmailScheduler`, WorkManager **не существуют** (отчёт шлёт `EmailReportSender` по таймеру из `TrackingJob`); cooldown алертов — раз в сутки на пакет через `alert_logs`, а не 1 минута; добавить `core/maintenance`, `core/util`.

---

## 9. Чек-лист регрессионной проверки (после каждого этапа)

1. Чистая установка: онбординг → выдача разрешений → переход на Home.
2. Установка поверх текущей `1.5.0`: данные сохранились (сессии, лимиты, получатели, пароль).
3. Home: вкладки «Сегодня / Вчера / Неделя», итоги, список, блок превышенных лимитов, график по часам.
4. Детали приложения: время, число сессий, график.
5. Настройки: смена имени, аватара, версия.
6. Приватные настройки: вход с паролем, неверный пароль → ошибка, добавление лимита приложения, исключение и возврат приложения, смена пароля, очистка таблицы трекинга.
7. Email: сохранение настроек, тестовое письмо, выбор времени; при времени отправки = текущая минута + 1 отчёт приходит всем активным получателям.
8. Лимит приложения ставим на 1 мин, используем приложение 2 мин → overlay и уведомление появляются один раз.
9. Debug: все 4 вкладки, «Почистить сейчас», диаграмма простоев.
10. Перезагрузка устройства → сервис поднялся (`BootReceiver`), heartbeat продолжается.

---

## 10. Сознательно НЕ трогаем (риск изменения поведения)

- `GlobalScope` + `java.util.Timer` в `TrackingJob` — решение зафиксировано в `CLAUDE.md`.
- `Locale.getDefault()` в формате ключа даты. `Locale.US` правильнее, но на локалях с нелатинскими цифрами уже записанные даты станут «чужими».
- Разные способы проверки usage-stats разрешения в `PermissionHelper` (AppOps) и в диагностике (`Settings.Secure "usage_stats_accessed"` + AppOps): в диагностике возможен ложный «✓», но это меняет только вывод Debug-экрана. Пусть решает владелец.
- `OverlayAlertService` с `START_STICKY` (после убийства процесса сервис пересоздаётся с `null`-intent и висит без дела). Смена на `START_NOT_STICKY` — изменение поведения, решение за владельцем.
- Русские названия категорий, хранимые в `tracked_apps.category` (A-9).
- `MIGRATION_2_3` остаётся без изменений (B-3: «оставить как есть», все устройства на v3+). Этап 7.1 (экспорт схемы) при этом по-прежнему полезен для будущих миграций.
- Все баги и заглушки из [`bugs_plan.md`](bugs_plan.md) ([B-1](bugs_plan.md#b-1-глобальный-лимит--нерабочая-функция) … [B-18](bugs_plan.md#b-18-побочные-эффекты-фильтра--1-мин-в-getdailyusage)).

---

## 11. Оценка объёма

| Этап | Файлов затронуто | Оценка | Риск |
|---|---|---|---|
| 0 | тесты, gradle | 0.5–1 день | 🟢 |
| 1 | ~30 | 0.5 дня | 🟢 |
| 2 | ~15 | 0.5–1 день | 🟢 |
| 3 | 6 | 1 день | 🟡 |
| 4 | 8 | 1 день | 🟡 |
| 5 | 5 | 1 день + прогон на устройстве | 🔴 |
| 6 | 8 | 1–1.5 дня (+1 день на `strings.xml`) | 🟢/🟡 |
| 7 | 4 | 0.5 дня | 🟢 |

Ожидаемый результат: −600…−900 строк; устранены гонки на `SimpleDateFormat`, блокировка main-потока (`runBlocking`, I/O в Debug-экране) и избыточные запросы к БД в минутном цикле. Функциональность не меняется.
