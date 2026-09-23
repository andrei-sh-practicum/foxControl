package com.andrew.foxcontrol.data.repository

import com.andrew.foxcontrol.core.security.SecretCipher
import com.andrew.foxcontrol.core.tracking.TrackingLogStorage
import com.andrew.foxcontrol.data.local.dao.EmailRecipientDao
import com.andrew.foxcontrol.data.local.dao.EmailSettingsDao
import com.andrew.foxcontrol.data.local.dao.ReportSendLogDao
import com.andrew.foxcontrol.data.local.entity.EmailRecipientEntity
import com.andrew.foxcontrol.data.local.entity.EmailSettingsEntity
import com.andrew.foxcontrol.data.local.entity.EmailSettingsKeys
import com.andrew.foxcontrol.data.local.entity.ReportSendLogEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailRepository @Inject constructor(
    private val recipientDao: EmailRecipientDao,
    private val settingsDao: EmailSettingsDao,
    private val reportLogDao: ReportSendLogDao,
    private val cipher: SecretCipher
) {
    private companion object {
        const val LOG_TAG = "Security"
        val SECRET_KEYS = setOf(EmailSettingsKeys.SMTP_APP_PASSWORD)
    }

    // Recipients
    suspend fun getRecipients(): List<EmailRecipientEntity> = recipientDao.getAllRecipients()
    suspend fun getActiveRecipients(): List<EmailRecipientEntity> = recipientDao.getActiveRecipients()
    suspend fun addRecipient(recipient: EmailRecipientEntity): Long =
        recipientDao.insertRecipient(recipient)
    suspend fun updateRecipient(recipient: EmailRecipientEntity) =
        recipientDao.updateRecipient(recipient)
    suspend fun deleteRecipient(recipient: EmailRecipientEntity) =
        recipientDao.deleteRecipient(recipient)
    suspend fun toggleRecipientActive(id: Long, isActive: Boolean) =
        recipientDao.toggleRecipientActive(id, isActive)

    // Settings (secret values are stored encrypted, see SECRET_KEYS)
    suspend fun getAllSettings(): Map<String, String> =
        settingsDao.getAllSettings()
            .mapNotNull { entity -> readValue(entity)?.let { entity.key to it } }
            .toMap()

    suspend fun saveSetting(key: String, value: String) {
        settingsDao.upsertSetting(
            EmailSettingsEntity(key = key, value = if (key in SECRET_KEYS) encryptOrPlain(value) else value)
        )
    }

    /**
     * Decrypts secret values. A legacy plain-text secret is returned as is and re-saved
     * encrypted; an undecryptable one (key lost, e.g. data moved to another device) is
     * treated as not set.
     */
    private suspend fun readValue(entity: EmailSettingsEntity): String? {
        if (entity.key !in SECRET_KEYS) return entity.value
        if (!cipher.isEncrypted(entity.value)) {
            saveSetting(entity.key, entity.value) // migrate to encrypted storage
            return entity.value
        }
        return try {
            cipher.decrypt(entity.value)
        } catch (e: Exception) {
            TrackingLogStorage.add(LOG_TAG, "Не удалось расшифровать ${entity.key}: ${e.message}")
            null
        }
    }

    /** Never lose the setting because of a Keystore failure: fall back to plain text. */
    private fun encryptOrPlain(value: String): String = try {
        cipher.encrypt(value)
    } catch (e: Exception) {
        TrackingLogStorage.add(LOG_TAG, "Шифрование недоступно, значение сохранено без шифрования: ${e.message}")
        value
    }

    // Report logs
    suspend fun saveLog(log: ReportSendLogEntity): Long = reportLogDao.insertLog(log)
    suspend fun getLogsByDate(date: String): List<ReportSendLogEntity> =
        reportLogDao.getLogsByDate(date)
}
