package com.andrew.foxcontrol.data.repository

import com.andrew.foxcontrol.data.local.dao.EmailRecipientDao
import com.andrew.foxcontrol.data.local.dao.EmailSettingsDao
import com.andrew.foxcontrol.data.local.dao.ReportSendLogDao
import com.andrew.foxcontrol.data.local.entity.EmailRecipientEntity
import com.andrew.foxcontrol.data.local.entity.EmailSettingsEntity
import com.andrew.foxcontrol.data.local.entity.ReportSendLogEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmailRepository @Inject constructor(
    private val recipientDao: EmailRecipientDao,
    private val settingsDao: EmailSettingsDao,
    private val reportLogDao: ReportSendLogDao
) {
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

    // Settings
    suspend fun getSetting(key: String): String? =
        settingsDao.getSetting(key)?.value
    suspend fun getAllSettings(): Map<String, String> =
        settingsDao.getAllSettings().associate { it.key to it.value }
    suspend fun saveSetting(key: String, value: String) {
        settingsDao.upsertSetting(
            EmailSettingsEntity(key = key, value = value)
        )
    }
    suspend fun deleteSetting(key: String) = settingsDao.deleteSetting(key)

    // Report logs
    suspend fun getLastLog(): ReportSendLogEntity? = reportLogDao.getLastLog()
    suspend fun getRecentLogs(limit: Int = 10): List<ReportSendLogEntity> =
        reportLogDao.getRecentLogs(limit)
    suspend fun saveLog(log: ReportSendLogEntity): Long = reportLogDao.insertLog(log)
}
