package com.andrew.foxcontrol.ui.settings

import android.net.Uri
import androidx.compose.runtime.Immutable
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

            // Ensure a user record exists before reading
            userRepository.ensureDefaultUser()

            // Combine user entity (Room) → name + avatarUri
            userRepository.user
                .catch { e -> _state.update { it.copy(usernameError = e.message ?: "Error") } }
                .collect { user ->
                    _state.update {
                        it.copy(
                            name = user?.name ?: "",
                            avatarUri = user?.avatarUri ?: "",
                            isLoading = false
                        )
                    }
                }
        }
    }

    /** Save the user's name (called when user taps "Сохранить имя"). */
    fun saveName(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            val result = userRepository.updateName(trimmed)
            if (result.isSuccess) {
                _state.update { it.copy(message = "Имя сохранено") }
            } else {
                _state.update { it.copy(message = "Ошибка сохранения имени") }
            }
        }
    }

    /** Save an avatar image (called after gallery picker returns). */
    fun saveAvatar(uri: Uri) {
        viewModelScope.launch {
            val result = userRepository.saveAvatar(uri)
            if (result.isSuccess) {
                _state.update { it.copy(message = "Аватар обновлён") }
            } else {
                _state.update { it.copy(message = "Ошибка обновления аватара") }
            }
        }
    }

    /** Clear the success/error message (called from UI after delay). */
    fun clearMessage() {
        _state.update { it.copy(message = "") }
    }
}

@Immutable
data class SettingsState(
    val name: String = "",
    val avatarUri: String = "",
    val appVersion: String = BuildConfig.VERSION_NAME,
    val usernameError: String? = null,
    val message: String = "",
    val isLoading: Boolean = true
)
