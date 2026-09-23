package com.andrew.foxcontrol.core.email

import com.andrew.foxcontrol.data.local.entity.AppLimitEntity
import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.domain.model.UsageStats
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden tests: the report text must stay exactly as it was built inline in EmailReportSender.
 */
class EmailReportBuilderTest {

    private fun app(pkg: String, name: String, ms: Long) = UsageStats(
        packageName = pkg,
        appName = name,
        totalDurationMs = ms,
        sessionCount = 1,
        isEntertainment = false
    )

    @Test
    fun `report with exceeded limits`() {
        val stats = DailyUsageStats(
            date = "2026-09-23",
            totalUsageMs = 7_200_000L,
            apps = listOf(
                app("yt", "YouTube", 3_600_000L),
                app("game", "Game", 2_400_000L),
                app("chrome", "Chrome", 1_200_000L),
                app("short", "Short", 59_000L)   // < 1 min: hidden from the list (B-18)
            )
        )
        val limits = listOf(
            AppLimitEntity("yt", dailyLimitMinutes = 50),     // +10
            AppLimitEntity("game", dailyLimitMinutes = 10),   // +30
            AppLimitEntity("chrome", dailyLimitMinutes = 20), // == limit, not exceeded
            AppLimitEntity("absent", dailyLimitMinutes = 1)
        )

        val report = EmailReportBuilder.build("2026-09-23", "Аня", stats, limits)

        assertEquals("Fox Control: Отчёт за 2026-09-23", report.subject)
        assertEquals(
            "Отчёт за 2026-09-23\n" +
                "========================\n" +
                "\n" +
                "Пользователь: Аня\n" +
                "Общее время использования: 2ч 0м\n" +
                "\n" +
                "⚠ Превышены суточные лимиты:\n" +
                "------------------------------\n" +
                "- Game: использовано 40 мин (лимит 10 мин, превышение +30 мин)\n" +
                "- YouTube: использовано 60 мин (лимит 50 мин, превышение +10 мин)\n" +
                "\n" +
                "Список приложений:\n" +
                "------------------------------\n" +
                "- YouTube: 1ч 0м\n" +
                "- Game: 40м\n" +
                "- Chrome: 20м\n",
            report.body
        )
    }

    @Test
    fun `report without activity`() {
        val stats = DailyUsageStats(date = "2026-09-23", totalUsageMs = 0L, apps = emptyList())

        val report = EmailReportBuilder.build("2026-09-23", "Пользователь", stats, emptyList())

        assertEquals(
            "Отчёт за 2026-09-23\n" +
                "========================\n" +
                "\n" +
                "Пользователь: Пользователь\n" +
                "Общее время использования: 0м\n" +
                "\n" +
                "Активности не зафиксировано.\n",
            report.body
        )
    }
}
