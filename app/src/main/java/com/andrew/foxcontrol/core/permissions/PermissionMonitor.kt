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
        val missingCount: Int = 0
    )

    interface Callback {
        fun onPermissionsChanged(status: PermissionStatus)
    }

    private var callback: Callback? = null
    private var lastStatus: PermissionStatus? = null

    fun setCallback(callback: Callback?) {
        this.callback = callback
    }

    /**
     * Check current permissions and notify callback if changed.
     */
    fun checkPermissions(): PermissionStatus {
        val status = getCurrentStatus()
        if (status != lastStatus) {
            Log.d(TAG, "Permissions changed: $status")
            lastStatus = status
            callback?.onPermissionsChanged(status)
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
            missingCount = PermissionHelper.getMissingPermissionCount(permissions)
        )
        return status
    }

    /**
     * Check if critical permissions (usage stats + overlay) are still granted.
     */
    fun areCriticalPermissionsGranted(): Boolean {
        return PermissionHelper.areCriticalPermissionsGranted(context)
    }

    /**
     * Check if all permissions for full functionality are granted.
     */
    fun areAllPermissionsGranted(): Boolean {
        return PermissionHelper.areAllPermissionsGranted(context)
    }
}
