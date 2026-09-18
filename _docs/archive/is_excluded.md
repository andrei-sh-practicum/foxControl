# Исключение приложений из трекинга (`tracked_apps.isExcluded`) — план реализации

> Это план, код не менялся. Составлен по результатам чтения текущего кода (состояние на коммит
> `93fcab5`, ветка `master`). Все ссылки на файлы/строки актуальны на этот момент.

## 1. Что уже есть (не переделывать)

Поле `isExcluded` в схеме БД уже существует и ждёт своего использования:

- `TrackedAppEntity.isExcluded: Boolean = false` (`data/local/entity/TrackedAppEntity.kt:14`) — часть
  схемы `tracked_apps` с самого начала, в миграциях (`Migrations.kt`) не появлялось отдельным шагом,
  то есть **изменений схемы БД не требуется** (`AppDatabase` `version = 4`).
- `TrackedAppDao.setExcluded(packageName, isExcluded)` (`data/local/dao/TrackedAppDao.kt:30-31`) —
  готовый `UPDATE`-запрос, но **нигде не вызывается** (единственные 3 упоминания `isExcluded` во всём
  проекте — это само поле сущности, этот DAO-метод и хардкод `isExcluded = false` при первой вставке
  в `UsageStatsRepositoryImpl.trackUsageSession`, см. §3). Ни в UI, ни во ViewModel, ни в domain-интерфейсе
  репозитория ничего с этим полем не работает.

Вывод: поле — "заготовка", реализовывать нужно всё: репозиторий, enforcement в трекинге, UI.

## 2. Что использовать как образец — "Индивидуальные лимиты приложений"

Секция в `PrivateSettingsScreen.kt` (см. `_docs/limit_apps.md` про историю этой фичи) — рабочий,
проверенный паттерн "заголовок + кнопка Добавить + список + диалог с выпадающим списком приложений":

- **Заголовок + список** — `PrivateSettingsScreen.kt:216-263`: `Column` с `Row` (заголовок +
  `TextButton("Добавить")`), ниже — либо `"Нет добавленных приложений"`, либо `forEach` по уже
  добавленным записям.
- **Диалог добавления** — `PrivateSettingsScreen.kt:368-483`: `ExposedDropdownMenuBox` +
  `OutlinedTextField` (обязательно с `.menuAnchor(MenuAnchorType.PrimaryNotEditable)`, это уже
  поправленный баг из `_docs/limit_apps.md` §1) + `ExposedDropdownMenu` со списком `DropdownMenuItem`,
  построенным из `state.trackedApps`, отфильтрованных от уже настроенных пакетов
  (`PrivateSettingsScreen.kt:375-378`).
- **ViewModel** — `PrivateSettingsViewModel.kt`: `state.trackedApps` грузится один раз в `init` через
  `usageStatsRepository.getTrackedApps()` (строки 47-51) и переиспользуется и для дропдауна, и для
  резолва `appName` в списке. Добавление — `PrivateSettingsEvent.OnAddAppLimit` (строки 121-139): вызов
  репозитория, при успехе — точечное обновление `_state` (без перезагрузки из БД).

