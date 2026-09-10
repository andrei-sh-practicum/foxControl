package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.ServiceDowntimeEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceDowntimeEventDao {
    @Query("SELECT * FROM service_downtime_events ORDER BY startTime DESC")
    fun getDowntimeEvents(): Flow<List<ServiceDowntimeEventEntity>>

    @Query("SELECT * FROM service_downtime_events ORDER BY startTime DESC")
    suspend fun getDowntimeEventsSync(): List<ServiceDowntimeEventEntity>

    @Insert
    suspend fun insertDowntimeEvent(event: ServiceDowntimeEventEntity)

    @Query("DELETE FROM service_downtime_events WHERE startTime < :cutoff")
    suspend fun deleteOldEvents(cutoff: Long)
}
