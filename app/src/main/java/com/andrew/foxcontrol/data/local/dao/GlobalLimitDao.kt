package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.GlobalLimitEntity

@Dao
interface GlobalLimitDao {
    @Query("SELECT * FROM global_limit WHERE id = 1")
    suspend fun getGlobalLimit(): GlobalLimitEntity?

    @Query("UPDATE global_limit SET dailyLimitMinutes = :minutes, enabled = :enabled WHERE id = 1")
    suspend fun setGlobalLimit(minutes: Int, enabled: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlobalLimit(limit: GlobalLimitEntity)
}
