package com.andrew.foxcontrol.data.repository

import android.content.pm.PackageManager
import com.andrew.foxcontrol.core.tracking.CategoryResolver
import com.andrew.foxcontrol.core.tracking.AppUsageHourBucket
import com.andrew.foxcontrol.core.tracking.AppUsageHourCalculator
import com.andrew.foxcontrol.core.tracking.DowntimeCalculator
import com.andrew.foxcontrol.core.tracking.DowntimeHourBucket
import com.andrew.foxcontrol.core.tracking.TrackingLogStorage
import com.andrew.foxcontrol.data.local.dao.*
import com.andrew.foxcontrol.data.local.entity.*
import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.domain.model.UsageStats
import com.andrew.foxcontrol.domain.model.WeeklyUsageStats
import com.andrew.foxcontrol.domain.repository.UsageStatsRepository
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.isNotBlank

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

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override suspend fun getDailyUsage(date: String): DailyUsageStats {
        val sessions = usageSessionDao.getSessionsByDateSync(date)
        val apps = sessions.groupBy { it.packageName to it.appName }.map { (key, list) ->
            UsageStats(
                packageName = key.first,
                appName = key.second,
                totalDurationMs = list.sumOf { it.durationMs },
                sessionCount = list.size,
                isEntertainment = list.firstOrNull()?.isEntertainment ?: false,
                category = "" // Will be filled from tracked_apps below
            )
        }.sortedByDescending { it.totalDurationMs }

        // Fill category from tracked_apps
        val trackedApps = trackedAppDao.getAllTrackedAppsSync()
        val categoryMap = trackedApps.associate { it.packageName to it.category }
        val appsWithCategory = apps.map { app ->
            val cat = categoryMap[app.packageName] ?: ""
            if (cat.isNotEmpty()) app.copy(category = cat) else app
        }

        return DailyUsageStats(
            date = date,
            totalUsageMs = sessions.sumOf { it.durationMs },
            apps = appsWithCategory
        )
    }

    override suspend fun getWeeklyUsage(startDate: String, endDate: String): WeeklyUsageStats {
        val summary = usageSessionDao.getWeeklyUsageByPackage(startDate, endDate)
        var apps = summary.map { s ->
            UsageStats(
                packageName = s.packageName,
                appName = s.appName,
                totalDurationMs = s.totalMs,
                sessionCount = s.sessionCount,
                isEntertainment = false,
                category = "" // Will be filled from tracked_apps below
            )
        }.sortedByDescending { it.totalDurationMs }

        // Fill category from tracked_apps
        val trackedApps = trackedAppDao.getAllTrackedAppsSync()
        val categoryMap = trackedApps.associate { it.packageName to it.category }
        apps = apps.map { app ->
            val cat = categoryMap[app.packageName] ?: ""
            if (cat.isNotEmpty()) app.copy(category = cat) else app
        }

        return WeeklyUsageStats(
            startDate = startDate,
            endDate = endDate,
            totalUsageMs = apps.sumOf { it.totalDurationMs },
            apps = apps
        )
    }

    override suspend fun getTopApps(count: Int): List<UsageStats> {
        val today = dateFormat.format(Date())
        val usage = getDailyUsage(today)
        return usage.apps.take(count)
    }

    override suspend fun getUsageForPackage(packageName: String, startDate: String): List<UsageStats> {
        val today = dateFormat.format(Date())
        val daily = getDailyUsage(today)
        return daily.apps.filter { it.packageName == packageName }
    }

    override suspend fun getTopEntertainmentApps(count: Int): List<UsageStats> {
        val today = dateFormat.format(Date())
        val usage = getDailyUsage(today)
        return usage.apps
            .filter { it.isEntertainment }
            .take(count)
    }

    override suspend fun getTodaySessionsForPackage(packageName: String, date: String): List<com.andrew.foxcontrol.data.local.entity.UsageSessionEntity> {
        return usageSessionDao.getSessionsByPackageAndDate(packageName, date)
    }

    override suspend fun getTrackedApp(packageName: String): TrackedAppEntity? {
        return trackedAppDao.getTrackedApp(packageName)
    }

    override suspend fun upsertTrackedApp(app: TrackedAppEntity) {
        trackedAppDao.insertTrackedApp(app)
    }

    override suspend fun clearTrackedApps() {
        TrackingLogStorage.add("Repo", "clearTrackedApps: START")
        trackedAppDao.deleteAllTrackedApps()
        usageSessionDao.deleteAllSessions()
        TrackingLogStorage.add("Repo", "clearTrackedApps: DONE")
    }

    // --- Tracking methods ---

    suspend fun trackUsageSession(
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

            val date = dateFormat.format(Date(startTime))
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

            // Upsert tracked app with category resolution
            val category = CategoryResolver.resolve(packageManager, packageName)
            if (existingApp == null) {
                // First time seeing this app — insert with category
                val trackedApp = TrackedAppEntity(
                    packageName = packageName,
                    appName = appName,
                    iconUri = null,
                    category = category,
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

    suspend fun upsertTrackedApps(apps: List<TrackedAppEntity>) {
        trackedAppDao.insertTrackedApps(apps)
    }

    // --- Limit checking ---

    suspend fun checkGlobalLimit(): Boolean {
        val limit = globalLimitDao.getGlobalLimit() ?: return false
        if (!limit.enabled) return false

        val today = dateFormat.format(Date())
        val dailyUsage = getDailyUsage(today)
        val limitMs = limit.dailyLimitMinutes * 60L * 1000L

        return dailyUsage.totalUsageMs > limitMs
    }

    suspend fun checkAppLimit(packageName: String): Boolean {
        val limit = appLimitDao.getLimit(packageName) ?: return false
        if (!limit.enabled) return false

        val today = dateFormat.format(Date())
        val dailyUsage = getDailyUsage(today)

        val appUsage = dailyUsage.apps.find { it.packageName == packageName }
            ?: return false

        val limitMs = limit.dailyLimitMinutes * 60L * 1000L
        return appUsage.totalDurationMs > limitMs
    }

    // --- Heartbeat ---

    suspend fun recordHeartbeat() {
        try {
            serviceHeartbeatDao.insertHeartbeat(
                ServiceHeartbeatEntity(timestamp = System.currentTimeMillis())
            )
        } catch (e: Exception) {
            // Log but don't crash
        }
    }

    suspend fun getLastHeartbeat(): Long? {
        return serviceHeartbeatDao.getLastHeartbeat()?.timestamp
    }

    // --- Limits ---

    suspend fun setGlobalLimit(dailyLimitMinutes: Int, enabled: Boolean) {
        globalLimitDao.setGlobalLimit(dailyLimitMinutes, enabled)
    }

    // --- App limits ---

    suspend fun getAppLimitsSync(): List<AppLimitEntity> {
        return runBlocking { appLimitDao.getEnabledLimitsSync() }
    }

    override suspend fun getTrackedApps(): List<TrackedAppEntity> {
        return trackedAppDao.getAllTrackedAppsSync()
    }

    override suspend fun getAppLimits(): List<AppLimitEntity> {
        return runBlocking { appLimitDao.getEnabledLimitsSync() }
    }

    override suspend fun setAppLimit(packageName: String, dailyLimitMinutes: Int, enabled: Boolean) {
        appLimitDao.insertLimit(AppLimitEntity(packageName, dailyLimitMinutes, enabled))
    }

    override suspend fun setAppExcluded(packageName: String, excluded: Boolean) {
        trackedAppDao.setExcluded(packageName, excluded)
    }

    // --- Alert logs ---

    suspend fun wasAlertShownToday(packageName: String, type: String, dayStart: Long): Boolean {
        return alertLogDao.wasAlertShownToday(packageName, type, dayStart)
    }

    suspend fun recordAlertLog(log: AlertLogEntity) {
        alertLogDao.insertAlertLog(log)
    }

    // --- Debug ---

    suspend fun getDebugInfo(): DebugInfo {
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
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val calendar = Calendar.getInstance()
            calendar.time = dateFormat.parse(date) ?: Calendar.getInstance().time
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val dayStart = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val dayEnd = calendar.timeInMillis + 1

            val heartbeats = serviceHeartbeatDao.getHeartbeatsBetween(dayStart, dayEnd)
            val timestamps = heartbeats.map { it.timestamp }

            return DowntimeCalculator.calculate(timestamps, System.currentTimeMillis())
        } catch (e: Exception) {
            TrackingLogStorage.add("Repo", "getServiceDowntimeBuckets EXCEPTION: ${e.message}")
            TrackingLogStorage.add("Repo", e.stackTraceToString())
            // Return empty buckets on error
            return (6..21).map { DowntimeHourBucket(it, 0, 0) }
        }
    }

    override suspend fun getHourlyUsageForPackage(packageName: String, date: String): List<AppUsageHourBucket> {
        try {
            val sessions = usageSessionDao.getSessionsByPackageAndDate(packageName, date)
            val intervals = sessions.map { it.startTime to it.endTime }

            return AppUsageHourCalculator.calculate(intervals, System.currentTimeMillis())
        } catch (e: Exception) {
            TrackingLogStorage.add("Repo", "getHourlyUsageForPackage EXCEPTION: ${e.message}")
            TrackingLogStorage.add("Repo", e.stackTraceToString())
            return (6..21).map { AppUsageHourBucket(it, 0) }
        }
    }

    override suspend fun getHourlyUsageForAllApps(date: String): List<AppUsageHourBucket> {
        try {
            val sessions = usageSessionDao.getSessionsByDateSync(date)
            val intervals = sessions.map { it.startTime to it.endTime }

            return AppUsageHourCalculator.calculate(intervals, System.currentTimeMillis())
        } catch (e: Exception) {
            TrackingLogStorage.add("Repo", "getHourlyUsageForAllApps EXCEPTION: ${e.message}")
            TrackingLogStorage.add("Repo", e.stackTraceToString())
            return (6..21).map { AppUsageHourBucket(it, 0) }
        }
    }
}

data class DebugInfo(
    val sessionCount: Int,
    val uniquePackageCount: Int,
    val dateRange: DateRange?,
    val recentSessions: List<UsageSessionEntity>,
    val heartbeatCount: Int,
    val lastHeartbeatTimestamp: Long?
)
