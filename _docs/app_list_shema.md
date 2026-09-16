# Главная (вкладка "Сегодня") — общая почасовая диаграмма использования по всем приложениям

**Статус: план, код не менялся.**

## 1. Текущее состояние

### 1.1 Диаграмма по часам (одно приложение), уже реализована в "Детали приложения"

- `AppUsageHourlyChart` — **private** composable внутри
  `app/src/main/java/com/andrew/foxcontrol/ui/appdetail/AppDetailScreen.kt:208-330`.
  Принимает `buckets: List<AppUsageHourBucket>`.
- Модель `AppUsageHourBucket(hour: Int /* 6..21 */, usageMinutes: Int /* 0..60 */)` —
  `core/tracking/AppUsageHourCalculator.kt:11-14`.
- Данные готовит `AppUsageHourCalculator.calculate(intervals, now)` (чистая функция,
  `core/tracking/AppUsageHourCalculator.kt:42-83`) — клиппит интервалы сессий по 16 часовым
  окнам (06..21) и суммирует пересечения в минутах. Функция **не завязана на пакет** — принимает
  произвольный список интервалов `(startTime, endTime)`.
- Данные для одного приложения загружаются в `AppDetailViewModel.loadAppDetail()` (`:40`) через
  `repository.getHourlyUsageForPackage(packageName, today)` —
  `domain/repository/UsageStatsRepository.kt:22` →
  `data/repository/UsageStatsRepository.kt:317-329` (класс `UsageStatsRepositoryImpl`):
  ```kotlin
  override suspend fun getHourlyUsageForPackage(packageName: String, date: String): List<AppUsageHourBucket> {
      val sessions = usageSessionDao.getSessionsByPackageAndDate(packageName, date)
      val intervals = sessions.map { it.startTime to it.endTime }
      return AppUsageHourCalculator.calculate(intervals, System.currentTimeMillis())
  }
  ```
- Источник данных — таблица `usage_sessions` (`data/local/entity/UsageSessionEntity.kt`, поля
  `packageName, startTime, endTime, date, ...`), **завершённые сессии**, не heartbeats.
- Визуально: 16 горизонтальных строк, бар растёт слева направо, легенда-шкала `0 10 20 30 40 50 60`
  над строками, сетка `gridLines = 6` (позиционирование через `fillMaxWidth(fraction) + CenterEnd`,
  фикс из коммита `93fcab5`). Заголовок сейчас захардкожен: `"Использование по часам (06:00–22:00)"`.

### 1.2 Экран "Главная" / вкладка "Сегодня"

- `app/src/main/java/com/andrew/foxcontrol/ui/home/HomeScreen.kt` +
  `.../ui/home/HomeViewModel.kt`.
- Это не Compose-табы, а `SingleChoiceSegmentedButtonRow` с двумя `HomePeriod` (`Today`/`Week`,
  `HomeViewModel.kt:133-136`) — один экран, ветвление по `state.period`.
- Структура `HomeContent` (`HomeScreen.kt:91-304`) сверху вниз:
  1. Переключатель периода "Сегодня"/"Неделя" (98-115).
  2. `ExceededLimitsBlock` — только если `Today && exceededApps.isNotEmpty()` (118-123).
  3. Карточка "Итого за день"/"Итого за неделю" (126-172).
  4. Ветвление `isLoading` → `error != null` → empty-state (Today/Week раздельно) → иначе:
  5. **`LazyColumn`** со списком `AppUsageCard` (279-302) — **это и есть "лисинг приложений"**,
     новую диаграмму нужно вставить непосредственно перед ним, т.е. в конце финальной
     (успешной, не-loading/не-error/не-empty) ветки, после карточки "Итого".
- `AppUsageCard` — тоже **private** composable, `HomeScreen.kt:308`.

### 1.3 Готовой агрегации "по всем приложениям за час" нет

Проверено: ни в `UsageStatsRepositoryImpl`, ни в DAO нет метода/SQL-запроса, суммирующего usage
по часам без разбивки на пакет. Ближайший по духу существующий паттерн — почасовой downtime
(`getServiceDowntimeBuckets` / `DowntimeCalculator`), но это другая метрика (простой сервиса по
heartbeats, не usage по сессиям) и трогать её не нужно.

