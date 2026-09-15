package com.andrew.foxcontrol.ui.appdetail

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andrew.foxcontrol.core.tracking.AppUsageHourBucket
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

                // Hourly usage chart
                AppUsageHourlyChart(buckets = state.hourlyUsage)
            }
        }
    }
}

@Composable
private fun AppUsageHourlyChart(buckets: List<AppUsageHourBucket>) {
    val chartHeight = 96.dp
    val gridLines = 6

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Summary text
        Text(
            text = "Использование по часам (06:00–22:00)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Chart area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight + 32.dp) // extra space for labels
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLow,
                    MaterialTheme.shapes.small
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            buckets.forEach { bucket ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 1.dp)
                ) {
                    // Column bars — bottom-aligned with empty space on top
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Grid lines background
                        for (i in 1 until gridLines) {
                            val yOffset = (-i * (chartHeight.value / gridLines)).dp
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = yOffset),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Divider(
                                    modifier = Modifier.fillMaxWidth(),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }

                        // Usage bar (primary color)
                        if (bucket.usageMinutes > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(bucket.usageMinutes / 60f)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.shapes.extraSmall
                                    )
                            )
                        }
                    }

                    // Hour label (every other, no ":00" suffix)
                    if (bucket.hour % 2 == 0) {
                        Text(
                            text = bucket.hour.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
