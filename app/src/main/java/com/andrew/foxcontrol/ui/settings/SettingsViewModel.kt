package com.andrew.foxcontrol.ui.settings

import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrew.foxcontrol.BuildConfig
import com.andrew.foxcontrol.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init {
        viewModelScope.launch {
            // Load app version from BuildConfig
            val version = BuildConfig.VERSION_NAME
            _state.update { it.copy(appVersion = version) }

            // Load username
            userRepository.username
                .catch { _state.update { it.copy(usernameError = it.message) } }
                .collect { username ->
                    _state.update { it.copy(username = username ?: "") }
                }

            // Load avatar URI
            userRepository.avatarUri
                .catch { /* ignore */ }
                .collect { avatarUri ->
                    _state.update { it.copy(avatarUri = avatarUri ?: "") }
                }
        }
    }

    fun setUsername(username: String) {
        viewModelScope.launch {
            userRepository.setUsername(username)
        }
    }

    fun setAvatarUri(uri: String) {
        viewModelScope.launch {
            userRepository.setAvatarUri(uri)
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.OnPrivateSettingsClick -> {
                // Handled in UI
            }
        }
    }
}

@Immutable
data class SettingsState(
    val username: String = "",
    val avatarUri: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val usernameError: String? = null,
    val message: String = ""
)

sealed class SettingsEvent {
    object OnPrivateSettingsClick : SettingsEvent()
}
