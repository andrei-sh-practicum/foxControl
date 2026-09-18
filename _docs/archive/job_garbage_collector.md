# Garbage Collector job — очистка старых данных (7 суток) — план реализации

> Это план, код не менялся. Составлен по результатам чтения текущего кода (ветка `master`, коммит `93fcab5`).
> Все ссылки на файлы/строки актуальны на этот момент.

## 1. Проблема

Данные приложения (сессии трекинга, heartbeat'ы, алерты, лог отправки отчётов) валидны/актуальны неделю,
но таблицы никогда не чистятся — БД растёт бесконечно.

## 2. Важная находка: чистящие DAO-методы уже написаны, но нигде не вызываются

Это не задача "добавить SQL-запросы" — они уже есть в коде, просто мёртвые (ни один класс их не вызывает,
проверено по всему `app/src/main/java`):

| Таблица | DAO | Метод | Что делает |
|---|---|---|---|
| `usage_sessions` | `UsageSessionDao` | `deleteOldSessions(keepDate: String)` | `DELETE ... WHERE date < :keepDate` |
| `service_heartbeats` | `ServiceHeartbeatDao` | `deleteOldHeartbeats(cutoff: Long)` | `DELETE ... WHERE timestamp < :cutoff` |
| `alert_logs` | `AlertLogDao` | `deleteOldLogs(cutoff: Long)` | `DELETE ... WHERE timestamp < :cutoff` |
| `report_send_log` | `ReportSendLogDao` | `deleteOldLogs(before: Long)` | `DELETE ... WHERE sentAt < :before` |

Значит объём работы — это **только "проводка"**: новый класс, который дергает эти 4 метода с правильным
cutoff, плюс периодический запуск этого класса. Схему/миграции/DAO трогать не нужно.

Обратите внимание на разнотипность cutoff между таблицами:
- `usage_sessions.date` — строка `YYYY-MM-DD` → нужен `keepDate: String` (формат как в
  `UsageStatsRepositoryImpl.dateFormat`, `data/repository/UsageStatsRepository.kt:34`).
- `service_heartbeats.timestamp`, `alert_logs.timestamp`, `report_send_log.sentAt` — `Long` epoch ms → один
  общий `cutoffMs: Long`.

## 3. Совместимость с "недельной" статистикой (проверено, конфликта нет)

`getWeeklyUsage(startDate, endDate)` (`UsageStatsRepository.kt:64`) агрегирует `usage_sessions` по
диапазону дат — судя по всему, ровно 7-дневное скользящее окно (то же "неделя", о которой говорит
пользователь в постановке задачи). Если чистить по `date < (сегодня - 7 суток)`, то весь диапазон, который
реально показывается как "неделя", всегда останется нетронутым — под нож попадают только сутки старше
недельного окна. Конфликта между "очисткой старше 7 суток" и "недельной статистикой за 7 суток" нет, но это
надо держать в уме, если retention-период когда-нибудь захотят сократить.

`alert_logs.wasAlertShownToday` использует окно в пределах текущих суток (`dayStart`), `report_send_log`
проверяется по конкретной дате отчёта (`getLogByDate`) — 7-дневный retention многократно больше обоих окон,
не задевает их.

## 4. Предлагаемая архитектура

### 4.1. Новый класс: `core/maintenance/DataCleanupManager.kt`

По аналогии с `AlertManager` (`core/alerts/AlertManager.kt`) — отдельный компонент в `core/`,
Hilt-`@Singleton @Inject constructor`, DAO инжектятся напрямую (как это уже сделано в `AlertManager`,
`EmailReportSender`), без похода через `UsageStatsRepositoryImpl`/`EmailRepository`:

```kotlin
@Singleton
class DataCleanupManager @Inject constructor(
    private val usageSessionDao: UsageSessionDao,
    private val serviceHeartbeatDao: ServiceHeartbeatDao,
    private val alertLogDao: AlertLogDao,
    private val reportSendLogDao: ReportSendLogDao,
) {
    suspend fun purgeOldData() {
        val cutoffMs = System.currentTimeMillis() - RETENTION_MS
        val keepDate = dateFormat.format(Date(cutoffMs))

        TrackingLogStorage.add("Cleanup", "purgeOldData started, cutoff=$keepDate")
        usageSessionDao.deleteOldSessions(keepDate)
        serviceHeartbeatDao.deleteOldHeartbeats(cutoffMs)
        alertLogDao.deleteOldLogs(cutoffMs)
        reportSendLogDao.deleteOldLogs(cutoffMs)
        TrackingLogStorage.add("Cleanup", "purgeOldData finished")
    }

    companion object {
        private const val RETENTION_DAYS = 7L
        private const val RETENTION_MS = RETENTION_DAYS * 24 * 60 * 60 * 1000
    }
}
```

Все 4 DAO уже провайдятся в `di/AppModule.kt` как Hilt `@Singleton` (см. `provideUsageSessionDao`,
`provideServiceHeartbeatDao`, `provideAlertLogDao`, `provideReportSendLogDao`) — новых `@Provides` не нужно,
класс просто получает их через конструктор.

Ошибки на отдельных `deleteOld*` не должны валить весь `purgeOldData()` — обернуть каждый вызов в
try/catch с логом через `TrackingLogStorage`, как это сделано в `TrackingJob` для heartbeat/poll/email
(`core/tracking/TrackingJob.kt:50-58, 81-86`), чтобы падение чистки одной таблицы не мешало остальным.

### 4.2. Механизм периодического запуска — рекомендация: 4-й таймер в `TrackingJob`

В проекте уже есть ровно такой паттерн — три `java.util.Timer` внутри `TrackingJob.start()`
(`heartbeatTimer`, `usageStatsTimer`, `emailReportTimer`, `TrackingJob.kt:46-89`), каждый со своим
интервалом, каждый вызывает `GlobalScope.launch { ... }` с try/catch и логом в `TrackingLogStorage`.
`WorkManager`/`AlarmManager`,  (`core/email` — `SendReportWorker`,
`ReportAlarmReceiver`) в коде фактически отсутствуют — email-отчёты тоже работают через **свой** таймер
(`emailReportTimer` → `emailReportSender.checkAndSendIfDue()`) В проекте нет ни одной зависимости `WorkManager` в
`app/build.gradle.kts`.

Предлагается **не вводить новую инфраструктуру**, а добавить `cleanupTimer` четвёртым таймером в
`TrackingJob`, период 12 часов:

```kotlin
cleanupTimer = Timer("data_cleanup").apply {
    scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            GlobalScope.launch {
                try {
                    dataCleanupManager.purgeOldData()
                } catch (e: Exception) {
                    TrackingLogStorage.add("Cleanup", "purgeOldData ERROR: ${e.message}")
                    TrackingLogStorage.add("Cleanup", e.stackTraceToString())
                }
            }
        }
    }, 0, CLEANUP_INTERVAL_MS) // 12 * 60 * 60 * 1000L
}
```

+ `cleanupTimer?.cancel()` в `stop()`, `dataCleanupManager: DataCleanupManager` — новый параметр
конструктора `TrackingJob` (провайдится Hilt-ом автоматически, конструктор `TrackingJob` не помечен
`@Inject` напрямую — нужно посмотреть, кто его создаёт (`TrackingForegroundService`), и прокинуть туда же).

**Почему не WorkManager.** `PeriodicWorkRequest` — более "правильный" с точки зрения Android способ
гонять периодическую задачу независимо от жизненного цикла сервиса, но:
- в проекте нет ни зависимости, ни Hilt-интеграции (`HiltWorkerFactory`, `Configuration.Provider` в
  `FoxControlApplication`) — пришлось бы заводить с нуля;
- минимальный интервал `PeriodicWorkRequest` — 15 минут, 12 часов укладывается без проблем, но сам факт
  новой инфраструктуры ради одной задачи, когда рядом уже есть работающий, проверенный паттерн (Timer +
  `TrackingLogStorage`-логирование) для точно такой же периодичной фоновой работы — лишняя сложность;
- `TrackingForegroundService` — `START_STICKY`, перезапускается `BootReceiver`-ом на `BOOT_COMPLETED` и
  сам себя чинит при потере критичного разрешения (опрос `PermissionMonitor` раз в минуту) — то есть у
  сервиса уже есть собственная модель "почти всегда жив", на которую полагаются остальные 3 таймера; GC-джоб
  ничем не отличается по требованиям надёжности.

Побочный эффект: если сервис перезапускается чаще, чем раз в 12 часов (например, после потери разрешения),
`cleanupTimer` стартует заново с `scheduleAtFixedRate(0, ...)` и `purgeOldData()` выполнится раньше
графика. Это не проблема — удаление идемпотентно (повторный `DELETE ... WHERE date < :keepDate` по уже
почищенным данным — no-op), только чуть больше DB-запросов чем строго раз в 12ч.

**Альтернатива, если пользователь захочет более "надёжный" вариант** независимо от foreground-сервиса —
`WorkManager PeriodicWorkRequest(12, TimeUnit.HOURS)` с `ExistingPeriodicWorkPolicy.KEEP`, зарегистрированный
в `FoxControlApplication.onCreate()`. Дороже по инфраструктуре (новая зависимость + `HiltWorkerFactory`),
но переживает уничтожение foreground-сервиса и не завязан на то, жив ли `TrackingJob`. Стоит выбрать, если
в будущем окажется, что сервис реально подолгу не живёт на части устройств (это и так тема для `core/vendor`
— агрессивные OEM-и типа Xiaomi/Huawei убивают фоновые сервисы).

## 5. Список файлов, которые придётся создать/тронуть

| Файл | Действие |
|---|---|
| `core/maintenance/DataCleanupManager.kt` | **Создать.** Логика очистки, см. 4.1 |
| `core/tracking/TrackingJob.kt` | Добавить `cleanupTimer`, параметр `dataCleanupManager` в конструктор, `CLEANUP_INTERVAL_MS` в `companion object`, `cleanupTimer?.cancel()` в `stop()` |
| `core/tracking/TrackingForegroundService.kt:45` | `trackingJob = TrackingJob(this, usageStatsRepository, alertManager, emailReportSender)` → добавить 5-м аргументом `dataCleanupManager`. `TrackingForegroundService` — `@AndroidEntryPoint`, `usageStatsRepository`/`alertManager`/`emailReportSender` уже инжектятся как `@Inject lateinit var` (`TrackingForegroundService.kt:28-36`) — `dataCleanupManager` добавляется туда же тем же способом |
| `di/AppModule.kt` | **Не требуется** — все 4 DAO уже `@Provides @Singleton`, `DataCleanupManager` соберётся автоматически через `@Inject constructor` |



## Дополнительно (не обязательно, но дёшево) — видимость в Debug-экране

`ui/debug/DebugScreen.kt` (`TabDatabase`) уже показывает `sessionCount`/`dateRange`/`uniquePackageCount`
через `DebugSection`/`DebugRow` (`DebugScreen.kt:117-193`). Имеет смысл добавить туда же:
- timestamp последнего запуска `purgeOldData()` (in-memory или новая key-value запись, по аналогии с тем,
  как `email_settings` хранит key-value);
- кнопку "Почистить сейчас" для ручного триггера (диагностика на девайсе без ожидания 12 часов).


## Что сознательно не входит в объём

- Изменения схемы БД / новые миграции — не нужны, все нужные колонки и DELETE-запросы уже существуют.
- `tracked_apps`, `app_limits`, `global_limit`, `email_recipients`, `email_settings`, `users` — не входят в
  список таблиц из задачи, не трогаем (это справочники/настройки, не логи с TTL).
- WorkManager-инфраструктура — не заводим, если пользователь явно не попросит более отказоустойчивый
  вариант (см. п.4.2).
