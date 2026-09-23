package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.ReportSendLogEntity

@Dao
interface ReportSendLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ReportSendLogEntity): Long

    @Query("DELETE FROM report_send_log WHERE sentAt < :before")
    suspend fun deleteOldLogs(before: Long): Int
}
