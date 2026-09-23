package com.andrew.foxcontrol.domain.repository

import com.andrew.foxcontrol.core.tracking.AppUsageHourBucket
import com.andrew.foxcontrol.core.tracking.DowntimeHourBucket
import com.andrew.foxcontrol.data.local.entity.AlertLogEntity
import com.andrew.foxcontrol.data.local.entity.AppLimitEntity
import com.andrew.foxcontrol.data.local.entity.GlobalLimitEntity
import com.andrew.foxcontrol.data.local.entity.TrackedAppEntity
import com.andrew.foxcontrol.data.local.entity.UsageSessionEntity
import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.domain.model.DebugInfo
import com.andrew.foxcontrol.domain.model.WeeklyUsageStats

interface UsageStatsRepository {
    // --- Statistics ---
    suspend fun getDailyUsage(date: String): DailyUsageStats
    suspend fun getWeeklyUsage(startDate: String, endDate: String): WeeklyUsageStats
    suspend fun getTodaySessionsForPackage(packageName: String, date: String): List<UsageSessionEntity>
    suspend fun getHourlyUsageForPackage(packageName: String, date: String): List<AppUsageHourBucket>
    suspend fun getHourlyUsageForAllApps(date: String): List<AppUsageHourBucket>

    // --- Tracking ---
    suspend fun trackUsageSession(
        packageName: String,
        appName: String,
        startTime: Long,
        endTime: Long,
        durationMs: Long,
        isEntertainment: Boolean
    )
    suspend fun recordHeartbeat()

    // --- Tracked apps ---
    suspend fun getTrackedApp(packageName: String): TrackedAppEntity?
    suspend fun getTrackedApps(): List<TrackedAppEntity>
    suspend fun setAppExcluded(packageName: String, excluded: Boolean)
    suspend fun clearTrackedApps()

    // --- Limits ---
    suspend fun getGlobalLimit(): GlobalLimitEntity?
    /** Enabled app limits only. */
    suspend fun getAppLimits(): List<AppLimitEntity>
    suspend fun setAppLimit(packageName: String, dailyLimitMinutes: Int, enabled: Boolean)

    // --- Alerts ---
    suspend fun wasAlertShownToday(packageName: String, type: String, dayStart: Long): Boolean
    suspend fun recordAlertLog(log: AlertLogEntity)

    // --- Debug ---
    suspend fun getServiceDowntimeBuckets(date: String): List<DowntimeHourBucket>
    suspend fun getDebugInfo(): DebugInfo
}
