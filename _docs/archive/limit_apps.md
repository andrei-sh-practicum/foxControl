# Индивидуальные лимиты приложений — план реализации

> Это план, код не менялся. Составлен по результатам чтения текущего кода (состояние на коммит
> `43544d7`, ветка `master`). Все ссылки на файлы/строки актуальны на этот момент.

## 1. Что уже есть (не переделывать)

Коммит `d262c74` ("feat: app limits — dropdown from tracked apps + minutes validation") уже добавил
рабочий диалог "Добавить лимит приложения":

- `PrivateSettingsViewModel` при инициализации грузит `usageStatsRepository.getTrackedApps()` и
  `usageStatsRepository.getAppLimits()` в `state.trackedApps` / `state.appLimits`
  (`PrivateSettingsViewModel.kt:41-51`).
- `PrivateSettingsScreen` строит `ExposedDropdownMenuBox` из `state.trackedApps`, отфильтрованных от уже
  залимиченных пакетов (`PrivateSettingsScreen.kt:375-424`), показывает в выпадающем списке `app.appName`,
  а на подтверждение шлёт `OnAddAppLimit(packageName = app.packageName, ...)` →
  `usageStatsRepository.setAppLimit(packageName, dailyLimitMinutes, enabled = true)` →
  `AppLimitDao.insertLimit` пишет `packageName` в `app_limits` (`PrivateSettingsViewModel.kt:121-139`,
  `AppLimitDao.kt:15-16`). Список уже добавленных лимитов на экране тоже резолвит `appName` через
  `state.trackedApps` (`PrivateSettingsScreen.kt:240-242`) — то есть требование "в UI показываем appName, а
  в app_limits пишем packageName" уже выполнено этой веткой кода.

**Обновление (перепроверено по факту сообщённого поведения):** пользователь подтвердил, что на главном
экране список приложений за сегодня отображается — значит `tracked_apps` не пустая, данные для дропдауна
есть. Симптом — "нажимаю на селект бокс, список не разворачивается" — это **не отсутствие данных**, а
конкретный баг вёрстки диалога, найден точно:

`PrivateSettingsScreen.kt:391-401` — `OutlinedTextField`, который служит "якорем" для
`ExposedDropdownMenuBox`, объявлен без модификатора `.menuAnchor(...)`:

```kotlin
OutlinedTextField(
    value = selectedAppName,
    onValueChange = {},
    readOnly = true,
    label = { Text("Приложение") },
    modifier = Modifier.fillMaxWidth(),   // <-- нет .menuAnchor(...)
    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
)
```

В Material3 `1.3.1` (версия в проекте, `app/build.gradle.kts:105`) якорный composable внутри
`ExposedDropdownMenuBoxScope` обязан иметь `Modifier.menuAnchor(...)` — без него клики по текстовому полю не
долетают до `onExpandedChange`, и `expanded` никогда не становится `true`. Это единственное использование
`ExposedDropdownMenuBox` во всём проекте (`grep -rn "ExposedDropdownMenuBox"`), сравнить с рабочим
аналогом негде.

**Фикс (Задача A, теперь обязательная, не опциональная):** добавить
`.menuAnchor(MenuAnchorType.PrimaryNotEditable)` (сигнатура для `readOnly`-полей в Material3 1.3.x;
понадобится импорт `androidx.compose.material3.MenuAnchorType`) к модификатору `OutlinedTextField` на
строке 396. Однострочная правка, изолированная, не задевает остальную логику диалога.

## 2. Что нужно реализовать

Три связанные части: (A) проверка/донастройка уже существующего диалога добавления лимита, (B) корректный
текст оповещения, (C) новый блок "Приложения с превышением лимита" на Home/"Сегодня". Плюс исправление
багов в цепочке алертов, которые становятся реальной проблемой именно тогда, когда лимитов на приложения
станет несколько одновременно (см. §4).

### Задача A — Диалог добавления лимита 

Код уже соответствует требованиям (см. §1). 
Единственное реальное отсутствующее место — **удаление/выключение** лимита: в `AppLimitDao` есть
`deleteLimit(packageName)` (`AppLimitDao.kt:18-19`), но им никто не пользуется — из UI лимит можно только
добавить, удалить/отредактировать нельзя. Пользователь не просил это в задаче явно, поэтому выношу отдельным
пунктом, не блокирующим остальное:

- (опционально, по согласованию) добавить свайп/кнопку "Удалить" в списке лимитов
  `PrivateSettingsScreen.kt:241-259`, вызывающую новый `PrivateSettingsEvent.OnDeleteAppLimit(packageName)`
  → `usageStatsRepository.deleteAppLimit(packageName)` (метод нужно будет добавить в
  `domain.repository.UsageStatsRepository` + `UsageStatsRepositoryImpl`, DAO-метод уже есть).

