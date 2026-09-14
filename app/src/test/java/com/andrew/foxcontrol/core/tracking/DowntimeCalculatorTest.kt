package com.andrew.foxcontrol.core.tracking

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for DowntimeCalculator pure logic.
 * Tests run on plain JVM — no Android dependencies needed.
 *
 * IMPORTANT: DowntimeCalculator.hourStartMs() uses Calendar.getInstance() which
 * always resolves to "today". All test timestamps must therefore fall within
 * the current calendar day, otherwise the window clipping discards them.
 *
 * KEY: heartbeat gaps >= 90s are treated as downtime. To have "alive" time,
 * consecutive heartbeats must be < 90s apart (like real 60s heartbeats).
 */
class DowntimeCalculatorTest {

    /**
     * Returns a timestamp for [hour:minute] on TODAY (the same day Calendar.getInstance
     * would use for the window boundaries inside DowntimeCalculator).
     */
    private fun todayAt(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    // --- Test cases from section 7 of service_downtime_fixes.md ---

    @Test
    fun `no heartbeats returns all zeros`() {
        val buckets = DowntimeCalculator.calculate(emptyList(), System.currentTimeMillis())
        assertEquals(16, buckets.size)
        buckets.forEach {
            assertEquals(0, it.aliveMinutes)
            assertEquals(0, it.deadMinutes)
            assertEquals(it.aliveMinutes + it.deadMinutes, 0)
        }
    }

    @Test
    fun `full past hour with no gaps`() {
        // Heartbeats every 60s for hour 09 — no downtime.
        // Each gap = 60s < 90s threshold → no gaps detected.
        val now = todayAt(10, 0) + 1000L // 10:00:01 today
        val hbStart = todayAt(9, 0)
        val timestamps = (0..59).map { hbStart + it * 60_000L }
        val buckets = DowntimeCalculator.calculate(timestamps, now)

        val bucket9 = buckets.find { it.hour == 9 }
        bucket9?.let {
            assertEquals(60, it.aliveMinutes)
            assertEquals(0, it.deadMinutes)
        }
    }

    @Test
    fun `full past hour inside a long downtime`() {
        // Only one heartbeat at window start (06:00), then nothing — entire day is downtime
        val windowStart = todayAt(6, 0)
        val now = todayAt(22, 0) // 22:00 today

        val buckets = DowntimeCalculator.calculate(listOf(windowStart), now)

        // All 16 buckets should be (0, 60) — fully dead
        buckets.forEach { bucket ->
            assertEquals(0, bucket.aliveMinutes)
            assertEquals(60, bucket.deadMinutes)
        }
    }

    @Test
    fun `gap spanning two hours distributes across three hours`() {
        // 90-minute gap: 09:40 to 11:10
        // Hour 09: 20 min dead (09:40-09:59)
        // Hour 10: 60 min dead (10:00-10:59)
        // Hour 11: 10 min dead (11:00-11:09)
        val base = todayAt(9, 40)
        val hb1 = base
        val hb2 = base + 90 * 60 * 1000L // 11:10
        val now = base + 90 * 60 * 1000L + 1000L // just after gap

        val buckets = DowntimeCalculator.calculate(listOf(hb1, hb2), now)

        val bucket9 = buckets.find { it.hour == 9 }
        val bucket10 = buckets.find { it.hour == 10 }
        val bucket11 = buckets.find { it.hour == 11 }

        // Hour 9: gap from 09:40 to 10:00 = 20 min dead
        bucket9?.let {
            assertEquals(20, it.deadMinutes)
            assertEquals(0, it.aliveMinutes)
        }

        // Hour 10: gap from 10:00 to 11:10, clipped to hour 10 = 60 min dead
        bucket10?.let {
            assertEquals(60, it.deadMinutes)
            assertEquals(0, it.aliveMinutes)
        }

        // Hour 11: gap from 11:00 to 11:10 = 10 min dead
        bucket11?.let {
            assertEquals(10, it.deadMinutes)
            assertEquals(0, it.aliveMinutes)
        }
    }

    @Test
    fun `gap below threshold does not produce deadMinutes`() {
        // Gap of 60s < 90s threshold — should not count as downtime
        val now = System.currentTimeMillis()
        val hb1 = now - 120_000L
        val hb2 = now - 60_000L
        val buckets = DowntimeCalculator.calculate(listOf(hb1, hb2), now + 10_000L)

        buckets.forEach {
            assertEquals(0, it.deadMinutes)
        }
    }

    @Test
    fun `current hour partial alive_plus_dead_equals_known_minutes`() {
        // Heartbeats: 08:00, 08:00:01 (alive), then gap 08:30 to 08:45 (dead).
        // The key is: hb1 and hb2 must be < 90s apart so they don't form a gap.
        // After hb2 at 08:00:01, there's no more heartbeat until now (08:45).
        // Open gap from 08:00:01 to 08:45 = 44:59 ≈ 45 min dead.
        // Known = 08:00 to 08:45 = 45 min. Dead = 45. Alive = 0.
        // That's not what we want. Let me restructure:
        //
        // Heartbeat at 08:00, then another at 08:29:30 (alive, < 90s gap from previous? NO, 29.5 min!)
        //
        // I need heartbeats close together to avoid gaps. Let's use real 60s heartbeats:
        // hb at 08:00, 08:01, 08:02, ... 08:29 (alive period)
        // gap from 08:29 to 08:45 = 16 min dead
        // known = 45 min, dead = 16, alive = 29
        val base = todayAt(8, 0)
        val timestamps = (0..29).map { base + it * 60_000L } // hb at 08:00, 08:01, ... 08:29
        val now = base + 45 * 60 * 1000L // 08:45

        val buckets = DowntimeCalculator.calculate(timestamps, now)

        val bucket8 = buckets.find { it.hour == 8 }
        bucket8?.let {
            // known = 45 min, dead = 16 min (08:29 to 08:45), alive = 29 min
            // Note: dead = (now - lastHb) = 45 - 29 = 16 min
            assertEquals(29, it.aliveMinutes)
            assertEquals(16, it.deadMinutes)
            assertEquals(45, it.aliveMinutes + it.deadMinutes)
        }
    }

    @Test
    fun `future hour returns zeros`() {
        val now = System.currentTimeMillis()
        val hb = now - 120_000L // 2 min ago
        val buckets = DowntimeCalculator.calculate(listOf(hb), now)

        // Find the hour that is in the future (more than 1 hour from now)
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val futureBucket = buckets.find { it.hour > currentHour }
        futureBucket?.let {
            assertEquals(0, it.aliveMinutes)
            assertEquals(0, it.deadMinutes)
        }
    }

    @Test
    fun `time_before_first_heartbeat_returns_zeros`() {
        // First heartbeat at 07:15, window starts at 06:00
        // Hour 06 and part of hour 07 before first hb should be (0, 0)
        val base = todayAt(7, 15)
        val hb1 = base
        val hb2 = base + 60 * 60 * 1000L // 08:15
        val now = base + 120 * 60 * 1000L // 09:15

        // Need heartbeats every 60s between 07:15 and 09:15 for them to be "alive"
        val timestamps = mutableListOf<Long>()
        for (i in 0 until 120) {
            timestamps.add(hb1 + i * 60_000L)
        }
        // hb at 07:15, 07:16, ..., 09:14. Now = 09:15.

        val buckets = DowntimeCalculator.calculate(timestamps, now)

        // Hour 06: entirely before first heartbeat (07:15) → (0, 0)
        val bucket6 = buckets.find { it.hour == 6 }
        bucket6?.let {
            assertEquals(0, it.aliveMinutes)
            assertEquals(0, it.deadMinutes)
        }

        // Hour 07: known time starts at 07:15, so 07:15 to 08:00 = 45 min known
        // All heartbeats present → all alive
        val bucket7 = buckets.find { it.hour == 7 }
        bucket7?.let {
            assertEquals(45, it.aliveMinutes)
            assertEquals(0, it.deadMinutes)
        }

        // Hour 08: known time = 08:00 to 09:00 = 60 min, all alive
        val bucket8 = buckets.find { it.hour == 8 }
        bucket8?.let {
            assertEquals(60, it.aliveMinutes)
            assertEquals(0, it.deadMinutes)
        }
    }

    @Test
    fun `multiple_separate_gaps_sum_in_same_hour`() {
        // Heartbeats: 08:00-08:09, gap 08:09-08:19 (dead=10), hb 08:19-08:29, gap 08:29-08:39 (dead=10),
        // hb 08:39-08:49, now 08:50 (open gap 60s < 90s threshold → not a gap)
        // Known = 08:00 to 08:50 = 50 min. Dead = 10 + 10 = 20 min. Alive = 30 min.
        val base = todayAt(8, 0)
        // First batch: 08:00 to 08:09 (10 heartbeats, 60s apart)
        val batch1 = (0..9).map { base + it * 60_000L }
        // Second batch: 08:19 to 08:29 (11 heartbeats)
        val batch2 = (0..10).map { base + (19 + it) * 60_000L }
        // Third batch: 08:39 to 08:49 (11 heartbeats)
        val batch3 = (0..10).map { base + (39 + it) * 60_000L }
        val timestamps = (batch1 + batch2 + batch3).sorted()
        val now = base + 50 * 60 * 1000L // 08:50

        val buckets = DowntimeCalculator.calculate(timestamps, now)

        val bucket8 = buckets.find { it.hour == 8 }
        bucket8?.let {
            assertEquals(30, it.aliveMinutes)
            assertEquals(20, it.deadMinutes)
            assertEquals(50, it.aliveMinutes + it.deadMinutes)
        }
    }

    // --- Additional regression tests ---

    @Test
    fun `single heartbeat no gap`() {
        val now = System.currentTimeMillis()
        val buckets = DowntimeCalculator.calculate(listOf(now), now + 100)
        assertEquals(16, buckets.size)
        buckets.forEach {
            assertEquals(0, it.deadMinutes)
        }
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
    fun `bucket hours are 6 through 21`() {
        val buckets = DowntimeCalculator.calculate(emptyList(), System.currentTimeMillis())
        for (i in buckets.indices) {
            assertEquals(6 + i, buckets[i].hour)
        }
    }

    @Test
    fun `multiple heartbeats no downtime`() {
        val now = System.currentTimeMillis()
        val timestamps = (0..10).map { now + it * 60_000L }
        val buckets = DowntimeCalculator.calculate(timestamps, now + 11 * 60_000L)
        buckets.forEach {
            assertEquals(0, it.deadMinutes)
        }
    }

    @Test
    fun `open gap from last heartbeat above threshold`() {
        val now = System.currentTimeMillis()
        val hb1 = now - 600_000L // 10 min ago
        val buckets = DowntimeCalculator.calculate(listOf(hb1), now)
        val totalDead = buckets.sumOf { it.deadMinutes }
        assertTrue("Open gap of ~10 min should produce non-zero total: $totalDead", totalDead > 0)
    }
}
