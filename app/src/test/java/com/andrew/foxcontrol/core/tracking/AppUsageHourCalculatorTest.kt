package com.andrew.foxcontrol.core.tracking

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * Characterization tests for [AppUsageHourCalculator] (see _docs/refactoring_plan.md, stage 0.3).
 *
 * Like DowntimeCalculator, the calculator builds its hour windows from Calendar.getInstance(),
 * i.e. always for TODAY, so all timestamps here are taken on the current day and `now` is set
 * to the end of the visible window.
 */
class AppUsageHourCalculatorTest {

    private fun todayAt(hour: Int, minute: Int, second: Int = 0): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, second)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private val endOfWindow get() = todayAt(22, 0)

    private fun minutesAt(buckets: List<AppUsageHourBucket>, hour: Int) =
        buckets.single { it.hour == hour }.usageMinutes

    @Test
    fun `returns 16 buckets for hours 6 to 21`() {
        val buckets = AppUsageHourCalculator.calculate(emptyList(), endOfWindow)
        assertEquals((6..21).toList(), buckets.map { it.hour })
        buckets.forEach { assertEquals(0, it.usageMinutes) }
    }

    @Test
    fun `session inside one hour`() {
        val buckets = AppUsageHourCalculator.calculate(
            listOf(todayAt(10, 10) to todayAt(10, 40)),
            endOfWindow
        )
        assertEquals(30, minutesAt(buckets, 10))
        assertEquals(0, minutesAt(buckets, 9))
        assertEquals(0, minutesAt(buckets, 11))
    }

    @Test
    fun `session crossing hour boundary is split`() {
        val buckets = AppUsageHourCalculator.calculate(
            listOf(todayAt(10, 50) to todayAt(11, 20)),
            endOfWindow
        )
        assertEquals(10, minutesAt(buckets, 10))
        assertEquals(20, minutesAt(buckets, 11))
    }

    @Test
    fun `sessions in the same hour are summed and floored once`() {
        // 30 s + 40 s = 70 s -> 1 minute (not 0 + 0)
        val buckets = AppUsageHourCalculator.calculate(
            listOf(
                todayAt(12, 0, 0) to todayAt(12, 0, 30),
                todayAt(12, 5, 0) to todayAt(12, 5, 40)
            ),
            endOfWindow
        )
        assertEquals(1, minutesAt(buckets, 12))
    }

    @Test
    fun `parts outside the 06-22 window are ignored`() {
        val buckets = AppUsageHourCalculator.calculate(
            listOf(todayAt(5, 30) to todayAt(6, 15), todayAt(21, 50) to todayAt(22, 30)),
            endOfWindow
        )
        assertEquals(15, minutesAt(buckets, 6))
        assertEquals(10, minutesAt(buckets, 21))
    }

    @Test
    fun `hours after now are empty and the current hour is clipped to now`() {
        val now = todayAt(14, 20)
        val buckets = AppUsageHourCalculator.calculate(
            listOf(todayAt(14, 0) to todayAt(15, 30)),
            now
        )
        assertEquals(20, minutesAt(buckets, 14))
        assertEquals(0, minutesAt(buckets, 15))
    }
}
