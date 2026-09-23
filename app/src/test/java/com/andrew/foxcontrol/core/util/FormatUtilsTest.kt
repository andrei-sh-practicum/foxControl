package com.andrew.foxcontrol.core.util

import org.junit.Assert.*
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun formatDuration_onlyMinutes() {
        // 30 minutes
        val duration = 30 * 60 * 1000L
        val result = formatDuration(duration)
        assertEquals("30м", result)
    }

    @Test
    fun formatDuration_zeroMinutes() {
        val result = formatDuration(0L)
        assertEquals("0м", result)
    }

    @Test
    fun formatDuration_oneHour() {
        val duration = 1 * 60 * 60 * 1000L
        val result = formatDuration(duration)
        assertEquals("1ч 0м", result)
    }

    @Test
    fun formatDuration_oneHourThirtyMinutes() {
        val duration = 1 * 60 * 60 * 1000L + 30 * 60 * 1000L
        val result = formatDuration(duration)
        assertEquals("1ч 30м", result)
    }

    @Test
    fun formatDuration_twoHours() {
        val duration = 2 * 60 * 60 * 1000L
        val result = formatDuration(duration)
        assertEquals("2ч 0м", result)
    }

    @Test
    fun formatDuration_hoursAndMinutes() {
        val duration = 3 * 60 * 60 * 1000L + 45 * 60 * 1000L
        val result = formatDuration(duration)
        assertEquals("3ч 45м", result)
    }

    @Test
    fun formatDuration_largeDuration() {
        val duration = 24 * 60 * 60 * 1000L
        val result = formatDuration(duration)
        assertEquals("24ч 0м", result)
    }

    @Test
    fun formatDuration_59Minutes() {
        val result = formatDuration(59 * 60 * 1000L)
        assertEquals("59м", result)
    }

    @Test
    fun formatDuration_oneSecond() {
        val result = formatDuration(1000L)
        assertEquals("0м", result)
    }
}
