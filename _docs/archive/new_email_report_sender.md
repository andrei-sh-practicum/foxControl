# План: переделка отправки email-отчётов (Таймер 3 вместо AlarmManager/WorkManager)

*Дата: 11 сентября 2026*

## 1. Проблема

Ежедневный email-отчёт не отправляется, а при фейле отправки в логах (Debug-экран) ничего не остаётся —
диагностировать вручную невозможно. На эту проблему уже потрачено много времени (см. историю в
`_docs/archive/report_email_problem.md` и цепочку коммитов `9fd79a1`, `7e8a79b`, `86b279f`, `afa8316`), несколько
раз чинили частные причины (разрешение на точный будильник, восстановление после ребута, реальная статистика
в письме, логирование в файл для видимости из процесса Worker) — отчёт всё равно не уходит стабильно.

**Решение: не чинить очередную частную причину, а убрать саму архитектуру `AlarmManager → BroadcastReceiver →
WorkManager`, заменив её таймером внутри уже работающего `TrackingForegroundService`, по образцу существующих
Таймера 1 (heartbeat) и Таймера 2 (usage stats poll).**

## 2. Как работает текущий алгоритм (кратко)

Полное описание — `_docs/email_reports.md`. Три звена:

```
EmailScheduler (AlarmManager.setExactAndAllowWhileIdle + setRepeating)
        │  (в момент настройки времени в EmailSettingsScreen)
        ▼
ReportAlarmReceiver (BroadcastReceiver, срабатывает по будильнику)
        │
        ▼
SendReportWorker (HiltWorker, CoroutineWorker) — собирает статистику и шлёт письмо
```

Плюс `EmailBootReceiver` — переустанавливает будильник после перезагрузки/обновления APK (через Hilt
`EntryPoint`, т.к. `BroadcastReceiver` не участвует в графе Hilt напрямую).

### Вероятная причина, почему фейлы не логируются

`SendReportWorker` — это `@HiltWorker`. Чтобы Hilt мог создавать воркеры со своими зависимостями
(`@AssistedInject`), `Application` обязан реализовать `Configuration.Provider` и отдать WorkManager
`HiltWorkerFactory` (`androidx.hilt:hilt-work`). В проекте это **не сделано** — `FoxControlApplication`
не реализует `Configuration.Provider`, `HiltWorkerFactory` нигде не регистрируется
(`grep -rn "Configuration.Provider\|HiltWorkerFactory"` находит только сами файлы `SendReportWorker.kt`/
`ReportAlarmReceiver.kt`, но не конфигурацию). Из-за этого WorkManager пытается создать `SendReportWorker`
через дефолтную фабрику (reflection, no-arg конструктор) — с `@AssistedInject`-конструктором это либо падает
ещё до `doWork()` (и тогда весь `try/catch` с `TrackingLogStorage.add` внутри `doWork()` вообще не
выполняется — отсюда и тишина в логах), либо ведёт себя непредсказуемо в зависимости от версии WorkManager.
Это не единственная возможная причина (есть ещё вопрос "убивает" ли вендор WorkManager/AlarmManager для
незапущенного процесса), но она достаточно объясняет и «не отправляется», и «в логах пусто».

**Проверять и чинить именно это мы не будем** — вместо этого убираем WorkManager/AlarmManager из цепочки
целиком, как и просил пользователь.

## 3. Новый концепт

Сбор данных (`_docs/data_collection.md`) уже держит `TrackingForegroundService` живым и гоняет в нём два
`java.util.Timer` с периодом 60 секунд. Добавляем третий, того же типа, в том же процессе:

```
┌──────────────────────────────────────────────────────────┐
│  TrackingForegroundService (уже жив, foreground)          │
│                                                             │
│  Таймер 1: HEARTBEAT (1 раз/мин)                            │
│  Таймер 2: USAGE_STATS_POLL (1 раз/мин)                     │
│  Таймер 3: EMAIL_REPORT_CHECK (1 раз/мин) ← НОВЫЙ           │
│         │                                                   │
│         ▼                                                   │
│   checkAndSendIfDue():                                      │
│     1. email_enabled? нет → выходим (без спама в лог)       │
│     2. текущие (час, минута) == (send_time_hour,            │
│        send_time_minute) из настроек?                       │
│        нет → выходим                                        │
│     3. да → проверяем кэш-метку "отправлено недавно"        │
│        (< 5 минут назад) → если есть, лог "пропуск,          │
│        дубликат" и выходим                                  │
│     4. ставим кэш-метку = сейчас                             │
│     5. отправляем отчёт СИНХРОННО (блокирующий вызов         │
│        прямо в потоке этого Timer'а, а не в WorkManager)     │
│     6. логируем каждый шаг и любую ошибку явно               │
│        (не проглатываем молча)                               │
└──────────────────────────────────────────────────────────┘
```

