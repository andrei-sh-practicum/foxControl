# База данных: таблицы Room (`AppDatabase`)

> Справочный документ, сгенерирован по коду. Не план — просто фиксация текущего состояния схемы.

- Файл БД: Room, `AppDatabase` (`data/local/AppDatabase.kt`), текущая **версия 3** (`DATABASE_VERSION`).
- Миграции регистрируются в `di/AppModule.kt` (`.addMigrations(MIGRATION_2_3)`), плюс подключён
  `.fallbackToDestructiveMigration()` как safety-net (см. предупреждение в конце документа).
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
| `service_downtime_events` | `ServiceDowntimeEventEntity` | `ServiceDowntimeEventDao` | Периоды простоя foreground-сервиса |
| `alert_logs` | `AlertLogEntity` | `AlertLogDao` | Журнал сработавших алертов о превышении лимита |
| `email_recipients` | `EmailRecipientEntity` | `EmailRecipientDao` | Получатели email-отчётов |
| `email_settings` | `EmailSettingsEntity` | `EmailSettingsDao` | SMTP-настройки как key-value пары |
| `report_send_log` | `ReportSendLogEntity` | `ReportSendLogDao` | Журнал отправки email-отчётов |

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

### `service_downtime_events`
Периоды, когда сервис не подавал heartbeat (вычисляются по разрывам между записями `service_heartbeats`).

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `Long`, PK, autoGenerate | |
| `startTime` / `endTime` | `Long` | Границы простоя |
| `reason` | `String` | Причина/описание простоя |

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
