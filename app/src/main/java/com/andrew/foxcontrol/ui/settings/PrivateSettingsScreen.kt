package com.andrew.foxcontrol.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

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
    // Package name of the app limit being edited
    var editingLimitPackage by remember { mutableStateOf<String?>(null) }

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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Errors of the last action (saving limits, excluding apps)
            val actionError = state.error ?: state.addAppLimitError
            if (actionError != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = actionError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        onEvent(PrivateSettingsEvent.OnClearError)
                        onEvent(PrivateSettingsEvent.OnClearAddAppLimitError)
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Скрыть ошибку")
                    }
                }
            }

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
                    text = if (state.globalDailyLimitMinutes > 0) {
                        "${state.globalDailyLimitMinutes} мин/день"
                    } else {
                        "Не установлен"
                    },
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
                    Text(
                        text = "Нажмите на приложение, чтобы изменить или удалить лимит",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                .clickable { editingLimitPackage = packageName }
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
            Spacer(modifier = Modifier.padding(top = 16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.padding(top = 16.dp))

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
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
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

        if (showGlobalLimitDialog) {
            GlobalLimitDialog(
                currentMinutes = state.globalDailyLimitMinutes,
                onConfirm = { minutes ->
                    onEvent(PrivateSettingsEvent.OnGlobalLimitChanged(minutes))
                    showGlobalLimitDialog = false
                },
                onDismiss = { showGlobalLimitDialog = false }
            )
        }

        if (showAppLimitDialog) {
            AddAppLimitDialog(
                // Apps that don't have a limit yet
                availableApps = state.trackedApps
                    .filter { app -> state.appLimits[app.packageName] == null }
                    .sortedBy { it.appName },
                onAdd = { packageName, appName, limitMinutes ->
                    onEvent(PrivateSettingsEvent.OnAddAppLimit(packageName, appName, limitMinutes))
                    onEvent(PrivateSettingsEvent.OnClearAddAppLimitError)
                    showAppLimitDialog = false
                },
                onDismiss = { showAppLimitDialog = false }
            )
        }

        // Edit / remove app limit dialog
        editingLimitPackage?.let { packageName ->
            val appName = state.trackedApps.firstOrNull { it.packageName == packageName }?.appName ?: packageName
            EditAppLimitDialog(
                appName = appName,
                currentMinutes = state.appLimits[packageName] ?: 0,
                onSave = { minutes ->
                    onEvent(PrivateSettingsEvent.OnAppLimitChanged(packageName, minutes))
                    editingLimitPackage = null
                },
                onRemove = {
                    onEvent(PrivateSettingsEvent.OnRemoveAppLimit(packageName))
                    editingLimitPackage = null
                },
                onDismiss = { editingLimitPackage = null }
            )
        }

        if (showExcludeAppDialog) {
            ExcludeAppDialog(
                availableApps = state.trackedApps
                    .filter { app -> !app.isExcluded }
                    .sortedBy { it.appName },
                onExclude = { packageName ->
                    onEvent(PrivateSettingsEvent.OnExcludeApp(packageName))
                    showExcludeAppDialog = false
                },
                onDismiss = { showExcludeAppDialog = false }
            )
        }

        if (showClearTrackedAppsDialog) {
            ClearTrackedAppsDialog(
                onConfirm = {
                    onEvent(PrivateSettingsEvent.OnClearTrackedApps)
                    showClearTrackedAppsDialog = false
                },
                onDismiss = { showClearTrackedAppsDialog = false }
            )
        }
    }
}

private const val PASSWORD_CHANGED_MESSAGE_MS = 2000L