### Задача B — Текст оповещения "Превышен суточный лимит приложения — {appName}"

Сейчас `NotificationHelper.showLimitExceededNotification` формирует другой текст
(`NotificationHelper.kt:46-56`):

```kotlin
val title = if (isGlobal) "Лимит времени превышен!" else "Лимит приложения превышен!"
val text = if (isGlobal) "..." else "$appName: использовано $usedMinutes из $limitMinutes минут"
```

Правки:
- `title` для `!isGlobal` → `"Превышен суточный лимит приложения — $appName"` (заголовок из задачи).
- `text` оставить как есть (полезная деталь "использовано X из Y минут") либо, если нужен ровно
  один визуальный текст без деталей — уточнить при реализации; по умолчанию оставляю деталь в `text`,
  т.к. она не противоречит формулировке задачи (заголовок как в задаче + подробности во втором ряду).
- Тот же текст продублировать в overlay (`OverlayAlertService.showAlert`,
  `overlay_alert.xml` → `tv_app_name`) — сейчас там просто имя приложения без слова "Превышен...", стоит
  привести к единой формулировке, чтобы оверлей и уведомление не расходились текстом.

### Задача C — Блок "Превышены лимиты" на Home / вкладка "Сегодня"

Данные уже почти есть: `HomeViewModel.loadStatistics()` грузит `dailyStats` (`DailyUsageStats.apps`,
каждый элемент — `UsageStats.totalDurationMs`), а `usageStatsRepository.getAppLimits()` уже есть в
интерфейсе (`domain/repository/UsageStatsRepository.kt:22`). Не хватает только объединения этих двух
источников на UI и на Home их сейчас никто не грузит.

**HomeViewModel (`ui/home/HomeViewModel.kt`):**
- В `init {}` добавить загрузку лимитов: `val limits = usageStatsRepository.getAppLimits()`
  (уже отфильтрованы `enabled = 1` на уровне DAO, `AppLimitDao.kt:8-9`).
- Посчитать список превышений при каждом обновлении `dailyStats` (в `loadStatistics()`, только для
  `HomePeriod.Today`): для каждого `AppLimitEntity` найти соответствующий `UsageStats` в
  `dailyStats.apps` по `packageName`; если `totalDurationMs > dailyLimitMinutes * 60_000L` — добавить в
  новый список `ExceededAppInfo(packageName, appName, totalMinutes, overMinutes)`, где
  `overMinutes = totalMinutes - dailyLimitMinutes`. Сортировать по `overMinutes` по убыванию (тот же
  принцип, что и у общего списка приложений, который сортируется по `totalDurationMs`).
- Добавить в `HomeState` новое поле `val exceededApps: List<ExceededAppInfo> = emptyList()`.
- Модель `ExceededAppInfo` можно объявить прямо в `HomeViewModel.kt` рядом с `HomeState` (по аналогии с
  `HomePeriod`), т.к. это чисто UI-агрегат, а не доменная модель для других экранов.

**HomeScreen (`ui/home/HomeScreen.kt`):**
- Новый composable `ExceededLimitsBlock(items: List<ExceededAppInfo>, onClick: (String) -> Unit)`,
  показывается только когда `state.period == HomePeriod.Today && state.exceededApps.isNotEmpty()`.
- Позиция — **первым блоком** вкладки "Сегодня", то есть выше текущего первого блока
  (`ServiceDowntimeChart`, `HomeScreen.kt:118-121`), сразу под переключателем периодов
  (`HomeScreen.kt:98-116`). Дословно это требование пользователя ("первым блоком").
- Содержимое каждой строки: `AppIcon(packageName, size = 40.dp)` (готовый компонент,
  `ui/common/AppIcon.kt`, уже используется в `AppUsageCard`) + `appName` + "Использовано: {total} мин" +
  "Превышение: +{over} мин", акцентный цвет (`MaterialTheme.colorScheme.error`) на строке превышения —
  по аналогии с уже используемым `Icons.Default.Error`/`colorScheme.error` в экране (`HomeScreen.kt:197-201`).
- По тапу — переиспользовать `onAppDetailClick(packageName)`, как у обычных карточек
  (`HomeScreen.kt:296`), чтобы поведение было единообразным с основным списком.
- Обычный список приложений ниже (`items(apps) { AppUsageCard(...) }`, `HomeScreen.kt:288-298`) не менять —
  превысившие лимит приложения остаются в нём тоже (задача не просит их оттуда убирать, только продублировать
  наверху в отдельном блоке).

