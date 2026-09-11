package com.andrew.foxcontrol.core.tracking

import kotlin.math.ceil
import kotlin.math.min

/**
 * Represents downtime minutes bucketed by hour.
 * @property hour Hour of day (6..21), representing the bucket [hour:00, hour+1:00).
 * @property downtimeMinutes Total downtime minutes in this hour bucket (0..60).
 * @property divisions Number of visual divisions to render (0..6).
 */
data class DowntimeHourBucket(
    val hour: Int,
    val downtimeMinutes: Int,
    val divisions: Int
)

/**
 * Pure function that computes hourly downtime buckets from heartbeat timestamps.
 *
 * Algorithm (no Android/Room dependencies — easily testable on JVM):
 * 1. Sort timestamps ascending.
 * 2. Compute gaps between adjacent heartbeats.
 * 3. Add an "open" gap from the last heartbeat to `now` (if the service appears down).
 * 4. Filter gaps shorter than the threshold.
 * 5. Clip each gap to the visible window [windowStartHour, windowEndHour).
 * 6. Bucket clipped gap minutes into the hour of the gap start.
 * 7. Sum minutes per hour (multiple gaps in the same hour).
 * 8. Convert minutes to divisions (0–6).
 */
object DowntimeCalculator {

    private const val HEARTBEAT_INTERVAL_MS = 60_000L
    private const val DOWNTIME_THRESHOLD_MS = 90_000L // 1.5× heartbeat interval
    private const val WINDOW_START_HOUR = 6
    private const val WINDOW_END_HOUR = 22
    private const val MAX_DIVISIONS = 6
    private const val MINUTES_PER_DIVISION = 10

    /**
     * Calculate downtime buckets from heartbeat timestamps.
     *
     * @param heartbeatTimestamps Sorted ascending timestamps in ms (for the current day).
     * @param now Current time in ms (used for the "open" gap if service seems down).
     * @param windowStartHour Start of visible window (default 6, i.e. 06:00).
     * @param windowEndHour End of visible window (default 22, i.e. 22:00).
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

        // Step 5: clip each gap to the visible window
        val clippedGaps = significantGaps.mapNotNull { (start, end) ->
            // Clip to window
            val clippedStart = maxOf(start, windowStartMs)
            val clippedEnd = minOf(end, windowEndMs)
            if (clippedStart < clippedEnd) {
                clippedStart to clippedEnd
            } else {
                null // entirely outside window
            }
        }

        // Step 6: bucket minutes by hour of gap start
        val hourMinutes = mutableMapOf<Int, Long>()
        for ((start, end) in clippedGaps) {
            val startHour = hourOf(start, windowStartHour)
            if (startHour != null) {
                hourMinutes[startHour] = hourMinutes.getOrDefault(startHour, 0) +
                    (end - start) / 1000 / 60
            }
        }

        // Step 7: build 16 buckets (hours 6..21)
        val buckets = mutableListOf<DowntimeHourBucket>()
        for (h in windowStartHour until windowEndHour) {
            val minutes = hourMinutes[h] ?: 0L
            val cappedMinutes = minutes.coerceAtMost(60)
            val divisions = if (cappedMinutes <= 0) 0
            else min(MAX_DIVISIONS, ceil(cappedMinutes.toDouble() / MINUTES_PER_DIVISION).toInt())
            buckets.add(DowntimeHourBucket(h, cappedMinutes.toInt(), divisions))
        }

        return buckets
    }

    /**
     * Returns the millisecond timestamp at the start of the given hour on the same date.
     * E.g., hourStartMs(6) with date 2025-09-11 → 2025-09-11T06:00:00.000
     */
    private fun hourStartMs(hour: Int): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Get the hour bucket index for a timestamp, or null if it falls outside the window.
     */
    private fun hourOf(ts: Long, windowStartHour: Int): Int? {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = ts
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        return if (hour >= windowStartHour && hour < windowStartHour + 16) hour else null
    }
}
