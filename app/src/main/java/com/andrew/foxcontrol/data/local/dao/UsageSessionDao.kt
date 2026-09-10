package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.UsageSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageSessionDao {
    @Query("SELECT * FROM usage_sessions WHERE date = :date ORDER BY startTime DESC")
    fun getSessionsByDate(date: String): Flow<List<UsageSessionEntity>>

    @Query("SELECT * FROM usage_sessions WHERE date = :date ORDER BY startTime DESC")
    suspend fun getSessionsByDateSync(date: String): List<UsageSessionEntity>

    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName AND date = :date ORDER BY startTime DESC")
    suspend fun getSessionsByPackageAndDate(packageName: String, date: String): List<UsageSessionEntity>

    @Query("""
        SELECT packageName, MAX(appName) as appName, SUM(durationMs) as totalMs, COUNT(*) as sessionCount 
        FROM usage_sessions 
        WHERE date >= :startDate AND date <= :endDate 
        GROUP BY packageName 
        ORDER BY totalMs DESC
    """)
    suspend fun getWeeklyUsageByPackage(startDate: String, endDate: String): List<UsageStatsSummary>

    @Query("SELECT * FROM usage_sessions WHERE packageName = :packageName AND date >= :startDate ORDER BY startTime DESC")
    fun getSessionsByPackageSince(packageName: String, startDate: String): Flow<List<UsageSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: UsageSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSessions(sessions: List<UsageSessionEntity>)

    @Query("DELETE FROM usage_sessions WHERE date < :keepDate")
    suspend fun deleteOldSessions(keepDate: String)

    // --- Debug methods ---
    @Query("SELECT COUNT(*) FROM usage_sessions")
    suspend fun getSessionCount(): Int

    @Query("SELECT COUNT(DISTINCT packageName) FROM usage_sessions")
    suspend fun getUniquePackageCount(): Int

    @Query("SELECT MIN(date) as minDate, MAX(date) as maxDate FROM usage_sessions")
    suspend fun getDateRange(): DateRange?

    @Query("SELECT * FROM usage_sessions ORDER BY endTime DESC LIMIT 20")
    suspend fun getRecentSessions(): List<UsageSessionEntity>

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAllSessions()
}

data class DateRange(
    val minDate: String?,
    val maxDate: String?
)

data class UsageStatsSummary(
    val packageName: String,
    val appName: String,
    val totalMs: Long,
    val sessionCount: Int
)
