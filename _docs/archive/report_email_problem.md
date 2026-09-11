# Проблема: не отправляется ежедневный отчёт на email


## Симптом

- Тестовое письмо (кнопка в `EmailSettingsScreen` → `OnTestSendClicked`) отправляется успешно.
- Плановая ежедневная отправка (пункт плана «Ежедневная отправка отчёта на 1+ email в заданное время») не происходит.

## Причина №1 (основная): исключение при планировании точного будильника — тихо проглатывается

`EmailScheduler.scheduleDailyReport()` вызывает `AlarmManager.setExactAndAllowWhileIdle(...)`:

- `app/src/main/java/com/andrew/foxcontrol/core/email/EmailScheduler.kt:53-57`

Начиная с Android 12 (API 31) для точных будильников (`setExact*`, `setAlarmClock`) приложению требуется разрешение `SCHEDULE_EXACT_ALARM` (на API 31-32 выдаётся автоматически при объявлении в манифесте) либо `USE_EXACT_ALARM`. Без объявленного разрешения вызов бросает `SecurityException`.

Проект собирается с `targetSdk = 35` (`app/build.gradle.kts:26`), а в `AndroidManifest.xml` **нет** ни `SCHEDULE_EXACT_ALARM`, ни `USE_EXACT_ALARM`:

```
grep -n "ALARM" app/src/main/AndroidManifest.xml   → ничего не найдено
```

При этом весь метод обёрнут в `try { ... } catch (e: Exception) { Log.e(TAG, "Failed to schedule report", e) }` (`EmailScheduler.kt:24-70`) — исключение просто пишется в logcat и теряется. Пользователю (и в Debug-экран через `TrackingLogStorage`) ничего не попадает. В результате:

- будильник **никогда не регистрируется**,
- `ReportAlarmReceiver` никогда не срабатывает,
- `SendReportWorker` никогда не запускается по расписанию.

Тестовая отправка работает, потому что `sendTestEmail()` (`EmailSettingsViewModel.kt:156-179`) вообще не использует `AlarmManager` — письмо уходит прямым вызовом `EmailSender.sendEmail` из ViewModel.

**Это объясняет симптом «отчёт по расписанию не приходит» практически полностью.**

## Причина №2: даже если будильник поставился — он не переживёт перезагрузку телефона

Все `AlarmManager`-будильники (в т.ч. точные) сбрасываются системой при перезагрузке устройства. `BootReceiver` (`core/tracking/BootReceiver.kt`) на `BOOT_COMPLETED` перезапускает только `TrackingForegroundService`, но **не** вызывает `EmailScheduler.scheduleDailyReport(...)`. Планирование сейчас происходит только вручную — при нажатии «Сохранить» или смене времени в `EmailSettingsScreen` (`EmailSettingsViewModel.kt:117-119, 145-149`).

На детском телефоне перезагрузки (разряд батареи, обновление ОС, вендорская «оптимизация» и т.п.) — обычное дело, поэтому отчёт перестаёт уходить после первой же перезагрузки, пока родитель снова не зайдёт в настройки email и не нажмёт «Сохранить».

## Причина №3: тело письма — заглушка, без реальной статистики

`SendReportWorker.doWork()` формирует письмо так:

- `app/src/main/java/com/andrew/foxcontrol/core/email/SendReportWorker.kt:79-88`

```kotlin
val subject = "Fox Control: Отчёт за ${getCurrentDate()}"
val body = buildString {
    appendLine("Отчёт за ${getCurrentDate()}")
    appendLine("========================")
    appendLine("")
    appendLine("Здесь будет статистика использования приложений.")
    appendLine("")
    appendLine("Это тестовое письмо.")
}
```

Комментарий в коде прямо говорит: `// Prepare report body (placeholder - in production, fetch usage stats)`. Реальные данные не запрашиваются — `SendReportWorker` не получает `UsageStatsRepository` вообще.

Нужные данные уже есть и используются на главном экране:

- `UsageStatsRepository.getDailyUsage(date: String): DailyUsageStats` (`domain/repository/UsageStatsRepository.kt`, реализация — `data/repository/UsageStatsRepository.kt:33-59`), формат `date` — `"yyyy-MM-dd"`, совпадает с `SendReportWorker.getCurrentDate()`.
- `DailyUsageStats(date, totalUsageMs, apps: List<UsageStats>)`, `UsageStats(packageName, appName, totalDurationMs, sessionCount, isEntertainment, category)`.
- Форматирование длительности — готовая функция `formatDuration(durationMs)` в `ui/common/FormatUtils.kt` (та же, что использует `HomeScreen`).

## Побочная проблема: диагностировать это было почти невозможно

