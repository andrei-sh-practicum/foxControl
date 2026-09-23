package com.andrew.foxcontrol.core.alerts

import android.content.Context
import android.content.Intent
import android.util.Log
import com.andrew.foxcontrol.core.util.DateUtils
import com.andrew.foxcontrol.data.local.entity.AlertLogEntity
import com.andrew.foxcontrol.domain.repository.UsageStatsRepository
import com.andrew.foxcontrol.domain.usecase.LimitCalculator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Values of `alert_logs.type`; for the global limit `packageName` is [GLOBAL] too. */
object AlertType {
    const val GLOBAL = "global"
    const val APP = "app"
}

@Singleton
class AlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageStatsRepository: UsageStatsRepository,
    private val notificationHelper: NotificationHelper
) {
    companion object {
        const val TAG = "AlertManager"
    }

    /**
     * Called after every usage poll. Each limit alert is shown at most once per day
     * (deduplicated through alert_logs).
     */
    suspend fun checkAndShowAlerts() {
        // One snapshot of today's usage for all checks of this tick
        val dailyUsage = usageStatsRepository.getDailyUsage(DateUtils.today())

        // Check global limit
        val globalLimit = usageStatsRepository.getGlobalLimit()
        if (globalLimit != null && globalLimit.enabled &&
            LimitCalculator.isExceeded(dailyUsage.totalUsageMs, globalLimit.dailyLimitMinutes)
        ) {
            if (!wasAlertShownToday(AlertType.GLOBAL, AlertType.GLOBAL)) {
                showLimitExceededAlert(
                    packageName = AlertType.GLOBAL,
                    appName = "Весь смартфон",
                    limitMinutes = globalLimit.dailyLimitMinutes,
                    usedMinutes = dailyUsage.totalUsageMs.toInt() / (1000 * 60),
                    isGlobal = true
                )
            }
        }

        // Check app-specific limits (enabled only)
        for (limit in usageStatsRepository.getAppLimits()) {
            val appUsage = dailyUsage.apps.find { it.packageName == limit.packageName } ?: continue
            if (!LimitCalculator.isExceeded(appUsage.totalDurationMs, limit.dailyLimitMinutes)) continue

            if (!wasAlertShownToday(appUsage.packageName, AlertType.APP)) {
                showLimitExceededAlert(
                    packageName = appUsage.packageName,
                    appName = appUsage.appName,
                    limitMinutes = limit.dailyLimitMinutes,
                    usedMinutes = appUsage.totalDurationMs.toInt() / (1000 * 60),
                    isGlobal = false
                )
            }
        }
    }

    private suspend fun wasAlertShownToday(packageName: String, type: String): Boolean =
        usageStatsRepository.wasAlertShownToday(packageName, type, DateUtils.todayStartMs())

    private suspend fun showLimitExceededAlert(
        packageName: String,
        appName: String,
        limitMinutes: Int,
        usedMinutes: Int,
        isGlobal: Boolean
    ) {
        val type = if (isGlobal) AlertType.GLOBAL else AlertType.APP

        // Try to show overlay first
        val overlayIntent = Intent(context, OverlayAlertService::class.java).apply {
            action = OverlayAlertService.ACTION_SHOW
            putExtra(OverlayAlertService.EXTRA_PACKAGE, packageName)
            putExtra(OverlayAlertService.EXTRA_APP_NAME, appName)
            putExtra(OverlayAlertService.EXTRA_LIMIT, limitMinutes)
            putExtra(OverlayAlertService.EXTRA_USED, usedMinutes)
        }
        context.startService(overlayIntent)

        // Always show notification as fallback
        notificationHelper.showLimitExceededNotification(
            packageName = packageName,
            appName = appName,
            limitMinutes = limitMinutes,
            usedMinutes = usedMinutes,
            isGlobal = isGlobal
        )

        // Record alert log (used as "shown today" flag)
        usageStatsRepository.recordAlertLog(
            AlertLogEntity(
                packageName = packageName,
                timestamp = System.currentTimeMillis(),
                type = type
            )
        )

        Log.d(TAG, "Alert shown: $appName - $usedMinutes/$limitMinutes minutes")
    }
}
