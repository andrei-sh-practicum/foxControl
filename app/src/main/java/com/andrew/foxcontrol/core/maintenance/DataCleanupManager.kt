package com.andrew.foxcontrol.core.maintenance

import com.andrew.foxcontrol.core.tracking.TrackingLogStorage
import com.andrew.foxcontrol.data.local.dao.AlertLogDao
import com.andrew.foxcontrol.data.local.dao.ReportSendLogDao
import com.andrew.foxcontrol.data.local.dao.ServiceHeartbeatDao
import com.andrew.foxcontrol.data.local.dao.UsageSessionDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Управляет очисткой устаревших данных из Room-базы.
 * Удалляет записи старше 7 суток из таблиц логов и сессий.
 * Запускается периодически через TrackingJob (каждые 12 часов)
 * или вручную через Debug-экран.
 */
class DataCleanupManager @Inject constructor(
    private val usageSessionDao: UsageSessionDao,
    private val serviceHeartbeatDao: ServiceHeartbeatDao,
    private val alertLogDao: AlertLogDao,
    private val reportSendLogDao: ReportSendLogDao,
) {

    @Volatile
    private var lastCleanupTimestamp: Long? = null

    /**
     * Выполняет полную очистку всех таблиц от данных старше 7 суток.
     * Ошибки на отдельных таблицах не влияют на остальные.
     */
    suspend fun purgeOldData() {
        val cutoffMs = System.currentTimeMillis() - RETENTION_MS
        val keepDate = dateFormat.format(Date(cutoffMs))

        TrackingLogStorage.add("Cleanup", "purgeOldData started, cutoff=$keepDate")

        // usage_sessions: date < keepDate (строковый формат YYYY-MM-DD)
        try {
            val deletedSessions = usageSessionDao.deleteOldSessions(keepDate)
            TrackingLogStorage.add("Cleanup", "usage_sessions: deleted $deletedSessions rows (keepDate=$keepDate)")
        } catch (e: Exception) {
            TrackingLogStorage.add("Cleanup", "deleteOldSessions ERROR: ${e.message}")
        }

        // service_heartbeats: timestamp < cutoffMs (Long epoch ms)
        try {
            val deletedHeartbeats = serviceHeartbeatDao.deleteOldHeartbeats(cutoffMs)
            TrackingLogStorage.add("Cleanup", "service_heartbeats: deleted $deletedHeartbeats rows")
        } catch (e: Exception) {
            TrackingLogStorage.add("Cleanup", "deleteOldHeartbeats ERROR: ${e.message}")
        }

        // alert_logs: timestamp < cutoffMs
        try {
            val deletedAlerts = alertLogDao.deleteOldLogs(cutoffMs)
            TrackingLogStorage.add("Cleanup", "alert_logs: deleted $deletedAlerts rows")
        } catch (e: Exception) {
            TrackingLogStorage.add("Cleanup", "deleteOldLogs (alerts) ERROR: ${e.message}")
        }

        // report_send_log: sentAt < cutoffMs
        try {
            val deletedReports = reportSendLogDao.deleteOldLogs(cutoffMs)
            TrackingLogStorage.add("Cleanup", "report_send_log: deleted $deletedReports rows")
        } catch (e: Exception) {
            TrackingLogStorage.add("Cleanup", "deleteOldLogs (reports) ERROR: ${e.message}")
        }

        TrackingLogStorage.add("Cleanup", "purgeOldData finished")
        lastCleanupTimestamp = System.currentTimeMillis()
    }

    /**
     * Возвращает timestamp последней успешной очистки, или null если не была.
     */
    fun getLastCleanupTimestamp(): Long? = lastCleanupTimestamp

    companion object {
        private const val RETENTION_DAYS = 7L
        private const val RETENTION_MS = RETENTION_DAYS * 24 * 60 * 60 * 1000L
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    }
}
