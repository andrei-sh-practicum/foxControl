package com.andrew.foxcontrol.ui.home

import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.domain.model.UsageStats
import com.andrew.foxcontrol.domain.model.WeeklyUsageStats
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for HomeState, HomePeriod, HomeEvent data classes.
 */
class HomeStateTest {

    @Test
    fun homeState_initialState_defaultValues() {
        val state = HomeState()
        assertTrue(state.isLoading)
        assertEquals(HomePeriod.Today, state.period)
        assertNull(state.dailyStats)
        assertNull(state.weeklyStats)
    }

    @Test
    fun homeState_copyUpdatesLoading() {
        val initial = HomeState()
        val updated = initial.copy(isLoading = false)

        assertFalse(updated.isLoading)
        assertTrue(initial.isLoading)
    }

    @Test
    fun homeState_copyUpdatesPeriod() {
        val initial = HomeState()
        val updated = initial.copy(period = HomePeriod.Week)

        assertEquals(HomePeriod.Week, updated.period)
        assertTrue(initial.period == HomePeriod.Today)
    }

    @Test
    fun homeState_copyUpdatesDailyStats() {
        val initial = HomeState()
        val stats = DailyUsageStats(
            date = "2024-01-01",
            totalUsageMs = 3600000L,
            apps = emptyList()
        )
        val updated = initial.copy(dailyStats = stats, isLoading = false)

        assertFalse(updated.isLoading)
        assertNotNull(updated.dailyStats)
        assertEquals("2024-01-01", updated.dailyStats?.date)
        assertEquals(3600000L, updated.dailyStats?.totalUsageMs)
    }

    @Test
    fun homeState_copyUpdatesWeeklyStats() {
        val initial = HomeState()
        val weekly = WeeklyUsageStats(
            startDate = "2024-01-01",
            endDate = "2024-01-07",
            totalUsageMs = 25200000L,
            apps = emptyList()
        )
        val updated = initial.copy(weeklyStats = weekly, isLoading = false)

        assertFalse(updated.isLoading)
        assertNotNull(updated.weeklyStats)
        assertEquals("2024-01-01", updated.weeklyStats?.startDate)
        assertEquals("2024-01-07", updated.weeklyStats?.endDate)
    }

    @Test
    fun homeState_copyMultipleFields() {
        val initial = HomeState()
        val stats = DailyUsageStats(
            date = "2024-01-01",
            totalUsageMs = 7200000L,
            apps = listOf(
                UsageStats(
                    packageName = "com.chrome",
                    appName = "Chrome",
                    totalDurationMs = 3600000L,
                    sessionCount = 5,
                    isEntertainment = true
                )
            )
        )
        val updated = initial.copy(
            isLoading = false,
            period = HomePeriod.Today,
            dailyStats = stats
        )

        assertFalse(updated.isLoading)
        assertEquals(HomePeriod.Today, updated.period)
        assertNotNull(updated.dailyStats)
        assertEquals(1, updated.dailyStats?.apps?.size)
        assertEquals(7200000L, updated.dailyStats?.totalUsageMs)
    }

    @Test
    fun homeState_dataClassEquality() {
        val stats = DailyUsageStats(
            date = "2024-01-01",
            totalUsageMs = 3600000L,
            apps = emptyList()
        )
        val state1 = HomeState(isLoading = false, dailyStats = stats)
        val state2 = HomeState(isLoading = false, dailyStats = stats)

        assertEquals(state1, state2)
    }

    @Test
    fun homeState_dataClassCopyImmutability() {
        val original = HomeState()
        val modified = original.copy(isLoading = false)

        assertTrue(original.isLoading)
        assertFalse(modified.isLoading)
    }

    @Test
    fun homeState_dataClassToString() {
        val state = HomeState()
        val string = state.toString()

        assertTrue(string.contains("HomeState"))
        assertTrue(string.contains("isLoading="))
        assertTrue(string.contains("period="))
    }

    @Test
    fun homeState_dataClassCopy() {
        val state = HomeState()
        val copy = state.copy()

        assertEquals(state, copy)
    }

    @Test
    fun homeState_dataClassHashCode() {
        val state1 = HomeState(isLoading = false, period = HomePeriod.Today)
        val state2 = HomeState(isLoading = false, period = HomePeriod.Today)

        assertEquals(state1.hashCode(), state2.hashCode())
    }

    @Test
    fun homeState_loadingTrueByDefault() {
        assertTrue(HomeState().isLoading)
    }

    @Test
    fun homeState_periodTodayByDefault() {
        assertEquals(HomePeriod.Today, HomeState().period)
    }

    @Test
    fun homeState_statsNullByDefault() {
        val state = HomeState()
        assertNull(state.dailyStats)
        assertNull(state.weeklyStats)
    }
}

class HomePeriodTest {

    @Test
    fun homePeriod_valuesCount() {
        assertEquals(3, HomePeriod.values().size)
    }

    @Test
    fun homePeriod_todayLabel() {
        assertEquals("Сегодня", HomePeriod.Today.label)
    }

    @Test
    fun homePeriod_weekLabel() {
        assertEquals("Неделя", HomePeriod.Week.label)
    }

    @Test
    fun homePeriod_valuesAreUnique() {
        val values = HomePeriod.values()
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun homePeriod_findByLabel() {
        val today = HomePeriod.entries.find { it.label == "Сегодня" }
        assertEquals(HomePeriod.Today, today)

        val week = HomePeriod.entries.find { it.label == "Неделя" }
        assertEquals(HomePeriod.Week, week)

        val invalid = HomePeriod.entries.find { it.label == "Год" }
        assertNull(invalid)
    }

    @Test
    fun homePeriod_entriesVsValues() {
        assertEquals(HomePeriod.Today, HomePeriod.entries[0])
        assertEquals(HomePeriod.Yesterday, HomePeriod.entries[1])
        assertEquals(HomePeriod.Week, HomePeriod.entries[2])
    }
}

class HomeEventTest {

    @Test
    fun changePeriod_createsEvent() {
        val event = HomeEvent.ChangePeriod(HomePeriod.Week)
        assertEquals(HomePeriod.Week, event.period)
    }

    @Test
    fun changePeriod_today() {
        val event = HomeEvent.ChangePeriod(HomePeriod.Today)
        assertEquals(HomePeriod.Today, event.period)
    }

    @Test
    fun changePeriod_dataClassEquality() {
        val event1 = HomeEvent.ChangePeriod(HomePeriod.Week)
        val event2 = HomeEvent.ChangePeriod(HomePeriod.Week)

        assertEquals(event1, event2)
    }

    @Test
    fun changePeriod_dataClassToString() {
        val event = HomeEvent.ChangePeriod(HomePeriod.Today)
        val string = event.toString()

        assertTrue(string.contains("ChangePeriod"))
        assertTrue(string.contains("period="))
    }

    @Test
    fun changePeriod_differentPeriods() {
        val today = HomeEvent.ChangePeriod(HomePeriod.Today)
        val week = HomeEvent.ChangePeriod(HomePeriod.Week)

        assertNotEquals(today, week)
    }
}
