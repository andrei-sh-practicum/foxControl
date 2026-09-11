# Как работает отправка отчётов на email

## Общая схема

Отправка ежедневных отчётов построена на трёх компонентах:

```
Пользователь настроил время (например, 21:00)
         │
         ▼
  EmailScheduler (AlarmManager)
  «Поставим будильник на 21:00»
         │
         ▼ (на следующий день в 21:00)
  ReportAlarmReceiver (будильник сработал)
  «О, время пришло! Запускаю отправку»
         │
         ▼
  SendReportWorker (работает в фоне)
  «Собираю данные, формирую письмо, отправляю»
```

## Звено 1: EmailScheduler — «Будильник»

Когда пользователь настраивает расписание отчётов, `EmailScheduler` делает две вещи:

### 1. Ставит будильник на сегодня (или завтра)

```kotlin
alarmManager.setExactAndAllowWhileIdle(
    RTC_WAKEUP,
    calendar.timeInMillis,  // 21:00
    pendingIntent
)
```

**`setExactAndAllowWhileIdle`** — критически важно. Будильник сработает **даже если телефон в спящем режиме**.

### 2. Ставит повторный будильник на 24 часа позже

```kotlin
alarmManager.setRepeating(
    calendar.timeInMillis + (24 * 60 * 60 * 1000L),
    24 * 60 * 60 * 1000L,  // каждые 24 часа
    pendingIntent
)
```

Это страховка: если Android «съест» первый будильник (иногда такое бывает), второй всё равно сработает.

### Отмена расписания

```kotlin
fun cancelScheduledReport() {
    alarmManager.cancel(pendingIntent)
}
```

Вызывается, когда пользователь отключает отчёты.

## Звено 2: ReportAlarmReceiver — «Сработал будильник»

В назначенное время Android запускает этот `BroadcastReceiver`. Он делает одну вещь:

```kotlin
val workRequest = OneTimeWorkRequestBuilder<SendReportWorker>()
    .setInitialDelay(0, TimeUnit.MILLISECONDS)
    .build()

WorkManager.getInstance(context).enqueueUniqueWork(
    "immediate_report_send",
    ExistingWorkPolicy.REPLACE,
    workRequest
)
```

**Почему не отправляет письмо прямо тут?** Потому что `BroadcastReceiver` должен отработать быстро (максимум 10 секунд). Отправка письма — сетевой запрос, который может занять дольше. `WorkManager` может работать в фоне.

**`REPLACE`** — если предыдущая отправка ещё не закончилась, заменяем её новой.

## Звено 3: SendReportWorker — «Собираю и отправляю»

Самый длинный этап. Выполняется в `CoroutineWorker.doWork()`.

### Шаг 1: Проверка настроек SMTP

```kotlin
val smtpHost = emailRepository.getSetting("smtp_host")
val smtpPort = emailRepository.getSetting("smtp_port")
val login = emailRepository.getSetting("smtp_login")
val appPassword = emailRepository.getSetting("smtp_app_password")
val fromEmail = emailRepository.getSetting("from_email")
val enabled = emailRepository.getSetting("email_enabled")
```

Если что-то отсутствует — `Result.failure()`.

### Шаг 2: Получение получателей

```kotlin
val activeRecipients = emailRepository.getActiveRecipients()
```

Если список пустой — ошибка, отмена.

### Шаг 3: Получение статистики за день

```kotlin
val today = getCurrentDate()  // "2025-09-10"
val stats = usageStatsRepository.getDailyUsage(today)
```

Возвращает:
- `stats.totalUsageMs` — общее время использования
- `stats.apps` — список приложений с временем

### Шаг 4: Формирование тела письма

```
Отчёт за 2025-09-10
========================

Общее время использования: 4ч 32мин

Список приложений:
------------------------------
- YouTube: 1ч 20мин
- Minecraft: 55мин
- Telegram: 45мин
```

### Шаг 5: Отправка через SMTP

```kotlin
emailSender.sendBulkEmail(
    config = config,
    recipients = activeRecipients.map { it.email },
    subject = subject,
    body = body
)
```

