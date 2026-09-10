package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.EmailSettingsEntity

@Dao
interface EmailSettingsDao {
    @Query("SELECT * FROM email_settings WHERE key = :key")
    suspend fun getSetting(key: String): EmailSettingsEntity?

    @Query("SELECT * FROM email_settings")
    suspend fun getAllSettings(): List<EmailSettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSetting(setting: EmailSettingsEntity)

    @Query("DELETE FROM email_settings WHERE key = :key")
    suspend fun deleteSetting(key: String)
}
