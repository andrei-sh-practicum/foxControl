# Экран "Детали приложения" — часовая диаграмма вместо блока "Сессии"

> Это план, код не менялся (по просьбе пользователя — только планирование). Составлен по результатам
> чтения текущего кода, ветка `master`, коммит `1ae6464`. Все ссылки на файлы/строки актуальны на этот
> момент.

## 1. Задача

На экране "Детали приложения" (`ui/appdetail`) убрать текущий блок "Сессии" (список карточек с датой и
длительностью каждой сессии) и заменить его столбчатой диаграммой использования приложения по часам —
16 столбцов, диапазон 06:00–22:00 (аналогично существующему графику простоя на Debug-экране, но по одному
приложению и по факту использования, а не простоя сервиса).

## 2. Что уже есть в коде (переиспользовать, не изобретать заново)

### 2.1 Текущий блок "Сессии" — что удаляется

`AppDetailScreen.kt:189-217` — инлайновый блок без отдельного composable: заголовок `Text("Сессии", ...)`
(строки 191-194) и `state.sessions.forEach { session -> Card { ... } }` (строки 196-216), каждая карточка
показывает `session.date` и `formatDuration(session.durationMs)`. Условие показа — `state.sessions.isNotEmpty()`
(строка 190). Именно этот блок (строки 189-217) удаляется целиком и заменяется новым composable с графиком.

Карточка "Сегодня" выше (`AppDetailScreen.kt:157-187`, время использования + `"${state.sessionCount} сессий"`)
**не трогается** — задача просит заменить только блок "Сессии", не сводку.

### 2.2 Источник данных — сессии уже грузятся по нужному пакету/дате

`AppDetailViewModel.loadAppDetail()` (`AppDetailViewModel.kt:24-52`) уже вызывает
`repository.getTodaySessionsForPackage(packageName, today)` (строка 33), где `today` — строка `yyyy-MM-dd`
текущей даты (строка 32, см. также §5 про отсутствие выбора дня). Возвращается `List<UsageSessionEntity>`
(`data/local/entity/UsageSessionEntity.kt:6-17`) с полями `startTime`/`endTime` (epoch ms) — этого достаточно
для разбивки по часам, **новый DAO-запрос не нужен**, метод `UsageSessionDao.getSessionsByPackageAndDate`
(`UsageSessionDao.kt:15-16`) уже отдаёт все сессии пакета за день одним списком.

Метод `getTodaySessionsForPackage` объявлен на доменном интерфейсе
(`domain/repository/UsageStatsRepository.kt:18`) и реализован в `UsageStatsRepositoryImpl`
(`data/repository/UsageStatsRepository.kt:111-113`, файл называется `UsageStatsRepository.kt`, но класс —
`UsageStatsRepositoryImpl`) — то есть данные уже полностью доступны там, где нужно.

### 2.3 Готовый алгоритм разбивки по часам — `DowntimeCalculator`

`core/tracking/DowntimeCalculator.kt` — чистая функция `DowntimeCalculator.calculate(...)` (строки 54-142),
которая уже делает почти то же самое, что нужно здесь, только для простоев сервиса:
- бьёт временные интервалы на часовые бакеты `06..21` (`WINDOW_START_HOUR=6`, `WINDOW_END_HOUR=22`,
  строки 42-43);
- клиппирует каждый интервал `[start, end)` по границам часа и по `now` (обработка "будущих" часов —
  строки 105-109);
- возвращает `List<DowntimeHourBucket>` (`hour`, две метрики в минутах) — ровно 16 бакетов на часы 6..21.

Для сессий использования алгоритм тот же самый: вместо "разрывов между heartbeat" на вход идут интервалы
`[session.startTime, session.endTime)` самих сессий, а вместо `aliveMinutes`/`deadMinutes` считается одна
метрика — `usageMinutes`. Логику клиппинга по границам часа и по "будущим" часам можно скопировать почти
дословно.

**Вычисление границ дня** (`dayStart`/`dayEnd`) уже есть готовым паттерном в
`UsageStatsRepositoryImpl.getServiceDowntimeBuckets()` (`data/repository/UsageStatsRepository.kt:285-313`,
конкретно `Calendar` на 00:00:00.000–23:59:59.999, строки 288-300) — использовать тот же способ, чтобы не
заводить в проекте второй способ считать "начало дня" (аналогичное замечание уже зафиксировано в
`_docs/bd_tables.md` для `alert_logs`).

### 2.4 Готовый визуальный паттерн — `ServiceDowntimeChart`

`ui/debug/DebugScreen.kt`, `ServiceDowntimeChart` (строки 474-576), сейчас на Debug-экране (
был перенесён туда с Home коммитом `14500e1`). Важно: **в проекте нет ни одной сторонней библиотеки чартов**
(`app/build.gradle.kts:98-142` — только Compose UI/Material3/Foundation, Navigation, Room, Hilt, DataStore,
security-crypto, Coil, Jakarta Mail) и нет использования `Canvas`/`drawXxx` — весь график собран из обычных
Compose-примитивов:
- `Row(horizontalArrangement = SpaceBetween)` — один `Column` на час, `Modifier.weight(1f)` (строки 493-503);
- сам столбец — `Box` с `Modifier.fillMaxHeight(fraction)`, где `fraction = минуты / 60f` (строки 536-560),
  плюс фон/форма через `Modifier.background(color, MaterialTheme.shapes.extraSmall)`;
