package com.andrew.foxcontrol.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.domain.model.UsageStats
import com.andrew.foxcontrol.domain.model.WeeklyUsageStats
import com.andrew.foxcontrol.domain.repository.UsageStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val usageStatsRepository: UsageStatsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    init {
        loadStatistics()
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.ChangePeriod -> {
                _state.update { it.copy(period = event.period) }
                loadStatistics()
            }
        }
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            try {
                val period = _state.value.period
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                if (period == HomePeriod.Today) {
                    val dailyStats = usageStatsRepository.getDailyUsage(today)
                    _state.update {
                        it.copy(
                            dailyStats = dailyStats,
                            weeklyStats = null,
                            isLoading = false
                        )
                    }
                } else {
                    val calendar = Calendar.getInstance()
                    val endDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                    calendar.add(Calendar.DAY_OF_YEAR, -6)
                    val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

                    val weeklyStats = usageStatsRepository.getWeeklyUsage(startDate, endDate)
                    _state.update {
                        it.copy(
                            dailyStats = null,
                            weeklyStats = weeklyStats,
                            isLoading = false
                        )
                    }
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
}

@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val period: HomePeriod = HomePeriod.Today,
    val dailyStats: DailyUsageStats? = null,
    val weeklyStats: WeeklyUsageStats? = null,
    val error: String? = null
)

enum class HomePeriod(val label: String) {
    Today("Сегодня"),
    Week("Неделя")
}

sealed class HomeEvent {
    data class ChangePeriod(val period: HomePeriod) : HomeEvent()
}