## 3. Проверка цепочки лимитирования (как это работает end-to-end)

```text
TrackingJob (таймер, каждые 60с)
  → считает дельту переднего плана по пакету
  → GlobalScope.launch { usageStatsRepository.trackUsageSession(...) }   // пишет usage_sessions + tracked_apps
  → checkAlerts() → GlobalScope.launch { alertManager.checkAndShowAlerts() }
                                                  │
                                                  ▼
AlertManager.checkAndShowAlerts()
  1) checkGlobalLimit() — сравнивает getDailyUsage(today).totalUsageMs с global_limit
  2) getAppLimitsSync() (enabled=1) → для каждого checkAppLimit(packageName):
       getDailyUsage(today).apps.find{packageName} и сравнение totalDurationMs с dailyLimitMinutes*60_000
  3) при превышении — shouldShowAlert(packageName) (cooldown) → showLimitExceededAlert(...)
       → OverlayAlertService (TYPE_APPLICATION_OVERLAY)
       → NotificationHelper (fallback/дублирующее уведомление)
```

Логика подсчёта самого превышения (`checkAppLimit`, `UsageStatsRepositoryImpl.kt:201-213`) — верная и не
требует правок. Ниже — то, что реально ломает многопользовательский (много приложений с лимитом
одновременно) сценарий, который эта задача как раз включает.

## 4. Найденные баги в цепочке алертов (включены в план по решению пользователя)

### 4.1 Повторный показ алерта — требование "сработал один раз за день — больше не показывать"

Явное требование пользователя: если алерт на превышение лимита приложения уже сработал один раз, повторно
его показывать не нужно (до следующего дня, когда счётчик использования обнуляется). Текущий код этого не
делает и делает не просто "недостаточно" — а принципиально другое:

```kotlin
// AlertManager.kt:20, 23-24, 66-74
private const val ALERT_COOLDOWN_MS = 60_000L // 1 minute cooldown

private var lastAlertTime: Long = 0
private var lastAlertPackage: String? = null

private fun shouldShowAlert(packageName: String): Boolean {
    val now = System.currentTimeMillis()
    return packageName != lastAlertPackage || (now - lastAlertTime) > ALERT_COOLDOWN_MS
}
```

Это **cooldown на 1 минуту**, а не "один раз за день", и хранится он в **одной** общей переменной на весь
`AlertManager`, а не по каждому `packageName` отдельно. Два независимых следствия:

- **Не тот механизм.** Даже если сделать `shouldShowAlert` per-package (карта `packageName → lastShownAt`),
  cooldown в 1 минуту всё равно даст повторный показ каждые ~60 секунд, пока приложение остаётся
  использованным сверх лимита — а `TrackingJob` опрашивает `UsageStatsManager` тоже каждые 60 секунд
  (`TrackingJob.kt`, `HEARTBEAT_INTERVAL_MS`/цикл опроса), то есть окно кулдауна практически равно периоду
  проверки. На практике это означает алерт почти на каждом цикле весь оставшийся день — то есть ровно то,
  чего просит избежать пользователь. Простого "сделать per-package" (как было заложено в первой версии
  этого плана) недостаточно — нужно менять саму модель дедупликации с "cooldown" на "разово в день".
- **Общая переменная вместо карты** — тот же дефект, что описан в предыдущей версии этого раздела: один
  пакет "перетирает" cooldown другого. При переходе на модель "разово в день" это отпадает само собой (см.
  фикс ниже), отдельно чинить эту часть не нужно.

**Фикс:** заменить cooldown на проверку "уже показывали сегодня для этого пакета" через уже существующую,
но не задействованную таблицу `alert_logs` (`AlertLogDao`, `data/local/dao/AlertLogDao.kt`) — совмещается с
п. 4.4 и даёт персистентность (переживает перезапуск `TrackingForegroundService`, в отличие от in-memory
переменных):

1. Добавить в `AlertLogDao` запрос вида
   `SELECT COUNT(*) FROM alert_logs WHERE packageName = :packageName AND type = :type AND timestamp >= :dayStartMs`
   (либо `getLatestAlertForPackage`, эквивалентно). Границу дня считать так же, как уже делает
   `getServiceDowntimeBuckets` (`UsageStatsRepositoryImpl.kt:277-290`, `Calendar` на 00:00:00.000) — чтобы
   не заводить второй способ вычисления "начала дня" в проекте.
