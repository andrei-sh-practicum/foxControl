package com.andrew.foxcontrol.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                            hasPassword = !hash.isNullOrEmpty(),
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun setPassword(password: String) {
        viewModelScope.launch {
            // Simple hash (in production use proper hashing)
            val hash = password.hashCode().toString()
            userRepository.setPasswordHash(hash)
            _state.update {
                it.copy(
                    hasPassword = true,
                    isLoading = false
                )
            }
        }
    }

    fun verifyPassword(password: String): Boolean {
        val currentHash = _state.value.passwordHash
        return if (currentHash.isNullOrEmpty()) {
            // No password set yet
            false
        } else {
            password.hashCode().toString() == currentHash
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        if (verifyPassword(oldPassword)) {
            setPassword(newPassword)
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
                    _state.update { it.copy(isAuthenticated = true) }
                } else {
                    _state.update { it.copy(error = "Неверный пароль") }
                }
            }
            is PrivateSettingsEvent.OnSetPassword -> {
                setPassword(event.password)
                _state.update { it.copy(isAuthenticated = true) }
            }
            is PrivateSettingsEvent.OnChangePassword -> {
                changePassword(event.oldPassword, event.newPassword)
                _state.update { it.copy(error = null) }
            }
            is PrivateSettingsEvent.OnGlobalLimitChanged -> {
                _state.update { it.copy(globalDailyLimitMinutes = event.minutes) }
            }
            is PrivateSettingsEvent.OnAppLimitChanged -> {
                _state.update {
                    val limits = it.appLimits.toMutableMap()
                    limits[event.packageName] = event.limitMinutes
                    it.copy(appLimits = limits)
                }
            }
            PrivateSettingsEvent.OnClearError -> {
                _state.update { it.copy(error = null) }
            }
        }
    }
}

@Immutable
data class PrivateSettingsState(
    val isLoading: Boolean = true,
    val isAuthenticated: Boolean = false,
    val hasPassword: Boolean = false,
    val passwordHash: String? = null,
    val globalDailyLimitMinutes: Int = 120,
    val appLimits: Map<String, Int> = emptyMap(),
    val error: String? = null
)

sealed class PrivateSettingsEvent {
    object OnClearTrackedApps : PrivateSettingsEvent()
    data class OnPasswordEntered(val password: String) : PrivateSettingsEvent()
    data class OnSetPassword(val password: String) : PrivateSettingsEvent()
    data class OnChangePassword(val oldPassword: String, val newPassword: String) : PrivateSettingsEvent()
    data class OnGlobalLimitChanged(val minutes: Int) : PrivateSettingsEvent()
    data class OnAppLimitChanged(val packageName: String, val limitMinutes: Int) : PrivateSettingsEvent()
    object OnClearError : PrivateSettingsEvent()
}
