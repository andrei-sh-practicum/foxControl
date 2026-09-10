package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.ReportSendLogEntity

@Dao
interface ReportSendLogDao {
    @Query("SELECT * FROM report_send_log ORDER BY sentAt DESC LIMIT 1")
    suspend fun getLastLog(): ReportSendLogEntity?

    @Query("SELECT * FROM report_send_log WHERE date = :date")
    suspend fun getLogByDate(date: String): ReportSendLogEntity?

    @Query("SELECT * FROM report_send_log ORDER BY sentAt DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 10): List<ReportSendLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ReportSendLogEntity): Long

    @Query("DELETE FROM report_send_log WHERE sentAt < :before")
    suspend fun deleteOldLogs(before: Long)
}
