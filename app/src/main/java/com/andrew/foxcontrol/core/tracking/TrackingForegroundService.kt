package com.andrew.foxcontrol.core.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.andrew.foxcontrol.core.alerts.AlertManager
import com.andrew.foxcontrol.core.email.EmailReportSender
import com.andrew.foxcontrol.core.maintenance.DataCleanupManager
import com.andrew.foxcontrol.core.permissions.PermissionMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class TrackingForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "fox_control_tracking"
        const val NOTIFICATION_ID = 1
        const val TAG = "TrackingService"
        private const val PERMISSION_CHECK_INTERVAL_MS = 60_000L
    }

    @Inject
    lateinit var usageStatsRepository: com.andrew.foxcontrol.data.repository.UsageStatsRepositoryImpl

    @Inject
    lateinit var alertManager: AlertManager

    @Inject
    lateinit var permissionMonitor: PermissionMonitor

    @Inject
    lateinit var emailReportSender: EmailReportSender

    @Inject
    lateinit var dataCleanupManager: DataCleanupManager

    private lateinit var trackingJob: TrackingJob
    private var checkPermissionJob: Job? = null
    // Log permission loss/recovery only on state change, not every minute
    @Volatile
    private var permissionsWereLost = false

    override fun onCreate() {
        super.onCreate()
        trackingJob = TrackingJob(this, usageStatsRepository, alertManager, emailReportSender, dataCleanupManager)
        TrackingLogStorage.add("Service", "TrackingForegroundService created")
        TrackingLogStorage.add("Permission", "Permissions at start:\n${TrackingLogStorage.getPermissionInfo(this)}")
        createNotificationChannel()
        trackingJob.start()
        startPermissionMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TrackingForegroundService destroyed")
        TrackingLogStorage.add("Service", "TrackingForegroundService destroyed")
        // Cancel the monitor first so it can't restart the job we are about to stop
        checkPermissionJob?.cancel()
        trackingJob.stop()
    }

    /**
     * Every minute:
     * - logs changes of the full permission set (all 4) — diagnostics only;
     * - critical permissions (usage stats + overlay) lost → stop TrackingJob once;
     * - critical permissions present but TrackingJob stopped → start it again,
     *   regardless of the non-critical ones (notifications, battery optimization).
     */
    private fun startPermissionMonitoring() {
        checkPermissionJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val status = permissionMonitor.checkPermissions()

                    if (!status.allGranted) {
                        Log.w(TAG, "Permissions not fully granted: missing ${status.missingCount}")
                        if (!permissionsWereLost) {
                            TrackingLogStorage.add(
                                "Permission",
                                "Permissions LOST (missing ${status.missingCount}):\n${TrackingLogStorage.getPermissionInfo(this@TrackingForegroundService)}"
                            )
                            permissionsWereLost = true
                        }
                    } else if (permissionsWereLost) {
                        TrackingLogStorage.add("Permission", "Permissions RESTORED")
                        permissionsWereLost = false
                    }

                    if (!status.criticalGranted) {
                        if (trackingJob.isRunning) {
                            Log.e(TAG, "Critical permissions lost! Stopping TrackingJob")
                            TrackingLogStorage.add("Service", "Critical permissions lost — TrackingJob stopped")
                            trackingJob.stop()
                        }
                    } else if (!trackingJob.isRunning && isActive) {
                        trackingJob.start()
                        Log.d(TAG, "TrackingJob restarted after critical permissions recovery")
                        TrackingLogStorage.add("Service", "TrackingJob restarted after permission recovery")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking permissions", e)
                    TrackingLogStorage.add("Permission", "checkPermissions ERROR: ${e.message}")
                    TrackingLogStorage.add("Permission", e.stackTraceToString())
                }
                delay(PERMISSION_CHECK_INTERVAL_MS)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Мониторинг использования",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Фоновый сервис для отслеживания использования приложений"
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fox Control")
            .setContentText("Мониторинг активен")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
