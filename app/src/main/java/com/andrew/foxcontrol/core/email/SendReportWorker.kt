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
import com.andrew.foxcontrol.data.local.entity.ReportSendLogEntity
import com.andrew.foxcontrol.data.repository.EmailRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.*

@HiltWorker
class SendReportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val emailRepository: EmailRepository,
    private val emailSender: EmailSender
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "SendReportWorker"
        const val CHANNEL_ID = "email_reports"
        const val NOTIFICATION_ID = 1002
    }

    override suspend fun doWork(): Result {
        // Create notification channel
        createNotificationChannel()

        // Show progress notification
        setForeground(ForegroundInfo(NOTIFICATION_ID, buildNotification("Подготовка отчёта...")))

        return try {
            // Get email settings
            val smtpHost = emailRepository.getSetting("smtp_host") ?: return Result.failure()
            val smtpPortStr = emailRepository.getSetting("smtp_port") ?: return Result.failure()
            val login = emailRepository.getSetting("smtp_login") ?: return Result.failure()
            val appPassword = emailRepository.getSetting("smtp_app_password") ?: return Result.failure()
            val fromEmail = emailRepository.getSetting("from_email") ?: return Result.failure()
            val enabled = emailRepository.getSetting("email_enabled") ?: "false"

            if (enabled != "true") {
                Log.d(TAG, "Email reports are disabled")
                return Result.success()
            }

            val smtpPort = smtpPortStr.toIntOrNull() ?: return Result.failure()

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
                Log.w(TAG, "No active recipients configured")
                val log = ReportSendLogEntity(
                    date = getCurrentDate(),
                    status = "FAILED",
                    errorMessage = "No active recipients",
                    recipientCount = 0
                )
                emailRepository.saveLog(log)
                return Result.failure()
            }

            // Prepare report body (placeholder - in production, fetch usage stats)
            val subject = "Fox Control: Отчёт за ${getCurrentDate()}"
            val body = buildString {
                appendLine("Отчёт за ${getCurrentDate()}")
                appendLine("========================")
                appendLine("")
                appendLine("Здесь будет статистика использования приложений.")
                appendLine("")
                appendLine("Это тестовое письмо.")
            }

            // Update notification
            setForeground(ForegroundInfo(NOTIFICATION_ID, buildNotification("Отправка ${activeRecipients.size} получателям...")))

            // Send emails
            val results = emailSender.sendBulkEmail(
                config = config,
                recipients = activeRecipients.map { it.email },
                subject = subject,
                body = body
            )

            val successCount = results.count { it.success }
            val failedCount = results.count { !it.success }

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

            if (successCount > 0) {
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending report", e)
            val log = ReportSendLogEntity(
                date = getCurrentDate(),
                status = "FAILED",
                errorMessage = e.message,
                recipientCount = 0
            )
            emailRepository.saveLog(log)
            Result.failure()
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
