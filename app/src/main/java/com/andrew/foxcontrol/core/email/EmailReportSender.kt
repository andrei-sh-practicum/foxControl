package com.andrew.foxcontrol.core.email

import android.content.Context
import com.andrew.foxcontrol.core.tracking.TrackingLogStorage
import com.andrew.foxcontrol.data.local.entity.ReportSendLogEntity
import com.andrew.foxcontrol.data.repository.EmailRepository
import com.andrew.foxcontrol.domain.repository.UsageStatsRepository
import com.andrew.foxcontrol.ui.common.formatDuration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailReportSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val emailRepository: EmailRepository,
    private val emailSender: EmailSender,
    private val usageStatsRepository: UsageStatsRepository,
    private val userRepository: com.andrew.foxcontrol.data.repository.UserRepository
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
     */
    fun checkAndSendIfDue() {
        try {
            // 1. Check if email is enabled — quiet return if not
            val enabled = runBlocking { emailRepository.getSetting("email_enabled") }
            if (enabled != "true") {
                return // Expected state, not an error
            }

            // 2. Read send time
            val hourStr = runBlocking { emailRepository.getSetting("send_time_hour") }
            val minuteStr = runBlocking { emailRepository.getSetting("send_time_minute") }

            val sendHour = hourStr?.toIntOrNull()
            val sendMinute = minuteStr?.toIntOrNull()

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

            // 6. Load SMTP settings
            val smtpHost = runBlocking { emailRepository.getSetting("smtp_host") }
            if (smtpHost == null) {
                TrackingLogStorage.add(TAG, "ОШИБКА: не задан smtp_host")
                return
            }

            val smtpPortStr = runBlocking { emailRepository.getSetting("smtp_port") }
            if (smtpPortStr == null) {
                TrackingLogStorage.add(TAG, "ОШИБКА: не задан smtp_port")
                return
            }

            val login = runBlocking { emailRepository.getSetting("smtp_login") }
            if (login == null) {
                TrackingLogStorage.add(TAG, "ОШИБКА: не задан smtp_login")
                return
            }

            val appPassword = runBlocking { emailRepository.getSetting("smtp_app_password") }
            if (appPassword == null) {
                TrackingLogStorage.add(TAG, "ОШИБКА: не задан smtp_app_password")
                return
            }

            val fromEmail = runBlocking { emailRepository.getSetting("from_email") }
            if (fromEmail == null) {
                TrackingLogStorage.add(TAG, "ОШИБКА: не задан from_email")
                return
            }

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
                    date = getCurrentDate(),
                    status = "FAILED",
                    errorMessage = "No active recipients",
                    recipientCount = 0
                )
                runBlocking { emailRepository.saveLog(log) }
                return
            }

            TrackingLogStorage.add(TAG, "${activeRecipients.size} получателей найдено")

            // 8. Build report body
            val today = getCurrentDate()
            val stats = runBlocking { usageStatsRepository.getDailyUsage(today) }
            val userName = runBlocking {
                userRepository.user.first()?.name ?: "Пользователь"
            }
            val appLimits = runBlocking { usageStatsRepository.getAppLimits() }

            // Compute exceeded apps
            data class ExceededApp(
                val appName: String,
                val totalMinutes: Int,
                val limitMinutes: Int,
                val overMinutes: Int
            )
            val exceededApps = mutableListOf<ExceededApp>()
            val limitMap = appLimits.associate { it.packageName to it.dailyLimitMinutes }
            for (app in stats.apps) {
                val limitMinutes = limitMap[app.packageName] ?: continue
                val totalMinutes = (app.totalDurationMs / (1000 * 60)).toInt()
                if (totalMinutes > limitMinutes) {
                    exceededApps.add(
                        ExceededApp(
                            appName = app.appName,
                            totalMinutes = totalMinutes,
                            limitMinutes = limitMinutes,
                            overMinutes = totalMinutes - limitMinutes
                        )
                    )
                }
            }
            exceededApps.sortByDescending { it.overMinutes }

            val subject = "Fox Control: Отчёт за $today"
            val body = buildString {
                appendLine("Отчёт за $today")
                appendLine("========================")
                appendLine("")
                appendLine("Пользователь: $userName")
                appendLine("Общее время использования: ${formatDuration(stats.totalUsageMs)}")
                appendLine("")

                // Exceeded limits section
                if (exceededApps.isNotEmpty()) {
                    appendLine("⚠ Превышены суточные лимиты:")
                    appendLine("-".repeat(30))
                    for (ex in exceededApps) {
                        appendLine("- ${ex.appName}: использовано ${ex.totalMinutes} мин (лимит ${ex.limitMinutes} мин, превышение +${ex.overMinutes} мин)")
                    }
                    appendLine("")
                }

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

            // 9. Send emails (blocking call inside runBlocking — safe, runs in Timer thread)
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
                subject = subject,
                body = body
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
                    date = getCurrentDate(),
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

    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
