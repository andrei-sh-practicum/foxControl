package com.andrew.foxcontrol.core.email

import com.andrew.foxcontrol.core.util.formatDuration
import com.andrew.foxcontrol.data.local.entity.AppLimitEntity
import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.domain.usecase.LimitCalculator

/**
 * Builds the plain-text daily usage report. Pure function — no I/O.
 */
object EmailReportBuilder {

    data class Report(val subject: String, val body: String)

    fun build(
        date: String,
        userName: String,
        stats: DailyUsageStats,
        appLimits: List<AppLimitEntity>
    ): Report {
        val exceededApps = LimitCalculator.exceededApps(stats.apps, appLimits)

        val subject = "Fox Control: Отчёт за $date"
        val body = buildString {
            appendLine("Отчёт за $date")
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
        return Report(subject, body)
    }
}
