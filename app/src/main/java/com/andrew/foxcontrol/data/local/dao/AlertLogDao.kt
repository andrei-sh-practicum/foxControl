package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.AlertLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertLogDao {
    @Query("SELECT * FROM alert_logs ORDER BY timestamp DESC")
    fun getAlertLogs(): Flow<List<AlertLogEntity>>

    @Query("SELECT * FROM alert_logs ORDER BY timestamp DESC")
    suspend fun getAlertLogsSync(): List<AlertLogEntity>

    @Insert
    suspend fun insertAlertLog(log: AlertLogEntity)

    @Query("DELETE FROM alert_logs WHERE timestamp < :cutoff")
    suspend fun deleteOldLogs(cutoff: Long)
}
