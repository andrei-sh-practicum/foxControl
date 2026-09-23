# SMTP-настройки по умолчанию: `local.properties`

> Актуальная версия инструкции (заменяет `_docs/archive/secret_data.md`).
> Реальные значения хранятся только в `local.properties` на ПК, где собирается APK.
> Файл в `.gitignore` и никогда не коммитится.

## Что добавить в `local.properties`

Файл лежит в корне проекта, рядом с `settings.gradle.kts`:

```properties
# SMTP (Brevo) — значения по умолчанию, которые подставятся в «Настройки email»
SMTP_LOGIN=ваш_логин@smtp-brevo.com
SMTP_APP_PASSWORD=ключ_brevo
# необязательно: адрес отправителя, по умолчанию = SMTP_LOGIN
SMTP_FROM=ваш_логин@smtp-brevo.com
```

| Ключ | BuildConfig | Если не задан |
|---|---|---|
| `SMTP_LOGIN` | `SMTP_LOGIN_DEFAULT` | пустая строка |
| `SMTP_APP_PASSWORD` | `SMTP_APP_PASSWORD_DEFAULT` | `CHANGE_ME_IN_PRODUCTION` |
| `SMTP_FROM` | `SMTP_FROM_DEFAULT` | значение `SMTP_LOGIN` |

Хост (`smtp-relay.brevo.com`), порт (`587`) и время отправки (20:00) по-прежнему
заданы в коде: `core/email/EmailDefaults.kt`.

## Как это работает

- `app/build.gradle.kts` читает `local.properties` и кладёт значения в `BuildConfig`.
- `EmailDefaults` отдаёт их как значения по умолчанию.
- Значения по умолчанию видны только до первого нажатия «Сохранить настройки».
  После этого действуют значения из БД (`email_settings`), и `local.properties` на
  уже установленное приложение не влияет.

> ⚠️ До версии с исправлением B-16 логин был зашит в код. Если на ПК сборки в
> `local.properties` нет `SMTP_LOGIN`, на **свежей** установке поля «Логин» и
> «От кого» будут пустыми, и их придётся заполнить вручную.

## Известный компромисс

Значения из `BuildConfig` попадают в APK как обычные строки: их не видно в git,
но их можно достать декомпиляцией. APK раздаётся вручную, поэтому любой получатель
файла потенциально может извлечь ключ. Учитывайте это при ротации ключа Brevo.

## Чек-лист перед коммитом

- [ ] `local.properties` не виден в `git status`.
- [ ] Реальные логин и ключ не встречаются ни в одном коммитящемся `.md`, `.kt` или `.gradle.kts`.
