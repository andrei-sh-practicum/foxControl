package com.andrew.foxcontrol.core.permissions

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object PermissionHelper {

    /**
     * Check if PACKAGE_USAGE_STATS permission is granted.
     * This permission is not a runtime permission - user must enable it manually.
     */
    fun isUsageStatsPermissionGranted(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("Deprecation")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Check if SYSTEM_ALERT_WINDOW (Overlay) permission is granted.
     */
    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun openOverlayPermissionSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Check if POST_NOTIFICATIONS permission is granted (API 33+).
     */
    fun isNotificationsPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }
    }

    /**
     * App notification settings — fallback when POST_NOTIFICATIONS was denied
     * permanently and the runtime dialog is no longer shown.
     */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * Check if battery optimization is disabled for our app.
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = context.packageName
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = android.net.Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /**
     * All permissions that need to be checked for full functionality.
     */
    fun getRequiredPermissions(context: Context): OnboardingPermissions {
        return OnboardingPermissions(
            usageStats = isUsageStatsPermissionGranted(context),
            overlay = isOverlayPermissionGranted(context),
            notifications = isNotificationsPermissionGranted(context),
            batteryOptimization = isBatteryOptimizationDisabled(context)
        )
    }

    /**
     * Count how many permissions are still needed.
     */
    fun getMissingPermissionCount(permissions: OnboardingPermissions): Int {
        var count = 0
        if (!permissions.usageStats) count++
        if (!permissions.overlay) count++
        if (!permissions.notifications) count++
        if (!permissions.batteryOptimization) count++
        return count
    }

    /**
     * Check if critical permissions (usage stats + overlay) are still granted.
     */
    fun areCriticalPermissionsGranted(context: Context): Boolean {
        return criticalGranted(
            usageStats = isUsageStatsPermissionGranted(context),
            overlay = isOverlayPermissionGranted(context)
        )
    }

    /**
     * Single definition of "critical": without usage stats nothing is tracked,
     * without overlay limit alerts can't be shown. Notifications and battery
     * optimization are recommended but not required.
     */
    fun criticalGranted(usageStats: Boolean, overlay: Boolean): Boolean =
        usageStats && overlay
}

data class OnboardingPermissions(
    val usageStats: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val batteryOptimization: Boolean
) {
    val allGranted: Boolean
        get() = usageStats && overlay && notifications && batteryOptimization

    val criticalGranted: Boolean
        get() = PermissionHelper.criticalGranted(usageStats, overlay)
}
