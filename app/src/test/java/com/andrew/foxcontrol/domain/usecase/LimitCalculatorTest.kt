package com.andrew.foxcontrol.domain.usecase

import com.andrew.foxcontrol.data.local.entity.AppLimitEntity
import com.andrew.foxcontrol.domain.model.UsageStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitCalculatorTest {

    private fun app(pkg: String, ms: Long) = UsageStats(pkg, pkg.uppercase(), ms, 1, false)

    @Test
    fun `isExceeded compares milliseconds strictly`() {
        assertFalse(LimitCalculator.isExceeded(1_800_000L, 30))
        assertTrue(LimitCalculator.isExceeded(1_800_001L, 30))
    }

    @Test
    fun `exceededApps compares floored minutes strictly`() {
        val result = LimitCalculator.exceededApps(
            apps = listOf(
                app("equal", 1_800_000L),       // 30 min, limit 30 → no
                app("seconds", 1_830_000L),     // 30.5 min → floored 30 → no
                app("over", 1_860_000L)         // 31 min → +1
            ),
            limits = listOf(
                AppLimitEntity("equal", 30),
                AppLimitEntity("seconds", 30),
                AppLimitEntity("over", 30)
            )
        )
        assertEquals(listOf("over"), result.map { it.packageName })
        assertEquals(31, result[0].totalMinutes)
        assertEquals(30, result[0].limitMinutes)
        assertEquals(1, result[0].overMinutes)
    }

    @Test
    fun `apps without limit are ignored and result is sorted by overuse`() {
        val result = LimitCalculator.exceededApps(
            apps = listOf(app("a", 3_600_000L), app("b", 3_600_000L), app("free", 9_000_000L)),
            limits = listOf(AppLimitEntity("a", 50), AppLimitEntity("b", 10))
        )
        assertEquals(listOf("b", "a"), result.map { it.packageName })
        assertEquals(listOf(50, 10), result.map { it.overMinutes })
    }
}
