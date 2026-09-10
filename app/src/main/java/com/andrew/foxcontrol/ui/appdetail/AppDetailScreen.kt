package com.andrew.foxcontrol.ui.appdetail

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andrew.foxcontrol.ui.common.AppIcon
import com.andrew.foxcontrol.ui.common.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppDetailScreen(
    packageName: String,
    onBackClick: () -> Unit,
    viewModel: AppDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Load app detail data when screen is displayed
    LaunchedEffect(packageName) {
        viewModel.loadAppDetail(packageName)
    }

    AppDetailContent(
        state = state,
        packageName = packageName,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailContent(
    state: AppDetailState,
    packageName: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    // Resolve app name from PackageManager
    var displayName by remember(packageName) {
        mutableStateOf("Загрузка...")
    }

    LaunchedEffect(packageName) {
        displayName = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val label = pm.getApplicationLabel(appInfo)
                if (label != null && label.isNotEmpty()) label.toString() else packageName
            } catch (e: Exception) {
                android.util.Log.e("AppDetailScreen", "Failed to get app info for $packageName: ${e.message}")
                packageName
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Детали приложения") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App header with icon and name
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppIcon(
                        packageName = packageName,
                        size = 64.dp
                    )

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    if (state.category.isNotBlank()) {
                        Text(
                            text = state.category,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Text(
                        text = packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Usage stats
            if (state.isLoading) {
                Text(
                    text = "Загрузка статистики...",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Today's usage
                Text(
                    text = "Сегодня",
                    style = MaterialTheme.typography.titleLarge
                )

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Время использования",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDuration(state.todayUsageMs),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${state.sessionCount} сессий",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Sessions list
                if (state.sessions.isNotEmpty()) {
                    Text(
                        text = "Сессии",
                        style = MaterialTheme.typography.titleLarge
                    )

                    state.sessions.forEach { session ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = session.date,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = formatDuration(session.durationMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
