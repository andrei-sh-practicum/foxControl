package com.andrew.foxcontrol.ui.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatAgoTest {

    @Test
    fun `same texts as the two former copies`() {
        assertEquals("59 сек назад", formatAgo(59, includeDays = true))
        assertEquals("1 мин назад", formatAgo(60, includeDays = true))
        assertEquals("59 мин назад", formatAgo(3599, includeDays = true))
        assertEquals("1 ч назад", formatAgo(3600, includeDays = true))
        assertEquals("23 ч назад", formatAgo(86_399, includeDays = true))
        assertEquals("1 дн назад", formatAgo(86_400, includeDays = true))
    }

    @Test
    fun `heartbeat variant stays in hours`() {
        assertEquals("48 ч назад", formatAgo(48 * 3600, includeDays = false))
    }
}