- сетка — не Canvas, а цикл `Divider` со смещением `Modifier.offset` (строки 519-534);
- подписи часов — `Text` через одну (`bucket.hour % 2 == 0`, строки 564-571), формат `padStart(2, '0')`.

**Решение:** новый график полностью повторяет этот паттерн (тот же `Row`/`Column`/`Box`/`weight`/
`fillMaxHeight(fraction)`), без новой зависимости — только с одним цветом столбца (использование) вместо
двух стековых (alive/dead), т.к. у нас одна метрика, а не две.

## 3. Домены/модели — что нужно добавить

Ничего в `domain/model/UsageStats.kt` (там только `UsageStats`/`DailyUsageStats`/`WeeklyUsageStats`,
все — дневные/недельные агрегаты, часовых моделей нет) переиспользовать не получится — нужна новая, но по
образцу уже существующей `DowntimeHourBucket`:

```kotlin
// core/tracking/ или domain/model/ — на усмотрение реализации, см. §6
data class AppUsageHourBucket(
    val hour: Int,        // 6..21, то же соглашение, что у DowntimeHourBucket
    val usageMinutes: Int // минуты использования приложения в этом часе, 0..60
)
```

И чистая функция-калькулятор по образцу `DowntimeCalculator`:

```kotlin
object AppUsageHourCalculator {
    // windowStartHour=6, windowEndHour=22 — те же константы
    fun calculate(
        sessions: List<Pair<Long, Long>>, // (startTime, endTime) в ms, за один день
        now: Long,
        windowStartHour: Int = 6,
        windowEndHour: Int = 22
    ): List<AppUsageHourBucket>
}
```

Логика: для каждого из 16 часовых бакетов просуммировать пересечения всех сессий с окном
`[hourStartMs, min(hourEndMs, now))` (тот же приём "клиппинг + сумма в мс, деление на 60_000 в конце", что
в `DowntimeCalculator.calculate`, строки 122-136) — в отличие от простоя, тут не нужен шаг "gaps между
heartbeat" (шаги 1-4 `DowntimeCalculator`), сессии уже готовые интервалы, отдаваемые репозиторием.

## 4. Слой данных/репозиторий — новый метод

Новый метод на `domain/repository/UsageStatsRepository.kt` (рядом с `getTodaySessionsForPackage`,
`getServiceDowntimeBuckets`) и его реализация в `UsageStatsRepositoryImpl`
(`data/repository/UsageStatsRepository.kt`, рядом с `getServiceDowntimeBuckets`, строки 285-313):

```kotlin
suspend fun getHourlyUsageForPackage(packageName: String, date: String): List<AppUsageHourBucket>
```

Реализация:
1. Границы дня — тот же `Calendar`-паттерн, что в `getServiceDowntimeBuckets` (строки 288-300).
2. Данные — переиспользовать уже существующий `usageSessionDao.getSessionsByPackageAndDate(packageName, date)`
   (тот же вызов, что уже прячется за `getTodaySessionsForPackage`, `UsageSessionDao.kt:15-16`) — **новый
   DAO-метод не нужен**, т.к. `startTime`/`endTime` уже есть в возвращаемых `UsageSessionEntity`.
3. Смапить `sessions.map { it.startTime to it.endTime }` → `AppUsageHourCalculator.calculate(..., now =
   System.currentTimeMillis())`.
4. try/catch с логированием через `TrackingLogStorage.add("Repo", ...)` — по аналогии с
   `getServiceDowntimeBuckets` (строки 304, 308-309) и правилом из CLAUDE.md "предпочитать
   `TrackingLogStorage` вместо `Log.d` в трекинг/repo пайплайне"; при ошибке — вернуть 16 пустых бакетов
   (`(6..21).map { AppUsageHourBucket(it, 0) }`, по аналогии со строкой 311).

Изменений схемы БД (`AppDatabase.DATABASE_VERSION`, миграции) **не требуется** — используются существующие
колонки `usage_sessions.startTime`/`endTime`, никаких новых таблиц/полей.

## 5. UI-слой

### 5.1 `AppDetailViewModel.kt`

- В `loadAppDetail()` (строки 24-52) добавить вызов `repository.getHourlyUsageForPackage(packageName, today)`
  рядом с уже существующим `getTodaySessionsForPackage` (строка 33) — используется тот же `today`, тот же
  паттерн "всегда сегодня" (см. §6 про отсутствие выбора дня — это не блокер, а фиксация текущего поведения).
- `AppDetailState` (строки 55-62): добавить `val hourlyUsage: List<AppUsageHourBucket> = emptyList()`.
- Поле `sessions: List<UsageSessionEntity>` — **можно удалить**, если оно после замены блока больше нигде
  не используется на экране (сейчас единственный потребитель — сам блок "Сессии", который убирается); перед
  удалением проверить, не читает ли его что-то ещё (по состоянию на текущий код — не читает, но перепроверить
  на момент реализации). Если оставить "на всякий случай" — не страшно, но по правилу CLAUDE.md
  ("не добавлять недостижимый код/не используемые абстракции") логичнее убрать вместе с блоком.

