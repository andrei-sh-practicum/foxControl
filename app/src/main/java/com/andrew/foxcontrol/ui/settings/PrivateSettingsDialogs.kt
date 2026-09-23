package com.andrew.foxcontrol.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.andrew.foxcontrol.data.local.entity.TrackedAppEntity

private const val MIN_PASSWORD_LENGTH = 4

/** Whole-phone daily limit: edits a local draft, saved only on "Готово"; empty / 0 disables it. */
@Composable
internal fun GlobalLimitDialog(
    currentMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var minutesText by remember {
        mutableStateOf(currentMinutes.takeIf { it > 0 }?.toString() ?: "")
    }
    val minutesValid = minutesText.all { it.isDigit() } && minutesText.length <= 4

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Общий дневной лимит") },
        text = {
            OutlinedTextField(
                value = minutesText,
                onValueChange = { minutesText = it },
                label = { Text("Минут в день") },
                singleLine = true,
                isError = !minutesValid,
                supportingText = {
                    Text(if (minutesValid) "Пусто или 0 — лимит выключен" else "Введите целое число минут")
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = minutesValid,
                onClick = { onConfirm(minutesText.toIntOrNull() ?: 0) }
            ) {
                Text("Готово")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

/** New app limit: pick an app without a limit and enter minutes. */
@Composable
internal fun AddAppLimitDialog(
    availableApps: List<TrackedAppEntity>,
    onAdd: (packageName: String, appName: String, limitMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedApp by remember { mutableStateOf<TrackedAppEntity?>(null) }
    var minutesText by remember { mutableStateOf("") }
    // Validate: only digits
    val minutesError = if (minutesText.isNotEmpty() && minutesText.any { !it.isDigit() }) "Введите целое число" else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить лимит приложения") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppPickerDropdown(
                    apps = availableApps,
                    selectedAppName = selectedApp?.appName ?: "",
                    onAppSelected = { selectedApp = it }
                )

                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it },
                    label = { Text("Лимит (минуты)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = minutesError != null,
                    supportingText = {
                        if (minutesError != null) {
                            Text(text = minutesError, color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        },
        confirmButton = {
            val app = selectedApp
            val limitMinutes = minutesText.toIntOrNull()
            TextButton(
                enabled = app != null && minutesError == null && limitMinutes != null,
                onClick = {
                    if (app != null && limitMinutes != null) {
                        onAdd(app.packageName, app.appName, limitMinutes)
                    }
                }
            ) {
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

/** Existing app limit: change minutes or remove the limit. */
@Composable
internal fun EditAppLimitDialog(
    appName: String,
    currentMinutes: Int,
    onSave: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var minutesText by remember { mutableStateOf(currentMinutes.toString()) }
    // Same rule as the "add limit" dialog: a whole number of minutes
    val minutes = minutesText.takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }?.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Лимит: $appName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it },
                    label = { Text("Лимит (минуты)") },
                    singleLine = true,
                    isError = minutes == null,
                    supportingText = if (minutes == null) { { Text("Введите целое число") } } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить лимит")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = minutes != null,
                onClick = { minutes?.let(onSave) }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

/** Exclude a tracked app from tracking. */
@Composable
internal fun ExcludeAppDialog(
    availableApps: List<TrackedAppEntity>,
    onExclude: (packageName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedApp by remember { mutableStateOf<TrackedAppEntity?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Исключить приложение из трекинга") },
        text = {
            AppPickerDropdown(
                apps = availableApps,
                selectedAppName = selectedApp?.appName ?: "",
                onAppSelected = { selectedApp = it }
            )
        },
        confirmButton = {
            TextButton(
                enabled = selectedApp != null,
                onClick = { selectedApp?.let { onExclude(it.packageName) } }
            ) { Text("Исключить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

/** Confirmation for deleting all tracked apps and sessions. */
@Composable
internal fun ClearTrackedAppsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        },
        title = { Text("Очистить таблицу трекинга") },
        text = {
            Text("Вы уверены, что хотите очистить таблицу трекинга приложений? Это действие необратимо — все данные о приложениях будут удалены.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Очистить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

/**
 * Password change. Validation errors appear after the first "Сменить" click;
 * [wrongOldPassword] comes from the view model.
 */
@Composable
internal fun ChangePasswordDialog(
    wrongOldPassword: Boolean,
    onOldPasswordEdited: () -> Unit,
    onDismiss: () -> Unit,
    onChangePassword: (String, String) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var submitAttempted by remember { mutableStateOf(false) }

    val newPasswordError = if (submitAttempted && newPassword.length < MIN_PASSWORD_LENGTH) {
        "Минимум $MIN_PASSWORD_LENGTH символа"
    } else null
    val confirmPasswordError = if (submitAttempted && confirmPassword != newPassword) {
        "Пароли не совпадают"
    } else null
    val oldPasswordError = if (wrongOldPassword) "Неверный текущий пароль" else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сменить пароль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordTextField(
                    value = oldPassword,
                    onValueChange = {
                        oldPassword = it
                        onOldPasswordEdited()
                    },
                    label = "Текущий пароль",
                    modifier = Modifier.fillMaxWidth(),
                    errorText = oldPasswordError
                )
                PasswordTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "Новый пароль",
                    modifier = Modifier.fillMaxWidth(),
                    errorText = newPasswordError
                )
                PasswordTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Подтвердите пароль",
                    modifier = Modifier.fillMaxWidth(),
                    errorText = confirmPasswordError
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    submitAttempted = true
                    if (newPassword.length >= MIN_PASSWORD_LENGTH && newPassword == confirmPassword) {
                        onChangePassword(oldPassword, newPassword)
                    }
                }
            ) {
                Text("Сменить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
