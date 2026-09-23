package com.andrew.foxcontrol.core.permissions

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val TAG = "PermissionMonitor"
    }

    data class PermissionStatus(
        val usageStats: Boolean = true,
        val overlay: Boolean = true,
        val notifications: Boolean = true,
        val batteryOptimization: Boolean = true,
        val allGranted: Boolean = true,
        val criticalGranted: Boolean = true,
        val missingCount: Int = 0
    )

    private var lastStatus: PermissionStatus? = null

    /**
     * Check current permissions and log if they changed since the previous check.
     */
    fun checkPermissions(): PermissionStatus {
        val status = getCurrentStatus()
        if (status != lastStatus) {
            Log.d(TAG, "Permissions changed: $status")
            lastStatus = status
        }
        return status
    }

    fun getCurrentStatus(): PermissionStatus {
        val permissions = PermissionHelper.getRequiredPermissions(context)
        val status = PermissionStatus(
            usageStats = permissions.usageStats,
            overlay = permissions.overlay,
            notifications = permissions.notifications,
            batteryOptimization = permissions.batteryOptimization,
            allGranted = permissions.allGranted,
            criticalGranted = permissions.criticalGranted,
            missingCount = PermissionHelper.getMissingPermissionCount(permissions)
        )
        return status
    }
}
