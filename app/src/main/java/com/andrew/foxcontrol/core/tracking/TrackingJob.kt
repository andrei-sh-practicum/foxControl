package com.andrew.foxcontrol.core.tracking

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.andrew.foxcontrol.core.alerts.AlertManager
import com.andrew.foxcontrol.core.email.EmailReportSender
import com.andrew.foxcontrol.core.maintenance.DataCleanupManager
import com.andrew.foxcontrol.core.util.AppLabelResolver
import com.andrew.foxcontrol.domain.repository.UsageStatsRepository
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class TrackingJob(
    private val context: Context,
    private val usageStatsRepository: UsageStatsRepository,
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
    // Log empty queryUsageStats result only on state change, not every minute
    @Volatile
    private var lastPollWasEmpty = false

    fun start() {
        if (!running.compareAndSet(false, true)) {
            TrackingLogStorage.add("Job", "TrackingJob already running, skipping start")
            return
        }
        TrackingLogStorage.add("Service", "TrackingJob STARTED")

        heartbeatTimer = scheduleTimer("heartbeat", HEARTBEAT_INTERVAL_MS) {
            launchLogged("Repo", "Heartbeat") { usageStatsRepository.recordHeartbeat() }
        }

        usageStatsTimer = scheduleTimer("usage_poll", USAGE_STATS_POLL_INTERVAL_MS) {
            runLogged("Job", "pollUsageStats") { pollUsageStats() }
        }

        emailReportTimer = scheduleTimer("email_report", EMAIL_REPORT_CHECK_INTERVAL_MS) {
            runLogged("EmailReport", "checkAndSendIfDue") { emailReportSender.checkAndSendIfDue() }
        }

        // Garbage collector for old DB rows
        cleanupTimer = scheduleTimer("data_cleanup", CLEANUP_INTERVAL_MS) {
            launchLogged("Cleanup", "purgeOldData") { dataCleanupManager.purgeOldData() }
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

    private fun pollUsageStats() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - USAGE_STATS_POLL_INTERVAL_MS


        // Per-package foreground time. NB: INTERVAL_YEARLY (= 3) is what has always been used
        // here, despite an old "INTERVAL_DAY" comment — see bugs_plan.md, B-7 (postponed).
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_YEARLY,
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
            // Update tracking state
            lastForegroundTime[packageName] = currentForeground

            val deltaMs = when {
                // App was in foreground during this interval
                previousTime != null && currentForeground >= previousTime -> currentForeground - previousTime
                // Time went backwards (app was killed / device rebooted / bucket changed):
                // just re-base on the new value, nothing is recorded for this poll
                previousTime != null -> continue
                // First time seeing this app — initialize tracking
                else -> 0L
            }

            if (deltaMs > 0) {
                val appName = getAppName(packageName)

                // Save session to Room
                launchLogged("Repo", "trackUsageSession") {
                    usageStatsRepository.trackUsageSession(
                        packageName = packageName,
                        appName = appName,
                        startTime = endTime - deltaMs,
                        endTime = endTime,
                        durationMs = deltaMs,
                        isEntertainment = false
                    )
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

    private fun getAppName(packageName: String): String =
        AppLabelResolver.resolve(context.packageManager, packageName)

    private fun scheduleTimer(name: String, periodMs: Long, task: () -> Unit): Timer =
        Timer(name).apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() = task()
            }, 0, periodMs)
        }

    /** Runs [block] on the timer thread; errors go to the Debug log as "<what> ERROR: …" + stack trace. */
    private inline fun runLogged(tag: String, what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logError(tag, what, e)
        }
    }

    /** Fire-and-forget coroutine (GlobalScope on purpose, see CLAUDE.md) with the same error logging. */
    @OptIn(DelicateCoroutinesApi::class)
    private fun launchLogged(tag: String, what: String, block: suspend () -> Unit) {
        GlobalScope.launch {
            try {
                block()
            } catch (e: Exception) {
                logError(tag, what, e)
            }
        }
    }

    private fun logError(tag: String, what: String, e: Exception) {
        TrackingLogStorage.add(tag, "$what ERROR: ${e.message}")
        TrackingLogStorage.add(tag, e.stackTraceToString())
    }

    companion object {
        const val TAG = "TrackingJob"
        const val HEARTBEAT_INTERVAL_MS = 60_000L // 1 minute
        const val USAGE_STATS_POLL_INTERVAL_MS = 60_000L // 1 minute
        const val EMAIL_REPORT_CHECK_INTERVAL_MS = 60_000L // 1 minute
        const val CLEANUP_INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 hours
    }
}
