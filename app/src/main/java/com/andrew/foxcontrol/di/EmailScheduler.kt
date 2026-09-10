package com.andrew.foxcontrol.core.email

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.andrew.foxcontrol.core.email.ReportAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "EmailScheduler"
        const val ALARM_REQUEST_CODE = 1001
        const val ACTION_SEND_REPORT = "com.andrew.foxcontrol.ACTION_SEND_REPORT"
    }

    fun scheduleDailyReport(hour: Int, minute: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Build calendar for today at the specified time
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If the time has already passed today, schedule for tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            // Create pending intent for the alarm receiver
            val intent = Intent(context, ReportAlarmReceiver::class.java).apply {
                action = ACTION_SEND_REPORT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Set exact alarm, repeat daily
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

            // Also set a repeating alarm 24h later to re-schedule (in case the first one is lost)
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis + (24 * 60 * 60 * 1000L),
                24 * 60 * 60 * 1000L,
                pendingIntent
            )

            Log.d(TAG, "Daily report scheduled at ${String.format("%02d:%02d", hour, minute)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule report", e)
        }
    }

    fun cancelScheduledReport() {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReportAlarmReceiver::class.java).apply {
                action = ACTION_SEND_REPORT
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Scheduled report cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel report", e)
        }
    }
}
