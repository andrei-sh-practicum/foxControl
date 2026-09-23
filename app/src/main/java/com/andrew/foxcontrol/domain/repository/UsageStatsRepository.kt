package com.andrew.foxcontrol.domain.repository

import com.andrew.foxcontrol.core.tracking.AppUsageHourBucket
import com.andrew.foxcontrol.core.tracking.DowntimeHourBucket
import com.andrew.foxcontrol.data.local.entity.AppLimitEntity
import com.andrew.foxcontrol.data.local.entity.TrackedAppEntity
import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.domain.model.WeeklyUsageStats

interface UsageStatsRepository {
    suspend fun getDailyUsage(date: String): DailyUsageStats
    suspend fun getWeeklyUsage(startDate: String, endDate: String): WeeklyUsageStats
    suspend fun getTrackedApp(packageName: String): com.andrew.foxcontrol.data.local.entity.TrackedAppEntity?
    suspend fun getTodaySessionsForPackage(packageName: String, date: String): List<com.andrew.foxcontrol.data.local.entity.UsageSessionEntity>
    suspend fun clearTrackedApps()
    suspend fun getServiceDowntimeBuckets(date: String): List<DowntimeHourBucket>
    suspend fun getHourlyUsageForPackage(packageName: String, date: String): List<AppUsageHourBucket>
    suspend fun getHourlyUsageForAllApps(date: String): List<AppUsageHourBucket>
    suspend fun getTrackedApps(): List<com.andrew.foxcontrol.data.local.entity.TrackedAppEntity>
    suspend fun getAppLimits(): List<com.andrew.foxcontrol.data.local.entity.AppLimitEntity>
    suspend fun setAppLimit(packageName: String, dailyLimitMinutes: Int, enabled: Boolean)
    suspend fun setAppExcluded(packageName: String, excluded: Boolean)
}
