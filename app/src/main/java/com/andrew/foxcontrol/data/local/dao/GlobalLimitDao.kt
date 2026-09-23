package com.andrew.foxcontrol.data.local.dao

import androidx.room.*
import com.andrew.foxcontrol.data.local.entity.GlobalLimitEntity

@Dao
interface GlobalLimitDao {
    @Query("SELECT * FROM global_limit WHERE id = 1")
    suspend fun getGlobalLimit(): GlobalLimitEntity?

    /** Upsert of the single row (id = 1). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGlobalLimit(limit: GlobalLimitEntity)
}
