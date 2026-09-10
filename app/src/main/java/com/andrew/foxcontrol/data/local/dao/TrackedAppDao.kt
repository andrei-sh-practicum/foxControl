package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.TrackedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedAppDao {
    @Query("SELECT * FROM tracked_apps ORDER BY lastUsedTime DESC")
    fun getAllTrackedApps(): Flow<List<TrackedAppEntity>>

    @Query("SELECT * FROM tracked_apps ORDER BY lastUsedTime DESC")
    suspend fun getAllTrackedAppsSync(): List<TrackedAppEntity>

    @Query("SELECT * FROM tracked_apps WHERE isEntertainment = 1 ORDER BY lastUsedTime DESC LIMIT 10")
    suspend fun getTopEntertainmentApps(): List<TrackedAppEntity>

    @Query("SELECT * FROM tracked_apps WHERE packageName = :packageName")
    suspend fun getTrackedApp(packageName: String): TrackedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackedApp(app: TrackedAppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackedApps(apps: List<TrackedAppEntity>)

    @Query("UPDATE tracked_apps SET isEntertainment = :isEntertainment WHERE packageName = :packageName")
    suspend fun setEntertainment(packageName: String, isEntertainment: Boolean)

    @Query("UPDATE tracked_apps SET isExcluded = :isExcluded WHERE packageName = :packageName")
    suspend fun setExcluded(packageName: String, isExcluded: Boolean)

    @Query("UPDATE tracked_apps SET totalUsageMs = totalUsageMs + :durationMs, lastUsedTime = :lastUsedTime WHERE packageName = :packageName")
    suspend fun updateUsage(packageName: String, durationMs: Long, lastUsedTime: Long)

    @Query("DELETE FROM tracked_apps")
    suspend fun deleteAllTrackedApps()
}
