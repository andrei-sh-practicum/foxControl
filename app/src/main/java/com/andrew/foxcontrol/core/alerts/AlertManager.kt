package com.andrew.foxcontrol.core.alerts

import android.content.Context
import android.content.Intent
import android.util.Log
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
        private const val ALERT_COOLDOWN_MS = 60_000L // 1 minute cooldown
    }

    private var lastAlertTime: Long = 0
    private var lastAlertPackage: String? = null

    suspend fun checkAndShowAlerts() {
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())

        // Check global limit
        if (usageStatsRepository.checkGlobalLimit()) {
            val dailyUsage = usageStatsRepository.getDailyUsage(today)
            val limitMinutes = getGlobalLimitMinutes()

            if (shouldShowAlert("global")) {
                showLimitExceededAlert(
                    packageName = "global",
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
                if (appUsage != null && shouldShowAlert(appUsage.packageName)) {
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
    }

    private fun shouldShowAlert(packageName: String): Boolean {
        val now = System.currentTimeMillis()
        return packageName != lastAlertPackage || (now - lastAlertTime) > ALERT_COOLDOWN_MS
    }

    private fun markAlertShown(packageName: String) {
        lastAlertTime = System.currentTimeMillis()
        lastAlertPackage = packageName
    }

    private suspend fun showLimitExceededAlert(
        packageName: String,
        appName: String,
        limitMinutes: Int,
        usedMinutes: Int,
        isGlobal: Boolean
    ) {
        markAlertShown(packageName)

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

        Log.d(TAG, "Alert shown: $appName - $usedMinutes/$limitMinutes minutes")
    }

    private fun getGlobalLimitMinutes(): Int {
        return 120 // Default 2 hours if not configured
    }
}