2. В `AlertManager.checkAndShowAlerts()` перед каждым `showLimitExceededAlert(...)` спрашивать репозиторий
   "уже был алерт `type="app"` для этого `packageName` сегодня?" — если да, пропустить; если нет, показать
   и сразу записать `alert_logs` (п. 4.4) — запись работает как "флаг показано" на весь остаток дня.
3. То же самое правило — для глобального лимита (`type = "global"`, `packageName = "global"`), для
   единообразия (пользователь просил это применительно к лимиту приложения, но логика "весь смартфон"
   устроена рядом и симметрично — не распространять только на app-лимиты означало бы разное поведение двух
   похожих алертов без причины).
4. `lastAlertTime`/`lastAlertPackage`/`ALERT_COOLDOWN_MS` в `AlertManager` становятся не нужны и удаляются
   вместе со старым `shouldShowAlert`/`markAlertShown`.

**Допущение (стоит подтвердить):** "один раз за день" привязан к паре (`packageName`, календарная дата), не
к значению лимита. Если родитель посреди дня изменит лимит (увеличит/уменьшит через тот же диалог,
`setAppLimit` делает `REPLACE`, т.е. апдейт лимита не создаёт нового "разрешения" на новый алерт) — алерт всё
равно не покажется повторно в тот же день, даже если из-за нового (более строгого) лимита превышение стало
"более новым" по сути. Если нужно другое поведение (например, сбрасывать флаг показа при изменении лимита),
это отдельное уточнение — по умолчанию делаю строго "раз в календарный день на пакет", как буквально сказано
в задаче.

### 4.1a Оверлей никогда не скрывается сам (OverlayAlertService.kt:16-52, overlay_alert.xml)

Нашёл при проверке этого же пункта, т.к. напрямую влияет на то, как будет выглядеть "показать один раз":
`OverlayAlertService` умеет прятать баннер по `ACTION_HIDE` (`OverlayAlertService.kt:45-47`,
`hideAlert()` на строке 94-104), но **никто в проекте не отправляет `ACTION_HIDE`**
(`grep -rn "ACTION_HIDE"` — только объявление и обработка, ни одного вызова). В layout
(`overlay_alert.xml`) тоже нет кнопки закрытия/клика. Результат: после перехода на "один раз за день" баннер
покажется один раз — и останется висеть поверх экрана до перезапуска `TrackingForegroundService`/устройства,
что хуже повторяющегося алерта с точки зрения UX (постоянный неубираемый баннер весь день).

**Фикс:** добавить авто-скрытие в `OverlayAlertService.showAlert()` — например,
`Handler(Looper.getMainLooper()).postDelayed({ hideAlert() }, OVERLAY_AUTO_HIDE_MS)` с разумной длительностью
(например, 5-8 секунд, по аналогии с тем, как обычно ведут себя системные оверлеи-баннеры), либо кнопка
"Закрыть" в `overlay_alert.xml`, шлющая `ACTION_HIDE` через `startService`. Конкретную длительность/UX стоит
согласовать отдельно — фиксирую сам факт бага и то, что он становится заметен именно в связи с этим пунктом.

### 4.2 Одно и то же ID уведомления для всех алертов (NotificationHelper.kt:24, 68)

```kotlin
const val ID_ALERT_NOTIFICATION = 999
...
notificationManager.notify(ID_ALERT_NOTIFICATION, notification)
```

Все уведомления (глобальный лимит и любое приложение) используют константный `ID = 999`. Второе
уведомление **заменяет** первое в шторке, а не появляется рядом — пользователь увидит только последнее
превышение, даже если превысили лимит два разных приложения.

**Фикс:** делать ID зависимым от пакета, например `ID_ALERT_NOTIFICATION_BASE + packageName.hashCode()`
(с проверкой на коллизии не нужен — `notify()` с разными ID просто создаёт отдельные уведомления), либо
использовать `NotificationCompat` group ("Оповещения о лимитах" как summary + отдельные уведомления по
пакетам).

### 4.3 Возможная утечка View в оверлее при повторном срабатывании (OverlayAlertService.kt:54-92)

```kotlin
private fun showAlert(...) {
    ...
    overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_alert, null)
    ...
    windowManager?.addView(overlayView, params)
}
```

Если `showAlert()` вызывается второй раз, пока предыдущий `overlayView` ещё не был убран через
`hideAlert()` (`ACTION_HIDE` никто в текущем коде не шлёт — оверлей самостоятельно не скрывается, только
переинициализируется), старая `View` остаётся добавленной в `WindowManager`, а ссылка на неё в поле
`overlayView` перезаписывается новой — старую убрать после этого уже нечем (`hideAlert()` удалит только
последнюю). При частых срабатываниях (несколько лимитов, короткий кулдаун) это копится как утечка окон
поверх экрана.

