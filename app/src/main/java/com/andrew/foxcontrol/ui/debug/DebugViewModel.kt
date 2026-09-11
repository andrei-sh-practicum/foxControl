package com.andrew.foxcontrol.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrew.foxcontrol.core.tracking.TrackingLogStorage
import com.andrew.foxcontrol.data.repository.DebugInfo
import com.andrew.foxcontrol.data.repository.UsageStatsRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val repository: UsageStatsRepositoryImpl
) : ViewModel() {

    private val _state = MutableStateFlow(DebugState())
    val state: StateFlow<DebugState> = _state

    fun loadDebugInfo() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val debugInfo = repository.getDebugInfo()
                
                val now = Date()
                val lastHeartbeatStr = debugInfo.lastHeartbeatTimestamp?.let { ts ->
                    val diff = (now.time - ts) / 1000
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val timeStr = sdf.format(Date(ts))
                    val diffStr = if (diff < 60) "$diff сек назад"
                    else if (diff < 3600) "${diff / 60} мин назад"
                    else "${diff / 3600} ч назад"
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
                        recentSessionsStr = recentSessionsStr
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

    fun getLogContent(): String {
        return TrackingLogStorage.getAllLogs()
    }

    fun getEmailSchedulerLogs(): List<String> {
        val allLines = TrackingLogStorage.getAllLogs()
            .split("\n")
            .filter { it.isNotBlank() }
        // Filter lines that contain "EmailReport" tag
        return allLines.filter { line ->
            line.contains("[EmailReport]")
        }
    }

    fun getLogStats(): String {
        return TrackingLogStorage.getLogStats()
    }

    fun getPermissionInfo(context: android.content.Context): String {
        return TrackingLogStorage.getPermissionInfo(context)
    }
}

data class DebugState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val sessionCount: Int = 0,
    val uniquePackageCount: Int = 0,
    val heartbeatCount: Int = 0,
    val lastHeartbeatStr: String = "",
    val dateRangeStr: String = "",
    val recentSessionsStr: String = ""
)
