package com.andrew.foxcontrol.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrew.foxcontrol.core.permissions.OnboardingPermissions
import com.andrew.foxcontrol.core.permissions.PermissionHelper
import com.andrew.foxcontrol.core.permissions.PermissionRepository
import com.andrew.foxcontrol.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissionRepository: PermissionRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state

    init {
        viewModelScope.launch {
            initApp()
        }
    }

    private suspend fun initApp() {
        _state.update { it.copy(status = OnboardingStatus.CheckingPermissions) }

        // Ensure default user exists in Room
        userRepository.ensureDefaultUser()

        // Ensure default password hash is set (12345) if not already set
        userRepository.initDefaultUser()

        refreshPermissions()
    }

    fun openUsageStatsSettings() {
        permissionRepository.openUsageStatsSettings()
    }

    fun openOverlayPermissionSettings() {
        permissionRepository.openOverlayPermissionSettings()
    }

    fun openBatteryOptimizationSettings() {
        permissionRepository.openBatteryOptimizationSettings()
    }

    fun openNotificationSettings() {
        permissionRepository.openNotificationSettings()
    }

    fun onCheckPermissions() {
        viewModelScope.launch {
            refreshPermissions()
        }
    }

    /**
     * "Продолжить без них": critical permissions are granted, the user skips the
     * recommended ones. Remembered so the onboarding isn't shown on every launch.
     */
    fun onContinueWithoutOptional() {
        viewModelScope.launch {
            val permissions = permissionRepository.checkPermissions()
            if (!permissions.criticalGranted) {
                _state.update { it.copy(permissions = permissions, status = OnboardingStatus.NeedsPermissions) }
                return@launch
            }
            userRepository.completeOnboarding()
            _state.update { it.copy(permissions = permissions, status = OnboardingStatus.Done) }
        }
    }

    /**
     * Onboarding is done when:
     * - all permissions are granted, or
     * - critical permissions (usage stats + overlay) are granted and the user
     *   has already chosen to continue without the recommended ones.
     * Missing critical permissions always bring the onboarding back.
     */
    private suspend fun refreshPermissions() {
        val permissions = permissionRepository.checkPermissions()
        val done = when {
            permissions.allGranted -> {
                userRepository.completeOnboarding()
                true
            }
            permissions.criticalGranted -> userRepository.isOnboardingCompleted()
            else -> false
        }
        _state.update {
            // Don't flip back from Done: navigation away is already in progress
            if (it.status == OnboardingStatus.Done) it.copy(permissions = permissions)
            else it.copy(
                permissions = permissions,
                status = if (done) OnboardingStatus.Done else OnboardingStatus.NeedsPermissions
            )
        }
    }
}

@Immutable
data class OnboardingState(
    val status: OnboardingStatus = OnboardingStatus.CheckingPermissions,
    val permissions: OnboardingPermissions = OnboardingPermissions(
        usageStats = false,
        overlay = false,
        notifications = false,
        batteryOptimization = false
    )
)

sealed interface OnboardingStatus {
    object CheckingPermissions : OnboardingStatus
    object NeedsPermissions : OnboardingStatus
    object Done : OnboardingStatus
}
