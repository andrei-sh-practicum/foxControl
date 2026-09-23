package com.andrew.foxcontrol.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrew.foxcontrol.data.local.entity.TrackedAppEntity
import com.andrew.foxcontrol.data.repository.UserRepository
import com.andrew.foxcontrol.domain.repository.UsageStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PrivateSettingsViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val usageStatsRepository: UsageStatsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PrivateSettingsState())
    val state: StateFlow<PrivateSettingsState> = _state

    init {
        viewModelScope.launch {
            // Load current password hash
            userRepository.passwordHash
                .collect { hash ->
                    _state.update {
                        it.copy(
                            passwordHash = hash,
                            isLoading = false
                        )
                    }
                }
        }

        // Load global limit from DB (0 = not set)
        viewModelScope.launch {
            val globalLimit = usageStatsRepository.getGlobalLimit()
            val minutes = globalLimit?.takeIf { it.enabled }?.dailyLimitMinutes ?: 0
            _state.update { it.copy(globalDailyLimitMinutes = minutes) }
        }

        // Load app limits from DB
        viewModelScope.launch {
            val limits = usageStatsRepository.getAppLimits()
            val limitsMap = limits.associate { it.packageName to it.dailyLimitMinutes }
            _state.update { it.copy(appLimits = limitsMap) }
        }

        // Load tracked apps
        viewModelScope.launch {
            val apps = usageStatsRepository.getTrackedApps()
            _state.update { it.copy(trackedApps = apps) }
        }
    }

    // Simple hash (kept as is — see _docs/bugs_plan.md, B-14)
    private fun hashPassword(password: String): String = password.hashCode().toString()

    fun verifyPassword(password: String): Boolean {
        val currentHash = _state.value.passwordHash
        return if (currentHash.isNullOrEmpty()) {
            // No password set yet
            false
        } else {
            hashPassword(password) == currentHash
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        if (!verifyPassword(oldPassword)) {
            _state.update { it.copy(passwordChangeResult = PasswordChangeResult.WrongOldPassword) }
            return
        }
        viewModelScope.launch {
            val hash = hashPassword(newPassword)
            userRepository.setPasswordHash(hash)
            _state.update {
                it.copy(
                    passwordHash = hash,
                    isLoading = false,
                    error = null,
                    passwordChangeResult = PasswordChangeResult.Success
                )
            }
        }
    }

    fun onEvent(event: PrivateSettingsEvent) {
        when (event) {
            is PrivateSettingsEvent.OnClearTrackedApps -> {
                viewModelScope.launch {
                    try {
                        android.util.Log.d("PrivateSettings", "OnClearTrackedApps: START")
                        usageStatsRepository.clearTrackedApps()
                        android.util.Log.d("PrivateSettings", "OnClearTrackedApps: SUCCESS")
                    } catch (e: Exception) {
                        android.util.Log.e("PrivateSettings", "OnClearTrackedApps: FAILED", e)
                    }
                }
            }
            is PrivateSettingsEvent.OnPasswordEntered -> {
                if (verifyPassword(event.password)) {
                    _state.update { it.copy(isAuthenticated = true, error = null) }
                } else {
                    _state.update { it.copy(error = "Неверный пароль") }
                }
            }
            is PrivateSettingsEvent.OnChangePassword -> {
                changePassword(event.oldPassword, event.newPassword)
            }
            PrivateSettingsEvent.OnPasswordChangeResultConsumed -> {
                _state.update { it.copy(passwordChangeResult = null) }
            }
            is PrivateSettingsEvent.OnGlobalLimitChanged -> {
                viewModelScope.launch {
                    try {
                        usageStatsRepository.setGlobalLimit(event.minutes)
                        _state.update { it.copy(globalDailyLimitMinutes = event.minutes) }
                    } catch (e: Exception) {
                        _state.update { it.copy(error = "Ошибка при сохранении лимита: ${e.message}") }
                    }
                }
            }
            is PrivateSettingsEvent.OnAppLimitChanged -> {
                viewModelScope.launch {
                    try {
                        usageStatsRepository.setAppLimit(event.packageName, event.limitMinutes, enabled = true)
                        _state.update { it.copy(appLimits = it.appLimits + (event.packageName to event.limitMinutes)) }
                    } catch (e: Exception) {
                        _state.update { it.copy(error = "Ошибка при изменении лимита: ${e.message}") }
                    }
                }
            }
            is PrivateSettingsEvent.OnRemoveAppLimit -> {
                viewModelScope.launch {
                    try {
                        usageStatsRepository.removeAppLimit(event.packageName)
                        _state.update { it.copy(appLimits = it.appLimits - event.packageName) }
                    } catch (e: Exception) {
                        _state.update { it.copy(error = "Ошибка при удалении лимита: ${e.message}") }
                    }
                }
            }
            PrivateSettingsEvent.OnClearError -> {
                _state.update { it.copy(error = null) }
            }
            is PrivateSettingsEvent.OnAddAppLimit -> {
                viewModelScope.launch {
                    try {
                        usageStatsRepository.setAppLimit(
                            packageName = event.packageName,
                            dailyLimitMinutes = event.limitMinutes,
                            enabled = true
                        )
                        // Update local state
                        val limits = _state.value.appLimits.toMutableMap()
                        limits[event.packageName] = event.limitMinutes
                        _state.update { it.copy(appLimits = limits) }
                        android.util.Log.d("PrivateSettings", "App limit added: ${event.packageName} = ${event.limitMinutes}")
                    } catch (e: Exception) {
                        android.util.Log.e("PrivateSettings", "Failed to add app limit", e)
                        _state.update { it.copy(addAppLimitError = "Ошибка при добавлении: ${e.message}") }
                    }
                }
            }
            is PrivateSettingsEvent.OnClearAddAppLimitError -> {
                _state.update { it.copy(addAppLimitError = null) }
            }
            is PrivateSettingsEvent.OnExcludeApp -> {
                viewModelScope.launch {
                    try {
                        usageStatsRepository.setAppExcluded(event.packageName, true)
                        _state.update { s ->
                            s.copy(trackedApps = s.trackedApps.map {
                                if (it.packageName == event.packageName) it.copy(isExcluded = true) else it
                            })
                        }
                    } catch (e: Exception) {
                        _state.update { it.copy(error = "Ошибка при исключении приложения: ${e.message}") }
                    }
                }
            }
            is PrivateSettingsEvent.OnIncludeApp -> {
                viewModelScope.launch {
                    try {
                        usageStatsRepository.setAppExcluded(event.packageName, false)
                        _state.update { s ->
                            s.copy(trackedApps = s.trackedApps.map {
                                if (it.packageName == event.packageName) it.copy(isExcluded = false) else it
                            })
                        }
                    } catch (e: Exception) {
                        _state.update { it.copy(error = "Ошибка при возврате приложения в трекинг: ${e.message}") }
                    }
                }
            }
        }
    }
}

