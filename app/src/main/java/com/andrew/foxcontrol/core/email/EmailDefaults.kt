package com.andrew.foxcontrol.core.email

import com.andrew.foxcontrol.BuildConfig

/**
 * Default SMTP settings (Brevo) used until the parent saves their own.
 * Login, sender address and app password come from local.properties → BuildConfig
 * (never committed, see _docs/build_secrets.md); empty if not configured.
 */
object EmailDefaults {
    const val SMTP_HOST = "smtp-relay.brevo.com"
    const val SMTP_PORT = "587"
    const val SMTP_PORT_INT = 587
    const val SEND_TIME_HOUR = 20
    const val SEND_TIME_MINUTE = 0

    /** Shown in the settings form before the stored values are loaded. */
    const val SMTP_APP_PASSWORD_PLACEHOLDER = "CHANGE_ME_IN_PRODUCTION"

    /** Neutral example for input placeholders. */
    const val EMAIL_PLACEHOLDER = "user@example.com"

    val smtpLogin: String
        get() = BuildConfig.SMTP_LOGIN_DEFAULT

    val fromEmail: String
        get() = BuildConfig.SMTP_FROM_DEFAULT

    val smtpAppPassword: String
        get() = BuildConfig.SMTP_APP_PASSWORD_DEFAULT
}
