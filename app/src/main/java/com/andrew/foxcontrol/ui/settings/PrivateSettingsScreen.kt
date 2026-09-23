package com.andrew.foxcontrol.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun PrivateSettingsScreen(
    onBackClick: () -> Unit,
    onEmailSettingsClick: () -> Unit,
    onEmailRecipientsClick: () -> Unit,
    onVendorInstructionsClick: () -> Unit,
    viewModel: PrivateSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Gate screen: password entry
    if (!state.isAuthenticated) {
        PrivateSettingsGate(
            onPasswordEntered = { viewModel.onEvent(PrivateSettingsEvent.OnPasswordEntered(it)) },
            onBackClick = onBackClick,
            error = state.error
        )
    } else {
        PrivateSettingsContent(
            state = state,
            onBackClick = onBackClick,
            onEmailSettingsClick = onEmailSettingsClick,
            onEmailRecipientsClick = onEmailRecipientsClick,
            onVendorInstructionsClick = onVendorInstructionsClick,
            onEvent = viewModel::onEvent
        )
    }
}

@Composable
private fun PrivateSettingsGate(
    onPasswordEntered: (String) -> Unit,
    onBackClick: () -> Unit,
    error: String?
) {
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Введите пароль",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp)
        )

        Text(
            text = "Введите пароль для доступа к приватным настройкам",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            label = { Text("Пароль для входа") },
            isError = error != null,
            singleLine = true,
            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                    Icon(
                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (isPasswordVisible) "Скрыть пароль" else "Показать пароль"
                    )
                }
            }
        )

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Button(
            onClick = {
                onPasswordEntered(password)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Вход")
        }

        TextButton(onClick = onBackClick) {
            Text("Назад")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivateSettingsContent(
    state: PrivateSettingsState,
    onBackClick: () -> Unit,
    onEmailSettingsClick: () -> Unit,
    onEmailRecipientsClick: () -> Unit,
    onVendorInstructionsClick: () -> Unit,
    onEvent: (PrivateSettingsEvent) -> Unit
) {
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showGlobalLimitDialog by remember { mutableStateOf(false) }
    var showAppLimitDialog by remember { mutableStateOf(false) }
    var showClearTrackedAppsDialog by remember { mutableStateOf(false) }
    var showExcludeAppDialog by remember { mutableStateOf(false) }

    // Close the change-password dialog only after the new password was saved,
    // then keep the confirmation visible for a moment
    LaunchedEffect(state.passwordChangeResult) {
        if (state.passwordChangeResult == PasswordChangeResult.Success) {
            showChangePasswordDialog = false
            delay(PASSWORD_CHANGED_MESSAGE_MS)
            onEvent(PrivateSettingsEvent.OnPasswordChangeResultConsumed)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Приватные настройки")
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Global daily limit
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Общий дневной лимит",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { showGlobalLimitDialog = true }) {
                        Text("Изменить")
                    }
                }
                Text(
                    text = "${state.globalDailyLimitMinutes} мин/день",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // App limits section
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Индивидуальные лимиты приложений",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = { showAppLimitDialog = true }) {
                        Text("Добавить")
                    }
                }

                if (state.appLimits.isEmpty()) {
                    Text(
                        text = "Нет добавленных приложений",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // Build packageName -> appName lookup
                    val appMap = state.trackedApps.associate { it.packageName to it.appName }
                    state.appLimits.forEach { (packageName, limit) ->
                        val appName = appMap[packageName] ?: packageName
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = appName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "$limit мин",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Excluded apps section
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

            // Change password
            Button(
                onClick = { showChangePasswordDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Text("Сменить пароль")
            }
            if (state.passwordChangeResult == PasswordChangeResult.Success) {
                Text(
                    text = "Пароль изменён",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Email settings
            Button(
                onClick = onEmailSettingsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Text("Настройки email")
            }

            // Email recipients
            Button(
                onClick = onEmailRecipientsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Person, contentDescription = null)
                Text("Получатели отчётов")
            }

            // Vendor instructions
            Button(
                onClick = onVendorInstructionsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Info, contentDescription = null)
                Text("Настройки производительности")
            }

            // Divider
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))
            androidx.compose.material3.Divider()
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 16.dp))

            // Danger zone
            Text(
                text = "Опасная зона",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )

            // Clear tracked apps button
            Button(
                onClick = { showClearTrackedAppsDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text("Очистить таблицу трекинга")
            }
        }

        // Change password dialog
        if (showChangePasswordDialog) {
            val wrongOldPassword = state.passwordChangeResult == PasswordChangeResult.WrongOldPassword
            ChangePasswordDialog(
                wrongOldPassword = wrongOldPassword,
                onOldPasswordEdited = {
                    if (wrongOldPassword) onEvent(PrivateSettingsEvent.OnPasswordChangeResultConsumed)
                },
                onDismiss = {
                    showChangePasswordDialog = false
                    onEvent(PrivateSettingsEvent.OnPasswordChangeResultConsumed)
                },
                onChangePassword = { oldPassword, newPassword ->
                    // Dialog stays open: it's closed by the LaunchedEffect above on success
                    onEvent(PrivateSettingsEvent.OnChangePassword(oldPassword, newPassword))
                }
            )
        }

        // Global limit dialog
        if (showGlobalLimitDialog) {
            AlertDialog(
                onDismissRequest = { showGlobalLimitDialog = false },
                title = { Text("Общий дневной лимит") },
                text = {
                    OutlinedTextField(
                        value = state.globalDailyLimitMinutes.toString(),
                        onValueChange = {
                            val minutes = it.toIntOrNull() ?: 0
                            onEvent(PrivateSettingsEvent.OnGlobalLimitChanged(minutes))
                        },
                        label = { Text("Минут в день") },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showGlobalLimitDialog = false }) {
                        Text("Готово")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGlobalLimitDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        // App limit dialog
        if (showAppLimitDialog) {
            var selectedAppName by remember { mutableStateOf("") }
            var selectedPackageName by remember { mutableStateOf("") }
            var minutesText by remember { mutableStateOf("") }
            var expanded by remember { mutableStateOf(false) }
            var minutesError by remember { mutableStateOf<String?>(null) }

            // Filter out apps that already have a limit
            val availableApps = state.trackedApps
                .filter { app -> state.appLimits[app.packageName] == null }
                .sortedBy { it.appName }

            AlertDialog(
                onDismissRequest = {
                    expanded = false
                    showAppLimitDialog = false
                },
                title = { Text("Добавить лимит приложения") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
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

                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = {
                                minutesText = it
                                // Validate: only digits
                                if (it.isNotEmpty() && it.any { c -> !c.isDigit() }) {
                                    minutesError = "Введите целое число"
                                } else {
                                    minutesError = null
                                }
                            },
                            label = { Text("Лимит (минуты)") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = minutesError != null,
                            supportingText = {
                                if (minutesError != null) {
                                    Text(text = minutesError!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = selectedPackageName.isNotEmpty() &&
                            minutesText.isNotEmpty() &&
                            minutesError == null &&
                            minutesText.toIntOrNull() != null,
                        onClick = {
                            val limitMinutes = minutesText.toIntOrNull()
                            if (limitMinutes != null) {
                                onEvent(
                                    PrivateSettingsEvent.OnAddAppLimit(
                                        packageName = selectedPackageName,
                                        appName = selectedAppName,
                                        limitMinutes = limitMinutes
                                    )
                                )
                                onEvent(PrivateSettingsEvent.OnClearAddAppLimitError)
                                showAppLimitDialog = false
                            }
                        }
                    ) {
                        Text("Добавить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        expanded = false
                        showAppLimitDialog = false
                    }) {
                        Text("Отмена")
                    }
                }
            )
        }

        // Exclude app dialog
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
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
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

        // Clear tracked apps confirmation dialog
        if (showClearTrackedAppsDialog) {
            AlertDialog(
                onDismissRequest = { showClearTrackedAppsDialog = false },
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
                        onClick = {
                            onEvent(PrivateSettingsEvent.OnClearTrackedApps)
                            showClearTrackedAppsDialog = false
                        },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Очистить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearTrackedAppsDialog = false }) {
                        Text("Отмена")
                    }
                }
            )
        }
    }
}

private const val MIN_PASSWORD_LENGTH = 4
private const val PASSWORD_CHANGED_MESSAGE_MS = 2000L

@Composable
private fun ChangePasswordDialog(
    wrongOldPassword: Boolean,
    onOldPasswordEdited: () -> Unit,
    onDismiss: () -> Unit,
    onChangePassword: (String, String) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var oldPasswordVisible by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    var confirmPassword by remember { mutableStateOf("") }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    // Validation errors are shown after the first "Сменить" click
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
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = {
                        oldPassword = it
                        onOldPasswordEdited()
                    },
                    label = { Text("Текущий пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = oldPasswordError != null,
                    supportingText = if (oldPasswordError != null) { { Text(oldPasswordError) } } else null,
                    visualTransformation = if (oldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { oldPasswordVisible = !oldPasswordVisible }) {
                            Icon(
                                imageVector = if (oldPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (oldPasswordVisible) "Скрыть пароль" else "Показать пароль"
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Новый пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = newPasswordError != null,
                    supportingText = if (newPasswordError != null) { { Text(newPasswordError) } } else null,
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (newPasswordVisible) "Скрыть пароль" else "Показать пароль"
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Подтвердите пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = confirmPasswordError != null,
                    supportingText = if (confirmPasswordError != null) { { Text(confirmPasswordError) } } else null,
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (confirmPasswordVisible) "Скрыть пароль" else "Показать пароль"
                            )
                        }
                    }
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
