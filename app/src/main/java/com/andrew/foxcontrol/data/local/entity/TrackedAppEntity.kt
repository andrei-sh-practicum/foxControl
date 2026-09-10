package com.andrew.foxcontrol.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val iconUri: String? = null,
    val category: String = "",
    val isEntertainment: Boolean = false,
    val isExcluded: Boolean = false,
    val lastUsedTime: Long = 0L,
    val totalUsageMs: Long = 0L
)
