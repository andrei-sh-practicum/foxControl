# SMTP-секреты: как зашить в APK без утечки в git

> ⚠️ Этот файл — инструкция. Он **не должен содержать реальный ключ**.
> Реальный SMTP app password хранится только в `local.properties` на машине,
> которая собирает APK, и никогда не коммитится.

## Идея

Сейчас дефолт пароля в коде — заглушка:

```kotlin
// app/src/main/java/com/andrew/foxcontrol/data/repository/EmailSettingsRepository.kt
const val SMTP_APP_PASSWORD_DEFAULT = "CHANGE_ME_IN_PRODUCTION"
```

Поэтому после установки APK отправка писем не работает, пока кто-то не введёт
пароль вручную через `Настройки → 🔒 Приватные настройки → Email настройки`.

Чтобы пароль был «зашит» в APK при сборке (и не требовал ручного ввода на
устройстве), но при этом не попадал в git — используем `local.properties`
(он уже в `.gitignore:10`) + Gradle `BuildConfig`.

## Шаг 1 — `local.properties` (на машине, где собирается APK)

Файл лежит в корне проекта, рядом с `settings.gradle.kts`. Добавить строку:

```properties
SMTP_APP_PASSWORD=реальный_ключ_brevo
```

Файл уже в `.gitignore` — коммитить его не нужно и не должно быть возможности.

## Шаг 2 — `app/build.gradle.kts`: прочитать файл и положить в BuildConfig

```kotlin
import java.util.Properties
import java.io.FileInputStream

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        load(FileInputStream(localFile))
    }
}

android {
    defaultConfig {
        buildConfigField(
            "String",
            "SMTP_APP_PASSWORD_DEFAULT",
            "\"${localProperties.getProperty("SMTP_APP_PASSWORD", "CHANGE_ME_IN_PRODUCTION")}\""
        )
    }
}
```

`buildFeatures.buildConfig = true` уже включён в `app/build.gradle.kts` —
ничего дополнительно включать не нужно.

Если `local.properties` отсутствует или строки нет — подставится та же
заглушка `CHANGE_ME_IN_PRODUCTION`, сборка не сломается (важно для CI/чужой
машины, где секрета нет).

## Шаг 3 — использовать BuildConfig вместо хардкода в коде

Заменить константу на значение из `BuildConfig`:

```kotlin
// EmailSettingsRepository.kt
const val SMTP_APP_PASSWORD_DEFAULT = BuildConfig.SMTP_APP_PASSWORD_DEFAULT
```

```kotlin
// EmailSettingsViewModel.kt (объект BrevoDefaults)
const val SMTP_APP_PASSWORD = BuildConfig.SMTP_APP_PASSWORD_DEFAULT
```

После этого свежеустановленный APK сразу использует реальный пароль как
дефолт — ручной ввод в UI не нужен (поле в настройках остаётся, можно
переопределить при желании).

## Известный компромисс

Значение из `BuildConfig` компилируется в APK как обычная строка — она не в
git, но **остаётся внутри самого файла APK** и достаётся декомпиляцией /
`strings` по `classes.dex`. Раз APK раздаётся вручную (Telegram/WhatsApp/почта
— см. `INSTALL_GUIDE.md`), каждый получатель файла потенциально может
вытащить ключ. Для приватного sideload-приложения это принятый риск, но
держать это в уме при ротации ключа.

## Чек-лист перед коммитом любых файлов в этот репозиторий

- [ ] `local.properties` не в `git status` (должен быть проигнорирован)
- [ ] Реальный ключ не встречается ни в одном `.md`/`.kt`/`.gradle.kts` файле,
      который коммитится
- [ ] Этот файл (`_docs/secret_data.md`) не содержит реального значения ключа
