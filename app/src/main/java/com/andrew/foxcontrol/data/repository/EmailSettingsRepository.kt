package com.andrew.foxcontrol.ui.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.andrew.foxcontrol.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "email_settings")

object EmailSettingsKeys {
    val ENABLED = booleanPreferencesKey("email_enabled")
    val SMTP_HOST = stringPreferencesKey("smtp_host")
    val SMTP_PORT = intPreferencesKey("smtp_port")
    val SMTP_LOGIN = stringPreferencesKey("smtp_login")
    val SMTP_APP_PASSWORD = stringPreferencesKey("smtp_app_password")
    val FROM_EMAIL = stringPreferencesKey("from_email")
    val SEND_TIME_HOUR = intPreferencesKey("send_time_hour")
    val SEND_TIME_MINUTE = intPreferencesKey("send_time_minute")
}

object EmailDefaults {
    const val SMTP_HOST_DEFAULT = "smtp-relay.brevo.com"
    const val SMTP_PORT_DEFAULT = 587
    const val SMTP_LOGIN_DEFAULT = "b84011001@smtp-brevo.com"
    const val SMTP_APP_PASSWORD_DEFAULT = "CHANGE_ME_IN_PRODUCTION"
    const val FROM_EMAIL_DEFAULT = "b84011001@smtp-brevo.com"
    const val SEND_TIME_HOUR_DEFAULT = 20
    const val SEND_TIME_MINUTE_DEFAULT = 0
    
    fun getSmtpAppPassword(): String {
        try {
            val configClass = Class.forName("com.andrew.foxcontrol.BuildConfig")
            val field = configClass.getDeclaredField("SMTP_APP_PASSWORD_DEFAULT")
            return field.get(null) as String
        } catch (e: Exception) {
            return SMTP_APP_PASSWORD_DEFAULT
        }
    }
}

class EmailSettingsRepository(private val context: Context) {

    private val dataStore = context.dataStore

    val isEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[EmailSettingsKeys.ENABLED] ?: false
    }

    val smtpHost: Flow<String> = dataStore.data.map { preferences ->
        preferences[EmailSettingsKeys.SMTP_HOST] ?: EmailDefaults.SMTP_HOST_DEFAULT
    }

    val smtpPort: Flow<Int> = dataStore.data.map { preferences ->
        preferences[EmailSettingsKeys.SMTP_PORT] ?: EmailDefaults.SMTP_PORT_DEFAULT
    }

    val smtpLogin: Flow<String> = dataStore.data.map { preferences ->
        preferences[EmailSettingsKeys.SMTP_LOGIN] ?: EmailDefaults.SMTP_LOGIN_DEFAULT
    }

    val smtpAppPassword: Flow<String> = dataStore.data.map { preferences ->
        preferences[EmailSettingsKeys.SMTP_APP_PASSWORD] ?: EmailDefaults.getSmtpAppPassword()
    }

    val fromEmail: Flow<String> = dataStore.data.map { preferences ->
        preferences[EmailSettingsKeys.FROM_EMAIL] ?: EmailDefaults.FROM_EMAIL_DEFAULT
    }

    val sendTimeHour: Flow<Int> = dataStore.data.map { preferences ->
        preferences[EmailSettingsKeys.SEND_TIME_HOUR] ?: EmailDefaults.SEND_TIME_HOUR_DEFAULT
    }

    val sendTimeMinute: Flow<Int> = dataStore.data.map { preferences ->
        preferences[EmailSettingsKeys.SEND_TIME_MINUTE] ?: EmailDefaults.SEND_TIME_MINUTE_DEFAULT
    }

    suspend fun saveSetting(key: String, value: String) {
        dataStore.edit { preferences ->
            when (key) {
                "smtp_host" -> preferences[EmailSettingsKeys.SMTP_HOST] = value
                "smtp_port" -> preferences[EmailSettingsKeys.SMTP_PORT] = value.toIntOrNull() ?: EmailDefaults.SMTP_PORT_DEFAULT
                "smtp_login" -> preferences[EmailSettingsKeys.SMTP_LOGIN] = value
                "smtp_app_password" -> preferences[EmailSettingsKeys.SMTP_APP_PASSWORD] = value
                "from_email" -> preferences[EmailSettingsKeys.FROM_EMAIL] = value
                "email_enabled" -> preferences[EmailSettingsKeys.ENABLED] = value.toBoolean()
                "send_time_hour" -> preferences[EmailSettingsKeys.SEND_TIME_HOUR] = value.toIntOrNull() ?: EmailDefaults.SEND_TIME_HOUR_DEFAULT
                "send_time_minute" -> preferences[EmailSettingsKeys.SEND_TIME_MINUTE] = value.toIntOrNull() ?: EmailDefaults.SEND_TIME_MINUTE_DEFAULT
            }
        }
    }

    suspend fun saveAll(
        enabled: Boolean,
        smtpHost: String,
        smtpPort: Int,
        smtpLogin: String,
        smtpAppPassword: String,
        fromEmail: String,
        sendTimeHour: Int,
        sendTimeMinute: Int
    ) {
        dataStore.edit { preferences ->
            preferences[EmailSettingsKeys.ENABLED] = enabled
            preferences[EmailSettingsKeys.SMTP_HOST] = smtpHost
            preferences[EmailSettingsKeys.SMTP_PORT] = smtpPort
            preferences[EmailSettingsKeys.SMTP_LOGIN] = smtpLogin
            preferences[EmailSettingsKeys.SMTP_APP_PASSWORD] = smtpAppPassword
            preferences[EmailSettingsKeys.FROM_EMAIL] = fromEmail
            preferences[EmailSettingsKeys.SEND_TIME_HOUR] = sendTimeHour
            preferences[EmailSettingsKeys.SEND_TIME_MINUTE] = sendTimeMinute
        }
    }
}
