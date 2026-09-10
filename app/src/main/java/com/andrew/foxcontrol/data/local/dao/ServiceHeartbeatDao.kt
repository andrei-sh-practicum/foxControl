package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.ServiceHeartbeatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceHeartbeatDao {
    @Query("SELECT * FROM service_heartbeats ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastHeartbeat(): ServiceHeartbeatEntity?

    @Query("SELECT * FROM service_heartbeats ORDER BY timestamp DESC LIMIT 1")
    fun getLastHeartbeatFlow(): Flow<ServiceHeartbeatEntity?>

    @Insert
    suspend fun insertHeartbeat(heartbeat: ServiceHeartbeatEntity)

    @Query("DELETE FROM service_heartbeats WHERE timestamp < :cutoff")
    suspend fun deleteOldHeartbeats(cutoff: Long)

    // --- Debug methods ---
    @Query("SELECT COUNT(*) FROM service_heartbeats")
    suspend fun getHeartbeatCount(): Int

    @Query("SELECT MAX(timestamp) FROM service_heartbeats")
    suspend fun getLastHeartbeatTimestamp(): Long?
}
