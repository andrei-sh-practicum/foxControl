# План: вкладка "Вчера"

## Цель
На главном экране между вкладками "Сегодня" и "Неделя" добавить вкладку "Вчера" —
полный аналог "Сегодня" (итог, блок превышенных лимитов, почасовой график, список
приложений), но с данными за вчерашний день.

## Затрагиваемые файлы
- `app/src/main/java/com/andrew/foxcontrol/ui/home/HomeViewModel.kt`
- `app/src/main/java/com/andrew/foxcontrol/ui/home/HomeScreen.kt`

Изменения только в UI-слое главного экрана. Репозиторий менять не требуется:
`getDailyUsage(date: String)` и `getHourlyUsageForAllApps(date: String)` уже
принимают произвольную дату в формате `yyyy-MM-dd`, так что для "вчера" их можно
вызвать с датой `today - 1 день`.

## 1. HomeViewModel.kt

### 1.1 Enum периода
Добавить `Yesterday` между `Today` и `Week`, сохранив порядок отображения вкладок:
```kotlin
enum class HomePeriod(val label: String) {
    Today("Сегодня"),
    Yesterday("Вчера"),
    Week("Неделя")
}
```
Порядок вкладок в UI определяется `HomePeriod.values()` (см. `HomeScreen.kt`), так
что достаточно правильно расположить константы в enum — дополнительной сортировки
не нужно.

### 1.2 Segmented-кнопки на 3 варианта
В `HomeScreen.kt` вызов `SegmentedButtonDefaults.itemShape(index = index, count = 2)`
жёстко использует `count = 2`. Нужно заменить на
`count = HomePeriod.values().size` (или `.entries.size`), иначе форма кнопок сломается
при трёх вкладках.

### 1.3 Состояние (HomeState)
Добавить отдельное поле для статистики за вчера, не переиспользуя `dailyStats`,
чтобы не путать "сегодня" и "вчера" и не терять данные при переключении:
```kotlin
val yesterdayStats: DailyUsageStats? = null,
val exceededAppsYesterday: List<ExceededAppInfo> = emptyList(),
val hourlyUsageYesterday: List<AppUsageHourBucket> = emptyList(),
```
Вопрос дизайна: показывать ли блок "Превышены лимиты" на вкладке "Вчера".
Рекомендация — показывать, т.к. это полезно ("вчера лимит был превышен"), но
рассчитывать его от **текущих** лимитов (`getAppLimits()`), т.к. отдельной
таблицы истории лимитов в БД нет. Это единственное смысловое допущение плана —
если пользователь менял лимит сегодня, "превышение" за вчера будет считаться по
новому лимиту. Об этом стоит явно решить при реализации (см. раздел "Открытые
вопросы").

### 1.4 Загрузка данных (loadStatistics)
Добавить ветку `HomePeriod.Yesterday`, параллельную существующей ветке `Today`:
```kotlin
HomePeriod.Yesterday -> {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

    val yesterdayStats = usageStatsRepository.getDailyUsage(yesterday)
    val appLimits = usageStatsRepository.getAppLimits()
    val exceededApps = computeExceededApps(yesterdayStats, appLimits)
    val hourlyUsageYesterday = usageStatsRepository.getHourlyUsageForAllApps(yesterday)

    _state.update {
        it.copy(
            yesterdayStats = yesterdayStats,
            exceededAppsYesterday = exceededApps,
            hourlyUsageYesterday = hourlyUsageYesterday,
            dailyStats = null,
            weeklyStats = null,
            isLoading = false
        )
    }
}
```
`computeExceededApps` переиспользуется как есть — она принимает `DailyUsageStats`
и список лимитов, от периода не зависит.

При желании оставить обнуление `dailyStats`/`weeklyStats` под вопросом: сейчас код
и так каждый раз перезатирает неактивные поля (см. текущие ветки Today/Week) —
паттерн сохраняем для консистентности.

## 2. HomeScreen.kt

Каждый блок, который сейчас использует `if (state.period == HomePeriod.Today) ... else ...`
(бинарное ветвление на 2 периода), нужно расширить до `when (state.period)` на 3 варианта.
Конкретно:

1. **Блок превышенных лимитов** (строка ~120): условие
   `state.period == HomePeriod.Today && state.exceededApps.isNotEmpty()` →
   добавить аналогичный блок для `HomePeriod.Yesterday`, использующий
   `state.exceededAppsYesterday`.

2. **Итоговая сводка "Итого за день/неделю"** (строки ~128–174): сейчас
   `if (Today) ... else (Week) ...`. Переписать как `when`:
   - `Today` → `state.dailyStats`, заголовок "Итого за день"
   - `Yesterday` → `state.yesterdayStats`, заголовок "Итого за вчера"
   - `Week` → `state.weeklyStats`, заголовок "Итого за неделю"

3. **Пустое состояние** (строки ~226–279): добавить ветку для `Yesterday`, аналогичную
   `Today`, но проверяющую `state.yesterdayStats`.

4. **Почасовой график** (строка ~282): сейчас показывается только для `Today`.
   Расширить условие до `Today || Yesterday`, при этом источник данных —
   `state.hourlyUsageToday` для Today и `state.hourlyUsageYesterday` для Yesterday.
   Заголовок графика можно оставить общим ("Активность по часам") или уточнить
   ("Активность по часам (вчера)").

5. **Список приложений** (строка ~293): сейчас
   `if (Today) dailyStats.apps else weeklyStats.apps`. Расширить до `when`, добавив
   `Yesterday -> state.yesterdayStats?.apps ?: emptyList()`.

6. **Кнопка "Повторить" при ошибке** — уже общая
   (`onEvent(HomeEvent.ChangePeriod(state.period))`), изменений не требует.

Компонент `AppUsageCard` и `ExceededLimitsBlock` переиспользуются без изменений —
они не завязаны на период, только принимают данные.

## 3. Что НЕ меняется
- `UsageStatsRepository` (интерфейс и реализация) — методы уже параметризованы датой.
- `AppUsageHourlyChart`, `AppIcon`, `formatDuration` и прочие общие компоненты.
- Экран деталей приложения (`appdetail`) — вне рамок задачи.
- Схема БД / миграции — не нужны, данные за вчера уже пишутся в `usage_sessions`
  как обычно.

## Открытые вопросы (на усмотрение пользователя перед реализацией)
1. Показывать ли блок "⚠ Превышены лимиты" на вкладке "Вчера"? Если да — по каким
   лимитам (текущим или "как они были вчера", учитывая, что истории лимитов нет)?
2. Нужен ли отдельный текст в пустом состоянии для "Вчера" ("Вчера не было
   активности" вместо общего "Статистика появится после начала использования
   приложений")?
3. Нужно ли включать вкладку "Вчера" в кнопку "Настройки" / экспорт отчётов
   (`core/email`) или это касается только Home-экрана? (По умолчанию план
   предполагает — только Home-экран.)

## Ответы
1. да, Показывать блок "⚠ Превышены лимиты"   
2. Нужен ли отдельный текст - нет.
3. только Home-экран.

## Порядок реализации (для последующего PR)
1. `HomePeriod` enum + `count` в `SegmentedButtonDefaults.itemShape`.
2. `HomeState` — новые поля.
3. `HomeViewModel.loadStatistics` — ветка Yesterday.
4. `HomeScreen.kt` — все `if/else` → `when` по пунктам 1–5 выше.
5. Ручная проверка на устройстве/эмуляторе: переключение между тремя вкладками,
   пустые состояния, состояние ошибки, блок превышенных лимитов.
