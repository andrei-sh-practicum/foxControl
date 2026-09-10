package com.andrew.foxcontrol.core.alerts

import org.junit.Assert.*
import org.junit.Test

class AlertManagerLogicTest {

    @Test
    fun cooldownLogic_cooldownActive() {
        // Simulate cooldown: 60 seconds
        val cooldownMs = 60_000L
        val lastAlertTime = System.currentTimeMillis()
        val now = lastAlertTime + 30_000L // 30 seconds later

        val shouldShow = (now - lastAlertTime) > cooldownMs
        assertFalse("Cooldown should prevent alert", shouldShow)
    }

    @Test
    fun cooldownLogic_cooldownExpired() {
        val cooldownMs = 60_000L
        val lastAlertTime = System.currentTimeMillis()
        val now = lastAlertTime + 120_000L // 2 minutes later

        val shouldShow = (now - lastAlertTime) > cooldownMs
        assertTrue("Alert should show after cooldown", shouldShow)
    }

    @Test
    fun cooldownLogic_differentPackage() {
        // Different package should always show (regardless of cooldown)
        val cooldownMs = 60_000L
        val lastAlertTime = System.currentTimeMillis()
        val now = lastAlertTime + 30_000L
        val lastAlertPackage = "com.old.app"
        val currentPackage = "com.new.app"

        val shouldShow = currentPackage != lastAlertPackage ||
                         (now - lastAlertTime) > cooldownMs
        assertTrue("Different package should show alert", shouldShow)
    }

    @Test
    fun cooldownLogic_samePackageWithinCooldown() {
        val cooldownMs = 60_000L
        val lastAlertTime = System.currentTimeMillis()
        val now = lastAlertTime + 30_000L
        val lastAlertPackage = "com.same.app"
        val currentPackage = "com.same.app"

        val shouldShow = currentPackage != lastAlertPackage ||
                         (now - lastAlertTime) > cooldownMs
        assertFalse("Same package within cooldown should not show", shouldShow)
    }

    @Test
    fun limitCalculation_withinLimit() {
        val usedMinutes = 30
        val limitMinutes = 60
        val exceeded = usedMinutes >= limitMinutes
        assertFalse("Should not exceed limit", exceeded)
    }

    @Test
    fun limitCalculation_atLimit() {
        val usedMinutes = 60
        val limitMinutes = 60
        val exceeded = usedMinutes >= limitMinutes
        assertTrue("Should exceed limit at exact value", exceeded)
    }

    @Test
    fun limitCalculation_exceeded() {
        val usedMinutes = 90
        val limitMinutes = 60
        val exceeded = usedMinutes >= limitMinutes
        assertTrue("Should exceed limit", exceeded)
    }

    @Test
    fun limitCalculation_zeroLimit() {
        val usedMinutes = 0
        val limitMinutes = 0
        val exceeded = usedMinutes >= limitMinutes
        assertTrue("Zero usage with zero limit should exceed", exceeded)
    }

    @Test
    fun percentageCalculation() {
        val used = 30
        val limit = 60
        val percentage = (used * 100) / limit
        assertEquals(50, percentage)
    }

    @Test
    fun percentageCalculation_overLimit() {
        val used = 120
        val limit = 60
        val percentage = (used * 100) / limit
        assertEquals(200, percentage)
    }
}
