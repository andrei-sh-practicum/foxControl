package com.andrew.foxcontrol.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andrew.foxcontrol.core.permissions.OnboardingPermissions

@Composable
fun OnboardingScreen(
    onNavigationCompleted: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-check after returning from system settings / permission dialog
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onCheckPermissions()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Denied permanently ("don't ask again" / second denial) — the dialog won't
            // show anymore, only the app notification settings can enable it
            val activity = context.findActivity()
            val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.POST_NOTIFICATIONS
            )
            if (!canAskAgain) viewModel.openNotificationSettings()
        }
        viewModel.onCheckPermissions()
    }

    OnboardingContent(
        state = state,
        onNavigationCompleted = onNavigationCompleted,
        onOpenUsageStats = viewModel::openUsageStatsSettings,
        onOpenOverlay = viewModel::openOverlayPermissionSettings,
        onRequestNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.openNotificationSettings()
            }
        },
        onOpenBattery = viewModel::openBatteryOptimizationSettings,
        onCheckPermissions = viewModel::onCheckPermissions,
        onContinueWithoutOptional = viewModel::onContinueWithoutOptional
    )

    // Navigate when done
    if (state.status == OnboardingStatus.Done) {
        LaunchedEffect(Unit) {
            onNavigationCompleted()
        }
    }
}

@Composable
private fun OnboardingContent(
    state: OnboardingState,
    onNavigationCompleted: () -> Unit,
    onOpenUsageStats: () -> Unit,
    onOpenOverlay: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBattery: () -> Unit,
    onCheckPermissions: () -> Unit,
    onContinueWithoutOptional: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        Text(
            text = "🦊 Fox Control",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Контроль времени использования смартфона",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        when (state.status) {
            OnboardingStatus.CheckingPermissions -> {
                CircularProgressIndicator()
                Text(
                    text = "Проверка разрешений...",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            OnboardingStatus.NeedsPermissions -> {
                PermissionCard(
                    icon = Icons.Default.Settings,
                    title = "Доступ к использованию приложений",
                    description = "Fox Control нужен доступ к статистике использования приложений для отслеживания времени.",
                    granted = state.permissions.usageStats,
                    onAction = onOpenUsageStats,
                    actionLabel = "Открыть настройки"
                )

                PermissionCard(
                    icon = Icons.Default.Lock,
                    title = "Поверх других приложений",
                    description = "Разрешение нужно для показа предупреждений о превышении лимита поверх других приложений.",
                    granted = state.permissions.overlay,
                    onAction = onOpenOverlay,
                    actionLabel = "Открыть настройки"
                )

                PermissionCard(
                    icon = Icons.Default.Notifications,
                    title = "Уведомления (рекомендуется)",
                    description = "Нужно для показа уведомлений о статистике и предупреждениях.",
                    granted = state.permissions.notifications,
                    onAction = onRequestNotifications,
                    actionLabel = "Разрешить уведомления"
                )

                PermissionCard(
                    icon = Icons.Default.Info,
                    title = "Экономия батареи (опционально)",
                    description = "Рекомендуется отключить оптимизацию батареи для стабильной работы в фоне.",
                    granted = state.permissions.batteryOptimization,
                    onAction = onOpenBattery,
                    actionLabel = "Отключить оптимизацию"
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onCheckPermissions,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Проверить ещё раз")
                }

                if (state.permissions.criticalGranted) {
                    OutlinedButton(
                        onClick = onContinueWithoutOptional,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Продолжить без них")
                    }

                    Text(
                        text = "Основные разрешения выданы. Уведомления и отключение оптимизации батареи рекомендуются для стабильной работы в фоне.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Text(
                        text = "Без доступа к использованию приложений и показа поверх других приложений Fox Control не сможет работать",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            OnboardingStatus.Done -> {
                CircularProgressIndicator()
                Text(
                    text = "Готово!",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onAction: (() -> Unit)?,
    actionLabel: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (granted) {
                Text(
                    text = "✓ Предоставлено",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            } else if (onAction != null && actionLabel != null) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(icon, contentDescription = null)
                    Text(actionLabel)
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
