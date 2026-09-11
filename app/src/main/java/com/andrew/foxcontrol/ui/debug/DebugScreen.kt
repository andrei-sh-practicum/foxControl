package com.andrew.foxcontrol.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    onBackClick: () -> Unit,
    viewModel: DebugViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedTab by mutableIntStateOf(0)
    val tabs = listOf("БД + Сервис", "Разрешения", "Лог", "Анализ")

    DisposableEffect(Unit) {
        viewModel.loadDebugInfo()
        onDispose {}
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug: Диагностика") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("← Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab buttons (simple Row with buttons for compatibility)
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEachIndexed { index, title ->
                    androidx.compose.material3.Button(
                        onClick = { selectedTab = index },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            HorizontalDivider()

            // Tab content
            when (selectedTab) {
                0 -> TabDatabase(state)
                1 -> TabPermissions(viewModel, context)
                2 -> TabLog(viewModel)
                3 -> TabAnalysis(state)
            }
        }
    }
}

@Composable
private fun TabDatabase(state: DebugState) {
    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text("Загрузка...", modifier = Modifier.padding(top = 16.dp))
        }
    } else if (state.error != null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text("Ошибка: ${state.error}", modifier = Modifier.padding(top = 8.dp))
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DebugSection(
                title = "База данных (Room)",
                status = if (state.sessionCount > 0) "OK" else "Пусто",
                icon = if (state.sessionCount > 0) Icons.Default.CheckCircle else Icons.Default.Error
            ) {
                DebugRow("Записей сессий", state.sessionCount.toString())
                DebugRow("Уникальных приложений", state.uniquePackageCount.toString())
                DebugRow("Диапазон дат", state.dateRangeStr)
            }

            DebugSection(
                title = "Сервис (Heartbeat)",
                status = if (state.heartbeatCount > 0) "OK" else "Нет данных",
                icon = if (state.heartbeatCount > 0) Icons.Default.CheckCircle else Icons.Default.Error
            ) {
                DebugRow("Записей heartbeat", state.heartbeatCount.toString())
                DebugRow("Последний heartbeat", state.lastHeartbeatStr)
            }

            DebugSection(
                title = "Последние сессии (из БД)",
                status = if (state.sessionCount > 0) "OK" else "Нет данных",
                icon = if (state.sessionCount > 0) Icons.Default.CheckCircle else Icons.Default.Error
            ) {
                Text(
                    text = state.recentSessionsStr,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun TabPermissions(viewModel: DebugViewModel, context: android.content.Context) {
    val permissionInfo = viewModel.getPermissionInfo(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DebugSection(
            title = "Разрешения и настройки",
            status = "Инфо",
            icon = Icons.Default.Security
        ) {
            Text(
                text = permissionInfo,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Если PACKAGE_USAGE_STATS = ✗ НЕТ:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Настройки → Безопасность → Доступ к данным об использовании → Fox Control = ВКЛ",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TabLog(viewModel: DebugViewModel) {
    val emailLogs = viewModel.getEmailSchedulerLogs()
    val logContent = viewModel.getLogContent()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // EmailReport log entries (top badge)
        if (emailLogs.isNotEmpty()) {
            DebugSection(
                title = "📧 EmailReport",
                status = "${emailLogs.size} записей",
                icon = Icons.Default.CheckCircle
            ) {
                Text(
                    text = emailLogs.joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }

        // Full log
        DebugSection(
            title = "Полный лог (последние 500 записей)",
            status = "Текст",
            icon = Icons.Default.BugReport
        ) {
            Text(
                text = logContent,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun TabAnalysis(state: DebugState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Check 1: Database empty
        if (state.sessionCount == 0) {
            DebugSection(
                title = "❌ База данных пуста",
                status = "ПРОБЛЕМА",
                icon = Icons.Default.Error
            ) {
                Text(
                    text = "Данные не записываются в Room!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Возможные причины:\n" +
                          "1. Нет разрешения PACKAGE_USAGE_STATS\n" +
                          "2. TrackingJob не запущен\n" +
                          "3. pollUsageStats() возвращает пустой список\n" +
                          "4. trackUsageSession() не вызывается\n" +
                          "5. insertSession() падает с ошибкой",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            DebugSection(
                title = "✅ База данных работает",
                status = "OK",
                icon = Icons.Default.CheckCircle
            ) {
                Text(
                    text = "${state.sessionCount} записей, ${state.uniquePackageCount} приложений",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Check 2: Heartbeat
        if (state.heartbeatCount == 0) {
            DebugSection(
                title = "❌ Heartbeat не записывается",
                status = "ПРОБЛЕМА",
                icon = Icons.Default.Error
            ) {
                Text(
                    text = "Сервис может не работать!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Возможные причины:\n" +
                          "1. Foreground service не запущен\n" +
                          "2. Service onCreate() не вызван\n" +
                          "3. TrackingJob.start() упал с ошибкой",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            DebugSection(
                title = "✅ Сервис активен",
                status = "OK",
                icon = Icons.Default.CheckCircle
            ) {
                Text(
                    text = "Heartbeat: ${state.heartbeatCount} записей",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Check 3: Combined analysis
        DebugSection(
            title = "🔍 Диагностика",
            status = "Вывод",
            icon = Icons.Default.BugReport
        ) {
            val sb = StringBuilder()

            if (state.sessionCount == 0 && state.heartbeatCount == 0) {
                sb.append("⚠️ КРИТИЧЕСКАЯ ПРОБЛЕМА:\n")
                sb.append("Сервис не работает И данные не пишутся.\n\n")
                sb.append("Что делать:\n")
                sb.append("1. Перейдите во вкладку 'Разрешения'\n")
                sb.append("2. Проверьте PACKAGE_USAGE_STATS\n")
                sb.append("3. Если нет — включите вручную\n")
                sb.append("4. Перезапустите приложение\n")
                sb.append("5. Подождите 1-2 минуты\n")
                sb.append("6. Обновите Debug-экран\n\n")
                sb.append("Также проверьте вкладку 'Лог' — там будет подробная цепочка вызовов.")
            } else if (state.sessionCount == 0 && state.heartbeatCount > 0) {
                sb.append("⚠️ Сервис работает, но данные не пишутся.\n\n")
                sb.append("Возможные причины:\n")
                sb.append("1. queryUsageStats() возвращает пустой список\n")
                sb.append("2. Нет разрешения PACKAGE_USAGE_STATS\n")
                sb.append("3. Все дельты = 0 (приложения не активны)\n\n")
                sb.append("Что делать:\n")
                sb.append("1. Откройте вкладку 'Лог'\n")
                sb.append("2. Ищите строки с 'queryUsageStats returned'\n")
                sb.append("3. Если 'NULL/EMPTY' — нет разрешения\n")
                sb.append("4. Если '0 apps found' — тоже нет разрешения\n")
            } else if (state.sessionCount > 0) {
                sb.append("✅ Данные пишутся в базу!\n\n")
                sb.append("Если HomeScreen показывает 'Нет данных':\n")
                sb.append("- Проверьте дату в HomeScreen (должна совпадать с датой в логе)\n")
                sb.append("- Проверьте getDailyUsage() в логе\n")
                sb.append("- Возможно, проблема в формате даты")
            }

            Text(
                text = sb.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun DebugSection(
    title: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    content: @Composable () -> Unit
) {
    val isOk = status in listOf("OK", "Инфо")

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
            content()
        }
    }
}

@Composable
private fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            maxLines = 3
        )
    }
}
