package com.andrew.foxcontrol.core.tracking

import android.content.Context
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

object TrackingLogStorage {

    private const val TAG = "FoxControlDebug"
    private const val LOG_FILE_NAME = "foxcontrol_tracking.log"
    private const val MAX_LOG_LINES = 500

    private val logBuffer = mutableListOf<String>()
    private lateinit var logFile: java.io.File
    private var contextRef: Context? = null

    fun init(context: Context) {
        try {
            logFile = java.io.File(context.filesDir, LOG_FILE_NAME)
            contextRef = context
            // Try to load existing log
            if (logFile.exists()) {
                try {
                    logFile.readText().lines().filter { it.isNotBlank() }.forEach { logBuffer.add(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load log file", e)
                }
            }
            Log.d(TAG, "TrackingLogStorage initialized, ${logBuffer.size} existing entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TrackingLogStorage", e)
        }
    }

    fun add(tag: String, message: String) {
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = "[$timestamp] [$tag] $message"
            
            // Add to in-memory buffer (always works)
            logBuffer.add(0, line)
            if (logBuffer.size > MAX_LOG_LINES) {
                logBuffer.removeAt(logBuffer.size - 1)
            }

            // Try to persist to file
            if (::logFile.isInitialized) {
                val existing = if (logFile.exists()) logFile.readText() else ""
                val newContent = line + "\n" + existing
                val allLines = newContent.lines().filter { it.isNotBlank() }
                val truncated = if (allLines.size > MAX_LOG_LINES) allLines.subList(0, MAX_LOG_LINES) else allLines
                logFile.writeText(truncated.joinToString("\n"))
            }
        } catch (e: Exception) {
            // Log to system logcat for debugging
            Log.e(TAG, "TrackingLogStorage.add failed: ${e.message}")
        }
    }

    fun getAllLogs(): String {
        if (logBuffer.isEmpty()) {
            // Try to read from file as fallback
            if (::logFile.isInitialized && logFile.exists()) {
                try {
                    return logFile.readText()
                } catch (e: Exception) {
                    // ignore
                }
            }
            return "Нет записей лога\n\nПричина: логгер инициализирован, но ни одного события не произошло.\nЭто означает, что сервис не запущен или упал при запуске."
        }
        return logBuffer.joinToString("\n")
    }

    fun getLogStats(): String {
        if (logBuffer.isEmpty()) {
            return "Всего записей: 0\nТеги: (пусто)\n\nКомпоненты:\n  Service: ✗ не запущен\n  TrackingJob: ✗ не запущен\n  Repository: ✗ не вызывался\n  Permission: ✗ не проверяется\n  UsageStats: ✗ не опрашивается\n\n⚠ Лог пуст — сервис не запускался или упал при старте.\nПроверьте вкладку 'Анализ' для рекомендаций."
        }

        val lines = logBuffer.filter { it.isNotBlank() }
        val tags = lines.map { line ->
            val start = line.indexOf('[') + 1
            val end = line.indexOf(']', start)
            if (end > start) line.substring(start, end) else "unknown"
        }

        val tagCounts = tags.groupingBy { it }.eachCount().toSortedMap()

        val sb = StringBuilder()
        sb.append("Всего записей: ${lines.size}\n")
        sb.append("Теги:\n")
        for ((tag, count) in tagCounts) {
            sb.append("  $tag: $count\n")
        }

        // Check if we have any entries from each critical component
        val hasService = tags.contains("Service")
        val hasJob = tags.contains("Job")
        val hasRepo = tags.contains("Repo")
        val hasPermission = tags.contains("Permission")
        val hasUsageStats = tags.contains("UsageStats")

        sb.append("\nКомпоненты:\n")
        sb.append("  Service: ${if (hasService) "✓ работает" else "✗ не запущен"}\n")
        sb.append("  TrackingJob: ${if (hasJob) "✓ работает" else "✗ не запущен"}\n")
        sb.append("  Repository: ${if (hasRepo) "✓ работает" else "✗ не вызывался"}\n")
        sb.append("  Permission: ${if (hasPermission) "✓ проверяется" else "✗ не проверяется"}\n")
        sb.append("  UsageStats: ${if (hasUsageStats) "✓ опрашивается" else "✗ не опрашивается"}\n")

        return sb.toString()
    }

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
