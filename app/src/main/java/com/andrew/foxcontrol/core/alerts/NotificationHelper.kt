package com.andrew.foxcontrol.core.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.andrew.foxcontrol.R
import com.andrew.foxcontrol.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val TAG = "NotificationHelper"
        const val CHANNEL_ALERTS = "fox_control_alerts"
        const val ID_ALERT_NOTIFICATION_BASE = 1000
    }

    fun showLimitExceededNotification(
        packageName: String,
        appName: String,
        limitMinutes: Int,
        usedMinutes: Int,
        isGlobal: Boolean
    ) {
        createNotificationChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isGlobal) {
            "Превышен суточный лимит"
        } else {
            "Превышен суточный лимит приложения — $appName"
        }

        val text = if (isGlobal) {
            "Вы использовали $usedMinutes из $limitMinutes минут за сегодня"
        } else {
            "Использовано $usedMinutes из $limitMinutes минут"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = ID_ALERT_NOTIFICATION_BASE + packageName.hashCode().coerceAtLeast(0)
        notificationManager.notify(notificationId, notification)
        Log.d(TAG, "Alert notification shown: $title (id=$notificationId)")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ALERTS,
                "Оповещения о лимитах",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о превышении лимитов использования"
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
