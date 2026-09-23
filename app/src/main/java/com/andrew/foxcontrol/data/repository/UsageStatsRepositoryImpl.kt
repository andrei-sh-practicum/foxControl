package com.andrew.foxcontrol.data.repository

import android.content.pm.PackageManager
import com.andrew.foxcontrol.core.tracking.CategoryResolver
import com.andrew.foxcontrol.core.tracking.ChartWindow
import com.andrew.foxcontrol.core.tracking.AppUsageHourBucket
import com.andrew.foxcontrol.core.tracking.AppUsageHourCalculator
import com.andrew.foxcontrol.core.tracking.DowntimeCalculator
import com.andrew.foxcontrol.core.tracking.DowntimeHourBucket
import com.andrew.foxcontrol.core.tracking.TrackingLogStorage
import com.andrew.foxcontrol.core.util.DateUtils
import com.andrew.foxcontrol.data.local.dao.*
import com.andrew.foxcontrol.data.local.entity.*
import com.andrew.foxcontrol.domain.model.DebugInfo
import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.domain.model.UsageStats
import com.andrew.foxcontrol.domain.model.WeeklyUsageStats
import com.andrew.foxcontrol.domain.repository.UsageStatsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsRepositoryImpl @Inject constructor(
    private val usageSessionDao: UsageSessionDao,
    private val trackedAppDao: TrackedAppDao,
    private val appLimitDao: AppLimitDao,
    private val globalLimitDao: GlobalLimitDao,
    private val serviceHeartbeatDao: ServiceHeartbeatDao,
    private val alertLogDao: AlertLogDao,
    private val packageManager: PackageManager
) : UsageStatsRepository {

    override suspend fun getDailyUsage(date: String): DailyUsageStats {
        val sessions = usageSessionDao.getSessionsByDateSync(date)
        // One entry per package; the name comes from the most recent session
        // (an app can be renamed during the day — update, system language change)
        val apps = sessions.groupBy { it.packageName }.map { (packageName, list) ->
            UsageStats(
                packageName = packageName,
                appName = list.maxBy { it.endTime }.appName,
                totalDurationMs = list.sumOf { it.durationMs },
                sessionCount = list.size,
                isEntertainment = list.firstOrNull()?.isEntertainment ?: false,
                category = "" // Will be filled from tracked_apps below
            )
        }.sortedByDescending { it.totalDurationMs }

        // Full data: short-use apps are hidden only in the UI lists (UsageListFilter)
        val appsWithCategory = withCategories(apps)

        return DailyUsageStats(
            date = date,
            totalUsageMs = sessions.sumOf { it.durationMs },
            apps = appsWithCategory
        )
    }

    override suspend fun getWeeklyUsage(startDate: String, endDate: String): WeeklyUsageStats {
        val summary = usageSessionDao.getWeeklyUsageByPackage(startDate, endDate)
        val apps = summary.map { s ->
            UsageStats(
                packageName = s.packageName,
                appName = s.appName,
                totalDurationMs = s.totalMs,
                sessionCount = s.sessionCount,
                isEntertainment = false,
                category = "" // Will be filled from tracked_apps below
            )
        }.sortedByDescending { it.totalDurationMs }
            .let { withCategories(it) }

        return WeeklyUsageStats(
            startDate = startDate,
            endDate = endDate,
            totalUsageMs = apps.sumOf { it.totalDurationMs },
            apps = apps
        )
    }

    /** Fills [UsageStats.category] from tracked_apps (apps without a category keep ""). */
    private suspend fun withCategories(apps: List<UsageStats>): List<UsageStats> {
        val categoryMap = trackedAppDao.getAllTrackedAppsSync().associate { it.packageName to it.category }
        return apps.map { app ->
            val cat = categoryMap[app.packageName] ?: ""
            if (cat.isNotEmpty()) app.copy(category = cat) else app
        }
    }

    override suspend fun getTodaySessionsForPackage(packageName: String, date: String): List<UsageSessionEntity> {
        return usageSessionDao.getSessionsByPackageAndDate(packageName, date)
    }

    override suspend fun getTrackedApp(packageName: String): TrackedAppEntity? {
        return trackedAppDao.getTrackedApp(packageName)
    }

    override suspend fun clearTrackedApps() {
        TrackingLogStorage.add("Repo", "clearTrackedApps: START")
        trackedAppDao.deleteAllTrackedApps()
        usageSessionDao.deleteAllSessions()
        TrackingLogStorage.add("Repo", "clearTrackedApps: DONE")
    }

    // --- Tracking methods ---

    override suspend fun trackUsageSession(
        packageName: String,
        appName: String,
        startTime: Long,
        endTime: Long,
        durationMs: Long,
        isEntertainment: Boolean
    ) {
        try {
            // Check exclusion BEFORE writing anything
            val existingApp = trackedAppDao.getTrackedApp(packageName)
            if (existingApp?.isExcluded == true) {
                return
            }

            val date = DateUtils.format(startTime)
            val session = UsageSessionEntity(
                packageName = packageName,
                appName = appName,
                startTime = startTime,
                endTime = endTime,
                durationMs = durationMs,
                date = date,
                isEntertainment = isEntertainment
            )
            usageSessionDao.insertSession(session)

            // Upsert tracked app; the category is resolved (PackageManager call) only once
            if (existingApp == null) {
                // First time seeing this app — insert with category
                val trackedApp = TrackedAppEntity(
                    packageName = packageName,
                    appName = appName,
                    iconUri = null,
                    category = CategoryResolver.resolve(packageManager, packageName),
                    isEntertainment = isEntertainment,
                    isExcluded = false,
                    lastUsedTime = endTime,
                    totalUsageMs = durationMs
                )
                trackedAppDao.insertTrackedApp(trackedApp)
            } else {
                // Update usage for existing app
                trackedAppDao.updateUsage(packageName, durationMs, endTime)
            }
        } catch (e: Exception) {
            TrackingLogStorage.add("Repo", "trackUsageSession EXCEPTION: ${e.message}")
            TrackingLogStorage.add("Repo", e.stackTraceToString())
            throw e
        }
    }

    // --- Heartbeat ---

    override suspend fun recordHeartbeat() {
        try {
            serviceHeartbeatDao.insertHeartbeat(
                ServiceHeartbeatEntity(timestamp = System.currentTimeMillis())
            )
        } catch (e: Exception) {
            // Log but don't crash
        }
    }

    // --- Limits ---

    override suspend fun getGlobalLimit(): GlobalLimitEntity? = globalLimitDao.getGlobalLimit()

    override suspend fun setGlobalLimit(dailyLimitMinutes: Int) {
        globalLimitDao.insertGlobalLimit(
            GlobalLimitEntity(
                dailyLimitMinutes = dailyLimitMinutes,
                enabled = dailyLimitMinutes > 0
            )
        )
    }

    // --- App limits ---

    override suspend fun getTrackedApps(): List<TrackedAppEntity> {
        return trackedAppDao.getAllTrackedAppsSync()
    }

    /** Enabled app limits only. */
    override suspend fun getAppLimits(): List<AppLimitEntity> = appLimitDao.getEnabledLimitsSync()

    override suspend fun setAppLimit(packageName: String, dailyLimitMinutes: Int, enabled: Boolean) {
        appLimitDao.insertLimit(AppLimitEntity(packageName, dailyLimitMinutes, enabled))
    }

    override suspend fun removeAppLimit(packageName: String) {
        appLimitDao.deleteLimit(packageName)
    }

    override suspend fun setAppExcluded(packageName: String, excluded: Boolean) {
        trackedAppDao.setExcluded(packageName, excluded)
    }

    // --- Alert logs ---

    override suspend fun wasAlertShownToday(packageName: String, type: String, dayStart: Long): Boolean {
        return alertLogDao.wasAlertShownToday(packageName, type, dayStart)
    }

    override suspend fun recordAlertLog(log: AlertLogEntity) {
        alertLogDao.insertAlertLog(log)
    }

    // --- Debug ---

    override suspend fun getDebugInfo(): DebugInfo {
        val sessionCount = usageSessionDao.getSessionCount()
        val uniquePackages = usageSessionDao.getUniquePackageCount()
        val dateRange = usageSessionDao.getDateRange()
        val recentSessions = usageSessionDao.getRecentSessions()
        val heartbeatCount = serviceHeartbeatDao.getHeartbeatCount()
        val lastHeartbeat = serviceHeartbeatDao.getLastHeartbeatTimestamp()

        return DebugInfo(
            sessionCount = sessionCount,
            uniquePackageCount = uniquePackages,
            dateRange = dateRange,
            recentSessions = recentSessions,
            heartbeatCount = heartbeatCount,
            lastHeartbeatTimestamp = lastHeartbeat
        )
    }

    override suspend fun getServiceDowntimeBuckets(date: String): List<DowntimeHourBucket> {
        try {
            val (dayStart, dayEnd) = DateUtils.dayBoundsMs(date)

            val heartbeats = serviceHeartbeatDao.getHeartbeatsBetween(dayStart, dayEnd)
            val timestamps = heartbeats.map { it.timestamp }

            return DowntimeCalculator.calculate(timestamps, System.currentTimeMillis(), dayStartMs = dayStart)
        } catch (e: Exception) {
            TrackingLogStorage.add("Repo", "getServiceDowntimeBuckets EXCEPTION: ${e.message}")
            TrackingLogStorage.add("Repo", e.stackTraceToString())
            // Return empty buckets on error
            return ChartWindow.emptyDowntimeBuckets()
        }
    }

    override suspend fun getHourlyUsageForPackage(packageName: String, date: String): List<AppUsageHourBucket> {
        try {
            val sessions = usageSessionDao.getSessionsByPackageAndDate(packageName, date)
            val intervals = sessions.map { it.startTime to it.endTime }

            return AppUsageHourCalculator.calculate(
                intervals,
                System.currentTimeMillis(),
                dayStartMs = DateUtils.dayBoundsMs(date).first
            )
        } catch (e: Exception) {
            TrackingLogStorage.add("Repo", "getHourlyUsageForPackage EXCEPTION: ${e.message}")
            TrackingLogStorage.add("Repo", e.stackTraceToString())
            return ChartWindow.emptyUsageBuckets()
        }
    }

    override suspend fun getHourlyUsageForAllApps(date: String): List<AppUsageHourBucket> {
        try {
            val sessions = usageSessionDao.getSessionsByDateSync(date)
            val intervals = sessions.map { it.startTime to it.endTime }

            return AppUsageHourCalculator.calculate(
                intervals,
                System.currentTimeMillis(),
                dayStartMs = DateUtils.dayBoundsMs(date).first
            )
        } catch (e: Exception) {
            TrackingLogStorage.add("Repo", "getHourlyUsageForAllApps EXCEPTION: ${e.message}")
            TrackingLogStorage.add("Repo", e.stackTraceToString())
            return ChartWindow.emptyUsageBuckets()
        }
    }
}
