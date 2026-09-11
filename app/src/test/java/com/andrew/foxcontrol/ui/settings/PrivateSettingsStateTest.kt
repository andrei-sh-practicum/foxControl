package com.andrew.foxcontrol.ui.settings

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for PrivateSettingsState data class.
 */
class PrivateSettingsStateTest {

    @Test
    fun initialState_defaultValues() {
        val state = PrivateSettingsState()

        assertTrue(state.isLoading)
        assertFalse(state.isAuthenticated)
        assertNull(state.passwordHash)
        assertEquals(120, state.globalDailyLimitMinutes)
        assertTrue(state.appLimits.isEmpty())
        assertNull(state.error)
    }

    @Test
    fun copyUpdatesIsLoading() {
        val initial = PrivateSettingsState()
        val updated = initial.copy(isLoading = false)

        assertFalse(updated.isLoading)
        assertTrue(initial.isLoading)
    }

    @Test
    fun copyUpdatesIsAuthenticated() {
        val initial = PrivateSettingsState()
        val updated = initial.copy(isAuthenticated = true)

        assertTrue(updated.isAuthenticated)
    }

    @Test
    fun copyUpdatesPasswordHash() {
        val initial = PrivateSettingsState()
        val updated = initial.copy(passwordHash = "abc123hash")

        assertEquals("abc123hash", updated.passwordHash)
    }

    @Test
    fun copyUpdatesGlobalLimit() {
        val initial = PrivateSettingsState()
        val updated = initial.copy(globalDailyLimitMinutes = 60)

        assertEquals(60, updated.globalDailyLimitMinutes)
    }

    @Test
    fun copyUpdatesAppLimits() {
        val initial = PrivateSettingsState()
        val limits = mapOf("com.chrome" to 30, "com.youtube" to 60)
        val updated = initial.copy(appLimits = limits)

        assertEquals(2, updated.appLimits.size)
        assertEquals(30, updated.appLimits["com.chrome"])
        assertEquals(60, updated.appLimits["com.youtube"])
    }

    @Test
    fun copyUpdatesError() {
        val initial = PrivateSettingsState()
        val updated = initial.copy(error = "Something went wrong")

        assertEquals("Something went wrong", updated.error)
    }

    @Test
    fun copyMultipleFields() {
        val initial = PrivateSettingsState()
        val updated = initial.copy(
            isLoading = false,
            isAuthenticated = true,
            passwordHash = "hash123",
            globalDailyLimitMinutes = 90,
            appLimits = mapOf("com.app" to 30),
            error = null
        )

        assertFalse(updated.isLoading)
        assertTrue(updated.isAuthenticated)
        assertEquals("hash123", updated.passwordHash)
        assertEquals(90, updated.globalDailyLimitMinutes)
        assertEquals(1, updated.appLimits.size)
        assertNull(updated.error)
    }

    @Test
    fun dataClassEquality() {
        val state1 = PrivateSettingsState(
            isLoading = false,
            isAuthenticated = true,
            passwordHash = "abc",
            globalDailyLimitMinutes = 60,
            appLimits = emptyMap(),
            error = null
        )
        val state2 = PrivateSettingsState(
            isLoading = false,
            isAuthenticated = true,
            passwordHash = "abc",
            globalDailyLimitMinutes = 60,
            appLimits = emptyMap(),
            error = null
        )

        assertEquals(state1, state2)
    }

    @Test
    fun dataClassInequality() {
        val state1 = PrivateSettingsState(globalDailyLimitMinutes = 60)
        val state2 = PrivateSettingsState(globalDailyLimitMinutes = 120)

        assertNotEquals(state1, state2)
    }

    @Test
    fun dataClassCopyImmutability() {
        val original = PrivateSettingsState()
        val modified = original.copy(globalDailyLimitMinutes = 999)

        assertEquals(120, original.globalDailyLimitMinutes)
        assertEquals(999, modified.globalDailyLimitMinutes)
    }

    @Test
    fun dataClassToString() {
        val state = PrivateSettingsState()
        val string = state.toString()

        assertTrue(string.contains("PrivateSettingsState"))
        assertTrue(string.contains("isLoading="))
    }

    @Test
    fun dataClassCopy() {
        val state = PrivateSettingsState()
        val copy = state.copy()

        assertEquals(state, copy)
    }

    @Test
    fun dataClassHashCode() {
        val state1 = PrivateSettingsState(globalDailyLimitMinutes = 60)
        val state2 = PrivateSettingsState(globalDailyLimitMinutes = 60)

        assertEquals(state1.hashCode(), state2.hashCode())
    }

    @Test
    fun appLimits_emptyByDefault() {
        val state = PrivateSettingsState()
        assertTrue(state.appLimits.isEmpty())
    }

    @Test
    fun appLimits_canBePopulated() {
        val state = PrivateSettingsState(
            appLimits = mapOf(
                "com.android.chrome" to 30,
                "com.youtube" to 60,
                "com.telegram" to 120
            )
        )

        assertEquals(3, state.appLimits.size)
        assertEquals(30, state.appLimits["com.android.chrome"])
        assertEquals(60, state.appLimits["com.youtube"])
        assertEquals(120, state.appLimits["com.telegram"])
    }

    @Test
    fun globalLimit_defaultIsTwoHours() {
        assertEquals(120, PrivateSettingsState().globalDailyLimitMinutes)
    }

    @Test
    fun globalLimit_canBeCustomized() {
        val state = PrivateSettingsState(globalDailyLimitMinutes = 30)
        assertEquals(30, state.globalDailyLimitMinutes)
    }

    @Test
    fun error_nullByDefault() {
        assertNull(PrivateSettingsState().error)
    }

    @Test
    fun error_canBeSet() {
        val state = PrivateSettingsState(error = "Connection failed")
        assertEquals("Connection failed", state.error)
    }

    @Test
    fun isAuthenticated_falseByDefault() {
        assertFalse(PrivateSettingsState().isAuthenticated)
    }

    @Test
    fun passwordHash_nullByDefault() {
        assertNull(PrivateSettingsState().passwordHash)
    }
}
