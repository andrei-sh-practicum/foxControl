# План: скрывать приложения с использованием < 1 минуты на вкладках "Сегодня"/"Вчера"

> Это план, код не менялся.

## Проблема
На вкладках "Сегодня" и "Вчера" главного экрана в списке приложений показываются
записи с суммарным временем использования меньше одной минуты. Это шум —
не несёт полезной информации родителю.

## Уточнение по вкладкам (важно)
Задача касается только
`Today` и `Yesterday`; вкладка `Week` ("Неделя") не меняется — таково явное условие
задачи ("Изменения должны коснуться только вывода данных на вкладках Сегодня, Вчера").

## Цель
1. В списке приложений на вкладках "Сегодня"/"Вчера" не показывать приложения,
   у которых `totalDurationMs < 60_000` (использование меньше минуты за день).
2. В сумме "Итого за день"/"Итого за вчера" (`totalUsageMs`) такие приложения
   не учитывать — сумма должна равняться сумме `totalDurationMs` только
   оставшихся (видимых) приложений.
3. Больше нигде поведение не меняется — лимиты, почасовой график, вкладка
   "Неделя", детальный экран приложения, email-отчёты продолжают работать
   с полными (нефильтрованными) данными.

## Затрагиваемые файлы
- `app/src/main/java/com/andrew/foxcontrol/ui/home/HomeViewModel.kt` — единственный
  файл, который нужно менять.
- `app/src/main/java/com/andrew/foxcontrol/ui/home/HomeScreen.kt` — менять не нужно:
  экран уже отображает то, что лежит в `state.dailyStats` / `state.yesterdayStats`
  (список `apps` и `totalUsageMs`), включая текст `"${dailyStats.apps.size}
  приложений"`. Если фильтрация выполнена в `ViewModel` до записи в `state`, экран
  автоматически получит уже отфильтрованные данные без изменений в своём коде.

## Почему фильтровать в `HomeViewModel`, а не в репозитории
`UsageStatsRepositoryImpl.getDailyUsage(date)` (`data/repository/UsageStatsRepository.kt:36`)
используется не только Home-экраном, но и:
- `getTopApps()`, `getUsageForPackage()`, `getTopEntertainmentApps()` (внутри того же
  репозитория, строки ~93-110);
- `AlertManager` / `TrackingJob` — проверка лимитов (вызывается напрямую через
  `UsageStatsRepositoryImpl`, не через интерфейс, см. `core/alerts`);
- потенциально `core/email` (отчёты).

Если убрать "короткие" приложения на уровне `getDailyUsage`, это незаметно изменит
поведение всех перечисленных потребителей (лимиты, отчёты, детальный экран) — а
задача явно ограничивает область изменений выводом на вкладках Home. Поэтому
фильтрация делается **только** в `HomeViewModel`, после получения данных из
репозитория, и **только** для полей, которые уходят в `HomeState` для отображения
списка/итога.

## Изменения в `HomeViewModel.kt`

### 1. Константа порога
```kotlin
companion object {
    private const val MIN_VISIBLE_APP_DURATION_MS = 60_000L // 1 минута
}
```

### 2. Функция фильтрации
```kotlin
private fun hideNegligibleApps(stats: DailyUsageStats): DailyUsageStats {
    val visibleApps = stats.apps.filter { it.totalDurationMs >= MIN_VISIBLE_APP_DURATION_MS }
    return stats.copy(
        apps = visibleApps,
        totalUsageMs = visibleApps.sumOf { it.totalDurationMs }
    )
}
```
Работает с `DailyUsageStats`, поэтому переиспользуется одинаково для `Today` и
`Yesterday` (обе ветки уже получают данные через `getDailyUsage(date)`).

### 3. Где вызывать — ветка `HomePeriod.Today`
Сейчас (`HomeViewModel.kt:49-66`):
```kotlin
val dailyStats = usageStatsRepository.getDailyUsage(today)
val appLimits = usageStatsRepository.getAppLimits()
val exceededApps = computeExceededApps(dailyStats, appLimits)
val hourlyUsageToday = usageStatsRepository.getHourlyUsageForAllApps(today)

_state.update { it.copy(dailyStats = dailyStats, ...) }
```
Важно: `computeExceededApps` и `hourlyUsageToday` должны считаться от **сырых**
данных (`dailyStats` до фильтрации) — превышение лимита и почасовая активность не
входят в область задачи и не должны зависеть от нового порога. Меняется только то,
что кладётся в `state.dailyStats`:
```kotlin
val rawDailyStats = usageStatsRepository.getDailyUsage(today)
val appLimits = usageStatsRepository.getAppLimits()
val exceededApps = computeExceededApps(rawDailyStats, appLimits)   // без изменений, от rawDailyStats
val hourlyUsageToday = usageStatsRepository.getHourlyUsageForAllApps(today) // без изменений

val dailyStats = hideNegligibleApps(rawDailyStats)                // <- новое: для отображения

_state.update {
    it.copy(
        dailyStats = dailyStats,
        exceededApps = exceededApps,
        hourlyUsageToday = hourlyUsageToday,
        ...
    )
}
```

