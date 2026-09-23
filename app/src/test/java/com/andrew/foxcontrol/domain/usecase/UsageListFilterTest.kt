package com.andrew.foxcontrol.domain.usecase

import com.andrew.foxcontrol.domain.model.UsageStats
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageListFilterTest {

    private fun app(pkg: String, ms: Long) = UsageStats(pkg, pkg, ms, 1, false)

    @Test
    fun `hides apps used less than a minute and keeps order`() {
        val apps = listOf(app("long", 120_000L), app("exact", 60_000L), app("short", 59_999L))
        assertEquals(listOf("long", "exact"), UsageListFilter.visibleApps(apps).map { it.packageName })
    }
}
