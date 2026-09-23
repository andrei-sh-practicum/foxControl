package com.andrew.foxcontrol.core.email

import com.andrew.foxcontrol.core.tracking.TrackingLogStorage
import com.andrew.foxcontrol.core.util.DateUtils
import com.andrew.foxcontrol.data.local.entity.EmailSettingsKeys
import com.andrew.foxcontrol.data.local.entity.ReportSendLogEntity
import com.andrew.foxcontrol.data.repository.EmailRepository
import com.andrew.foxcontrol.data.repository.UserRepository
import com.andrew.foxcontrol.domain.repository.UsageStatsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailReportSender @Inject constructor(
    private val emailRepository: EmailRepository,
    private val emailSender: EmailSender,
    private val usageStatsRepository: UsageStatsRepository,
    private val userRepository: UserRepository
) {

    companion object {
        private const val TAG = "EmailReport"
        private const val DEDUP_MINUTES = 5L
        private const val DEDUP_MS = DEDUP_MINUTES * 60_000L
    }

    @Volatile
    private var lastSentAtMs: Long = 0L

    /**
     * Called from the Timer 3 thread (once per minute).
     * Checks if a report should be sent and sends it synchronously.
     *
     * Log messages (tag [TAG]) are shown on the Debug screen — keep them stable.
     */
    fun checkAndSendIfDue() {
        try {
            // All email settings in one query (key → value)
            val settings = runBlocking { emailRepository.getAllSettings() }

            // 1. Check if email is enabled — quiet return if not
            if (settings[EmailSettingsKeys.ENABLED] != "true") {
                return // Expected state, not an error
            }

            // 2. Read send time
            val sendHour = settings[EmailSettingsKeys.SEND_TIME_HOUR]?.toIntOrNull()
            val sendMinute = settings[EmailSettingsKeys.SEND_TIME_MINUTE]?.toIntOrNull()

            if (sendHour == null || sendMinute == null) {
                TrackingLogStorage.add(TAG, "email_enabled=true, но время отправки не задано")
                return
            }

            // 3. Compare with current time
            val cal = Calendar.getInstance()
            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            val currentMinute = cal.get(Calendar.MINUTE)

            if (currentHour != sendHour || currentMinute != sendMinute) {
                return // Not time yet
            }

            // 4. Dedup: skip if sent within last 5 minutes
            val now = System.currentTimeMillis()
            if (now - lastSentAtMs < DEDUP_MS) {
                val minsAgo = (now - lastSentAtMs) / 60_000
                TrackingLogStorage.add(TAG, "пропуск: уже отправляли $minsAgo мин назад (дедуп $DEDUP_MINUTES мин)")
                return
            }

            // 5. Mark as sent BEFORE sending (prevents parallel sends on timer overlap)
            lastSentAtMs = now

            // 6. SMTP settings — each one must be present
            fun required(key: String): String? =
                settings[key] ?: run {
                    TrackingLogStorage.add(TAG, "ОШИБКА: не задан $key")
                    null
                }

            val smtpHost = required(EmailSettingsKeys.SMTP_HOST) ?: return
            val smtpPortStr = required(EmailSettingsKeys.SMTP_PORT) ?: return
            val login = required(EmailSettingsKeys.SMTP_LOGIN) ?: return
            val appPassword = required(EmailSettingsKeys.SMTP_APP_PASSWORD) ?: return
            val fromEmail = required(EmailSettingsKeys.FROM_EMAIL) ?: return

            val smtpPort = smtpPortStr.toIntOrNull()
            if (smtpPort == null) {
                TrackingLogStorage.add(TAG, "ОШИБКА: некорректный smtp_port='$smtpPortStr'")
                return
            }

            TrackingLogStorage.add(TAG, "START отправка в $sendHour:$sendMinute")

            // 7. Get active recipients
            val activeRecipients = runBlocking { emailRepository.getActiveRecipients() }
            if (activeRecipients.isEmpty()) {
                TrackingLogStorage.add(TAG, "ОШИБКА: нет активных получателей")
                val log = ReportSendLogEntity(
                    date = DateUtils.today(),
                    status = "FAILED",
                    errorMessage = "No active recipients",
                    recipientCount = 0
                )
                runBlocking { emailRepository.saveLog(log) }
                return
            }

            TrackingLogStorage.add(TAG, "${activeRecipients.size} получателей найдено")

            // 8. Build report
            val today = DateUtils.today()
            val stats = runBlocking { usageStatsRepository.getDailyUsage(today) }
            val userName = runBlocking {
                userRepository.user.first()?.name ?: "Пользователь"
            }
            val appLimits = runBlocking { usageStatsRepository.getAppLimits() }
            val report = EmailReportBuilder.build(today, userName, stats, appLimits)

            // 9. Send emails (blocking, runs in the Timer thread)
            val config = EmailSender.EmailConfig(
                smtpHost = smtpHost,
                smtpPort = smtpPort,
                login = login,
                appPassword = appPassword,
                fromEmail = fromEmail
            )

            val results = emailSender.sendBulkEmail(
                config = config,
                recipients = activeRecipients.map { it.email },
                subject = report.subject,
                body = report.body
            )

            val successCount = results.count { it.success }
            val failedCount = results.count { !it.success }

            TrackingLogStorage.add(TAG, "отправлено $successCount/${activeRecipients.size} (ошибок: $failedCount)")

            // 10. Save log
            val log = ReportSendLogEntity(
                date = today,
                status = if (failedCount == 0) "SUCCESS" else "PARTIAL",
                errorMessage = if (failedCount > 0) {
                    results.filterNot { it.success }.firstOrNull()?.message
                } else null,
                recipientCount = activeRecipients.size
            )
            runBlocking { emailRepository.saveLog(log) }

            TrackingLogStorage.add(TAG, "DONE ${if (successCount > 0) "success" else "all failed"}")

        } catch (e: Exception) {
            TrackingLogStorage.add(TAG, "UNHANDLED ERROR: ${e.message}")
            TrackingLogStorage.add(TAG, e.stackTraceToString())

            // Try to save a failure log
            try {
                val log = ReportSendLogEntity(
                    date = DateUtils.today(),
                    status = "FAILED",
                    errorMessage = e.message,
                    recipientCount = 0
                )
                runBlocking { emailRepository.saveLog(log) }
            } catch (ignore: Exception) {
                // Even the failure log failed — nothing more we can do
            }
        }
    }
}