**Фикс:** перед `inflate()`+`addView()` в `showAlert()` сначала вызывать `hideAlert()` (убрать предыдущий
View, если он есть), либо переиспользовать существующий `overlayView`/обновлять его текстовые поля вместо
пересоздания.

### 4.4 Таблица `alert_logs` заведена, но не используется (AlertLogDao.kt, AlertLogEntity.kt)

`UsageStatsRepositoryImpl` получает `alertLogDao` в конструкторе, но нигде не вызывает
`insertAlertLog(...)`. Таблица есть в схеме (`AppDatabase`, см. `_docs/bd_tables.md`), в `Debug`/истории
алертов её показать было бы можно, но сейчас туда ничего не пишется.

**Фикс (совмещается с 4.1):** при каждом реальном показе алерта (`AlertManager.showLimitExceededAlert`)
писать `alertLogDao.insertAlertLog(AlertLogEntity(packageName = ..., timestamp = ..., type = "global"/"app"))`
через новый метод в репозитории (`UsageStatsRepositoryImpl.recordAlertLog(...)`). Это же даёт бесплатный
источник данных для истории оповещений на будущее (не в рамках этой задачи, но становится дешёвым).

## 5. Не в рамках этой задачи, но замечено при ревью (для отдельного тикета)

Эти находки не связаны напрямую с индивидуальными лимитами приложений, но были обнаружены при чтении той
же цепочки и могут ввести в заблуждение при дальнейшей работе с лимитами в целом:

- **Глобальный лимит фактически не персистится.** `PrivateSettingsEvent.OnGlobalLimitChanged` только
  обновляет `_state` (`PrivateSettingsViewModel.kt:108-110`) и никогда не вызывает
  `usageStatsRepository.setGlobalLimit(...)`; `init {}` также никогда не подгружает сохранённое значение
  из `globalLimitDao` в `state.globalDailyLimitMinutes` (дефолт захардкожен `120`,
  `PrivateSettingsViewModel.kt:152`). Плюс `GlobalLimitDao.setGlobalLimit` — это `UPDATE ... WHERE id = 1`
  без `INSERT`-фолбэка (`GlobalLimitDao.kt:11-12`), а строка с `id = 1` нигде не сеется (нет
  `RoomDatabase.Callback`/сида в `AppModule`) — то есть даже если бы `setGlobalLimit` вызывался, `UPDATE`
  обновил бы 0 строк.
- **`AlertManager.getGlobalLimitMinutes()` захардкожен на `120`** (`AlertManager.kt:108-110`) и не читает
  реальный `global_limit` — согласуется с предыдущим пунктом (раз лимит всё равно не персистится, читать
  его пока было бы неоткуда).
- Эти два пункта касаются только глобального (на весь смартфон) лимита, не индивидуальных — но стоит
  учитывать, что если решите чинить п. 4 (алерты), заодно логично завести отдельную задачу на глобальный
  лимит, т.к. код рядом и структурно симметричен.

## 6. Итоговый чек-лист реализации

- [ ] Задача A — добавить `.menuAnchor(MenuAnchorType.PrimaryNotEditable)` на `OutlinedTextField` в
      `PrivateSettingsScreen.kt:396`; отдельно — по желанию — добавить удаление лимита через уже
      существующий `AppLimitDao.deleteLimit`.
- [ ] Задача B — текст уведомления и оверлея → `"Превышен суточный лимит приложения — {appName}"`.
- [ ] Задача C — `HomeViewModel`: загрузка `getAppLimits()` + вычисление `exceededApps`;
      `HomeScreen`: блок `ExceededLimitsBlock` первым в вкладке "Сегодня".
- [ ] П. 4.1 — "разово в день на пакет" через `alert_logs` вместо 60-секундного cooldown (требование
      пользователя: сработавший алерт повторно не показывать).
- [ ] П. 4.1a — авто-скрытие оверлея (сейчас не скрывается никогда, `ACTION_HIDE` не вызывается нигде).
- [ ] П. 4.2 — уникальный ID уведомления на пакет.
- [ ] П. 4.3 — `hideAlert()` перед повторным `showAlert()` в `OverlayAlertService`.
- [ ] П. 4.4 — запись в `alert_logs` при каждом показанном алерте (включается уже в рамках 4.1, отдельно
      делать не нужно).
- [ ] (вне рамок, отдельный тикет) п. 5 — персистентность глобального лимита.

Изменений схемы БД не требуется — `app_limits`, `tracked_apps`, `alert_logs` уже существуют
(`AppDatabase.DATABASE_VERSION` менять не нужно).
