package com.andrew.foxcontrol.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.andrew.foxcontrol.BuildConfig
import com.andrew.foxcontrol.core.email.EmailSender
import com.andrew.foxcontrol.data.local.entity.EmailRecipientEntity
import com.andrew.foxcontrol.data.repository.EmailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

object BrevoDefaults {
    const val SMTP_HOST = "smtp-relay.brevo.com"
    const val SMTP_PORT = "587"
    const val SMTP_LOGIN = "b84011001@smtp-brevo.com"
    const val SMTP_APP_PASSWORD = "CHANGE_ME_IN_PRODUCTION"
    const val FROM_EMAIL = "b84011001@smtp-brevo.com"
    
    fun getSmtpAppPassword(): String {
        try {
            val configClass = Class.forName("com.andrew.foxcontrol.BuildConfig")
            val field = configClass.getDeclaredField("SMTP_APP_PASSWORD_DEFAULT")
            return field.get(null) as String
        } catch (e: Exception) {
            return SMTP_APP_PASSWORD
        }
    }
}

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
                isEnabled = settings["email_enabled"] == "true",
                smtpHost = settings["smtp_host"] ?: BrevoDefaults.SMTP_HOST,
                smtpPort = settings["smtp_port"] ?: BrevoDefaults.SMTP_PORT,
                smtpLogin = settings["smtp_login"] ?: BrevoDefaults.SMTP_LOGIN,
                smtpAppPassword = settings["smtp_app_password"] ?: BrevoDefaults.getSmtpAppPassword(),
                fromEmail = settings["from_email"] ?: BrevoDefaults.FROM_EMAIL,
                sendTimeHour = settings["send_time_hour"]?.toIntOrNull() ?: 20,
                sendTimeMinute = settings["send_time_minute"]?.toIntOrNull() ?: 0,
                isLoading = false
            )
        }
    }

    fun onEvent(event: EmailSettingsEvent) {
        when (event) {
            is EmailSettingsEvent.OnToggleEnabled -> {
                _state.update {
                    if (event.enabled) {
                        it.copy(isEnabled = true, lastTestResult = null)
                    } else {
                        it.copy(isEnabled = false, lastTestResult = null)
                    }
                }
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
                    emailRepository.saveSetting("send_time_hour", event.hour.toString())
                    emailRepository.saveSetting("send_time_minute", event.minute.toString())
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
            emailRepository.saveSetting("email_enabled", _state.value.isEnabled.toString().lowercase())
            emailRepository.saveSetting("smtp_host", _state.value.smtpHost)
            emailRepository.saveSetting("smtp_port", _state.value.smtpPort)
            emailRepository.saveSetting("smtp_login", _state.value.smtpLogin)
            emailRepository.saveSetting("smtp_app_password", _state.value.smtpAppPassword)
            emailRepository.saveSetting("from_email", _state.value.fromEmail)
            emailRepository.saveSetting("send_time_hour", _state.value.sendTimeHour.toString())
            emailRepository.saveSetting("send_time_minute", _state.value.sendTimeMinute.toString())
        }
    }

    private fun sendTestEmail() {
        viewModelScope.launch {
            val config = EmailSender.EmailConfig(
                smtpHost = _state.value.smtpHost,
                smtpPort = _state.value.smtpPort.toIntOrNull() ?: 587,
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
    val smtpHost: String = BrevoDefaults.SMTP_HOST,
    val smtpPort: String = BrevoDefaults.SMTP_PORT,
    val smtpLogin: String = BrevoDefaults.SMTP_LOGIN,
    val smtpAppPassword: String = BrevoDefaults.SMTP_APP_PASSWORD,
    val fromEmail: String = BrevoDefaults.FROM_EMAIL,
    val sendTimeHour: Int = 20,
    val sendTimeMinute: Int = 0,
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
