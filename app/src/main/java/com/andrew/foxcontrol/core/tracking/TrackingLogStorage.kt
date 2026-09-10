package com.andrew.foxcontrol.core.tracking

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

object TrackingLogStorage {

    private const val TAG = "FoxControlDebug"
    private const val LOG_FILE_NAME = "foxcontrol_tracking.log"
    private const val MAX_LOG_LINES = 1000

    // In-memory buffer for fast UI access (filled from file on init)
    private val logBuffer = mutableListOf<String>()
    private var logFile: java.io.File? = null
    private var contextRef: Context? = null
    private var initialized = false

    fun init(context: Context) {
        try {
            val file = java.io.File(context.filesDir, LOG_FILE_NAME)
            logFile = file
            contextRef = context
            initialized = true

            // Load existing log into buffer
            if (file.exists()) {
                try {
                    file.readLines().filter { it.isNotBlank() }.take(MAX_LOG_LINES).forEach { logBuffer.add(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load log file", e)
                }
            }
            Log.d(TAG, "TrackingLogStorage initialized, ${logBuffer.size} existing entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TrackingLogStorage", e)
        }
    }

    /**
     * Add a log entry. Auto-initializes file if not yet initialized.
     * This ensures logs from Worker processes (separate JVM) are persisted.
     */
    fun add(tag: String, message: String) {
        try {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = "[$timestamp] [$tag] $message"

            // Auto-initialize file if needed (for Worker processes)
            if (!initialized) {
                try {
                    val context = contextRef ?: return // Can't initialize without context
                    logFile = java.io.File(context.filesDir, LOG_FILE_NAME)
                    initialized = true
                    // Load existing lines
                    if (logFile!!.exists()) {
                        logFile!!.readLines().filter { it.isNotBlank() }.take(MAX_LOG_LINES).forEach { logBuffer.add(it) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Auto-init failed: ${e.message}")
                    return
                }
            }

            // Add to in-memory buffer
            logBuffer.add(0, line)
            if (logBuffer.size > MAX_LOG_LINES) {
                logBuffer.removeAt(logBuffer.size - 1)
            }

            // Append to file (append is atomic and fast)
            logFile?.let { file ->
                try {
                    RandomAccessFile(file, "rw").use { raf ->
                        raf.seek(raf.length())
                        raf.writeBytes(line + "\n")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to write log file: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TrackingLogStorage.add failed: ${e.message}")
        }
    }

    /**
     * Get all logs — always reads from file to ensure consistency
     * across processes (e.g., Worker processes).
     */
    fun getAllLogs(): String {
        // Always try to read from file first (authoritative source)
        if (logFile?.exists() == true) {
            try {
                val lines = logFile!!.readLines().filter { it.isNotBlank() }
                if (lines.isNotEmpty()) {
                    // Take last MAX_LOG_LINES to handle overflow
                    val recent = if (lines.size > MAX_LOG_LINES) lines.subList(lines.size - MAX_LOG_LINES, lines.size) else lines
                    return recent.joinToString("\n")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read log file: ${e.message}")
            }
        }

        // Fallback to in-memory buffer
        if (logBuffer.isEmpty()) {
            return "Нет записей лога\n\nПричина: логгер инициализирован, но ни одного события не произошло.\nЭто означает, что сервис не запущен или упал при запуске."
        }
        return logBuffer.joinToString("\n")
    }

    fun getLogStats(): String {
        val lines = getAllLogs().split("\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return "Всего записей: 0\nТеги: (пусто)\n\nКомпоненты:\n  Service: ✗ не запущен\n  TrackingJob: ✗ не запущен\n  Repository: ✗ не вызывался\n  Permission: ✗ не проверяется\n  UsageStats: ✗ не опрашивается\n\n⚠ Лог пуст — сервис не запускался или упал при старте.\nПроверьте вкладку 'Анализ' для рекомендаций."
        }

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
        val hasEmailScheduler = tags.contains("EmailScheduler")

        sb.append("\nКомпоненты:\n")
        sb.append("  Service: ${if (hasService) "✓ работает" else "✗ не запущен"}\n")
        sb.append("  TrackingJob: ${if (hasJob) "✓ работает" else "✗ не запущен"}\n")
        sb.append("  Repository: ${if (hasRepo) "✓ работает" else "✗ не вызывался"}\n")
        sb.append("  Permission: ${if (hasPermission) "✓ проверяется" else "✗ не проверяется"}\n")
        sb.append("  UsageStats: ${if (hasUsageStats) "✓ опрашивается" else "✗ не опрашивается"}\n")
        sb.append("  EmailScheduler: ${if (hasEmailScheduler) "✓ работает" else "✗ не вызывался"}\n")

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
