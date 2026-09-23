package com.andrew.foxcontrol.core.permissions

import org.junit.Assert.*
import org.junit.Test

class OnboardingPermissionsTest {

    @Test
    fun allGranted_true() {
        val permissions = OnboardingPermissions(
            usageStats = true,
            overlay = true,
            notifications = true,
            batteryOptimization = true
        )
        assertTrue(permissions.allGranted)
    }

    @Test
    fun allGranted_false_whenUsageStatsMissing() {
        val permissions = OnboardingPermissions(
            usageStats = false,
            overlay = true,
            notifications = true,
            batteryOptimization = true
        )
        assertFalse(permissions.allGranted)
    }

    @Test
    fun allGranted_false_whenOverlayMissing() {
        val permissions = OnboardingPermissions(
            usageStats = true,
            overlay = false,
            notifications = true,
            batteryOptimization = true
        )
        assertFalse(permissions.allGranted)
    }

    @Test
    fun allGranted_false_whenNotificationsMissing() {
        val permissions = OnboardingPermissions(
            usageStats = true,
            overlay = true,
            notifications = false,
            batteryOptimization = true
        )
        assertFalse(permissions.allGranted)
    }

    @Test
    fun allGranted_false_whenBatteryMissing() {
        val permissions = OnboardingPermissions(
            usageStats = true,
            overlay = true,
            notifications = true,
            batteryOptimization = false
        )
        assertFalse(permissions.allGranted)
    }

    @Test
    fun allGranted_false_whenAllMissing() {
        val permissions = OnboardingPermissions(
            usageStats = false,
            overlay = false,
            notifications = false,
            batteryOptimization = false
        )
        assertFalse(permissions.allGranted)
    }

    @Test
    fun getMissingPermissionCount_allGranted() {
        val permissions = OnboardingPermissions(
            usageStats = true,
            overlay = true,
            notifications = true,
            batteryOptimization = true
        )
        assertEquals(0, getMissingPermissionCount(permissions))
    }

    @Test
    fun getMissingPermissionCount_someMissing() {
        val permissions = OnboardingPermissions(
            usageStats = false,
            overlay = true,
            notifications = false,
            batteryOptimization = true
        )
        assertEquals(2, getMissingPermissionCount(permissions))
    }

    @Test
    fun getMissingPermissionCount_allMissing() {
        val permissions = OnboardingPermissions(
            usageStats = false,
            overlay = false,
            notifications = false,
            batteryOptimization = false
        )
        assertEquals(4, getMissingPermissionCount(permissions))
    }

    @Test
    fun getMissingPermissionCount_oneMissing() {
        val permissions = OnboardingPermissions(
            usageStats = true,
            overlay = true,
            notifications = true,
            batteryOptimization = false
        )
        assertEquals(1, getMissingPermissionCount(permissions))
    }

    @Test
    fun criticalGranted_onlyUsageStatsAndOverlayMatter() {
        val permissions = OnboardingPermissions(
            usageStats = true,
            overlay = true,
            notifications = false,
            batteryOptimization = false
        )
        assertTrue(permissions.criticalGranted)
        assertFalse(permissions.allGranted)
    }

    @Test
    fun criticalGranted_false_whenUsageStatsMissing() {
        val permissions = OnboardingPermissions(
            usageStats = false,
            overlay = true,
            notifications = true,
            batteryOptimization = true
        )
        assertFalse(permissions.criticalGranted)
    }

    @Test
    fun criticalGranted_false_whenOverlayMissing() {
        val permissions = OnboardingPermissions(
            usageStats = true,
            overlay = false,
            notifications = true,
            batteryOptimization = true
        )
        assertFalse(permissions.criticalGranted)
    }

    private fun getMissingPermissionCount(permissions: OnboardingPermissions): Int =
        PermissionHelper.getMissingPermissionCount(permissions)
}
