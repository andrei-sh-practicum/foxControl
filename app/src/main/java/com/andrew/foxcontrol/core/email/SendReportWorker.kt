package com.andrew.foxcontrol.core.email

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.andrew.foxcontrol.R
import com.andrew.foxcontrol.core.tracking.TrackingLogStorage
import com.andrew.foxcontrol.data.local.entity.ReportSendLogEntity
import com.andrew.foxcontrol.data.repository.EmailRepository
import com.andrew.foxcontrol.ui.common.formatDuration
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.*

@HiltWorker
class SendReportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val emailRepository: EmailRepository,
    private val emailSender: EmailSender,
    private val usageStatsRepository: com.andrew.foxcontrol.domain.repository.UsageStatsRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SendReportWorker"
        const val CHANNEL_ID = "email_reports"
        const val NOTIFICATION_ID = 1002
    }

    override suspend fun doWork(): Result {
        try {
            // Create notification channel
            createNotificationChannel()

            // Show progress notification
            setForeground(ForegroundInfo(NOTIFICATION_ID, buildNotification("Подготовка отчёта...")))

            TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: START")

            // Get email settings
            val smtpHost = emailRepository.getSetting("smtp_host")
            if (smtpHost == null) {
                TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: MISSING smtp_host")
                return Result.failure()
            }

            val smtpPortStr = emailRepository.getSetting("smtp_port")
            if (smtpPortStr == null) {
                TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: MISSING smtp_port")
                return Result.failure()
            }

            val login = emailRepository.getSetting("smtp_login")
            if (login == null) {
                TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: MISSING smtp_login")
                return Result.failure()
            }

            val appPassword = emailRepository.getSetting("smtp_app_password")
            if (appPassword == null) {
                TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: MISSING smtp_app_password")
                return Result.failure()
            }

            val fromEmail = emailRepository.getSetting("from_email")
            if (fromEmail == null) {
                TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: MISSING from_email")
                return Result.failure()
            }

            val enabled = emailRepository.getSetting("email_enabled") ?: "false"

            if (enabled != "true") {
                TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: reports disabled")
                return Result.success()
            }

            val smtpPort = smtpPortStr.toIntOrNull()
            if (smtpPort == null) {
                TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: invalid smtp_port='$smtpPortStr'")
                return Result.failure()
            }

            TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: settings loaded, host=$smtpHost port=$smtpPort")

            val config = EmailSender.EmailConfig(
                smtpHost = smtpHost,
                smtpPort = smtpPort,
                login = login,
                appPassword = appPassword,
                fromEmail = fromEmail
            )

            // Get active recipients
            val activeRecipients = emailRepository.getActiveRecipients()
            if (activeRecipients.isEmpty()) {
                TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: NO active recipients")
                val log = ReportSendLogEntity(
                    date = getCurrentDate(),
                    status = "FAILED",
                    errorMessage = "No active recipients",
                    recipientCount = 0
                )
                emailRepository.saveLog(log)
                return Result.failure()
            }

            TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: ${activeRecipients.size} recipients found")

            // Get today's usage stats
            val today = getCurrentDate()
            TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: fetching stats for $today")
            val stats = usageStatsRepository.getDailyUsage(today)
            TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: ${stats.apps.size} apps, total=${stats.totalUsageMs}ms")

            // Build report body with real stats
            val subject = "Fox Control: Отчёт за $today"
            val body = buildString {
                appendLine("Отчёт за $today")
                appendLine("========================")
                appendLine("")
                appendLine("Общее время использования: ${formatDuration(stats.totalUsageMs)}")
                appendLine("")

                if (stats.apps.isNotEmpty()) {
                    appendLine("Список приложений:")
                    appendLine("-".repeat(30))
                    stats.apps.forEach { app ->
                        appendLine("- ${app.appName}: ${formatDuration(app.totalDurationMs)}")
                    }
                } else {
                    appendLine("Активности не зафиксировано.")
                }
            }

            // Update notification
            setForeground(ForegroundInfo(NOTIFICATION_ID, buildNotification("Отправка ${activeRecipients.size} получателям...")))

            TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: sending to ${activeRecipients.size} recipients...")

            // Send emails
            val results = emailSender.sendBulkEmail(
                config = config,
                recipients = activeRecipients.map { it.email },
                subject = subject,
                body = body
            )

            val successCount = results.count { it.success }
            val failedCount = results.count { !it.success }

            TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: sent $successCount/${activeRecipients.size} (failed=$failedCount)")

            // Update notification
            setForeground(ForegroundInfo(NOTIFICATION_ID, buildNotification(
                if (successCount > 0) "Отправлено $successCount/${activeRecipients.size}" else "Ошибка отправки"
            )))

            // Save log
            val log = ReportSendLogEntity(
                date = getCurrentDate(),
                status = if (failedCount == 0) "SUCCESS" else "PARTIAL",
                errorMessage = if (failedCount > 0) {
                    results.filterNot { it.success }.firstOrNull()?.message
                } else null,
                recipientCount = activeRecipients.size
            )
            emailRepository.saveLog(log)

            return if (successCount > 0) {
                TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: DONE success")
                Result.success()
            } else {
                TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: DONE all failed")
                Result.failure()
            }
        } catch (e: Exception) {
            // Catch-all for any unhandled exceptions
            TrackingLogStorage.add(applicationContext, "EmailScheduler", "SendReportWorker: UNHANDLED ERROR: ${e.message}")
            Log.e(TAG, "SendReportWorker failed with exception", e)
            return Result.failure()
        }
    }

    private fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            "Email отчёты",
            android.app.NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Уведомления об отправке email-отчётов"
        }
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): android.app.Notification {
        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Fox Control")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
