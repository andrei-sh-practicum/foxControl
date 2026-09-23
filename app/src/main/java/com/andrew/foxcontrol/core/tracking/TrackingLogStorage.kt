package com.andrew.foxcontrol.core.tracking

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

object TrackingLogStorage {

    private const val TAG = "FoxControlDebug"
    private const val LOG_FILE_NAME = "foxcontrol_tracking.log"
    const val MAX_LOG_LINES = 1000

    // Upper bound of bytes read from the end of the file — protects from OOM on a huge log
    private const val MAX_TAIL_BYTES = 1024 * 1024L
    private const val TAIL_CHUNK_BYTES = 16 * 1024

    // In-memory buffer (oldest first, same order as the file) — fallback when the file is unreadable
    private val logBuffer = kotlin.collections.ArrayDeque<String>()
    private var logFile: File? = null
    @Volatile
    private var initialized = false

    fun init(context: Context) {
        synchronized(this) {
            initFile(context)
        }
    }

    /**
     * Add a log entry without context — uses already initialized file.
     * Call from UI thread where TrackingLogStorage.init() was called.
     */
    fun add(tag: String, message: String) {
        if (!initialized) {
            Log.w(TAG, "TrackingLogStorage.add called before init — log will not be persisted")
            return
        }
        addInternal(tag, message)
    }

    /**
     * Opens the log file, trims it to the last [MAX_LOG_LINES] lines and loads them into the buffer.
     * Only the tail of the file is read, so an oversized log can't cause OutOfMemoryError.
     */
    private fun initFile(context: Context) {
        if (initialized) return
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            logFile = file
            initialized = true

            if (!file.exists()) {
                Log.d(TAG, "Log file does not exist yet: ${file.absolutePath}")
                return
            }

            val sizeBefore = file.length()
            val lines = readTailLines(file, MAX_LOG_LINES)
            rewriteFile(file, lines)
            logBuffer.clear()
            logBuffer.addAll(lines)
            Log.d(TAG, "Log file trimmed: $sizeBefore -> ${file.length()} bytes, ${lines.size} lines kept")
        } catch (t: Throwable) {
            // Throwable, not Exception: must never crash app start (e.g. OutOfMemoryError)
            Log.e(TAG, "Failed to initialize TrackingLogStorage: ${t.message}", t)
            try {
                logFile?.delete()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Reads the last [maxLines] non-blank lines of [file], scanning backwards from the end
     * and reading at most [MAX_TAIL_BYTES].
     */
    private fun readTailLines(file: File, maxLines: Int): List<String> {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            if (length == 0L) return emptyList()

            val minPos = maxOf(0L, length - MAX_TAIL_BYTES)
            var pos = length
            var newlines = 0
            val buf = ByteArray(TAIL_CHUNK_BYTES)

            // Move pos backwards until we've seen enough line breaks or hit the limit
            while (pos > minPos && newlines <= maxLines) {
                val readSize = minOf(TAIL_CHUNK_BYTES.toLong(), pos - minPos).toInt()
                pos -= readSize
                raf.seek(pos)
                raf.readFully(buf, 0, readSize)
                for (i in 0 until readSize) {
                    if (buf[i] == '\n'.code.toByte()) newlines++
                }
            }

            val bytes = ByteArray((length - pos).toInt())
            raf.seek(pos)
            raf.readFully(bytes)

            var lines = String(bytes, Charsets.UTF_8).split('\n')
            // First line is likely cut in the middle if we didn't start at the beginning of the file
            if (pos > 0 && lines.isNotEmpty()) lines = lines.drop(1)
            return lines.filter { it.isNotBlank() }.takeLast(maxLines)
        }
    }

    private fun rewriteFile(file: File, lines: List<String>) {
        val tmp = File(file.parentFile, "$LOG_FILE_NAME.tmp")
        tmp.writeText(if (lines.isEmpty()) "" else lines.joinToString("\n", postfix = "\n"))
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) {
                Log.e(TAG, "Failed to replace log file with trimmed version")
                tmp.delete()
            }
        }
    }

    private fun addInternal(tag: String, message: String) {
        val file = logFile ?: run {
            Log.w(TAG, "addInternal called but logFile is null")
            return
        }

        try {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            val line = "[$timestamp] [$tag] $message"

            synchronized(this) {
                logBuffer.addLast(line)
                while (logBuffer.size > MAX_LOG_LINES) {
                    logBuffer.removeFirst()
                }

                try {
                    file.appendText(line + "\n")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to append to log file '${file.absolutePath}': ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TrackingLogStorage.addInternal failed: ${e.message}", e)
        }
    }

    /**
     * Get the last [MAX_LOG_LINES] log lines — reads only the tail of the file
     * to stay consistent across processes (e.g., Worker processes).
     */
    fun getAllLogs(): String {
        val file = logFile

        if (file?.exists() == true) {
            try {
                val lines = readTailLines(file, MAX_LOG_LINES)
                if (lines.isNotEmpty()) {
                    return lines.joinToString("\n")
                } else {
                    Log.d(TAG, "Log file is empty: ${file.absolutePath}")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to read log file: ${t.message}", t)
            }
        } else {
            Log.d(TAG, "Log file does not exist or is not readable: ${file?.absolutePath}")
        }

        // Fallback to in-memory buffer
        val snapshot = synchronized(this) { logBuffer.toList() }
        if (snapshot.isEmpty()) {
            return "Нет записей лога\n\nПричина: логгер инициализирован, но ни одного события не произошло.\nЭто означает, что сервис не запущен или упал при запуске."
        }
        return snapshot.joinToString("\n")
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
        val hasEmailReport = tags.contains("EmailReport")

        sb.append("\nКомпоненты:\n")
        sb.append("  Service: ${if (hasService) "✓ работает" else "✗ не запущен"}\n")
        sb.append("  TrackingJob: ${if (hasJob) "✓ работает" else "✗ не запущен"}\n")
        sb.append("  Repository: ${if (hasRepo) "✓ работает" else "✗ не вызывался"}\n")
        sb.append("  Permission: ${if (hasPermission) "✓ проверяется" else "✗ не проверяется"}\n")
        sb.append("  UsageStats: ${if (hasUsageStats) "✓ опрашивается" else "✗ не опрашивается"}\n")
        sb.append("  EmailReport: ${if (hasEmailReport) "✓ работает" else "✗ не вызывался"}\n")

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
