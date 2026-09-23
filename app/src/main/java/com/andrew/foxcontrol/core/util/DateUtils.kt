package com.andrew.foxcontrol.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Day keys and day/hour boundaries used across the app.
 *
 * The `yyyy-MM-dd` key is stored in the DB (`usage_sessions.date`, `report_send_log.date`),
 * so it must stay byte-for-byte the same: [SimpleDateFormat] with [Locale.getDefault] —
 * a new instance per call, because SimpleDateFormat is not thread-safe and this is called
 * from the tracking timers and the UI at the same time.
 * (DateTimeFormatter is not a drop-in replacement: it ignores locale digits and calendars,
 * e.g. the Buddhist calendar on th_TH.)
 */
object DateUtils {

    const val DATE_PATTERN = "yyyy-MM-dd"

    private fun dateFormat() = SimpleDateFormat(DATE_PATTERN, Locale.getDefault())

    /** Day key for the given moment. */
    fun format(timeMs: Long): String = dateFormat().format(Date(timeMs))

    /** Day key for today. */
    fun today(): String = format(System.currentTimeMillis())

    /** Day key for [days] days before today (calendar days, not 24 h periods). */
    fun daysAgo(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        return format(calendar.timeInMillis)
    }

    /** 00:00:00.000 of today. */
    fun todayStartMs(): Long = startOfDay(Calendar.getInstance())

    /**
     * 00:00:00.000 of the day [date] (a `yyyy-MM-dd` key) and 23:59:59.999 + 1 ms of that day.
     * @throws java.text.ParseException if [date] is not a valid key (callers handle it).
     */
    fun dayBoundsMs(date: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.time = dateFormat().parse(date) ?: Calendar.getInstance().time
        val dayStart = startOfDay(calendar)

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val dayEnd = calendar.timeInMillis + 1

        return dayStart to dayEnd
    }

    /** Start of [hour]:00 today. */
    fun todayHourStartMs(hour: Int): Long = hourStartMs(todayStartMs(), hour)

    /**
     * Start of [hour]:00 on the day that starts at [dayStartMs].
     * Calendar-based, so it stays correct on DST-change days (not dayStart + hour * 3600 s).
     */
    fun hourStartMs(dayStartMs: Long, hour: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dayStartMs
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun startOfDay(calendar: Calendar): Long {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
