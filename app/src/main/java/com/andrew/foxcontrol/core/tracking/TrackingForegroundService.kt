package com.andrew.foxcontrol.core.tracking

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.andrew.foxcontrol.core.alerts.AlertManager
import com.andrew.foxcontrol.core.email.EmailReportSender
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
    }

    @Inject
    lateinit var usageStatsRepository: com.andrew.foxcontrol.data.repository.UsageStatsRepositoryImpl

    @Inject
    lateinit var alertManager: AlertManager

    @Inject
    lateinit var permissionMonitor: PermissionMonitor

    @Inject
    lateinit var emailReportSender: EmailReportSender

    private lateinit var trackingJob: TrackingJob
    private var checkPermissionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        trackingJob = TrackingJob(this, usageStatsRepository, alertManager, emailReportSender)
        TrackingLogStorage.add("Service", "TrackingForegroundService created")
        TrackingLogStorage.add("Service", "Package: ${packageName}")
        TrackingLogStorage.add("Permission", "UsageStats: ${TrackingLogStorage.getPermissionInfo(this)}")
        createNotificationChannel()
        TrackingLogStorage.add("Service", "Starting trackingJob.start()")
        trackingJob.start()
        TrackingLogStorage.add("Service", "trackingJob.start() called")
        startPermissionMonitoring()
        TrackingLogStorage.add("Service", "startPermissionMonitoring() called")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TrackingForegroundService destroyed")
        trackingJob.stop()
        checkPermissionJob?.cancel()
    }

    private fun startPermissionMonitoring() {
        checkPermissionJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val status = permissionMonitor.checkPermissions()
                    if (!status.allGranted) {
                        Log.w(TAG, "Permissions not fully granted: missing ${status.missingCount}")
                        if (!status.usageStats || !status.overlay) {
                            Log.e(TAG, "Critical permissions lost! Restarting service...")
                            restartService()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking permissions", e)
                }
                delay(60_000) // Check every minute
            }
        }
    }

    private fun restartService() {
        // Stop current service
        trackingJob.stop()

        // Restart after a delay
        GlobalScope.launch {
            delay(5000) // Wait 5 seconds
            try {
                val status = permissionMonitor.getCurrentStatus()
                if (status.allGranted) {
                    trackingJob.start()
                    Log.d(TAG, "Service restarted successfully after permission recovery")
                } else {
                    Log.w(TAG, "Cannot restart service - permissions still not granted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restarting service", e)
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