`EmailSender` использует **Jakarta Mail** (не старый JavaMail):

```kotlin
val properties = Properties().apply {
    put("mail.smtp.host", config.smtpHost)
    put("mail.smtp.port", config.smtpPort.toString())
    put("mail.smtp.auth", "true")
    put("mail.smtp.starttls.enable", "true")
    put("mail.smtp.starttls.required", "true")
    put("mail.smtp.connectiontimeout", "5000")
    put("mail.smtp.timeout", "10000")
    put("mail.smtp.writetimeout", "10000")
}
```

Отправляет каждому получателю отдельно, собирает результат.

### Шаг 6: Логирование результата

```kotlin
val log = ReportSendLogEntity(
    date = today,
    status = if (failedCount == 0) "SUCCESS" else "PARTIAL",
    errorMessage = if (failedCount > 0) results.filterNot { it.success }.firstOrNull()?.message else null,
    recipientCount = activeRecipients.size
)
emailRepository.saveLog(log)
```

### Шаг 7: Уведомление

Показывает пользователю на телефоне:
- «Отправлено 2/2» — успех
- «Ошибка отправки» — провал

## Восстановление после перезагрузки

`EmailBootReceiver` ловит `ACTION_BOOT_COMPLETED` и `ACTION_MY_PACKAGE_REPLACED`:

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
        intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
    ) return

    // Проверяет, включены ли отчёты
    // Считывает сохранённое время
    // Пересоздаёт будильник
    scheduler.scheduleDailyReport(hour, minute)
}
```

Использует Hilt `EntryPoint` для получения зависимостей, так как `BroadcastReceiver` не имеет доступа к Dagger-компонентам напрямую.

## Полный цикл отправки

```
Пользователь настраивает:
  ✓ email_enabled = "true"
  ✓ send_time_hour = 21
  ✓ send_time_minute = 0
  ✓ smtp_host = "smtp.gmail.com"
  ✓ smtp_port = 587
  ✓ smtp_login = "parent@gmail.com"
  ✓ smtp_app_password = "xxxx xxxx xxxx xxxx"
  ✓ from_email = "parent@gmail.com"
  ✓ recipients: mom@gmail.com, dad@gmail.com

         │
         ▼

EmailScheduler ставит будильник на 21:00
  ├─ setExactAndAllowWhileIdle (на сегодня/завтра)
  └─ setRepeating (каждые 24 часа — страховка)

         │
         ▼ (завтра в 21:00)

Будильник срабатывает → ReportAlarmReceiver
  └─ Запускает SendReportWorker через WorkManager

         │
         ▼

SendReportWorker:
  1. Проверяет SMTP-настройки ✓
  2. Берёт активных получателей ✓
  3. Загружает статистику за день из базы ✓
  4. Формирует текст письма ✓
  5. Отправляет через SMTP (Jakarta Mail) ✓
  6. Записывает результат в лог ✓
  7. Показывает уведомление ✓

         │
         ▼

Письмо приходит на mom@gmail.com и dad@gmail.com
```

## Отказоустойчивость

| Сценарий | Поведение |
|----------|-----------|
| SMTP не настроен | Ошибка, отмена, лог в Debug |
| Нет получателей | Ошибка, отмена, лог в Debug |
| Частичная отправка | Статус PARTIAL, лог с ошибкой |
| Телефон перезагружен | BootReceiver восстанавливает будильник |
| Предыдущая отправка не завершилась | REPLACE — запускается новая |
| Ошибка сети | SendResult.success = false, лог ошибки |

## Логирование

Все события логируются в `TrackingLogStorage` с тегом `[EmailScheduler]`:

- `SendReportWorker: START`
- `SendReportWorker: MISSING smtp_host` (если нет настроек)
- `SendReportWorker: reports disabled` (если отключены)
- `SendReportWorker: NO active recipients`
- `SendReportWorker: settings loaded`
- `SendReportWorker: sending to N recipients...`
- `SendReportWorker: sent X/N (failed=Y)`
- `SendReportWorker: DONE success` / `DONE all failed`

---

*Обновлено: 10 сентября 2025*
