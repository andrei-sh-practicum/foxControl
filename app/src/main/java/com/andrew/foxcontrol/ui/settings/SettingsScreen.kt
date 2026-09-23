package com.andrew.foxcontrol.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.andrew.foxcontrol.R
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onPrivateSettingsClick: () -> Unit,
    onDebugClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Activity result launcher for picking image from gallery
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.saveAvatar(it)
        }
    }

    SettingsContent(
        state = state,
        onBackClick = onBackClick,
        onPrivateSettingsClick = onPrivateSettingsClick,
        onDebugClick = onDebugClick,
        onNameSave = viewModel::saveName,
        onClearMessage = viewModel::clearMessage,
        onAvatarPickClick = { imagePickerLauncher.launch("image/*") }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    state: SettingsState,
    onBackClick: () -> Unit,
    onPrivateSettingsClick: () -> Unit,
    onDebugClick: () -> Unit,
    onNameSave: (String) -> Unit,
    onClearMessage: () -> Unit,
    onAvatarPickClick: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Настройки")
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Avatar image
                val avatarPainter = if (state.avatarUri.isNotEmpty()) {
                    rememberAsyncImagePainter(
                        ImageRequest.Builder(LocalContext.current)
                            .data(state.avatarUri)
                            .build()
                    )
                } else {
                    null
                }

                Image(
                    painter = avatarPainter ?: painterResource(id = R.drawable.ic_launcher_custom),
                    contentDescription = "Аватар пользователя",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .clickable { onAvatarPickClick() },
                    contentScale = ContentScale.Crop
                )

                Text(
                    text = "Нажмите, чтобы изменить аватар",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Name editing section (NEW)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Имя пользователя",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Keep draft in sync with state.name on first load or when user hasn't edited yet
                var nameDraft by remember { mutableStateOf(state.name) }
                val currentName by rememberUpdatedState(state.name)

                // Sync draft with state.name only if draft is empty (user hasn't typed yet)
                LaunchedEffect(currentName) {
                    if (nameDraft.isEmpty() && currentName.isNotEmpty()) {
                        nameDraft = currentName
                    }
                }

                // Clear success/error message after 2 seconds
                LaunchedEffect(state.message) {
                    if (state.message.isNotEmpty()) {
                        delay(2000)
                        onClearMessage()
                    }
                }

                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Введите имя") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        keyboardType = KeyboardType.Text
                    ),
                    singleLine = true
                )

                OutlinedButton(
                    onClick = {
                        onNameSave(nameDraft)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nameDraft.isNotBlank()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Text("Сохранить имя")
                }

                // Success/error message
                if (state.message.isNotEmpty()) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.message.contains("Ошибка")) {
                            Color.Red
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            // Private settings button
            Button(
                onClick = {
                    onPrivateSettingsClick()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Text("Приватные настройки")
            }

            // Debug button
            Button(
                onClick = {
                    onDebugClick()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BugReport, contentDescription = null)
                Text("Debug: Цепочка данных")
            }

            // App version
            Text(
                text = "Версия: ${state.appVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

    }
}
