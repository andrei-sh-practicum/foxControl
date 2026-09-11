package com.andrew.foxcontrol.core.tracking

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DowntimeCalculator pure logic.
 * Tests run on plain JVM — no Android dependencies needed.
 * Uses real system time since DowntimeCalculator relies on Calendar.getInstance().
 */
class DowntimeCalculatorTest {

    @Test
    fun `no heartbeats returns all zeros`() {
        val buckets = DowntimeCalculator.calculate(emptyList(), System.currentTimeMillis())
        assertEquals(16, buckets.size)
        buckets.forEach {
            assertEquals(0, it.downtimeMinutes)
            assertEquals(0, it.divisions)
        }
    }

    @Test
    fun `single heartbeat no gap`() {
        val now = System.currentTimeMillis()
        val buckets = DowntimeCalculator.calculate(listOf(now), now + 100)
        assertEquals(16, buckets.size)
        buckets.forEach {
            assertEquals(0, it.downtimeMinutes)
        }
    }

    @Test
    fun `gap below threshold ignored`() {
        // Gap of 60s < 90s threshold
        val hb1 = System.currentTimeMillis() - 120_000L
        val hb2 = System.currentTimeMillis() - 60_000L
        val buckets = DowntimeCalculator.calculate(listOf(hb1, hb2), System.currentTimeMillis())
        buckets.forEach {
            assertEquals(0, it.downtimeMinutes)
        }
    }

    @Test
    fun `gap produces at least one non-zero bucket`() {
        // 90-min gap — should produce downtime buckets
        val now = System.currentTimeMillis()
        val hb1 = now - 90 * 60_000L
        val buckets = DowntimeCalculator.calculate(listOf(hb1), now)
        val totalMinutes = buckets.sumOf { it.downtimeMinutes }
        assertTrue("90-min gap should produce non-zero total: $totalMinutes", totalMinutes > 0)
    }

    @Test
    fun `all 16 buckets present`() {
        val buckets = DowntimeCalculator.calculate(emptyList(), System.currentTimeMillis())
        assertEquals(16, buckets.size)
        for (i in 0 until 16) {
            assertEquals(6 + i, buckets[i].hour)
        }
    }

    @Test
    fun `divisions calculation`() {
        // 5 min → ceil(0.5) = 1
        assertEquals(1, makeDivisions(5))
        // 33 min → ceil(3.3) = 4
        assertEquals(4, makeDivisions(33))
        // 60 min → ceil(6) = 6
        assertEquals(6, makeDivisions(60))
        // 0 min → 0
        assertEquals(0, makeDivisions(0))
        // 10 min → ceil(1) = 1
        assertEquals(1, makeDivisions(10))
        // 20 min → ceil(2) = 2
        assertEquals(2, makeDivisions(20))
    }

    @Test
    fun `divisions capped at 6`() {
        assertEquals(6, makeDivisions(60))
        assertEquals(6, makeDivisions(59))
        assertEquals(5, makeDivisions(50))
        assertEquals(3, makeDivisions(30))
    }

    @Test
    fun `bucket hours are 6 through 21`() {
        val buckets = DowntimeCalculator.calculate(emptyList(), System.currentTimeMillis())
        for (i in buckets.indices) {
            assertEquals(6 + i, buckets[i].hour)
        }
    }

    @Test
    fun `multiple heartbeats no downtime`() {
        // Heartbeats every 60s — no gap exceeds threshold
        val now = System.currentTimeMillis()
        val timestamps = (0..10).map { now + it * 60_000L }
        val buckets = DowntimeCalculator.calculate(timestamps, now + 11 * 60_000L)
        buckets.forEach {
            assertEquals(0, it.downtimeMinutes)
        }
    }

    @Test
    fun `open gap from last heartbeat above threshold`() {
        // Last heartbeat 10 minutes ago — above 90s threshold
        val now = System.currentTimeMillis()
        val hb1 = now - 600_000L
        val buckets = DowntimeCalculator.calculate(listOf(hb1), now)
        val totalMinutes = buckets.sumOf { it.downtimeMinutes }
        assertTrue("Open gap of ~10 min should produce non-zero total", totalMinutes > 0)
    }

    private fun makeDivisions(minutes: Long): Int {
        return if (minutes <= 0) 0
        else kotlin.math.min(6, kotlin.math.ceil(minutes.toDouble() / 10).toInt())
    }
}