Ключевое отличие от старой схемы: письмо шлётся **в том же процессе, тем же потоком таймера**, без
`AlarmManager` (нет проблем с точными будильниками / Doze / ребутом), без `WorkManager` (нет проблем с
`HiltWorkerFactory` и видимостью логов из отдельного процесса worker'а). Ровно то же самое допущение о
надёжности, на котором уже держится сбор статистики (Таймеры 1 и 2) — если оно работает для них, будет
работать и для отправки писем.

## 4. Детальный план реализации

### 4.1. Новый класс `EmailReportSender` (`core/email/EmailReportSender.kt`)

Обычный Hilt `@Singleton`-класс (по образцу `AlertManager`), с зависимостями:

```kotlin
@Singleton
class EmailReportSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emailRepository: EmailRepository,
    private val emailSender: EmailSender,
    private val usageStatsRepository: UsageStatsRepository // domain-интерфейс, как в AlertManager
) {
    @Volatile private var lastSentAtMs: Long = 0L

    fun checkAndSendIfDue() {
        // синхронная функция, вызывается из TimerTask; сама открывает runBlocking
        // для suspend-вызовов к Room (по аналогии с EmailBootReceiver.onReceive)
    }
}
```

Логика `checkAndSendIfDue()` (псевдокод, детали ниже):

1. `runBlocking { emailRepository.getSetting("email_enabled") }` — если не `"true"`, тихо `return`
   (это ожидаемое поведение 1439 раз в сутки, не ошибка — не логируем, чтобы не забить Debug-лог).
2. Считать `send_time_hour` / `send_time_minute`. Если не настроены — `TrackingLogStorage.add("EmailReport",
   "email_enabled=true, но время отправки не задано")` и `return` (это уже ошибка конфигурации, логируем).
3. Сравнить с `Calendar.getInstance()` (час, минута). Не совпало → `return` без лога.
4. Совпало → проверить `System.currentTimeMillis() - lastSentAtMs < 5 * 60_000L`. Если да —
   `TrackingLogStorage.add("EmailReport", "пропуск: уже отправляли ${..} назад (дедуп 5 мин)")`, `return`.
5. `lastSentAtMs = System.currentTimeMillis()` — ставим метку **до** отправки (чтобы повторный тик таймера
   в течение той же/следующей минуты не мог уйти в параллельную отправку).
6. Дальше — то же самое, что сейчас делает `SendReportWorker.doWork()`, но без `setForeground`/уведомления
   о прогрессе (это был воркер-специфичный API): читаем SMTP-настройки, при отсутствии любого поля —
   `TrackingLogStorage.add("EmailReport", "ОШИБКА: не задан smtp_host")` (и т.д. по каждому полю, как
   сейчас) и `return`, не проглатывая молча.
7. `getActiveRecipients()` — если пусто, логируем ошибку, пишем `ReportSendLogEntity(status = "FAILED",
   errorMessage = "No active recipients")`, `return`.
8. `usageStatsRepository.getDailyUsage(today)`, собрать текст письма — переиспользовать 1-в-1 текущую
   логику формирования `subject`/`body` из `SendReportWorker` (строки 124–141 текущего файла).
9. `emailSender.sendBulkEmail(...)` — **блокирующий** вызов (уже сейчас синхронный, не suspend — вызывает
   `Transport.send` напрямую), поэтому вызывается прямо внутри `runBlocking`-блока безопасно, в отдельном
   потоке `Timer`'а — не блокирует ни UI, ни Таймер 1/2 (у каждого свой `Timer`/поток).
10. Обернуть шаги 6–9 в `try/catch (e: Exception)`, аналогично `pollUsageStats()`/`recordHeartbeat()` в
    `TrackingJob` — при исключении: `TrackingLogStorage.add("EmailReport", "UNHANDLED ERROR: ${e.message}")`
    + `TrackingLogStorage.add("EmailReport", e.stackTraceToString())`, и сохранить `ReportSendLogEntity`
    со статусом `FAILED`.
11. Сохранить `ReportSendLogEntity` (SUCCESS/PARTIAL/FAILED) — переиспользовать существующую сущность/DAO
    без изменений.
12. (опционально, для паритета со старым поведением) показать обычную `Notification` об успехе/неудаче
    через `NotificationCompat` напрямую (не foreground/ongoing, т.к. это больше не `Worker`) — не
    обязательно для решения задачи, можно сделать отдельным шагом после проверки, что сама отправка
    заработала.

Дедуп-метка (`lastSentAtMs`) — **в памяти** (`@Volatile`-поле), не персистентная. Это осознанный
компромисс: она переживает штатные рестарты `TrackingJob` внутри процесса (например,
`TrackingForegroundService.restartService()` при потере разрешений — `EmailReportSender` создаётся
Hilt'ом один раз и живёт дольше отдельных `start()/stop()` `TrackingJob`), но не переживает убийство
процесса. Если процесс убьют и пересоздадут ровно в ту же минуту, что и время отправки — возможен
повторный email в этот день. Это редкий и не критичный случай (отчёт «пришёл дважды» несравнимо лучше
текущего «не пришёл вообще»); при желании можно на шаге 5 дополнительно писать метку в
`EmailRepository` (`saveSetting("last_report_sent_at", ...)`) для межпроцессной защиты — вынести отдельным
пунктом, не обязательным для первой итерации.

### 4.2. Изменения в `TrackingJob`

Добавить третий `Timer` и передать `EmailReportSender` через конструктор:

```kotlin
class TrackingJob(
    private val context: Context,
    private val usageStatsRepository: UsageStatsRepositoryImpl,
    private val alertManager: AlertManager,
    private val emailReportSender: EmailReportSender   // НОВОЕ
) {
    private var emailReportTimer: Timer? = null

    fun start() {
        ...
        emailReportTimer = Timer("email_report").apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        emailReportSender.checkAndSendIfDue()
                    } catch (e: Exception) {
                        TrackingLogStorage.add("EmailReport", "checkAndSendIfDue ERROR: ${e.message}")
                        TrackingLogStorage.add("EmailReport", e.stackTraceToString())
                    }
                }
            }, 0, EMAIL_REPORT_CHECK_INTERVAL_MS) // 60_000L
        }
    }

    fun stop() {
        ...
        emailReportTimer?.cancel()
    }
}
```

Именно отдельный `Timer`, а не встраивание в Таймер 2 (`usage_poll`) — чтобы синхронная отправка письма
(сетевой SMTP-вызов, может занять несколько секунд) никогда не задерживала сбор статистики использования.

### 4.3. Изменения в `TrackingForegroundService`

```kotlin
@Inject
lateinit var emailReportSender: EmailReportSender

override fun onCreate() {
    ...
    trackingJob = TrackingJob(this, usageStatsRepository, alertManager, emailReportSender)
    ...
}
```

### 4.4. Изменения в `EmailSettingsViewModel`

Расписание больше не «ставится» явно — таймер сам сверяет настройки каждую минуту. Убрать:
- поле `emailScheduler: EmailScheduler` и его инъекцию;
- вызов `emailScheduler.scheduleDailyReport(...)` в `OnSendTimeChanged` (просто сохраняем `send_time_hour`/
  `send_time_minute`, как сейчас, без реджипланирования);
- вызовы `emailScheduler.scheduleDailyReport(...)` / `cancelScheduledReport()` в `saveSettings()` — просто
  сохраняем `email_enabled` в базу, этого достаточно.

`sendTestEmail()` не трогаем — она и так шлёт письмо напрямую через `EmailSender`, без участия таймера/
будильника, это отдельный ручной путь и должен остаться как есть.

## 5. Что удаляем (старый функционал)

| Файл | Действие |
|---|---|
| `core/email/EmailScheduler.kt` (физически лежит в `di/`, пакет `core.email`) | **Удалить полностью** |
| `core/email/ReportAlarmReceiver.kt` | **Удалить полностью** |
| `core/email/EmailBootReceiver.kt` (+ `EmailSchedulerEntryPoint`) | **Удалить полностью** |
| `core/email/SendReportWorker.kt` | **Удалить полностью** (логика перенесена в `EmailReportSender`) |
| `AndroidManifest.xml` | Убрать `<receiver android:name=".core.email.ReportAlarmReceiver">` и `<receiver android:name=".core.email.EmailBootReceiver">`; убрать `<uses-permission android:name="android.permission.USE_EXACT_ALARM" />` (AlarmManager больше не используется). `RECEIVE_BOOT_COMPLETED` — **оставить**, он нужен для `core.tracking.BootReceiver` (перезапуск сервиса трекинга, не относится к email) |
| `ui/settings/EmailSettingsViewModel.kt` | Убрать `EmailScheduler`-зависимость и оба вызова `scheduleDailyReport`/`cancelScheduledReport` (см. 4.4) |
| `ui/debug/DebugScreen.kt` / `DebugViewModel.kt` | Тег `[EmailScheduler]` в фильтре логов и бейдж «📧 EmailScheduler» — обновить под новый тег `EmailReport` (см. 4.1, шаг логирования), иначе бейдж на Debug-экране перестанет находить записи |
| `androidx.work:work-runtime`, `androidx.hilt:hilt-work` в `app/build.gradle.kts` | Можно удалить зависимости WorkManager/hilt-work, если в проекте больше нет других `Worker` (проверено — `SendReportWorker` единственный) |

Не трогаем: `EmailSender`, `EmailRepository`, `EmailSettingsEntity`/DAO, `EmailRecipientEntity`/DAO,
`ReportSendLogEntity`/DAO, `EmailSettingsScreen`/`EmailRecipientsScreen` (UI), `BrevoDefaults` — вся
инфраструктура настроек/получателей/лога отправок остаётся как есть, меняется только *кто и как* вызывает
отправку.

## 6. Логирование (явно, без тихих провалов)

Единый тег `"EmailReport"` в `TrackingLogStorage` (замена тега `"EmailScheduler"`, который был у
`SendReportWorker`/`EmailScheduler`). Обязательные точки логирования в `EmailReportSender`:

- совпадение времени найдено → `START` с указанием часа:минуты;
- пропуск по дедуп-кэшу → явный лог с указанием, сколько времени прошло с прошлой отправки;
- отсутствует любое из полей SMTP-настроек → `ОШИБКА: не задан <имя поля>` (по одному логу на каждое
  проверяемое поле, как сейчас в `SendReportWorker`);
- нет активных получателей → `ОШИБКА: нет получателей` + запись в `ReportSendLogEntity`;
- результат отправки по каждому получателю (успех/фейл) — как сейчас передаётся `EmailSender.SendResult`;
- итоговый результат (`DONE success` / `DONE all failed` / `DONE partial`);
- любое необработанное исключение — `UNHANDLED ERROR` + `e.stackTraceToString()`.

Поскольку вся эта логика теперь выполняется в процессе `TrackingForegroundService` (том же процессе, что
и UI/Debug-экран), больше не нужен `TrackingLogStorage.add(context, tag, message)` — версия с явным
`Context` была добавлена ранее специально для видимости логов из отдельного процесса `WorkManager`-воркера
(см. коммиты `9fd79a1`, `7e8a79b`). Она может остаться в коде (не мешает), но в `EmailReportSender`
достаточно обычного `TrackingLogStorage.add(tag, message)`, как в `TrackingJob`.

## 7. Риски и особые случаи

- **Точность минуты.** Таймер тикает раз в 60 000 мс от момента `start()`, не от начала календарной
  минуты — но, как и с Таймерами 1/2, каждая календарная минута гарантированно попадёт ровно в один тик
  (интервал точный, дрейфа на масштабе суток не накапливается настолько, чтобы пропустить минуту). Такое
  же допущение уже держит на себе весь сбор статистики.
- **DST/перевод часов.** `Calendar.getInstance()` берёт локальное время устройства — при переводе часов
  минута «совпадёт» либо дважды, либо ни разу в переходную ночь. Это уже присутствующий риск для любой
  схемы «раз в день по часам:минутам» (в т.ч. был и в старой схеме с `AlarmManager`), не регрессия.
- **Блокировка потока.** `checkAndSendIfDue()` блокирует поток именно Таймера 3 (SMTP-запрос может занять
  до ~10 секунд по текущим таймаутам в `EmailSender`) — соседние тики этого же таймера пропускаются, что
  не страшно (отправлять всё равно нужно не чаще раза в день). Таймеры 1 и 2 не затронуты, т.к. у каждого
  свой `Timer`/поток.
- **Дубли при убийстве процесса** — см. пункт 4.1 про in-memory дедуп-метку.

## 8. Порядок внедрения

1. Написать `EmailReportSender` (логика перенесена из `SendReportWorker`, дедуп-кэш, новое логирование).
2. Подключить Таймер 3 в `TrackingJob` + прокинуть `EmailReportSender` через `TrackingForegroundService`.
3. Убрать вызовы `EmailScheduler` из `EmailSettingsViewModel`.
4. Удалить `EmailScheduler.kt`, `ReportAlarmReceiver.kt`, `EmailBootReceiver.kt`, `SendReportWorker.kt`.
5. Почистить `AndroidManifest.xml` (receiver'ы + `USE_EXACT_ALARM`).
6. Обновить тег/бейдж в `DebugScreen`/`DebugViewModel` (`EmailScheduler` → `EmailReport`).
7. (Опционально) убрать `work-runtime`/`hilt-work` из `build.gradle.kts`, если больше нигде не используются.
8. Обновить `_docs/email_reports.md` под новую схему (текущая версия описывает уже удалённую архитектуру).
9. Прогнать `./gradlew :app:assembleDebug`, вручную проверить на устройстве: выставить время отправки на
   +2 минуты от текущего, дождаться, убедиться в Debug-логе (тег `EmailReport`) и в письме.

---


