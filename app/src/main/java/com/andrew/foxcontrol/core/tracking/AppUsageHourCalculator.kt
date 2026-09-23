package com.andrew.foxcontrol.core.tracking

import com.andrew.foxcontrol.core.util.DateUtils

/**
 * Represents app usage minutes bucketed by hour.
 *
 * @property hour          Hour of day (6..21), representing the bucket [hour:00, hour+1:00).
 * @property usageMinutes  Minutes within this hour that the app was in foreground, 0..60.
 */
data class AppUsageHourBucket(
    val hour: Int,
    val usageMinutes: Int
)

/**
 * Pure function that computes hourly usage buckets from session intervals.
 *
 * Algorithm:
 * 1. For each of 16 hour buckets (6..21):
 *    - Clip bucket window to [hourStartMs, min(hourEndMs, now))
 *    - Sum intersections of all session intervals with the clipped window
 *    - Convert ms → minutes (floor)
 * 2. No "gaps" step needed — sessions are already concrete foreground intervals.
 * 3. Future hours (elapsedEndMs <= hourStartMs) → (0, 0).
 * 4. Sum milliseconds per hour, divide by 60_000 once at the end (avoids rounding errors).
 */
object AppUsageHourCalculator {

    /**
     * Calculate hourly usage buckets from session intervals.
     *
     * @param sessions        List of (startTime, endTime) pairs in ms (for the current day).
     * @param now             Current time in ms (used to clip future hours).
     * @param windowStartHour Start of visible window (default 6, i.e. 06:00).
     * @param windowEndHour   End of visible window (default 22, i.e. 22:00).
     * @return List of 16 buckets for hours 6..21.
     */
    fun calculate(
        sessions: List<Pair<Long, Long>>,
        now: Long,
        windowStartHour: Int = ChartWindow.START_HOUR,
        windowEndHour: Int = ChartWindow.END_HOUR
    ): List<AppUsageHourBucket> {

        // Sort sessions by start time for consistent processing
        val sorted = sessions.toMutableList().sortedBy { it.first }

        // Build 16 buckets (hours 6..21)
        val buckets = mutableListOf<AppUsageHourBucket>()
        for (h in windowStartHour until windowEndHour) {
            val hourStartMs = DateUtils.todayHourStartMs(h)
            val hourEndMs = hourStartMs + 3_600_000L // 1 hour in ms
            val elapsedEndMs = minOf(hourEndMs, now)

            // Future hour — no data yet
            if (elapsedEndMs <= hourStartMs) {
                buckets.add(AppUsageHourBucket(h, 0))
                continue
            }

            // usageMs = sum of intersections of all sessions with [hourStartMs, elapsedEndMs)
            var usageMs = 0L
            for ((sessionStart, sessionEnd) in sorted) {
                val interStart = maxOf(sessionStart, hourStartMs)
                val interEnd = minOf(sessionEnd, elapsedEndMs)
                if (interStart < interEnd) {
                    usageMs += interEnd - interStart
                }
            }

            val usageMinutes = usageMs / 60_000L
            buckets.add(AppUsageHourBucket(h, usageMinutes.toInt()))
        }

        return buckets
    }
}
