# База данных: таблицы Room (`AppDatabase`)

> Справочный документ, сгенерирован по коду. Не план — просто фиксация текущего состояния схемы.

- Файл БД: Room, `AppDatabase` (`data/local/AppDatabase.kt`), `@Database(version = 4)`. Компаньон-константа
  `AppDatabase.DATABASE_VERSION` при этом всё ещё захардкожена как `3` и нигде не используется (Room берёт
  версию из аннотации, а не из неё) — рассинхронизация в коде, не влияет на поведение, но сбивает с толку
  при чтении.
- Миграции регистрируются в `di/AppModule.kt` (`.addMigrations(MIGRATION_2_3, MIGRATION_3_4)`), плюс
  подключён `.fallbackToDestructiveMigration()` как safety-net (см. предупреждение в конце документа).
- Внешних ключей (`@ForeignKey`) и индексов (`@Index`) ни у одной таблицы нет — связи между таблицами
  (`packageName`, `date`) поддерживаются только на уровне кода репозиториев, не СУБД.
- Основной репозиторий, который держит бизнес-логику поверх большинства DAO — `UsageStatsRepositoryImpl`.

## Список таблиц

| Таблица | Entity | DAO | Назначение |
|---|---|---|---|
| `users` | `UserEntity` | `UserDao` | Единственная запись профиля пользователя (имя, аватар, пароль в Room) |
| `usage_sessions` | `UsageSessionEntity` | `UsageSessionDao` | Сессии использования приложений (основные данные трекинга) |
| `tracked_apps` | `TrackedAppEntity` | `TrackedAppDao` | Справочник отслеживаемых приложений + агрегированное время |
| `app_limits` | `AppLimitEntity` | `AppLimitDao` | Индивидуальные дневные лимиты по приложениям |
| `global_limit` | `GlobalLimitEntity` | `GlobalLimitDao` | Общий дневной лимит (singleton-строка) |
| `service_heartbeats` | `ServiceHeartbeatEntity` | `ServiceHeartbeatDao` | Отметки "сервис трекинга жив" (heartbeat каждые 60с) |
| `alert_logs` | `AlertLogEntity` | `AlertLogDao` | Журнал сработавших алертов о превышении лимита |
| `email_recipients` | `EmailRecipientEntity` | `EmailRecipientDao` | Получатели email-отчётов |
| `email_settings` | `EmailSettingsEntity` | `EmailSettingsDao` | SMTP-настройки как key-value пары |
| `report_send_log` | `ReportSendLogEntity` | `ReportSendLogDao` | Журнал отправки email-отчётов |

## Связи между таблицами

Как сказано выше, ни `@ForeignKey`, ни `@Index` в схеме нет — все связи ниже это связи "по значению"
(одинаковый `packageName` / `date` в разных таблицах), которые поддерживает код репозиториев, а не СУБД.

### Трекинг: `tracked_apps` как справочник по `packageName`

```text
                      ┌────────────────┐
                 ┌───▶│ usage_sessions │
                 │    └────────────────┘
┌──────────────┐ │    ┌────────────┐
│ tracked_apps │─┼───▶│ app_limits │
└──────────────┘ │    └────────────┘
                 │    ┌────────────┐
                 └───▶│ alert_logs │
                      └────────────┘
```

`tracked_apps.packageName` — де-факто справочник приложений; `usage_sessions.packageName`,
`app_limits.packageName` и `alert_logs.packageName` ссылаются на него по значению. Для `alert_logs` это
верно только для записей с `type = "app"` — глобальные алерты (`type = "global"`) пишутся с
`packageName = "global"`, служебным значением-заглушкой, а не реальным пакетом, и с `tracked_apps` не
связаны.

### Email-отчёт: несколько таблиц сходятся в `report_send_log`

```text
┌──────────────────┐
│ usage_sessions   │───┐
└──────────────────┘   │
┌──────────────────┐   │    ┌─────────────────┐
│ email_recipients │───┼───▶│ report_send_log │
└──────────────────┘   │    └─────────────────┘
┌──────────────────┐   │
│ email_settings   │───┘
└──────────────────┘
```

Связь не хранится в БД, а происходит во время выполнения в `EmailReportSender` (`core/email/`): при отправке
ежедневного отчёта он читает `usage_sessions` за дату отчёта (через `usageStatsRepository.getDailyUsage`),
`email_recipients` (активных получателей) и `email_settings` (SMTP-параметры), а по итогам пишет одну
строку в `report_send_log`.

### Изолированные таблицы

