package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.AlertLogEntity

@Dao
interface AlertLogDao {

    @Insert
    suspend fun insertAlertLog(log: AlertLogEntity)

    @Query("DELETE FROM alert_logs WHERE timestamp < :cutoff")
    suspend fun deleteOldLogs(cutoff: Long): Int

    @Query("SELECT COUNT(*) > 0 FROM alert_logs WHERE packageName = :packageName AND type = :type AND timestamp >= :dayStart")
    suspend fun wasAlertShownToday(packageName: String, type: String, dayStart: Long): Boolean
}
