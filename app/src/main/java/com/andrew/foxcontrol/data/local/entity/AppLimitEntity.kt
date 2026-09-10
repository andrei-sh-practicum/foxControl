package com.andrew.foxcontrol.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey
    val packageName: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean = true
)
