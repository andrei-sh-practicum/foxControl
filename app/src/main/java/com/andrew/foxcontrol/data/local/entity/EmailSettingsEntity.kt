package com.andrew.foxcontrol.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "email_settings")
data class EmailSettingsEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

object EmailSettingsKeys {
    const val SMTP_HOST = "smtp_host"
    const val SMTP_PORT = "smtp_port"
    const val SMTP_LOGIN = "smtp_login"
    const val SMTP_APP_PASSWORD = "smtp_app_password"
    const val FROM_EMAIL = "from_email"
    const val ENABLED = "email_enabled"
    const val SEND_TIME_HOUR = "send_time_hour"
    const val SEND_TIME_MINUTE = "send_time_minute"
}
