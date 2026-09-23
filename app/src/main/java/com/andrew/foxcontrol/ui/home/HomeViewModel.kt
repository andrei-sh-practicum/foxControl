package com.andrew.foxcontrol.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrew.foxcontrol.core.tracking.AppUsageHourBucket
import com.andrew.foxcontrol.data.local.entity.AppLimitEntity
import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.domain.model.WeeklyUsageStats
import com.andrew.foxcontrol.domain.repository.UsageStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.andrew.foxcontrol.core.util.DateUtils
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
                val today = DateUtils.today()

                when (period) {
                    HomePeriod.Today -> {
                        val dailyStats = usageStatsRepository.getDailyUsage(today)
                        val appLimits = usageStatsRepository.getAppLimits()
                        val exceededApps = computeExceededApps(dailyStats, appLimits)
                        val hourlyUsageToday = usageStatsRepository.getHourlyUsageForAllApps(today)

                        _state.update {
                            it.copy(
                                dailyStats = dailyStats,
                                yesterdayStats = null,
                                weeklyStats = null,
                                exceededApps = exceededApps,
                                exceededAppsYesterday = emptyList(),
                                hourlyUsageToday = hourlyUsageToday,
                                hourlyUsageYesterday = emptyList(),
                                isLoading = false
                            )
                        }
                    }
                    HomePeriod.Yesterday -> {
                        val yesterday = DateUtils.daysAgo(1)

                        val yesterdayStats = usageStatsRepository.getDailyUsage(yesterday)
                        val appLimits = usageStatsRepository.getAppLimits()
                        val exceededAppsYesterday = computeExceededApps(yesterdayStats, appLimits)
                        val hourlyUsageYesterday = usageStatsRepository.getHourlyUsageForAllApps(yesterday)

                        _state.update {
                            it.copy(
                                yesterdayStats = yesterdayStats,
                                dailyStats = null,
                                weeklyStats = null,
                                exceededAppsYesterday = exceededAppsYesterday,
                                exceededApps = emptyList(),
                                hourlyUsageYesterday = hourlyUsageYesterday,
                                hourlyUsageToday = emptyList(),
                                isLoading = false
                            )
                        }
                    }
                    HomePeriod.Week -> {
                        val endDate = DateUtils.today()
                        val startDate = DateUtils.daysAgo(6)

                        val weeklyStats = usageStatsRepository.getWeeklyUsage(startDate, endDate)
                        _state.update {
                            it.copy(
                                dailyStats = null,
                                yesterdayStats = null,
                                weeklyStats = weeklyStats,
                                exceededApps = emptyList(),
                                exceededAppsYesterday = emptyList(),
                                hourlyUsageToday = emptyList(),
                                hourlyUsageYesterday = emptyList(),
                                isLoading = false
                            )
                        }
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

    private fun computeExceededApps(
        dailyStats: DailyUsageStats,
        appLimits: List<AppLimitEntity>
    ): List<ExceededAppInfo> {
        val exceeded = mutableListOf<ExceededAppInfo>()
        val limitMap = appLimits.associate { it.packageName to it.dailyLimitMinutes }

        for (app in dailyStats.apps) {
            val limitMinutes = limitMap[app.packageName] ?: continue
            val totalMinutes = app.totalDurationMs / (1000 * 60)

            if (totalMinutes > limitMinutes) {
                exceeded.add(
                    ExceededAppInfo(
                        packageName = app.packageName,
                        appName = app.appName,
                        totalMinutes = totalMinutes.toInt(),
                        limitMinutes = limitMinutes,
                        overMinutes = totalMinutes.toInt() - limitMinutes
                    )
                )
            }
        }

        return exceeded.sortedByDescending { it.overMinutes }
    }
}

@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val period: HomePeriod = HomePeriod.Today,
    val dailyStats: DailyUsageStats? = null,
    val yesterdayStats: DailyUsageStats? = null,
    val weeklyStats: WeeklyUsageStats? = null,
    val exceededApps: List<ExceededAppInfo> = emptyList(),
    val exceededAppsYesterday: List<ExceededAppInfo> = emptyList(),
    val hourlyUsageToday: List<AppUsageHourBucket> = emptyList(),
    val hourlyUsageYesterday: List<AppUsageHourBucket> = emptyList(),
    val error: String? = null
)

data class ExceededAppInfo(
    val packageName: String,
    val appName: String,
    val totalMinutes: Int,
    val limitMinutes: Int,
    val overMinutes: Int
)

enum class HomePeriod(val label: String) {
    Today("Сегодня"),
    Yesterday("Вчера"),
    Week("Неделя")
}

sealed class HomeEvent {
    data class ChangePeriod(val period: HomePeriod) : HomeEvent()
}
