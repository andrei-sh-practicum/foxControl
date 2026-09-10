package com.andrew.foxcontrol.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.andrew.foxcontrol.R

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
            hasPassword = state.hasPassword,
            onPasswordEntered = { viewModel.onEvent(PrivateSettingsEvent.OnPasswordEntered(it)) },
            onSetPassword = { viewModel.onEvent(PrivateSettingsEvent.OnSetPassword(it)) },
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
    hasPassword: Boolean,
    onPasswordEntered: (String) -> Unit,
    onSetPassword: (String) -> Unit,
    onBackClick: () -> Unit,
    error: String?
) {
    var password by remember { mutableStateOf("") }
    var isSetMode by remember { mutableStateOf(!hasPassword) }

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
            text = if (isSetMode) "Создайте пароль" else "Введите пароль",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp)
        )

        Text(
            text = if (isSetMode) "Пароль нужен для доступа к приватным настройкам" else "Введите пароль для доступа к приватным настройкам",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            label = { Text("Пароль") },
            isError = error != null,
            singleLine = true
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
                if (isSetMode) {
                    if (password.length >= 4) {
                        onSetPassword(password)
                    }
                } else {
                    onPasswordEntered(password)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text(if (isSetMode) "Установить пароль" else "Войти")
        }

        if (hasPassword) {
            TextButton(onClick = {
                isSetMode = false
                password = ""
            }) {
                Text("У меня есть пароль")
            }
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
                    state.appLimits.forEach { (packageName, limit) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = packageName,
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

            // Change password
            Button(
                onClick = { showChangePasswordDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Text("Сменить пароль")
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
            ChangePasswordDialog(
                onDismiss = { showChangePasswordDialog = false },
                onChangePassword = { oldPassword, newPassword ->
                    onEvent(PrivateSettingsEvent.OnChangePassword(oldPassword, newPassword))
                    showChangePasswordDialog = false
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
            AlertDialog(
                onDismissRequest = { showAppLimitDialog = false },
                title = { Text("Добавить лимит приложения") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = { /* TODO: implement */ },
                            label = { Text("Пакет приложения") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = "",
                            onValueChange = { /* TODO: implement */ },
                            label = { Text("Лимит (минуты)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAppLimitDialog = false }) {
                        Text("Добавить")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAppLimitDialog = false }) {
                        Text("Отмена")
                    }
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

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onChangePassword: (String, String) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сменить пароль") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPassword,
                    onValueChange = { oldPassword = it },
                    label = { Text("Текущий пароль") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Новый пароль") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Подтвердите пароль") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newPassword == confirmPassword && newPassword.length >= 4) {
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
