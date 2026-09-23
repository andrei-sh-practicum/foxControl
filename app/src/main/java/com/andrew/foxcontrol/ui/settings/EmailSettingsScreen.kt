package com.andrew.foxcontrol.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andrew.foxcontrol.core.email.EmailDefaults

@Composable
fun EmailSettingsScreen(
    onBackClick: () -> Unit,
    viewModel: EmailSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EmailSettingsContent(
        state = state,
        onBackClick = onBackClick,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailSettingsContent(
    state: EmailSettingsState,
    onBackClick: () -> Unit,
    onEvent: (EmailSettingsEvent) -> Unit
) {
    var showTimePicker by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Настройки email") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status indicator
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.isEnabled)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (state.isEnabled) "Отчёты включены" else "Отчёты отключены",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (state.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (state.isEnabled) "Отчёты отправляются автоматически каждый день"
                            else "Включите для автоматической отправки отчётов",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Enable/disable toggle
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Включить отчёты", style = MaterialTheme.typography.titleMedium)
                    androidx.compose.material3.Switch(
                        checked = state.isEnabled,
                        onCheckedChange = { onEvent(EmailSettingsEvent.OnToggleEnabled(it)) }
                    )
                }
            }

            // Send time picker
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Время отправки отчёта", style = MaterialTheme.typography.titleMedium)
                    androidx.compose.material3.Text(
                        text = String.format("%02d:%02d", state.sendTimeHour, state.sendTimeMinute),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = { showTimePicker = 1 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                        Text("Изменить время")
                    }
                    if (showTimePicker == 1) {
                        val timePickerState = rememberTimePickerState(
                            initialHour = state.sendTimeHour,
                            initialMinute = state.sendTimeMinute,
                            is24Hour = true
                        )
                        TimePicker(
                            state = timePickerState,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Button(
                                onClick = {
                                    // Read actual values from TimePickerState at save time
                                    val actualHour = timePickerState.hour
                                    val actualMinute = timePickerState.minute
                                    onEvent(EmailSettingsEvent.OnSendTimeChanged(actualHour, actualMinute))
                                    showTimePicker = 0
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Сохранить")
                            }
                            androidx.compose.material3.TextButton(
                                onClick = { showTimePicker = 0 },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Отмена")
                            }
                        }
                    }
                }
            }

            // SMTP Host
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SMTP-сервер", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.smtpHost,
                        onValueChange = { onEvent(EmailSettingsEvent.OnSmtpHostChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(EmailDefaults.SMTP_HOST) },
                        placeholder = { Text("Например: ${EmailDefaults.SMTP_HOST}") }
                    )
                }
            }

            // SMTP Port
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Порт SMTP", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.smtpPort,
                        onValueChange = { onEvent(EmailSettingsEvent.OnSmtpPortChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Порт") },
                        placeholder = { Text(EmailDefaults.SMTP_PORT) },
                        singleLine = true
                    )
                }
            }

            // Login
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Логин", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.smtpLogin,
                        onValueChange = { onEvent(EmailSettingsEvent.OnSmtpLoginChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email для отправки") },
                        placeholder = { Text(EmailDefaults.SMTP_LOGIN) }
                    )
                }
            }

            // App Password
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Пароль приложения", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.smtpAppPassword,
                        onValueChange = { onEvent(EmailSettingsEvent.OnSmtpAppPasswordChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Пароль приложения") },
                        placeholder = { Text("Введите пароль") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            }

            // From Email
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("От кого", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.fromEmail,
                        onValueChange = { onEvent(EmailSettingsEvent.OnFromEmailChanged(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email отправителя") },
                        placeholder = { Text(EmailDefaults.FROM_EMAIL) }
                    )
                }
            }

            // Save button
            Button(
                onClick = { onEvent(EmailSettingsEvent.OnSaveClicked) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text("Сохранить настройки")
            }

            // Test send button
            Button(
                onClick = { onEvent(EmailSettingsEvent.OnTestSendClicked) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Email, contentDescription = null)
                Text("Отправить тестовое письмо")
            }

            // Status message
            state.lastTestResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (result.success)
                            MaterialTheme.colorScheme.secondaryContainer
                        else
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (result.success) "Успешно!" else "Ошибка",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (result.success) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = result.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (result.success) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}
