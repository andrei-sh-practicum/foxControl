package com.andrew.foxcontrol.core.alerts

import android.content.Context
import android.content.Intent
import android.util.Log
import com.andrew.foxcontrol.data.local.entity.AlertLogEntity
import com.andrew.foxcontrol.data.repository.UsageStatsRepositoryImpl
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageStatsRepository: UsageStatsRepositoryImpl
) {
    companion object {
        const val TAG = "AlertManager"
    }

    suspend fun checkAndShowAlerts() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())

        // Check global limit
        if (usageStatsRepository.checkGlobalLimit()) {
            val dailyUsage = usageStatsRepository.getDailyUsage(today)
            val limitMinutes = getGlobalLimitMinutes()
            val packageName = "global"

            if (!wasAlertShownToday(packageName, "global")) {
                showLimitExceededAlert(
                    packageName = packageName,
                    appName = "Весь смартфон",
                    limitMinutes = limitMinutes,
                    usedMinutes = dailyUsage.totalUsageMs.toInt() / (1000 * 60),
                    isGlobal = true
                )
            }
        }

        // Check app-specific limits
        val appLimits = usageStatsRepository.getAppLimitsSync()
        for (limit in appLimits) {
            if (usageStatsRepository.checkAppLimit(limit.packageName)) {
                val dailyUsage = usageStatsRepository.getDailyUsage(today)
                val appUsage = dailyUsage.apps.find { it.packageName == limit.packageName }
                if (appUsage != null) {
                    val packageName = appUsage.packageName
                    if (!wasAlertShownToday(packageName, "app")) {
                        showLimitExceededAlert(
                            packageName = packageName,
                            appName = appUsage.appName,
                            limitMinutes = limit.dailyLimitMinutes,
                            usedMinutes = appUsage.totalDurationMs.toInt() / (1000 * 60),
                            isGlobal = false
                        )
                    }
                }
            }
        }
    }

    private suspend fun wasAlertShownToday(packageName: String, type: String): Boolean {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        calendar.time = dateFormat.parse(dateFormat.format(Date())) ?: return false
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val dayStart = calendar.timeInMillis

        return usageStatsRepository.wasAlertShownToday(packageName, type, dayStart)
    }

    private suspend fun showLimitExceededAlert(
        packageName: String,
        appName: String,
        limitMinutes: Int,
        usedMinutes: Int,
        isGlobal: Boolean
    ) {
        val type = if (isGlobal) "global" else "app"

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
        val notificationHelper = NotificationHelper(context)
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

    private fun getGlobalLimitMinutes(): Int {
        return 120 // Default 2 hours if not configured
    }
}