### 4. Где вызывать — ветка `HomePeriod.Yesterday`
Аналогично, по образцу `HomeViewModel.kt:68-90`:
```kotlin
val rawYesterdayStats = usageStatsRepository.getDailyUsage(yesterday)
val appLimits = usageStatsRepository.getAppLimits()
val exceededAppsYesterday = computeExceededApps(rawYesterdayStats, appLimits) // от raw
val hourlyUsageYesterday = usageStatsRepository.getHourlyUsageForAllApps(yesterday) // без изменений

val yesterdayStats = hideNegligibleApps(rawYesterdayStats)          // <- новое

_state.update {
    it.copy(
        yesterdayStats = yesterdayStats,
        exceededAppsYesterday = exceededAppsYesterday,
        hourlyUsageYesterday = hourlyUsageYesterday,
        ...
    )
}
```

### 5. Ветка `HomePeriod.Week` — без изменений
`getWeeklyUsage` и всё, что кладётся в `weeklyStats`, не трогаем.

## Побочные эффекты (уже "бесплатно" работают благодаря фильтрации в одном месте)
- Текст `"${dailyStats.apps.size} приложений"` / `"${yesterdayStats.apps.size}
  приложений"` в `HomeScreen.kt` покажет уже отфильтрованное количество —
  дополнительных изменений в `HomeScreen.kt` не требуется.
- Пустое состояние ("Нет данных"): если у пользователя за день все приложения
  использовались меньше минуты, `dailyStats.apps` станет пустым списком, и уже
  существующее условие `state.dailyStats.apps.isEmpty()` (`HomeScreen.kt:268`,
  аналогично для `Yesterday` на строке ~296) само покажет экран "Нет данных" —
  тоже без изменений в `HomeScreen.kt`.

## Что НЕ меняется (явно)
- `UsageStatsRepository` / `UsageStatsRepositoryImpl` — сигнатуры и логика
  агрегации не трогаются.
- `computeExceededApps` и блок "⚠ Превышены лимиты" — считаются от полных данных,
  не от отфильтрованных (на практике не влияет: лимиты обычно заданы в минутах
  ≥ 1, приложение с использованием < 1 минуты почти никогда не превышает лимит,
  но семантически это разные вещи, и задача просит менять только "вывод", а не
  проверку лимитов).
- Почасовой график (`AppUsageHourlyChart`, `hourlyUsageToday` /
  `hourlyUsageYesterday`) — источник данных (`getHourlyUsageForAllApps`) отдельный
  от списка приложений и не фильтруется. Возможна лёгкая несогласованность: график
  теоретически может показать активность в каком-то часе от приложения, которое не
  попало в список ниже (если весь дневной суммарный расход у него < минуты). Это
  крайний случай (доли минуты, размазанные по часам) и, по формулировке задачи,
  вне её рамок — см. "Открытые вопросы".
- Вкладка "Неделя" (`Week`) — не меняется.
- Экран деталей приложения (`ui/appdetail`) — вне рамок задачи, продолжает
  показывать полную статистику по конкретному пакету независимо от порога.
- `core/email` (отчёты), `core/alerts` (`AlertManager`) — используют репозиторий
  напрямую, не через `HomeViewModel`, поэтому не затронуты в принципе.
- Схема БД / миграции — не требуются, изменение чисто в UI-слое (`ViewModel`).

## Открытые вопросы (на усмотрение пользователя перед реализацией)
1. Порог ровно `< 60_000 мс`: приложение с использованием **ровно 60 секунд**
   считается видимым (не меньше минуты). Подтвердить, что граница именно такая
   ("меньше минуты" исключаем, "минута и больше" показываем).
2. Почасовой график — оставить без фильтрации (как описано выше) или тоже
   убирать вклад "коротких" приложений из часовых столбцов? По умолчанию план
   предполагает **не трогать график** (задача говорит про список и итог, не про
   график).
3. Не нужно ли то же поведение впоследствии для вкладки "Неделя"? Сейчас явно
   вне рамок — фиксирую как потенциальный follow-up, отдельным запросом.

## Ответы
1. Да ровно `< 60_000 мс`
2. Почасовой график — оставить без фильтрации
3. Не нужно 

## Порядок реализации (для последующего PR)
1. Добавить константу `MIN_VISIBLE_APP_DURATION_MS` и функцию `hideNegligibleApps`
   в `HomeViewModel.kt`.
2. В ветке `HomePeriod.Today`: посчитать `exceededApps`/`hourlyUsageToday` от
   "сырых" данных, применить `hideNegligibleApps` только к тому, что уходит в
   `state.dailyStats`.
3. Аналогично для ветки `HomePeriod.Yesterday` (`state.yesterdayStats`).
4. Ручная проверка: день/вчера с приложениями и < 1 мин, и ≥ 1 мин — короткие
   пропадают из списка, итог уменьшается ровно на их суммарное время, счётчик
   "N приложений" уменьшается, блок превышенных лимитов и график не меняются,
   вкладка "Неделя" не меняется.
