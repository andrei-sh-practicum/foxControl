package com.andrew.foxcontrol.core.permissions

import android.content.Context
import android.os.Build
import android.util.Log

/**
 * Human-readable permission report for the Debug screen and the tracking log.
 * Moved as is from TrackingLogStorage: note that its checks differ from [PermissionHelper]
 * (e.g. the Settings.Secure "usage_stats_accessed" shortcut) — kept unchanged on purpose,
 * the output is diagnostics only.
 */
object PermissionDiagnostics {

    private const val TAG = "FoxControlDebug"

    fun getPermissionInfo(context: Context): String {
        val sb = StringBuilder()
        val pm = context.packageManager

        // PACKAGE_USAGE_STATS — use Settings.Secure for most reliable check
        val usageAccess = isUsageStatsPermissionGranted(context)
        sb.append("PACKAGE_USAGE_STATS: ${if (usageAccess) "✓ ДА" else "✗ НЕТ (нужно вручную включить)"}\n")

        // SYSTEM_ALERT_WINDOW
        val canDraw = android.provider.Settings.canDrawOverlays(context)
        sb.append("SYSTEM_ALERT_WINDOW: ${if (canDraw) "✓ ДА" else "✗ НЕТ"}\n")

        // Foreground service permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val fgPermission = pm.checkPermission(
                android.Manifest.permission.FOREGROUND_SERVICE,
                context.packageName
            )
            sb.append("FOREGROUND_SERVICE: ${if (fgPermission == android.content.pm.PackageManager.PERMISSION_GRANTED) "✓ ДА" else "✗ НЕТ"}\n")
        }

        // Boot completed
        val hasBootPermission = pm.checkPermission(
            android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
            context.packageName
        )
        sb.append("RECEIVE_BOOT_COMPLETED: ${if (hasBootPermission == android.content.pm.PackageManager.PERMISSION_GRANTED) "✓ ДА" else "✗ НЕТ"}\n")

        // Notification permission (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifPermission = pm.checkPermission(
                android.Manifest.permission.POST_NOTIFICATIONS,
                context.packageName
            )
            sb.append("POST_NOTIFICATIONS: ${if (notifPermission == android.content.pm.PackageManager.PERMISSION_GRANTED) "✓ ДА" else "✗ НЕТ"}\n")
        }

        // Battery optimization — multiple attempts for compatibility
        val pkgName = context.packageName
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val batteryOpNames = listOf(
            "android:ignore_battery_optimize",
            "OPSTR_IGNORE_BATTERY_OPTIMIZATIONS",
            "OP_RUN_IN_BACKGROUND"
        )
        var batteryMode = -1
        for (opName in batteryOpNames) {
            try {
                batteryMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOps.unsafeCheckOpNoThrow(opName, android.os.Process.myUid(), pkgName)
                } else {
                    @Suppress("DEPRECATION")
                    appOps.checkOpNoThrow(opName, android.os.Process.myUid(), pkgName)
                }
                if (batteryMode != android.app.AppOpsManager.MODE_ERRORED) break
            } catch (e: Exception) {
                // Try next op name
            }
        }
        val batteryStatus = when {
            batteryMode == android.app.AppOpsManager.MODE_IGNORED -> "✓ ИГНОРИРУЕТСЯ"
            batteryMode == android.app.AppOpsManager.MODE_ERRORED -> "⚠ ОПЦИЯ НЕРАСПОЗНАНА (ROM)"
            else -> "⚠ АКТИВИРОВАНА (может убивать сервис)"
        }
        sb.append("Battery optimization: $batteryStatus\n")

        // Android version
        sb.append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")

        return sb.toString()
    }

    private fun isUsageStatsPermissionGranted(context: Context): Boolean {
        return try {
            // Primary: check via Settings.Secure (most reliable on all ROMs)
            val secureValue = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "usage_stats_accessed"
            )
            if (secureValue != null) return true

            // Secondary: check via AppOpsManager
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "isUsageStatsPermissionGranted failed", e)
            false
        }
    }
}
