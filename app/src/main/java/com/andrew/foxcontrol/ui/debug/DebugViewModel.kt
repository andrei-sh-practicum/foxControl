package com.andrew.foxcontrol.ui.debug

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrew.foxcontrol.core.maintenance.DataCleanupManager
import com.andrew.foxcontrol.core.permissions.PermissionDiagnostics
import com.andrew.foxcontrol.core.tracking.DowntimeHourBucket
import com.andrew.foxcontrol.core.tracking.TrackingLogStorage
import com.andrew.foxcontrol.domain.repository.UsageStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.andrew.foxcontrol.core.util.DateUtils
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: UsageStatsRepository,
    private val dataCleanupManager: DataCleanupManager
) : ViewModel() {

    private val _state = MutableStateFlow(DebugState())
    val state: StateFlow<DebugState> = _state

    fun loadDebugInfo() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val debugInfo = repository.getDebugInfo()
                
                // Load downtime buckets
                val today = DateUtils.today()
                val downtimeBuckets = try {
                    repository.getServiceDowntimeBuckets(today)
                } catch (e: Exception) {
                    emptyList()
                }
                
                val lastHeartbeatStr = debugInfo.lastHeartbeatTimestamp?.let { ts ->
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val timeStr = sdf.format(Date(ts))
                    val diffStr = formatAgo((System.currentTimeMillis() - ts) / 1000, includeDays = false)
                    "$timeStr ($diffStr)"
                } ?: "Нет данных"
                
                val dateRangeStr = debugInfo.dateRange?.let { dr ->
                    if (dr.minDate != null && dr.maxDate != null) {
                        "Первая запись: ${dr.minDate}\nПоследняя запись: ${dr.maxDate}"
                    } else {
                        "Нет записей в базе"
                    }
                } ?: "Нет данных"
                
                val recentSessionsStr = if (debugInfo.recentSessions.isNotEmpty()) {
                    debugInfo.recentSessions.take(10).joinToString("\n") { session ->
                        "${session.packageName}: ${session.durationMs}ms (${session.date})"
                    }
                } else {
                    "Нет сессий в базе"
                }
                
                _state.update {
                    it.copy(
                        isLoading = false,
                        sessionCount = debugInfo.sessionCount,
                        uniquePackageCount = debugInfo.uniquePackageCount,
                        heartbeatCount = debugInfo.heartbeatCount,
                        lastHeartbeatStr = lastHeartbeatStr,
                        dateRangeStr = dateRangeStr,
                        recentSessionsStr = recentSessionsStr,
                        downtimeBuckets = downtimeBuckets
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Ошибка загрузки: ${e.message}"
                    )
                }
            }
        }
    }

    /** Reads the log file on a background thread (up to ~1 MB) for the "Лог" tab. */
    fun loadLogs() {
        viewModelScope.launch {
            val logContent = withContext(Dispatchers.IO) { TrackingLogStorage.getAllLogs() }
            // Lines of the EmailReport tag, shown separately at the top of the tab
            val emailLogs = logContent.split("\n")
                .filter { it.isNotBlank() && it.contains("[EmailReport]") }
            _state.update { it.copy(logContent = logContent, emailLogs = emailLogs) }
        }
    }

    /** Collects the permission report on a background thread for the "Разрешения" tab. */
    fun loadPermissionInfo() {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { PermissionDiagnostics.getPermissionInfo(appContext) }
            _state.update { it.copy(permissionInfo = info) }
        }
    }

    fun triggerCleanup() {
        viewModelScope.launch {
            try {
                dataCleanupManager.purgeOldData()
                loadDebugInfo() // reload after cleanup
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Ошибка очистки: ${e.message}")
                }
            }
        }
    }

    fun getCleanupStatusText(): String {
        val lastTs = dataCleanupManager.getLastCleanupTimestamp()
        return if (lastTs != null) {
            val diffStr = formatAgo((System.currentTimeMillis() - lastTs) / 1000, includeDays = true)
            "Последняя очистка: $diffStr"
        } else {
            "Очистка ещё не проводилась"
        }
    }
}

/**
 * "N сек/мин/ч назад"; with [includeDays] durations of a day and more are shown as "N дн назад",
 * otherwise they stay in hours.
 */
internal fun formatAgo(diffSeconds: Long, includeDays: Boolean): String = when {
    diffSeconds < 60 -> "$diffSeconds сек назад"
    diffSeconds < 3600 -> "${diffSeconds / 60} мин назад"
    !includeDays || diffSeconds < 86400 -> "${diffSeconds / 3600} ч назад"
    else -> "${diffSeconds / 86400} дн назад"
}

data class DebugState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val sessionCount: Int = 0,
    val uniquePackageCount: Int = 0,
    val heartbeatCount: Int = 0,
    val lastHeartbeatStr: String = "",
    val dateRangeStr: String = "",
    val recentSessionsStr: String = "",
    val downtimeBuckets: List<DowntimeHourBucket> = emptyList(),
    val logContent: String = "",
    val emailLogs: List<String> = emptyList(),
    val permissionInfo: String = ""
)