@Immutable
data class PrivateSettingsState(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val passwordHash: String? = null,
    /** Whole-phone daily limit in minutes; 0 = not set. */
    val globalDailyLimitMinutes: Int = 0,
    val appLimits: Map<String, Int> = emptyMap(),
    val trackedApps: List<TrackedAppEntity> = emptyList(),
    val error: String? = null,
    val addAppLimitError: String? = null,
    val passwordChangeResult: PasswordChangeResult? = null
)

sealed interface PasswordChangeResult {
    object Success : PasswordChangeResult
    object WrongOldPassword : PasswordChangeResult
}

sealed class PrivateSettingsEvent {
    object OnClearTrackedApps : PrivateSettingsEvent()
    data class OnPasswordEntered(val password: String) : PrivateSettingsEvent()
    data class OnChangePassword(val oldPassword: String, val newPassword: String) : PrivateSettingsEvent()
    object OnPasswordChangeResultConsumed : PrivateSettingsEvent()
    /** Saved on dialog confirm; 0 disables the limit. */
    data class OnGlobalLimitChanged(val minutes: Int) : PrivateSettingsEvent()
    /** Edit of an existing app limit (saved to the DB). */
    data class OnAppLimitChanged(val packageName: String, val limitMinutes: Int) : PrivateSettingsEvent()
    data class OnRemoveAppLimit(val packageName: String) : PrivateSettingsEvent()
    data class OnAddAppLimit(val packageName: String, val appName: String, val limitMinutes: Int) : PrivateSettingsEvent()
    object OnClearError : PrivateSettingsEvent()
    object OnClearAddAppLimitError : PrivateSettingsEvent()
    data class OnExcludeApp(val packageName: String) : PrivateSettingsEvent()
    data class OnIncludeApp(val packageName: String) : PrivateSettingsEvent()
}
