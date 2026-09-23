package com.andrew.foxcontrol.core.util

import android.content.pm.PackageManager
import android.util.Log

/**
 * Resolves the user-visible app name (launcher label) for a package.
 * Falls back to the package name when the app is not installed or has no label.
 */
object AppLabelResolver {

    private const val TAG = "AppLabelResolver"

    fun resolve(packageManager: PackageManager, packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val label = packageManager.getApplicationLabel(appInfo)
            if (label.isNotEmpty()) label.toString() else packageName
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app name for $packageName: ${e.message}")
            packageName
        }
    }
}