**Важное отличие от лимитов, которое нужно учесть:** у лимитов сейчас нет рабочего "убрать" —
`AppLimitDao.deleteLimit` существует, но не вызывается ни из UI, ни из ViewModel (см.
`_docs/limit_apps.md` §1, "Задача A"). Для исключений это явно требуется в задаче ("список уже
исключённых приложений — с возможностью кликнуть и убрать isExcluded"), поэтому "клик по строке снимает
исключение" — часть, которую нужно спроектировать заново, копировать неоткуда.

## 3. Где реально писать enforcement — важная деталь, не как в первом прочтении кода

`UsageStatsRepositoryImpl.trackUsageSession(...)` (`data/repository/UsageStatsRepository.kt:134-184`) —
единственная точка записи usage-данных по пакету (вызывается из `TrackingJob.pollUsageStats()`,
`core/tracking/TrackingJob.kt:165-181`, при `deltaMs > 0`, строка 160). Других мест записи в
`usage_sessions`/`tracked_apps` в проекте нет — `upsertTrackedApp`/`upsertTrackedApps`
(`data/repository/UsageStatsRepository.kt:121`, `186-188`) объявлены, но нигде не вызываются
(`grep -rn "upsertTrackedApp"` — только определения).

Внутри `trackUsageSession` порядок сейчас такой:

```kotlin
suspend fun trackUsageSession(...) {
    try {
        // 1. Пишем сессию — БЕЗУСЛОВНО, до какой-либо проверки isExcluded
        val session = UsageSessionEntity(...)
        usageSessionDao.insertSession(session)                    // строка 154

        // 2. Только здесь читаем существующую запись tracked_apps
        val category = CategoryResolver.resolve(packageManager, packageName)
        val existingApp = trackedAppDao.getTrackedApp(packageName) // строка 159
        if (existingApp == null) {
            trackedAppDao.insertTrackedApp(TrackedAppEntity(..., isExcluded = false, ...)) // строка 172
        } else {
            trackedAppDao.updateUsage(packageName, durationMs, endTime)                    // строка 176
        }
    } ...
}
```

**Проверку `isExcluded` нельзя вставлять после строки 159 (там, где лежит `existingApp`), как может
показаться на первый взгляд** — к этому моменту `usageSessionDao.insertSession(session)` (строка 154)
уже отработал и строка в `usage_sessions` уже записана. Задача требует "не записывай данные по этому
приложению" — значит нужно проверить `isExcluded` **до** `insertSession`, то есть переставить чтение
`existingApp` в начало функции:

```kotlin
suspend fun trackUsageSession(
    packageName: String, appName: String, startTime: Long, endTime: Long,
    durationMs: Long, isEntertainment: Boolean
) {
    try {
        val existingApp = trackedAppDao.getTrackedApp(packageName)   // читаем ДО записи сессии
        if (existingApp?.isExcluded == true) {
            TrackingLogStorage.add("Repo", "trackUsageSession SKIPPED (excluded): $packageName")
            return                                                    // ничего не пишем — ни сессию, ни usage
        }

        TrackingLogStorage.add("Repo", "insertSession: $packageName ($appName) durationMs=$durationMs")
        val date = dateFormat.format(Date(startTime))
        val session = UsageSessionEntity(...)
        usageSessionDao.insertSession(session)
        ...
        val category = CategoryResolver.resolve(packageManager, packageName)
        if (existingApp == null) {
            trackedAppDao.insertTrackedApp(TrackedAppEntity(...))
        } else {
            trackedAppDao.updateUsage(packageName, durationMs, endTime)
        }
    } catch (...) { ... }
}
```

Побочный плюс: `getTrackedApp` теперь вызывается один раз вместо переиспользования той же переменной —
поведение и число запросов к БД не меняется, просто порядок операций.

Первая вставка нового (ещё не встречавшегося) пакета не может быть заблокирована этой проверкой:
`existingApp` в этом случае `null`, `isExcluded` физически невозможно выставить в `true` для пакета,
которого ещё нет в `tracked_apps` (UI строится над уже загруженным `state.trackedApps`, см. §4) — то
есть новый пакет всегда будет писаться при первом появлении, а становится исключаемым только после
того, как уже появился в списке трекнутых приложений и родитель явно его исключил.

### Что произойдёт само по себе, без отдельных правок

- **Алерты** (`AlertManager.checkAndShowAlerts()`, `checkAppLimit`/`checkGlobalLimit`) считают
  превышение по `getDailyUsage(...)`, которая агрегирует `usage_sessions`. Если для исключённого пакета
  сессии перестают писаться — он перестаёт учитываться и в лимитах/алертах автоматически, без изменений
  в `core/alerts`.
- **Отчёты по email** (`core/email`) агрегируют те же данные — аналогично, исключённое приложение
  просто не будет давать новых цифр за дни после исключения.
- **Исторические данные не удаляются.** Уже накопленные строки в `usage_sessions` за прошлые дни
  остаются как есть — задача про "не записывай" (будущее время), а не "сотри прошлое". Если понадобится
  ретроактивная очистка — это отдельное решение, требующее явного подтверждения (удаление данных —
  необратимая операция).

## 4. Repository / domain слой — что добавить

`PrivateSettingsViewModel` работает с `domain.repository.UsageStatsRepository`
(`domain/repository/UsageStatsRepository.kt`), а не с `UsageStatsRepositoryImpl` напрямую — метод
нужно завести в интерфейсе и реализовать в импле, по аналогии с уже существующим `setAppLimit`:

**`domain/repository/UsageStatsRepository.kt`** — добавить в интерфейс (после строки 25,
`setAppLimit(...)`):
```kotlin
suspend fun setAppExcluded(packageName: String, excluded: Boolean)
```

**`data/repository/UsageStatsRepository.kt`** (класс `UsageStatsRepositoryImpl`) — реализация рядом с
`setAppLimit`:
```kotlin
override suspend fun setAppExcluded(packageName: String, excluded: Boolean) {
    trackedAppDao.setExcluded(packageName, excluded)
}
```

Отдельный DAO-метод "получить только исключённые" не требуется — `state.trackedApps` уже загружается
целиком в `PrivateSettingsViewModel.init` (см. §2), исключённые/не исключённые приложения различаются
фильтром `it.isExcluded` на уровне UI/ViewModel, как это уже делает диалог лимитов
(`state.trackedApps.filter { app -> state.appLimits[app.packageName] == null }`,
`PrivateSettingsScreen.kt:376-378`).

## 5. ViewModel — `PrivateSettingsViewModel.kt`

Новые события в `PrivateSettingsEvent` (после `OnClearAddAppLimitError`, строка 167):
```kotlin
data class OnExcludeApp(val packageName: String) : PrivateSettingsEvent()
data class OnIncludeApp(val packageName: String) : PrivateSettingsEvent()   // "вернуть в трекинг"
```

(Два отдельных события вместо одного `OnSetAppExcluded(packageName, excluded: Boolean)` — исключительно
вопрос стиля; в проекте уже есть прецедент двух похожих событий на выбор, критичности нет. Можно
объединить в одно, если для консистентности с `OnAddAppLimit`/`OnGlobalLimitChanged` захочется
единообразия — на усмотрение реализующего.)

Обработка в `onEvent(...)` (после блока `OnAddAppLimit`, ориентир — строки 121-139), по образцу того же
блока — вызов репозитория, при успехе точечно обновляем `TrackedAppEntity` в `state.trackedApps`
(не перезагружаем весь список из БД):
```kotlin
is PrivateSettingsEvent.OnExcludeApp -> {
    viewModelScope.launch {
        try {
            usageStatsRepository.setAppExcluded(event.packageName, true)
            _state.update { s ->
                s.copy(trackedApps = s.trackedApps.map {
                    if (it.packageName == event.packageName) it.copy(isExcluded = true) else it
                })
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Ошибка при исключении приложения: ${e.message}") }
        }
    }
}
is PrivateSettingsEvent.OnIncludeApp -> {
    viewModelScope.launch {
        try {
            usageStatsRepository.setAppExcluded(event.packageName, false)
            _state.update { s ->
                s.copy(trackedApps = s.trackedApps.map {
                    if (it.packageName == event.packageName) it.copy(isExcluded = false) else it
                })
            }
        } catch (e: Exception) {
            _state.update { it.copy(error = "Ошибка при возврате приложения в трекинг: ${e.message}") }
        }
    }
}
```

`PrivateSettingsState` менять не обязательно — `trackedApps: List<TrackedAppEntity>` (строка 154) уже
содержит поле `isExcluded` на каждой записи, отдельное поле-дублёр в state не нужно (аналогично тому,
как `appLimits` не дублирует `trackedApps`, а связывается через `packageName`).

## 6. UI — `PrivateSettingsScreen.kt`

Новая секция "Исключённые приложения", размещается сразу после секции лимитов (после строки 263, перед
блоком "Change password", строка 265) — та же структура, что и у лимитов:

```kotlin
// Excluded apps section
var showExcludeAppDialog by remember { mutableStateOf(false) }   // объявить рядом с showAppLimitDialog (строка 168)

Column(
    verticalArrangement = Arrangement.spacedBy(8.dp)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Исключённые приложения",
            style = MaterialTheme.typography.titleMedium
        )
        TextButton(onClick = { showExcludeAppDialog = true }) {
            Text("Добавить")
        }
    }

    val excludedApps = state.trackedApps.filter { it.isExcluded }.sortedBy { it.appName }

    if (excludedApps.isEmpty()) {
        Text(
            text = "Нет исключённых приложений",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        Text(
            text = "Нажмите на приложение, чтобы вернуть его в трекинг",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        excludedApps.forEach { app ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable { onEvent(PrivateSettingsEvent.OnIncludeApp(app.packageName)) }
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyMedium
                )
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Убрать из исключений",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

Диалог добавления — копия диалога лимитов (`PrivateSettingsScreen.kt:368-483`), но без поля "минуты" и
без валидации числа, фильтр дропдауна — по НЕ исключённым:

```kotlin
if (showExcludeAppDialog) {
    var selectedAppName by remember { mutableStateOf("") }
    var selectedPackageName by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val availableApps = state.trackedApps
        .filter { app -> !app.isExcluded }
        .sortedBy { it.appName }

    AlertDialog(
        onDismissRequest = {
            expanded = false
            showExcludeAppDialog = false
        },
        title = { Text("Исключить приложение из трекинга") },
        text = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedAppName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Приложение") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    if (availableApps.isEmpty()) {
                        DropdownMenuItem(
                            onClick = { expanded = false },
                            text = { Text("Нет доступных приложений") }
                        )
                    } else {
                        availableApps.forEach { app ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedAppName = app.appName
                                    selectedPackageName = app.packageName
                                    expanded = false
                                },
                                text = { Text(app.appName) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedPackageName.isNotEmpty(),
                onClick = {
                    onEvent(PrivateSettingsEvent.OnExcludeApp(selectedPackageName))
                    showExcludeAppDialog = false
                }
            ) { Text("Исключить") }
        },
        dismissButton = {
            TextButton(onClick = {
                expanded = false
                showExcludeAppDialog = false
            }) { Text("Отмена") }
        }
    )
}
```

Требует импорта `androidx.compose.foundation.clickable` и `androidx.compose.material.icons.filled.Close`
(или использовать уже импортированную иконку по аналогии с остальным экраном, если `Close` там ещё не
подключён — проверить блок `import` при реализации).

**Важный нюанс дропдобавления (тот же, что уже словили на лимитах, см. `_docs/limit_apps.md` §1):**
если копировать `ExposedDropdownMenuBox` без `.menuAnchor(MenuAnchorType.PrimaryNotEditable)` на
`OutlinedTextField`-якоре, клики по полю не откроют список — использовать шаблон выше как есть, не
упрощать этот модификатор.

## 7. Источник списка приложений — то же ограничение, что и у лимитов

И дропдаун "Добавить в исключения", и список уже исключённых строятся из `state.trackedApps`, то есть
из таблицы `tracked_apps` (`SELECT * FROM tracked_apps ORDER BY lastUsedTime DESC`,
`TrackedAppDao.kt:9`/`12-13`) — приложений, которые уже хотя бы раз попали в трекинг, а не из
`PackageManager.getInstalledApplications()`. Значит, только что установленное, но ни разу не
использованное приложение нельзя исключить заранее — оно появится в списке только после первого
зафиксированного использования. Это ограничение уже существует для лимитов и переносится на исключения
без дополнительных решений (не баг, а свойство источника данных).

`state.trackedApps` загружается один раз в `init` (`PrivateSettingsViewModel.kt:48-51`), без `Flow` —
если пока открыт экран приватных настроек трекинг допишет новое использование в БД, список в state не
обновится сам. Это существующее поведение экрана (то же самое верно для `appLimits`), фиксация — не
исправление в рамках этой задачи.

## 8. Итоговый чек-лист реализации

- [ ] `domain/repository/UsageStatsRepository.kt` — добавить `suspend fun setAppExcluded(packageName: String, excluded: Boolean)` в интерфейс.
- [ ] `data/repository/UsageStatsRepository.kt` — реализовать `setAppExcluded`, делегируя в `trackedAppDao.setExcluded(...)`.
- [ ] `data/repository/UsageStatsRepository.kt`, `trackUsageSession` (строки 134-184) — переставить
      `val existingApp = trackedAppDao.getTrackedApp(packageName)` в начало функции, добавить ранний
      `return` при `existingApp?.isExcluded == true`, **до** `usageSessionDao.insertSession(session)`.
      Это единственная точка enforcement — `TrackingJob.kt` не трогать.
- [ ] `PrivateSettingsViewModel.kt` — добавить события `OnExcludeApp`/`OnIncludeApp` (или единое
      `OnSetAppExcluded`) и их обработку в `onEvent(...)` с точечным обновлением `state.trackedApps`.
- [ ] `PrivateSettingsScreen.kt` — новая секция "Исключённые приложения" (после секции лимитов, строка
      263): заголовок + кнопка "Добавить" + список исключённых с кликом по строке для возврата в
      трекинг (`OnIncludeApp`); диалог добавления — `ExposedDropdownMenuBox` по образцу диалога лимитов,
      с `.menuAnchor(...)`, фильтр по `!app.isExcluded`.
- [ ] Проверить вручную: исключить приложение → убедиться, что новые сессии по нему не появляются в
      `usage_sessions` (например, через `Debug`-экран/`TrackingLogStorage`, там появится запись
      `SKIPPED (excluded)`) → вернуть из исключений → убедиться, что трекинг возобновился.

Изменений схемы БД не требуется — `isExcluded` уже есть в `tracked_apps` (`AppDatabase` version 4).
