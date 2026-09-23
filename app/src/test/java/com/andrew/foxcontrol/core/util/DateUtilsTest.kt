package com.andrew.foxcontrol.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DateUtilsTest {

    private val reference = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    @Test
    fun `format matches the previous SimpleDateFormat output`() {
        val ts = 1_758_600_000_000L
        assertEquals(reference.format(Date(ts)), DateUtils.format(ts))
    }

    @Test
    fun `today and daysAgo use calendar days`() {
        assertEquals(reference.format(Date()), DateUtils.today())

        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        assertEquals(reference.format(cal.time), DateUtils.daysAgo(6))
    }

    @Test
    fun `dayBoundsMs covers the whole day`() {
        val (start, end) = DateUtils.dayBoundsMs("2026-03-15")

        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))

        cal.timeInMillis = end
        assertEquals(16, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0L, cal.get(Calendar.MILLISECOND).toLong())
    }

    @Test(expected = ParseException::class)
    fun `unparsable key throws like SimpleDateFormat did`() {
        DateUtils.dayBoundsMs("garbage")
    }

    @Test
    fun `todayHourStartMs is the hour boundary of today`() {
        val cal = Calendar.getInstance().apply { timeInMillis = DateUtils.todayHourStartMs(6) }
        assertEquals(reference.format(Date()), reference.format(cal.time))
        assertEquals(6, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
    }
}
