# Иконки приложений в списке на главном экране — анализ и решение

## Наблюдаемая проблема

На главном экране (`HomeScreen`) в списке приложений у большинства пунктов вместо
реальной иконки приложения показывается заглушка — серая шестерёнка
(`Icons.Default.Settings`), хотя у самих приложений иконки в системе есть
(видно в лаунчере). Корректно отображаются лишь единицы (на скрине —
«Системная оболочка», «Редактор»).

## Как сейчас устроена загрузка иконок

Код дублирован в двух местах и в обоих одинаковая логика:

- `app/src/main/java/com/andrew/foxcontrol/ui/home/HomeScreen.kt` — `AppUsageCard` (строки ~305-333, отрисовка ~347-369)
- `app/src/main/java/com/andrew/foxcontrol/ui/appdetail/AppDetailScreen.kt` — аналогичный блок (строки ~73-142)

Алгоритм (на примере `HomeScreen.kt`):

1. На каждую карточку приложения в `LaunchedEffect(packageName)` идёт запрос в
   `PackageManager`:
   ```kotlin
   val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
   val label = pm.getApplicationLabel(appInfo)
   val appIcon = pm.getApplicationIcon(packageName)
   ```
2. Полученный `Drawable` кладётся в состояние `appIcon`.
3. При отрисовке иконка приводится **только** к `BitmapDrawable`:
   ```kotlin
   val bitmapDrawable = appIcon as? BitmapDrawable
   val bitmap = bitmapDrawable?.bitmap
   if (bitmap != null) {
       Image(bitmap = bitmap.asImageBitmap(), ...)
   } else {
       Icon(Icons.Default.Settings, ...) // заглушка
   }
   ```

## В чём проблема

`PackageManager.getApplicationIcon()` возвращает `Drawable`, а не обязательно
`BitmapDrawable`. Начиная с Android 8.0 (API 26) — а это как раз `minSdk` данного
проекта — подавляющее большинство приложений используют **адаптивные иконки**
(`AdaptiveIconDrawable`), которые состоят из отдельных слоёв фона/переднего плана
и растеризуются только во время отрисовки. Также встречаются `VectorDrawable`,
`LayerDrawable`, `InsetDrawable` и т.п. — тоже не `BitmapDrawable`.

Текущий код проверяет `appIcon as? BitmapDrawable` — для `AdaptiveIconDrawable`
и прочих не-битмап дровейблов это приведение даёт `null`, код уходит в ветку
`else` и рисует шестерёнку-заглушку.

Именно поэтому иконки показываются только у тех немногих приложений, чей
`Drawable` по факту оказался `BitmapDrawable` (например, системные компоненты со
старым форматом иконки), а у всех остальных (Galerie, MEGA, Яндекс.Почта,
собственно Fox Control и т.д., у которых иконка адаптивная) — заглушка.

Это чистый баг в способе конвертации `Drawable → Bitmap`, а не в получении
самой иконки: `pm.getApplicationIcon(packageName)` отрабатывает нормально,
проблема только в рендеринге результата в Compose `Image`.

Дополнительно (менее критично, но стоит отметить):

- Логика полностью продублирована в `HomeScreen.kt` и `AppDetailScreen.kt` —
  любой фикс нужно вносить в двух местах, и они уже могут разойтись.
- Иконка перезагружается из `PackageManager` на каждое создание/пересоздание
  карточки (`LaunchedEffect(packageName)` на уровне композабла-элемента списка),
  без какого-либо кэша. Это лишние операции ввода-вывода/диска при каждом
  скролле `LazyColumn`, если карточки выходят из `remember`-скоупа и
  создаются заново (например, из-за отсутствия `key` в `items(apps)`).

## Предлагаемое решение

### 1) Правильно конвертировать любой `Drawable` в `Bitmap`

Добавить единую util-функцию, которая работает для любого `Drawable`
(`AdaptiveIconDrawable`, `VectorDrawable`, `BitmapDrawable` и т.д.), а не только
для `BitmapDrawable`:

```kotlin
fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap

    val width = intrinsicWidth.takeIf { it > 0 } ?: 1
    val height = intrinsicHeight.takeIf { it > 0 } ?: 1
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}
```

Такая функция корректно растеризует `AdaptiveIconDrawable` (у него как раз есть
`intrinsicWidth/Height` и штатная отрисовка через `draw(canvas)`), поэтому
заглушка будет использоваться только при реальном отсутствии иконки/ошибке
`PackageManager`, а не как основной путь.

Разместить её, например, в `core/util/DrawableExt.kt` (или рядом с уже
существующими UI-утилитами), чтобы использовать из обоих экранов.

### 2) Убрать дублирование между `HomeScreen` и `AppDetailScreen`

Вынести общий кусок «загрузить название + иконку приложения по packageName и
отрисовать» в один переиспользуемый composable/хелпер (например,
`ui/common/AppIcon.kt` с `@Composable fun AppIcon(packageName: String, size: Dp)`),
и использовать его в обоих местах вместо копии кода.

### 3) Добавить кэш иконок

Иконка приложения меняется крайне редко (по сути — только при обновлении
самого приложения). Есть смысл кэшировать уже сконвертированные `ImageBitmap`
по `packageName` в памяти (простой `mutableMapOf`/`LruCache` на уровне,
например, `HomeViewModel`/отдельного `AppIconRepository` с `@Singleton` через
Hilt), чтобы:

- не дёргать `PackageManager` и не растеризовывать `Drawable` заново при
  каждом скролле/рекомпозиции;
- ускорить первую отрисовку списка (сейчас иконка у каждой карточки
  подгружается асинхронно и одномоментно "допрыгивает" в UI после
  `LaunchedEffect`).

### 4) (опционально) Обработать edge-cases

- Если `pm.getApplicationIcon(packageName)` бросает
  `NameNotFoundException` (приложение удалено пользователем, но осталась
  запись об использовании в БД) — это уже обрабатывается `catch (e: Exception)`,
  заглушка в этом случае корректна и её стоит оставить.
- Для отсутствующего/невалидного `Drawable` (`intrinsicWidth/Height <= 0`)
  предложенная `toBitmap()` не должна падать — учтено через `takeIf { it > 0 } ?: 1`.

## Итог

Причина проблемы — `appIcon as? BitmapDrawable` в `HomeScreen.kt` и
`AppDetailScreen.kt`: приведение типов не работает для адаптивных и прочих
не-битмап иконок, которые есть у большинства современных приложений (Android
8.0+, а `minSdk = 26` у проекта — то есть адаптивные иконки актуальны для всех
поддерживаемых устройств). Достаточно заменить приведение типа на
универсальную растеризацию `Drawable → Bitmap` через `Canvas`, дополнительно
стоит убрать дублирование логики между двумя экранами и добавить кэш иконок.