### 5.2 `AppDetailScreen.kt`

- Удалить блок строк 189-217 ("Sessions list").
- На его место — новый composable `AppUsageHourlyChart(buckets: List<AppUsageHourBucket>)`, по образцу
  `ServiceDowntimeChart` (`DebugScreen.kt:474-576`):
  - Заголовок-сводка вместо "Простои сегодня (06:00–22:00): N мин" → что-то вроде
    `"Активность по часам (06:00–22:00)"` (без суммы — сумма уже показана выше в карточке "Сегодня",
    дублировать не нужно).
  - Тот же `Row`/`Column`/`Box`, но **один** цвет столбца (`MaterialTheme.colorScheme.primary`, как
    "alive"-цвет в исходнике) на всю высоту `fillMaxHeight(bucket.usageMinutes / 60f)`, вместо двух
    стековых Box'ов (dead+alive) — стек не нужен, т.к. метрика одна.
  - Сетка (`Divider`-линии) и подписи часов через одну — переносятся без изменений (строки 519-534, 563-571).
  - Условие показа: по аналогии с текущим `state.sessions.isNotEmpty()` — можно показывать график всегда
    (даже если весь день пустой, столбцы будут нулевой высоты, это нормально и соответствует поведению
    `ServiceDowntimeChart`, который на Debug-экране рисуется безусловно) **либо** прятать при
    `hourlyUsage.all { it.usageMinutes == 0 }`, чтобы не показывать пустой график вообще — выбор поведения
    стоит согласовать при реализации, в остальном не блокирует план.
- Заголовок блока: `Text("Использование по часам", style = MaterialTheme.typography.titleLarge)` вместо
  `Text("Сессии", ...)` (строки 191-194) — конкретную формулировку можно уточнить при реализации.

### 5.3 Размещение файла новых моделей/калькулятора

По аналогии с `DowntimeHourBucket`/`DowntimeCalculator`, которые лежат в `core/tracking/` (не в `domain/model`,
хотя формально это доменные данные) — для консистентности с уже принятым в проекте местом лучше положить
`AppUsageHourBucket`/`AppUsageHourCalculator` тоже в `core/tracking/` (новый файл, например
`AppUsageHourCalculator.kt`), а не заводить новый паттерн размещения специально для этой фичи.

## 6. Открытый вопрос — день всегда "сегодня"

На экране нет выбора даты: `AppDetailViewModel.loadAppDetail()` жёстко берёт `today =
SimpleDateFormat("yyyy-MM-dd").format(Date())` (строка 32), вызывается один раз через
`LaunchedEffect(packageName)` в `AppDetailScreen.kt` (строки 51-53). Новый график по этой же причине тоже
будет показывать часы только за сегодня (без возможности посмотреть график за вчера/другой день) — это
**не регрессия** относительно текущего блока "Сессии" (он тоже жёстко "сегодня"), просто фиксирую как факт:
если в будущем понадобится выбор дня для графика — это отдельная фича (добавление date-picker'а на экран),
не входит в объём данной задачи.

## 7. Итоговый чек-лист реализации

- [ ] Новая модель `AppUsageHourBucket(hour: Int, usageMinutes: Int)` — `core/tracking/AppUsageHourCalculator.kt`.
- [ ] Новая чистая функция `AppUsageHourCalculator.calculate(sessions: List<Pair<Long, Long>>, now: Long, ...)`
      — по образцу `DowntimeCalculator.calculate` (`core/tracking/DowntimeCalculator.kt:54-142`), без шагов
      1-4 (gaps между heartbeat не нужны — сессии уже готовые интервалы).
- [ ] Новый метод `getHourlyUsageForPackage(packageName, date)` — добавить в
      `domain/repository/UsageStatsRepository.kt` (рядом со строкой 18/20) и реализовать в
      `UsageStatsRepositoryImpl` (`data/repository/UsageStatsRepository.kt`, рядом со строками 285-313),
      переиспользуя существующий `usageSessionDao.getSessionsByPackageAndDate`.
- [ ] `AppDetailViewModel.kt`: загрузка `hourlyUsage` в `loadAppDetail()` (строка 33), новое поле в
      `AppDetailState` (строки 55-62), убрать `sessions`, если больше не используется.
- [ ] `AppDetailScreen.kt`: удалить блок "Сессии" (строки 189-217), добавить `AppUsageHourlyChart(...)` по
      образцу `ServiceDowntimeChart` (`ui/debug/DebugScreen.kt:474-576`), с одним цветом столбца вместо двух.
- [ ] Согласовать при реализации: точный текст заголовка блока и поведение при пустом дне (показывать
      нулевой график или скрывать блок целиком, см. §5.2).

Изменений схемы БД не требуется. Новая зависимость (библиотека чартов) не требуется — переиспользуется
имеющийся hand-rolled Compose-подход из `ServiceDowntimeChart`.
