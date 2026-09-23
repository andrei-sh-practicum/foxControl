package com.andrew.foxcontrol.core.tracking

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.andrew.foxcontrol.core.alerts.AlertManager
import com.andrew.foxcontrol.core.email.EmailReportSender
import com.andrew.foxcontrol.core.maintenance.DataCleanupManager
import com.andrew.foxcontrol.core.tracking.TrackingLogStorage.add
import com.andrew.foxcontrol.data.repository.UsageStatsRepositoryImpl
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class TrackingJob(
    private val context: Context,
    private val usageStatsRepository: UsageStatsRepositoryImpl,
    private val alertManager: AlertManager,
    private val emailReportSender: EmailReportSender,
    private val dataCleanupManager: DataCleanupManager
) {
    // Started/stopped from the main thread (service) and from the permission-monitor coroutine
    private val running = AtomicBoolean(false)
    val isRunning: Boolean
        get() = running.get()
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var heartbeatTimer: Timer? = null
    private var usageStatsTimer: Timer? = null
    private var emailReportTimer: Timer? = null
    private var cleanupTimer: Timer? = null

    // Track last known foreground time per package to compute deltas
    private val lastForegroundTime = ConcurrentHashMap<String, Long>()
    // Track the last poll end time per package to compute accurate deltas
    private val lastPollEndTime = ConcurrentHashMap<String, Long>()
    // Log empty queryUsageStats result only on state change, not every minute
    @Volatile
    private var lastPollWasEmpty = false

    @OptIn(DelicateCoroutinesApi::class)
    fun start() {
        if (!running.compareAndSet(false, true)) {
            TrackingLogStorage.add("Job", "TrackingJob already running, skipping start")
            return
        }
        TrackingLogStorage.add("Service", "TrackingJob STARTED")

        // Heartbeat timer
        heartbeatTimer = Timer("heartbeat").apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    GlobalScope.launch {
                        try {
                            usageStatsRepository.recordHeartbeat()
                        } catch (e: Exception) {
                            TrackingLogStorage.add("Repo", "Heartbeat ERROR: ${e.message}")
                            TrackingLogStorage.add("Repo", e.stackTraceToString())
                        }
                    }
                }
            }, 0, HEARTBEAT_INTERVAL_MS)
        }

        // Usage stats polling timer
        usageStatsTimer = Timer("usage_poll").apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        pollUsageStats()
                    } catch (e: Exception) {
                        TrackingLogStorage.add("Job", "pollUsageStats ERROR: ${e.message}")
                        TrackingLogStorage.add("Job", e.stackTraceToString())
                    }
                }
            }, 0, USAGE_STATS_POLL_INTERVAL_MS)
        }

        // Email report check timer
        emailReportTimer = Timer("email_report").apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        emailReportSender.checkAndSendIfDue()
                    } catch (e: Exception) {
                        TrackingLogStorage.add("EmailReport", "checkAndSendIfDue ERROR: ${e.message}")
                        TrackingLogStorage.add("EmailReport", e.stackTraceToString())
                    }
                }
            }, 0, EMAIL_REPORT_CHECK_INTERVAL_MS)
        }

        // Data cleanup timer (garbage collector, every 12 hours)
        cleanupTimer = Timer("data_cleanup").apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    GlobalScope.launch {
                        try {
                            dataCleanupManager.purgeOldData()
                        } catch (e: Exception) {
                            TrackingLogStorage.add("Cleanup", "purgeOldData ERROR: ${e.message}")
                            TrackingLogStorage.add("Cleanup", e.stackTraceToString())
                        }
                    }
                }
            }, 0, CLEANUP_INTERVAL_MS)
        }
    }

    fun stop() {
        running.set(false)
        Log.d(TAG, "TrackingJob stopped")
        heartbeatTimer?.cancel()
        usageStatsTimer?.cancel()
        emailReportTimer?.cancel()
        cleanupTimer?.cancel()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun pollUsageStats() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - USAGE_STATS_POLL_INTERVAL_MS


        // Use queryUsageStats for per-package usage time
        val usageStatsList = usageStatsManager.queryUsageStats(
            3, // INTERVAL_DAY
            startTime,
            endTime
        )

        if (usageStatsList.isNullOrEmpty()) {
            if (!lastPollWasEmpty) {
                TrackingLogStorage.add("UsageStats", "queryUsageStats returned NULL/EMPTY — no apps found (no permission?)")
                lastPollWasEmpty = true
            }
            return
        }

        if (lastPollWasEmpty) {
            TrackingLogStorage.add("UsageStats", "queryUsageStats recovered: ${usageStatsList.size} apps")
            lastPollWasEmpty = false
        }

        for (usageStats in usageStatsList) {
            val packageName = usageStats.packageName
            val currentForeground = usageStats.totalTimeInForeground

            // Compute delta: usage since last poll
            val previousTime = lastForegroundTime[packageName]
            val previousPollEnd = lastPollEndTime[packageName]
            var deltaMs: Long

            if (previousTime != null && currentForeground >= previousTime) {
                // App was in foreground during this interval
                deltaMs = currentForeground - previousTime
            } else if (previousTime != null && currentForeground < previousTime) {
                // Time went backwards (app was killed or device rebooted)
                // Use the previous poll's end time to compute delta from last known state
                val lastKnownForeground = previousTime
                if (previousPollEnd != null) {
                    // App was in foreground since last poll, but time went backwards
                    // Assume the app was in foreground until the time went backwards
                    deltaMs = 0L
                } else {
                    deltaMs = 0L
                }
                // Reset tracking for this app
                lastForegroundTime[packageName] = currentForeground
                lastPollEndTime[packageName] = endTime
                continue
            } else {
                // First time seeing this app - initialize tracking
                deltaMs = 0L
            }

            // Update tracking state
            lastForegroundTime[packageName] = currentForeground
            lastPollEndTime[packageName] = endTime

            if (deltaMs > 0) {
                val appName = getAppName(packageName)

                // Save session to Room
                GlobalScope.launch {
                    try {
                        usageStatsRepository.trackUsageSession(
                            packageName = packageName,
                            appName = appName,
                            startTime = endTime - deltaMs,
                            endTime = endTime,
                            durationMs = deltaMs,
                            isEntertainment = false
                        )
                    } catch (e: Exception) {
                        TrackingLogStorage.add("Repo", "trackUsageSession ERROR: ${e.message}")
                        TrackingLogStorage.add("Repo", e.stackTraceToString())
                    }
                }
            }
        }

        // Check limits and show alerts
        checkAlerts()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun checkAlerts() {
        GlobalScope.launch {
            try {
                alertManager.checkAndShowAlerts()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking alerts", e)
            }
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val label = pm.getApplicationLabel(appInfo)
            if (label != null && label.isNotEmpty()) {
                label.toString()
            } else {
                packageName
            }
        } catch (e: Exception) {
            android.util.Log.e("TrackingJob", "Failed to get app name for $packageName: ${e.message}")
            packageName
        }
    }

    companion object {
        const val TAG = "TrackingJob"
        const val HEARTBEAT_INTERVAL_MS = 60_000L // 1 minute
        const val USAGE_STATS_POLL_INTERVAL_MS = 60_000L // 1 minute
        const val EMAIL_REPORT_CHECK_INTERVAL_MS = 60_000L // 1 minute
        const val CLEANUP_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 hours
    }
}
