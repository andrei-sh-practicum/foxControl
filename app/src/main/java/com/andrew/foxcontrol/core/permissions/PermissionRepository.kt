package com.andrew.foxcontrol.core.permissions

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun checkPermissions(): OnboardingPermissions {
        return PermissionHelper.getRequiredPermissions(context)
    }

    fun openUsageStatsSettings() {
        PermissionHelper.openUsageStatsSettings(context)
    }

    fun openOverlayPermissionSettings() {
        PermissionHelper.openOverlayPermissionSettings(context)
    }

    fun openBatteryOptimizationSettings() {
        PermissionHelper.openBatteryOptimizationSettings(context)
    }

    fun isNotificationsPermissionGranted(): Boolean {
        return PermissionHelper.isNotificationsPermissionGranted(context)
    }
}
