package com.andrew.foxcontrol.ui.home

import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
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
import com.andrew.foxcontrol.core.tracking.DowntimeHourBucket
import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.ui.common.AppIcon
import com.andrew.foxcontrol.ui.common.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onSettingsClick: () -> Unit,
    onAppDetailClick: (packageName: String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeContent(
        state = state,
        onEvent = viewModel::onEvent,
        onSettingsClick = onSettingsClick,
        onAppDetailClick = onAppDetailClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeState,
    onEvent: (HomeEvent) -> Unit,
    onSettingsClick: () -> Unit,
    onAppDetailClick: (packageName: String) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Fox Control") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onSettingsClick
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Настройки")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Period switcher
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                HomePeriod.values().forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = state.period == period,
                        onClick = { onEvent(HomeEvent.ChangePeriod(period)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 2)
                    ) {
                        Text(
                            text = period.label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Service downtime chart (Today tab only, first)
            if (state.period == HomePeriod.Today && state.downtimeBuckets.isNotEmpty()) {
                ServiceDowntimeChart(state.downtimeBuckets)
            }

            // Total usage summary
            if (state.period == HomePeriod.Today) {
                state.dailyStats?.let { dailyStats ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Итого за день",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDuration(dailyStats.totalUsageMs),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${dailyStats.apps.size} приложений",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                state.weeklyStats?.let { weeklyStats ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Итого за неделю",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDuration(weeklyStats.totalUsageMs),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${weeklyStats.apps.size} приложений",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Loading state
            if (state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Загрузка статистики...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else if (state.error != null) {
                // Error state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Ошибка",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Button(
                        onClick = { onEvent(HomeEvent.ChangePeriod(state.period)) },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Повторить")
                    }
                }
            } else if (state.period == HomePeriod.Today && (state.dailyStats == null || state.dailyStats.apps.isEmpty())) {
                // Empty state for Today
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Нет данных",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "Статистика появится после начала использования приложений",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else if (state.period == HomePeriod.Week && (state.weeklyStats == null || state.weeklyStats.apps.isEmpty())) {
                // Empty state for Week
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Нет данных",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "Статистика появится после начала использования приложений",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                // App list
                val apps = if (state.period == HomePeriod.Today) {
                    state.dailyStats?.apps ?: emptyList()
                } else {
                    state.weeklyStats?.apps ?: emptyList()
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(apps) { app ->
                        AppUsageCard(
                            packageName = app.packageName,
                            appName = app.appName,
                            totalDurationMs = app.totalDurationMs,
                            sessionCount = app.sessionCount,
                            isEntertainment = app.isEntertainment,
                            category = app.category,
                            onClick = { onAppDetailClick(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageCard(
    packageName: String,
    appName: String,
    totalDurationMs: Long,
    sessionCount: Int,
    isEntertainment: Boolean,
    category: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    // Resolve app name from PackageManager
    var displayName by remember(packageName) {
        mutableStateOf(appName.takeIf { it.isNotEmpty() && it != packageName } ?: packageName)
    }

    LaunchedEffect(packageName) {
        displayName = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                val label = pm.getApplicationLabel(appInfo)
                if (label != null && label.isNotEmpty()) label.toString() else packageName
            } catch (e: Exception) {
                android.util.Log.e("AppUsageCard", "Failed to get app name for $packageName: ${e.message}")
                packageName
            }
        }
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App icon
            AppIcon(
                packageName = packageName,
                size = 40.dp
            )

            // App info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                if (category.isNotBlank()) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "$sessionCount сессий",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Duration
            Text(
                text = formatDuration(totalDurationMs),
                style = MaterialTheme.typography.titleMedium,
                color = if (isEntertainment) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
        }
    }
}

@Composable
private fun ServiceDowntimeChart(buckets: List<DowntimeHourBucket>) {
    val totalDeadMinutes = buckets.sumOf { it.deadMinutes }
    val chartHeight = 96.dp
    val gridLines = 6

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Summary text
        Text(
            text = "Простои сегодня (06:00–22:00): $totalDeadMinutes мин",
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

                        // Dead minutes (red) at bottom
                        if (bucket.deadMinutes > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(bucket.deadMinutes / 60f)
                                    .background(
                                        MaterialTheme.colorScheme.error,
                                        MaterialTheme.shapes.extraSmall
                                    )
                            )
                        }

                        // Alive minutes (green/primary) on top
                        if (bucket.aliveMinutes > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(bucket.aliveMinutes / 60f)
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
