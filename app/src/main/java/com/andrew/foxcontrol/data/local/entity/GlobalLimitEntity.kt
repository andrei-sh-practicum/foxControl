package com.andrew.foxcontrol.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "global_limit")
data class GlobalLimitEntity(
    @PrimaryKey
    val id: Int = 1,
    val dailyLimitMinutes: Int,
    val enabled: Boolean = false
)
