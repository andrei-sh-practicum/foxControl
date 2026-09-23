package com.andrew.foxcontrol.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrew.foxcontrol.core.email.EmailDefaults
import com.andrew.foxcontrol.core.email.EmailSender
import com.andrew.foxcontrol.data.local.entity.EmailSettingsKeys
import com.andrew.foxcontrol.data.repository.EmailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class EmailSettingsViewModel @Inject constructor(
    private val emailRepository: EmailRepository,
    private val emailSender: EmailSender
) : ViewModel() {

    private val _state = MutableStateFlow(EmailSettingsState())
    val state: StateFlow<EmailSettingsState> = _state

    init {
        viewModelScope.launch {
            loadSettings()
        }
    }

    private suspend fun loadSettings() {
        val settings = emailRepository.getAllSettings()
        _state.update {
            it.copy(
                isEnabled = settings[EmailSettingsKeys.ENABLED] == "true",
                smtpHost = settings[EmailSettingsKeys.SMTP_HOST] ?: EmailDefaults.SMTP_HOST,
                smtpPort = settings[EmailSettingsKeys.SMTP_PORT] ?: EmailDefaults.SMTP_PORT,
                smtpLogin = settings[EmailSettingsKeys.SMTP_LOGIN] ?: EmailDefaults.smtpLogin,
                smtpAppPassword = settings[EmailSettingsKeys.SMTP_APP_PASSWORD] ?: EmailDefaults.smtpAppPassword,
                fromEmail = settings[EmailSettingsKeys.FROM_EMAIL] ?: EmailDefaults.fromEmail,
                sendTimeHour = settings[EmailSettingsKeys.SEND_TIME_HOUR]?.toIntOrNull() ?: EmailDefaults.SEND_TIME_HOUR,
                sendTimeMinute = settings[EmailSettingsKeys.SEND_TIME_MINUTE]?.toIntOrNull() ?: EmailDefaults.SEND_TIME_MINUTE,
                isLoading = false
            )
        }
    }

    fun onEvent(event: EmailSettingsEvent) {
        when (event) {
            is EmailSettingsEvent.OnToggleEnabled -> {
                _state.update { it.copy(isEnabled = event.enabled, lastTestResult = null) }
            }

            is EmailSettingsEvent.OnSmtpHostChanged -> {
                _state.update { it.copy(smtpHost = event.host) }
            }

            is EmailSettingsEvent.OnSmtpPortChanged -> {
                _state.update { it.copy(smtpPort = event.port) }
            }

            is EmailSettingsEvent.OnSmtpLoginChanged -> {
                _state.update { it.copy(smtpLogin = event.login) }
            }

            is EmailSettingsEvent.OnSmtpAppPasswordChanged -> {
                _state.update { it.copy(smtpAppPassword = event.password) }
            }

            is EmailSettingsEvent.OnFromEmailChanged -> {
                _state.update { it.copy(fromEmail = event.email) }
            }

            is EmailSettingsEvent.OnSendTimeChanged -> {
                _state.update {
                    it.copy(
                        sendTimeHour = event.hour,
                        sendTimeMinute = event.minute
                    )
                }
                // Persist immediately so the time picker's "Save" button actually saves
                viewModelScope.launch {
                    emailRepository.saveSetting(EmailSettingsKeys.SEND_TIME_HOUR, event.hour.toString())
                    emailRepository.saveSetting(EmailSettingsKeys.SEND_TIME_MINUTE, event.minute.toString())
                }
            }

            EmailSettingsEvent.OnSaveClicked -> {
                saveSettings()
            }

            EmailSettingsEvent.OnTestSendClicked -> {
                sendTestEmail()
            }
        }
    }

    private fun saveSettings() {
        viewModelScope.launch {
            val state = _state.value
            emailRepository.saveSetting(EmailSettingsKeys.ENABLED, state.isEnabled.toString().lowercase())
            emailRepository.saveSetting(EmailSettingsKeys.SMTP_HOST, state.smtpHost)
            emailRepository.saveSetting(EmailSettingsKeys.SMTP_PORT, state.smtpPort)
            emailRepository.saveSetting(EmailSettingsKeys.SMTP_LOGIN, state.smtpLogin)
            emailRepository.saveSetting(EmailSettingsKeys.SMTP_APP_PASSWORD, state.smtpAppPassword)
            emailRepository.saveSetting(EmailSettingsKeys.FROM_EMAIL, state.fromEmail)
            emailRepository.saveSetting(EmailSettingsKeys.SEND_TIME_HOUR, state.sendTimeHour.toString())
            emailRepository.saveSetting(EmailSettingsKeys.SEND_TIME_MINUTE, state.sendTimeMinute.toString())
        }
    }

    private fun sendTestEmail() {
        viewModelScope.launch {
            val config = EmailSender.EmailConfig(
                smtpHost = _state.value.smtpHost,
                smtpPort = _state.value.smtpPort.toIntOrNull() ?: EmailDefaults.SMTP_PORT_INT,
                login = _state.value.smtpLogin,
                appPassword = _state.value.smtpAppPassword,
                fromEmail = _state.value.fromEmail
            )

            val result = withContext(Dispatchers.IO) {
                emailSender.sendEmail(
                    config = config,
                    to = _state.value.fromEmail,
                    subject = "Fox Control: Тестовое письмо",
                    body = "Это тестовое письмо от Fox Control.\n\nЕсли вы его получили, настройки email работают корректно."
                )
            }

            _state.update {
                it.copy(lastTestResult = result)
            }
        }
    }
}

@Immutable
data class EmailSettingsState(
    val isEnabled: Boolean = false,
    val smtpHost: String = EmailDefaults.SMTP_HOST,
    val smtpPort: String = EmailDefaults.SMTP_PORT,
    val smtpLogin: String = EmailDefaults.smtpLogin,
    val smtpAppPassword: String = EmailDefaults.SMTP_APP_PASSWORD_PLACEHOLDER,
    val fromEmail: String = EmailDefaults.fromEmail,
    val sendTimeHour: Int = EmailDefaults.SEND_TIME_HOUR,
    val sendTimeMinute: Int = EmailDefaults.SEND_TIME_MINUTE,
    val isLoading: Boolean = true,
    val lastTestResult: EmailSender.SendResult? = null
)

sealed class EmailSettingsEvent {
    data class OnToggleEnabled(val enabled: Boolean) : EmailSettingsEvent()
    data class OnSmtpHostChanged(val host: String) : EmailSettingsEvent()
    data class OnSmtpPortChanged(val port: String) : EmailSettingsEvent()
    data class OnSmtpLoginChanged(val login: String) : EmailSettingsEvent()
    data class OnSmtpAppPasswordChanged(val password: String) : EmailSettingsEvent()
    data class OnFromEmailChanged(val email: String) : EmailSettingsEvent()
    data class OnSendTimeChanged(val hour: Int, val minute: Int) : EmailSettingsEvent()
    object OnSaveClicked : EmailSettingsEvent()
    object OnTestSendClicked : EmailSettingsEvent()
}
