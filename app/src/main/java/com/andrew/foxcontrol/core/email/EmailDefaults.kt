package com.andrew.foxcontrol.core.email

import com.andrew.foxcontrol.BuildConfig

/**
 * Default SMTP settings (Brevo) used until the parent saves their own.
 * The default app password comes from local.properties → BuildConfig (never committed).
 */
object EmailDefaults {
    const val SMTP_HOST = "smtp-relay.brevo.com"
    const val SMTP_PORT = "587"
    const val SMTP_PORT_INT = 587
    const val SMTP_LOGIN = "b84011001@smtp-brevo.com"
    const val FROM_EMAIL = "b84011001@smtp-brevo.com"
    const val SEND_TIME_HOUR = 20
    const val SEND_TIME_MINUTE = 0

    /** Shown in the settings form before the stored values are loaded. */
    const val SMTP_APP_PASSWORD_PLACEHOLDER = "CHANGE_ME_IN_PRODUCTION"

    val smtpAppPassword: String
        get() = BuildConfig.SMTP_APP_PASSWORD_DEFAULT
}