- Ошибка планирования будильника уходит только в `Log.e` 
- Результат отправки пишется в `ReportSendLogEntity` через `ReportSendLogDao`, но нигде в UI (ни `EmailSettingsScreen`, ни `DebugScreen`) эти логи не отображаются — проверить историю отправок из приложения нельзя.

## Предлагаемое решение

1. **Разрешение на точный будильник.** Два варианта на выбор:

   | | `USE_EXACT_ALARM` | `SCHEDULE_EXACT_ALARM` |
   |---|---|---|
   | Как выдаётся | Автоматически при установке, всегда (как `INTERNET`) | Автоматически на API 31-32; **на API 33+ (Android 13+) НЕ выдаётся автоматически** |
   | Нужен доп. экран/диалог | Нет | Да, на API 33+ — только через системный Settings (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`), обычного runtime-диалога `requestPermissions()` для него нет |
   | Ограничение | Google Play не пропускает в листинг приложения не из категории «будильник/календарь» | Ограничений Play нет |
   | Нужны ли правки онбординга | Нет | Да — новый пункт в `OnboardingPermissions` (`PermissionHelper.kt`) + шаг на первом экране (`OnboardingScreen.kt`), по образцу уже существующих `isOverlayPermissionGranted`/`openOverlayPermissionSettings` |

   **Рекомендуемый вариант — `USE_EXACT_ALARM`:**
   ```xml
   <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
   ```
   Приложение не публикуется в Google Play (см. CLAUDE.md — «Not published to Google Play»; APK раздаётся напрямую), поэтому Play-ограничение по категориям на нас не действует. Разрешение выдаётся автоматически сразу после установки на всех API 26-35 (на API < 33 просто не имеет эффекта, но и не мешает) — правок онбординга/UI не требуется, будильник заработает без каких-либо действий пользователя.

   Альтернатива — `SCHEDULE_EXACT_ALARM`. Проще с точки зрения манифеста, но на API 33+ требует ручного шага пользователя через системные настройки, а значит:
   - перед планированием нужно проверять `alarmManager.canScheduleExactAlarms()` (API 31+),
   - если `false` — не проглатывать это молча, а логировать через `TrackingLogStorage` и добавить шаг в онбординг / статус в `EmailSettingsScreen` (например, предупреждение «нужно разрешение "Будильники и напоминания"» со ссылкой на `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`).
   - Имеет смысл только если в будущем планируется публикация в Google Play в категории, допускающей `USE_EXACT_ALARM`-ограничение — иначе усложнение не оправдано.

2. **Восстановление расписания после перезагрузки/обновления приложения.**
   - В `BootReceiver.onReceive` (или в отдельном `@AndroidEntryPoint`-ресивере на `ACTION_BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`) читать `email_enabled` / `send_time_hour` / `send_time_minute` через `EmailRepository` и, если включено, вызывать `EmailScheduler.scheduleDailyReport(hour, minute)`.
   - Дополнительно можно на старте `FoxControlApplication.onCreate()` тоже пере-регистрировать будильник (идемпотентно — `setExactAndAllowWhileIdle` с тем же `requestCode` просто перезапишет существующий), это подстрахует от случаев, когда `BOOT_COMPLETED` не дошёл (например, ограничения вендора).

3. **Реальная статистика в письме.** В `SendReportWorker`:
   - добавить зависимость `UsageStatsRepository` (интерфейс из `domain.repository`, уже забинжен в `RepositoryModule`) через `@AssistedInject`.
   - получить `val stats = usageStatsRepository.getDailyUsage(getCurrentDate())`.
   - собрать тело письма из `stats.apps` (отсортированы по убыванию, как на главном экране) + `stats.totalUsageMs`, используя `formatDuration(...)` из `ui/common/FormatUtils.kt`; для пустого списка (`stats.apps.isEmpty()`) — отдельная фраза «Активности не зафиксировано» вместо пустой таблицы.
   - пример содержимого:
     ```
     Отчёт за 2026-09-10
     ========================
     Общее время использования: 3ч 42м

     - YouTube: 1ч 20м
     - Instagram: 55м
     - Chrome: 40м
     ...
     ```

4. **Видимость истории отправок.** Показать последние записи `ReportSendLogEntity` (уже есть DAO/Entity) в `EmailSettingsScreen` (или `DebugScreen`) — статус последней отправки, дата, ошибка при неудаче. Это не обязательно для исправления бага, но закрывает то, из-за чего проблема оставалась незамеченной так долго.

## Порядок внедрения (по приоритету)

1. Разрешение на точный будильник (без этого расписание физически не работает) — причина №1.
2. Восстановление расписания в `BootReceiver` — причина №2.
3. Реальные данные в письме вместо заглушки — причина №3.
4. Экран/список истории отправок для диагностики в будущем.