`users`, `global_limit` и `service_heartbeats` ни с кем не связаны по значению — каждая живёт сама по себе
(соответственно: единственный профиль пользователя, единственный singleton-лимит и лог heartbeat'ов сервиса,
из которого простои считаются на лету, см. `getServiceDowntimeBuckets` выше, тоже без записи в отдельную
таблицу).

## Описание таблиц

### `users`
Единственная запись профиля (создаётся `UserRepository.ensureDefaultUser()` при онбординге).

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `Long`, PK, autoGenerate | |
| `name` | `String` | Имя пользователя, редактируется в "Настройки" |
| `passwordHash` | `String` | **Не используется** — гейт "Приватных настроек" сегодня работает через отдельный `passwordHash` в DataStore (`UserRepository`, файл `settings`), а не через это поле. См. `_docs/settings_user_plan.md`, п.7 и `_docs/private_settings_login_plan.md` |
| `avatarUri` | `String?` | `file://` путь к ресайзнутому аватару во внутреннем хранилище |
| `createdAt` | `Long` | Timestamp создания, по умолчанию `System.currentTimeMillis()` |

### `usage_sessions`
Ядро трекинга — каждая запись — это отрезок непрерывного использования одного приложения. Пишутся из
`TrackingJob`/`UsageStatsRepositoryImpl` по итогам опроса `UsageStatsManager` (раз в 60с). `date` хранится
как строка `YYYY-MM-DD`, используется для дневной/недельной агрегации вместо `startTime`/`endTime`.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `Long`, PK, autoGenerate | |
| `packageName` | `String` | Пакет приложения |
| `appName` | `String` | Отображаемое имя (денормализовано, дублирует `tracked_apps.appName`) |
| `startTime` / `endTime` | `Long` | Границы сессии (epoch ms) |
| `durationMs` | `Long` | Длительность сессии |
| `date` | `String` | `YYYY-MM-DD`, ключ агрегации по дням/неделям |
| `isEntertainment` | `Boolean` | Денормализованный флаг категории на момент записи сессии |

Вставка — `OnConflictStrategy.IGNORE` (дублирующиеся `id` не перезаписываются). Есть агрегирующий запрос
`getWeeklyUsageByPackage` (SUM/COUNT по диапазону дат) и debug-запросы (`getSessionCount`,
`getUniquePackageCount`, `getDateRange`, `getRecentSessions`) для `ui/debug/DebugScreen`.

### `tracked_apps`
Справочник приложений, которые когда-либо попадали в трекинг, плюс накопленное время использования и
пользовательские флаги категории.

| Колонка | Тип | Описание |
|---|---|---|
| `packageName` | `String`, PK | |
| `appName` | `String` | Отображаемое имя |
| `iconUri` | `String?` | Ссылка на иконку (если есть) |
| `category` | `String` | Категория (строка) |
| `isEntertainment` | `Boolean` | Признак "развлекательное" (влияет на лимиты/отчёты) |
| `isExcluded` | `Boolean` | Исключено из трекинга/лимитов |
| `lastUsedTime` | `Long` | Последнее время использования |
| `totalUsageMs` | `Long` | Суммарное накопленное время (инкрементально, `updateUsage`) |

`deleteAllTrackedApps()` вызывается кнопкой "Очистить таблицу трекинга" в "Приватных настройках"
(`UsageStatsRepository.clearTrackedApps()`).

### `app_limits`
Индивидуальные дневные лимиты по конкретным приложениям, задаются в "Приватных настройках".

| Колонка | Тип | Описание |
|---|---|---|
| `packageName` | `String`, PK | |
| `dailyLimitMinutes` | `Int` | Лимит в минутах в день |
| `enabled` | `Boolean` | Лимит активен (по умолчанию `true`) |

### `global_limit`
Общий дневной лимит на всё время экрана — singleton-строка с фиксированным `id = 1`.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `Int`, PK, всегда `1` | Гарантирует ровно одну строку |
| `dailyLimitMinutes` | `Int` | Лимит в минутах в день |
| `enabled` | `Boolean` | По умолчанию `false` (лимит выключен) |

### `service_heartbeats`
Отметки живости foreground-сервиса трекинга (`TrackingJob`, таймер heartbeat каждые 60с). Используется для
диагностики простоев в связке с `service_downtime_events`.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `Long`, PK, autoGenerate | |
| `timestamp` | `Long` | Момент heartbeat |

