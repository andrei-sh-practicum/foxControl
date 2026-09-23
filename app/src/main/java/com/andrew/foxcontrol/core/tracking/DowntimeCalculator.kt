package com.andrew.foxcontrol.core.tracking

import java.util.Calendar

/**
 * Represents downtime minutes bucketed by hour.
 *
 * @property hour         Hour of day (6..21), representing the bucket [hour:00, hour+1:00).
 * @property aliveMinutes Minutes within this hour that the service was alive (heartbeat coverage).
 * @property deadMinutes  Minutes within this hour that the service was down (no heartbeat).
 *
 * Invariant: aliveMinutes + deadMinutes <= 60.
 * The remainder (60 - aliveMinutes - deadMinutes) represents "no data" (future hours of the day,
 * or minutes before the first heartbeat of the day) and is not rendered at all on the chart.
 */
data class DowntimeHourBucket(
    val hour: Int,
    val aliveMinutes: Int,
    val deadMinutes: Int
)

/**
 * Pure function that computes hourly downtime buckets from heartbeat timestamps.
 *
 * Algorithm:
 * 1. Sort timestamps ascending.
 * 2. Compute gaps between adjacent heartbeats.
 * 3. Add an "open" gap from the last heartbeat to `now` (if the service appears down).
 * 4. Filter gaps shorter than the threshold.
 * 5. Clip each gap to the visible window [windowStartHour, windowEndHour) and to `now`.
 * 6. For each hour bucket:
 *    - Skip future hours (elapsedEndMs <= hourStartMs) → (0, 0)
 *    - Skip time before the first heartbeat of the day → (0, 0)
 *    - Compute deadMs as the sum of intersections of dead intervals with [knownStartMs, elapsedEndMs)
 *    - aliveMinutes = knownMinutes - deadMinutes
 * 7. Sum milliseconds of intersections and divide by 60_000 once at the end (avoids rounding errors).
 */
object DowntimeCalculator {

    private const val DOWNTIME_THRESHOLD_MS = 90_000L // 1.5x heartbeat interval
    private const val WINDOW_START_HOUR = 6
    private const val WINDOW_END_HOUR = 22

    /**
     * Calculate downtime buckets from heartbeat timestamps.
     *
     * @param heartbeatTimestamps Sorted ascending timestamps in ms (for the current day).
     * @param now                 Current time in ms (used for the "open" gap if service seems down).
     * @param windowStartHour     Start of visible window (default 6, i.e. 06:00).
     * @param windowEndHour       End of visible window (default 22, i.e. 22:00).
     * @return List of 16 buckets for hours 6..21.
     */
    fun calculate(
        heartbeatTimestamps: List<Long>,
        now: Long,
        windowStartHour: Int = WINDOW_START_HOUR,
        windowEndHour: Int = WINDOW_END_HOUR
    ): List<DowntimeHourBucket> {

        val windowStartMs = hourStartMs(windowStartHour)
        val windowEndMs = hourStartMs(windowEndHour)

        // Step 1: sort ascending (should already be sorted, but be safe)
        val sorted = heartbeatTimestamps.toMutableList().sorted()

        // Step 2: compute gaps between adjacent heartbeats
        val rawGaps = mutableListOf<Pair<Long, Long>>() // (start, end) in ms
        for (i in 1 until sorted.size) {
            rawGaps.add(sorted[i - 1] to sorted[i])
        }

        // Step 3: add "open" gap from last heartbeat to now (if service seems down)
        if (sorted.isNotEmpty()) {
            val lastHb = sorted.last()
            if (now - lastHb >= DOWNTIME_THRESHOLD_MS) {
                rawGaps.add(lastHb to now)
            }
        }
        // If no heartbeats today, no gaps at all → all zeros (documented assumption)

        // Step 4: filter gaps below threshold
        val significantGaps = rawGaps.filter { (start, end) ->
            (end - start) >= DOWNTIME_THRESHOLD_MS
        }

        // Step 5: clip each gap to the visible window and to `now`
        val deadIntervals = significantGaps.mapNotNull { (start, end) ->
            val clippedStart = maxOf(start, windowStartMs)
            val clippedEnd = minOf(end, windowEndMs, now)
            if (clippedStart < clippedEnd) {
                clippedStart to clippedEnd
            } else {
                null // entirely outside window or future
            }
        }

        // Step 6: build 16 buckets (hours 6..21)
        val buckets = mutableListOf<DowntimeHourBucket>()
        for (h in windowStartHour until windowEndHour) {
            val hourStartMs = hourStartMs(h)
            val hourEndMs = hourStartMs + 3_600_000L // 1 hour in ms
            val elapsedEndMs = minOf(hourEndMs, now)

            // Future hour — no data yet
            if (elapsedEndMs <= hourStartMs) {
                buckets.add(DowntimeHourBucket(h, 0, 0))
                continue
            }

            // Skip time before the first heartbeat of the day — we know nothing about it
            val firstHb = sorted.firstOrNull()
            val knownStartMs = if (firstHb == null) elapsedEndMs
            else maxOf(hourStartMs, firstHb)

            if (knownStartMs >= elapsedEndMs) {
                // Entire remaining portion of this hour is before first heartbeat
                buckets.add(DowntimeHourBucket(h, 0, 0))
                continue
            }

            // knownMinutes = how many minutes of this hour we actually have data for
            val knownMinutes = (elapsedEndMs - knownStartMs) / 60_000L

            // deadMs = sum of intersections of dead intervals with [knownStartMs, elapsedEndMs)
            var deadMs = 0L
            for ((gapStart, gapEnd) in deadIntervals) {
                val interStart = maxOf(gapStart, knownStartMs)
                val interEnd = minOf(gapEnd, elapsedEndMs)
                if (interStart < interEnd) {
                    deadMs += interEnd - interStart
                }
            }

            val deadMinutes = minOf(knownMinutes, deadMs / 60_000L)
            val aliveMinutes = knownMinutes - deadMinutes

            buckets.add(DowntimeHourBucket(h, aliveMinutes.toInt(), deadMinutes.toInt()))
        }

        return buckets
    }

    /**
     * Returns the millisecond timestamp at the start of the given hour on the same date.
     * E.g., hourStartMs(6) with date 2025-09-11 → 2025-09-11T06:00:00.000
     */
    private fun hourStartMs(hour: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
