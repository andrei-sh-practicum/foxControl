package com.andrew.foxcontrol.core.email

import android.util.Log
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailSender @Inject constructor() {

    companion object {
        private const val TAG = "EmailSender"
    }

    data class EmailConfig(
        val smtpHost: String,
        val smtpPort: Int,
        val login: String,
        val appPassword: String,
        val fromEmail: String
    )

    data class SendResult(
        val success: Boolean,
        val message: String
    )

    fun sendEmail(
        config: EmailConfig,
        to: String,
        subject: String,
        body: String
    ): SendResult = sendBulkEmail(config, listOf(to), subject, body).single()

    /**
     * Sends a separate message to each recipient over one SMTP connection
     * (reconnects if the server dropped it). One result per recipient, in order.
     */
    fun sendBulkEmail(
        config: EmailConfig,
        recipients: List<String>,
        subject: String,
        body: String
    ): List<SendResult> {
        if (recipients.isEmpty()) return emptyList()

        val session = Session.getInstance(smtpProperties(config), null)
        session.setDebug(false)
        val transport = session.getTransport("smtp")
        try {
            return recipients.map { to -> send(transport, session, config, to, subject, body) }
        } finally {
            try {
                if (transport.isConnected) transport.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to close SMTP connection", e)
            }
        }
    }

    private fun send(
        transport: Transport,
        session: Session,
        config: EmailConfig,
        to: String,
        subject: String,
        body: String
    ): SendResult {
        return try {
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(config.fromEmail))
                setRecipient(Message.RecipientType.TO, InternetAddress(to))
                setSubject(subject)
                setText(body)
            }
            // Transport.send() did this implicitly; sendMessage() does not
            message.saveChanges()

            if (!transport.isConnected) {
                transport.connect(config.login, config.appPassword)
            }
            transport.sendMessage(message, message.allRecipients)

            Log.d(TAG, "Email sent successfully to $to")
            SendResult(success = true, message = "Email sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send email to $to", e)
            SendResult(success = false, message = "Failed: ${e.message}")
        }
    }

    private fun smtpProperties(config: EmailConfig) = Properties().apply {
        put("mail.smtp.host", config.smtpHost)
        put("mail.smtp.port", config.smtpPort.toString())
        put("mail.smtp.auth", "true")
        put("mail.smtp.starttls.enable", "true")
        put("mail.smtp.starttls.required", "true")
        put("mail.smtp.connectiontimeout", "5000")
        put("mail.smtp.timeout", "10000")
        put("mail.smtp.writetimeout", "10000")
    }
}
