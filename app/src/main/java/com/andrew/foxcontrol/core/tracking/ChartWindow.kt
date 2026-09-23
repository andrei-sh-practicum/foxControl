package com.andrew.foxcontrol.core.tracking

/**
 * Visible window of the hourly charts: buckets for hours [START_HOUR] until [END_HOUR]
 * (06:00–22:00, i.e. 16 buckets for hours 6..21).
 */
object ChartWindow {
    const val START_HOUR = 6
    const val END_HOUR = 22

    val hours: IntRange get() = START_HOUR until END_HOUR

    /** All-zero usage buckets — fallback when the data can't be loaded. */
    fun emptyUsageBuckets(): List<AppUsageHourBucket> = hours.map { AppUsageHourBucket(it, 0) }

    /** All-zero downtime buckets — fallback when the data can't be loaded. */
    fun emptyDowntimeBuckets(): List<DowntimeHourBucket> = hours.map { DowntimeHourBucket(it, 0, 0) }
}
