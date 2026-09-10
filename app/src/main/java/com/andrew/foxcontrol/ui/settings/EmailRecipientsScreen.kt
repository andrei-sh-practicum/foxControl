package com.andrew.foxcontrol.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EmailRecipientsScreen(
    onBackClick: () -> Unit,
    viewModel: EmailRecipientsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    EmailRecipientsContent(
        state = state,
        onBackClick = onBackClick,
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailRecipientsContent(
    state: EmailRecipientsState,
    onBackClick: () -> Unit,
    onEvent: (EmailRecipientsEvent) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingRecipient by remember { mutableStateOf<EmailRecipientState?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Получатели отчётов")
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.recipients.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Нет получателей",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "Добавьте получателей для отправки отчётов",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.recipients, key = { it.id }) { recipient ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (recipient.name.isNotEmpty()) recipient.name else recipient.email,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = recipient.email,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            editingRecipient = recipient
                                            showAddDialog = true
                                        }) {
                                            Icon(Icons.Default.Save, contentDescription = "Редактировать")
                                        }
                                        IconButton(onClick = {
                                            onEvent(EmailRecipientsEvent.OnDeleteClicked(recipient))
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (recipient.isActive) "Активен" else "Неактивен",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (recipient.isActive)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(onClick = {
                                        onEvent(EmailRecipientsEvent.OnToggleActive(recipient.copy(isActive = !recipient.isActive)))
                                    }) {
                                        Icon(
                                            if (recipient.isActive) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                                            contentDescription = if (recipient.isActive) "Деактивировать" else "Активировать",
                                            tint = if (recipient.isActive)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add/Edit dialog
    if (showAddDialog) {
        AddRecipientDialog(
            recipient = editingRecipient,
            onDismiss = {
                showAddDialog = false
                editingRecipient = null
            },
            onSave = { name, email ->
                if (editingRecipient != null) {
                    onEvent(EmailRecipientsEvent.OnUpdateClicked(editingRecipient!!.copy(name = name, email = email)))
                } else {
                    onEvent(EmailRecipientsEvent.OnAddClicked(name, email))
                }
                showAddDialog = false
                editingRecipient = null
            }
        )
    }
}

@Composable
private fun AddRecipientDialog(
    recipient: EmailRecipientState?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(recipient?.name ?: "") }
    var email by remember { mutableStateOf(recipient?.email ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (recipient != null) "Редактировать получателя" else "Добавить получателя") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Имя (необязательно)") },
                    placeholder = { Text("Иван Иванов") }
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    placeholder = { Text("ivan@example.com") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            ElevatedButton(onClick = {
                if (email.isNotBlank()) {
                    onSave(name, email)
                }
            }) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text(if (recipient != null) "Сохранить" else "Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
