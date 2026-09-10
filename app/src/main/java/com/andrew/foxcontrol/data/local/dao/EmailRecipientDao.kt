package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.EmailRecipientEntity

@Dao
interface EmailRecipientDao {
    @Query("SELECT * FROM email_recipients ORDER BY name")
    suspend fun getAllRecipients(): List<EmailRecipientEntity>

    @Query("SELECT * FROM email_recipients WHERE isActive = 1 ORDER BY name")
    suspend fun getActiveRecipients(): List<EmailRecipientEntity>

    @Query("SELECT * FROM email_recipients WHERE id = :id")
    suspend fun getRecipientById(id: Long): EmailRecipientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipient(recipient: EmailRecipientEntity): Long

    @Update
    suspend fun updateRecipient(recipient: EmailRecipientEntity)

    @Delete
    suspend fun deleteRecipient(recipient: EmailRecipientEntity)

    @Query("UPDATE email_recipients SET isActive = :isActive WHERE id = :id")
    suspend fun toggleRecipientActive(id: Long, isActive: Boolean)
}