Зато уже есть всё нужное, чтобы получить агрегат "малой кровью":

- `UsageSessionDao.getSessionsByDate(date)` (`data/local/dao/UsageSessionDao.kt:9-13`) — уже
  возвращает **все** сессии за дату по **всем** пакетам, без фильтра по `packageName`.
- `AppUsageHourCalculator.calculate(...)` — уже общий, ему всё равно, откуда взялись интервалы.

Значит: новый SQL/DAO-запрос **не требуется**, новая агрегация в БД **не требуется** — нужен
только новый repository-метод, повторяющий `getHourlyUsageForPackage`, но без фильтра по пакету.

### 1.4 Переиспользуемые composable-компоненты

Единственный существующий shared-package — `ui/common/` (`AppIcon.kt`, `FormatUtils.kt`).
Папок `ui/components/` или `ui/shared/` в проекте нет. `AppUsageHourlyChart` сейчас лежит
private в `ui/appdetail/AppDetailScreen.kt` — чтобы использовать её и на Home, и в Details, её
нужно вынести в `ui/common/` по аналогии с `AppIcon.kt`.

## 2. Задача

На вкладке "Сегодня" экрана "Главная" показать ту же диаграмму по часам (что уже есть в "Деталях
приложения"), но с агрегацией **по всем приложениям сразу** за сегодня. Расположение — **перед
списком приложений** (перед `LazyColumn`, после карточки "Итого за день").

## 3. Что меняется / что не меняется

| Элемент | Меняется? |
|---|---|
| `AppUsageHourBucket`, `AppUsageHourCalculator` | Нет — переиспользуются как есть |
| `usage_sessions` (схема БД, `AppDatabase.DATABASE_VERSION`) | Нет — новых таблиц/колонок/миграций не требуется |
| `UsageSessionDao.getSessionsByDate(date)` | Нет — уже существует и уже подходит |
| `getHourlyUsageForPackage` / `AppDetailViewModel` / детали приложения (логика) | Нет |
| `AppUsageHourlyChart` — **расположение** | Да — переносится из `private fun` в `AppDetailScreen.kt` в public composable в `ui/common/` |
| `AppUsageHourlyChart` — **визуал/логика отрисовки** | Нет — 1-в-1 та же раскладка (16 строк, легенда 0-60, сетка), только заголовок параметризуется |
| `AppDetailScreen.kt` | Да — минимальная правка: импорт общего компонента вместо локального |
| `domain/repository/UsageStatsRepository.kt` | Да — новый метод `getHourlyUsageForAllApps(date)` |
| `data/repository/UsageStatsRepository.kt` (`UsageStatsRepositoryImpl`) | Да — реализация нового метода |
| `HomeViewModel` / `HomeState` | Да — новое поле с почасовыми данными за сегодня + его загрузка |
| `HomeScreen.kt` | Да — вставка чарта перед `LazyColumn`, только для `period == Today` |
| Вкладка "Неделя" | Нет — почасовой агрегации для недели не будет (только "за сегодня", по задаче) |
| `ServiceDowntimeChart` (Debug-экран) | Нет, не относится к задаче |

## 4. Реализация

### 4.1 Вынести `AppUsageHourlyChart` в shared-компонент

- Новый файл `ui/common/AppUsageHourlyChart.kt` (package `com.andrew.foxcontrol.ui.common`),
  публичный `@Composable fun AppUsageHourlyChart(buckets: List<AppUsageHourBucket>, title: String)`.
  Вся текущая раскладка (`AppDetailScreen.kt:208-330`) переносится как есть, единственное
  изменение — заголовок (`"Использование по часам (06:00–22:00)"`) становится параметром `title`
  вместо захардкоженной строки.
- `AppDetailScreen.kt` обновляется: локальный `private fun AppUsageHourlyChart` удаляется, вызов
  (строка 202) заменяется на импорт из `ui.common` с прежним заголовком — поведение экрана
  "Детали приложения" не меняется.

### 4.2 Новый repository-метод — агрегация по всем приложениям

`domain/repository/UsageStatsRepository.kt`:
```kotlin
suspend fun getHourlyUsageForAllApps(date: String): List<AppUsageHourBucket>
```

`data/repository/UsageStatsRepository.kt` (`UsageStatsRepositoryImpl`), по образцу
`getHourlyUsageForPackage` (:317-329), без фильтра по пакету:
```kotlin
override suspend fun getHourlyUsageForAllApps(date: String): List<AppUsageHourBucket> {
    val sessions = usageSessionDao.getSessionsByDate(date)
    val intervals = sessions.map { it.startTime to it.endTime }
    return AppUsageHourCalculator.calculate(intervals, System.currentTimeMillis())
}
```

### 4.3 `HomeViewModel`

- В `HomeState` — новое поле, например `hourlyUsageToday: List<AppUsageHourBucket> = emptyList()`.
- В корутине загрузки данных для `Today` (там же, где сейчас грузится `dailyStats`) —
  дополнительный вызов `repository.getHourlyUsageForAllApps(today)`, результат кладётся в state.
- Для `Week` поле не используется (диаграмма на вкладке "Неделя" не показывается).

### 4.4 `HomeScreen.kt`

- Вызов `AppUsageHourlyChart(buckets = state.hourlyUsageToday, title = "...")` вставляется
  строго перед `LazyColumn` (перед строкой ~279), т.е. в конце финальной ветки — после карточки
  "Итого за день", но уже вне веток `isLoading` / `error != null` / empty-state.
- Показывается только при `state.period == HomePeriod.Today` (аналогично `ExceededLimitsBlock`,
  который тоже условен на `Today`).

## 5. Что не меняется

- Схема БД / миграции — не требуются, `usage_sessions` не меняется.
- `AppUsageHourCalculator`, `AppUsageHourBucket` — без изменений.
- Логика/визуал диаграммы (16 строк 06..21, легенда 0-60, сетка `gridLines = 6`) — без изменений,
  только вынос в переиспользуемый public-компонент.
- Вкладка "Неделя", `ExceededLimitsBlock`, карточка "Итого" — без изменений.
- "Детали приложения" — поведение и данные не меняются, меняется только физическое расположение
  кода компонента.

## 6. Открытые вопросы (уточнить перед реализацией)

- Точный текст заголовка диаграммы на Home (например "Общее использование по часам (06:00–22:00)"
  или короче — "Активность по часам").
- Нужно ли скрывать диаграмму, если за все 16 часов usage = 0 (устройство сегодня не
  использовалось вовсе) — по аналогии с существующей empty-state веткой Today.
- Нужен ли отступ/разделитель между карточкой "Итого" и диаграммой, и между диаграммой и списком
  приложений (визуальное отделение блоков).

## Ответы:
- "Активность по часам"
- Нужно скрывать
- Нужен разделитель

## 7. Чек-лист реализации (для будущего PR)

- [ ] Вынести `AppUsageHourlyChart` из `AppDetailScreen.kt:208-330` в
      `ui/common/AppUsageHourlyChart.kt`, сделать public, параметризовать `title`.
- [ ] Обновить `AppDetailScreen.kt` — импорт общего компонента вместо локального private fun.
- [ ] Добавить `getHourlyUsageForAllApps(date)` в `domain/repository/UsageStatsRepository.kt`.
- [ ] Реализовать `getHourlyUsageForAllApps` в `UsageStatsRepositoryImpl`
      (`data/repository/UsageStatsRepository.kt`), переиспользуя `getSessionsByDate` +
      `AppUsageHourCalculator.calculate`.
- [ ] Добавить `hourlyUsageToday` в `HomeState`/`HomeViewModel.kt` и загрузку в существующей
      корутине загрузки Today-данных.
- [ ] Вставить `AppUsageHourlyChart` в `HomeScreen.kt` перед `LazyColumn` (строка ~279), только
      для `period == Today`, вне loading/error/empty веток.
- [ ] Прогнать существующие unit-тесты (`AppUsageHourCalculator` не менялся — не должны сломаться);
      по желанию — новый unit-тест на `getHourlyUsageForAllApps`.
- [ ] Собрать debug APK, вручную проверить обе вкладки (Home/"Сегодня" и AppDetail) на
      реальном устройстве/эмуляторе.

Новых зависимостей и изменений схемы БД не требуется.
