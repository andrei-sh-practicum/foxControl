package com.andrew.foxcontrol.domain.repository

import com.andrew.foxcontrol.core.tracking.DowntimeHourBucket
import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.domain.model.UsageStats
import com.andrew.foxcontrol.domain.model.WeeklyUsageStats

interface UsageStatsRepository {
    suspend fun getDailyUsage(date: String): DailyUsageStats
    suspend fun getWeeklyUsage(startDate: String, endDate: String): WeeklyUsageStats
    suspend fun getTopApps(count: Int = 10): List<UsageStats>
    suspend fun getUsageForPackage(packageName: String, startDate: String): List<UsageStats>
    suspend fun getTopEntertainmentApps(count: Int = 10): List<UsageStats>
    suspend fun getTrackedApp(packageName: String): com.andrew.foxcontrol.data.local.entity.TrackedAppEntity?
    suspend fun upsertTrackedApp(app: com.andrew.foxcontrol.data.local.entity.TrackedAppEntity)
    suspend fun getTodaySessionsForPackage(packageName: String, date: String): List<com.andrew.foxcontrol.data.local.entity.UsageSessionEntity>
    suspend fun clearTrackedApps()
    suspend fun getServiceDowntimeBuckets(date: String): List<DowntimeHourBucket>
}
