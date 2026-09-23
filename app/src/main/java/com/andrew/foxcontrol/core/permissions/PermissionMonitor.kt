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

    /** Snapshot of the required permissions; everything is derived from [permissions]. */
    data class PermissionStatus(val permissions: OnboardingPermissions) {
        val usageStats: Boolean get() = permissions.usageStats
        val overlay: Boolean get() = permissions.overlay
        val notifications: Boolean get() = permissions.notifications
        val batteryOptimization: Boolean get() = permissions.batteryOptimization
        val allGranted: Boolean get() = permissions.allGranted
        val criticalGranted: Boolean get() = permissions.criticalGranted
        val missingCount: Int get() = PermissionHelper.getMissingPermissionCount(permissions)
    }

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

    fun getCurrentStatus(): PermissionStatus =
        PermissionStatus(PermissionHelper.getRequiredPermissions(context))
}