> Отдельной таблицы простоев больше нет: `service_downtime_events` была удалена миграцией `MIGRATION_3_4`
> (версия 3 → 4, `DROP TABLE IF EXISTS`). Периоды простоя foreground-сервиса теперь считаются "на лету" из
> разрывов между записями `service_heartbeats` — `DowntimeCalculator.calculate()`
> (`core/tracking/DowntimeCalculator.kt`), вызывается из
> `UsageStatsRepositoryImpl.getServiceDowntimeBuckets(date)` и отображается графиком на `HomeScreen`
> (вкладка "Сегодня"). Ничего не пишется в БД — только чтение `service_heartbeats` за сутки.

### `alert_logs`
Журнал сработавших алертов о превышении лимита (глобального или по приложению), с cooldown 1 минута на
пакет (`AlertManager`).

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `Long`, PK, autoGenerate | |
| `packageName` | `String` | Пакет, по которому сработал алерт |
| `timestamp` | `Long` | Момент срабатывания |
| `type` | `String` | `"global"` или `"app"` |

### `email_recipients`
Получатели ежедневных email-отчётов об использовании.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `Long`, PK, autoGenerate | |
| `email` | `String` | Адрес |
| `name` | `String` | Имя получателя (по умолчанию `""`) |
| `isActive` | `Boolean` | Включён в рассылку (по умолчанию `true`) |
| `createdAt` | `Long` | Timestamp создания |

### `email_settings`
SMTP-настройки как key-value пары (по одной строке на настройку), ключи — константы
`EmailSettingsKeys` (`SMTP_HOST`, `SMTP_PORT`, `SMTP_LOGIN`, `SMTP_APP_PASSWORD`, `FROM_EMAIL`, `ENABLED`,
`SEND_TIME_HOUR`, `SEND_TIME_MINUTE`). Учётные данные SMTP хранятся здесь в открытом виде.

| Колонка | Тип | Описание |
|---|---|---|
| `key` | `String`, PK | Один из `EmailSettingsKeys` |
| `value` | `String` | Значение (всё как строка, включая числа/булевы) |
| `updatedAt` | `Long` | Timestamp последнего изменения |

### `report_send_log`
Журнал отправки email-отчётов, по одной записи на дату отправки.

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `Long`, PK, autoGenerate | |
| `date` | `String` | `YYYY-MM-DD` |
| `status` | `String` | `SUCCESS` / `FAILED` / `PENDING` |
| `errorMessage` | `String?` | Текст ошибки при неудаче |
| `recipientCount` | `Int` | Сколько получателей затронула отправка |
| `sentAt` | `Long` | Timestamp отправки |

## ⚠️ Находка: `MIGRATION_2_3` не соответствует текущим entity

`Migrations.kt` (`MIGRATION_2_3`, версия 2 → 3) создаёт таблицы `email_recipient` (без `s`), `email_settings`
и `report_send_log` со схемой, которая **не совпадает** с текущими `@Entity`-классами:

- `email_recipient` (миграция) vs `email_recipients` (реальная entity/DAO) — **разные имена таблиц**.
- Колонки `email_settings` в миграции (`smtp_host`, `login`, `app_password`, `from_address`, `send_time` —
  единое числовое поле) не совпадают со схемой `EmailSettingsEntity` (key-value: `key`, `value`,
  `updatedAt`).
- Колонки `report_send_log` в миграции (`error`, без `recipientCount`) не совпадают с `ReportSendLogEntity`
  (`errorMessage`, `recipientCount`).

Это значит, что при апгрейде БД с версии 2 на 3 Room, скорее всего, обнаружит несовпадение схемы после
миграции и попадёт в `.fallbackToDestructiveMigration()` — то есть **вся база будет пересоздана с нуля**,
без предупреждения, вместо аккуратного переноса данных. Согласно `CLAUDE.md`, destructive fallback — это
safety-net, на который **не следует полагаться** при реальной необходимости миграции; здесь, похоже, именно
такой случай. Фиксирую как находку, в код не вмешивался — правка миграции не входила в задачу.

`MIGRATION_3_4` (версия 3 → 4, добавлена вместе с фичей "downtime из heartbeat gaps") — простой
`DROP TABLE IF EXISTS service_downtime_events`, схем не создаёт и потому не подвержена той же проблеме.

## ⚠️ Находка: `AppDatabase.DATABASE_VERSION` не совпадает с `@Database(version = ...)`

`AppDatabase.kt` объявляет `@Database(version = 4)`, но компаньон-константа `DATABASE_VERSION` всё ещё
равна `3`. Room использует версию из аннотации `@Database`, а не эту константу, так что на поведение схемы
это не влияет — но сама константа нигде фактически не читается для реальной версии БД, и её значение вводит
в заблуждение при чтении кода. Фиксирую как находку, в код не вмешивался — правка не входила в задачу.
