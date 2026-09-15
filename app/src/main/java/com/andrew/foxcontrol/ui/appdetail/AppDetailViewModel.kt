package com.andrew.foxcontrol.ui.appdetail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrew.foxcontrol.core.tracking.AppUsageHourBucket
import com.andrew.foxcontrol.data.repository.UsageStatsRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val repository: UsageStatsRepositoryImpl
) : ViewModel() {

    private val _state = MutableStateFlow(AppDetailState())
    val state: StateFlow<AppDetailState> = _state

    fun loadAppDetail(packageName: String) {
        viewModelScope.launch {
            try {
                // Load tracked app for category
                val trackedApp = repository.getTrackedApp(packageName)
                val category = trackedApp?.category ?: ""

                // Load today's date
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                // Load today's sessions for summary
                val sessions = repository.getTodaySessionsForPackage(packageName, today)
                val totalUsageMs = sessions.sumOf { it.durationMs }
                val sessionCount = sessions.size

                // Load hourly usage buckets
                val hourlyUsage = repository.getHourlyUsageForPackage(packageName, today)

                _state.value = AppDetailState(
                    isLoading = false,
                    category = category,
                    todayUsageMs = totalUsageMs,
                    sessionCount = sessionCount,
                    hourlyUsage = hourlyUsage
                )
            } catch (e: Exception) {
                _state.value = AppDetailState(
                    isLoading = false,
                    category = "",
                    todayUsageMs = 0L,
                    sessionCount = 0,
                    hourlyUsage = emptyList()
                )
            }
        }
    }
}

@Immutable
data class AppDetailState(
    val isLoading: Boolean = true,
    val category: String = "",
    val todayUsageMs: Long = 0L,
    val sessionCount: Int = 0,
    val hourlyUsage: List<AppUsageHourBucket> = emptyList()
)
