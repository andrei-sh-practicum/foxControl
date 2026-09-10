package com.andrew.foxcontrol.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "email_recipients")
data class EmailRecipientEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val email: String,
    val name: String = "",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
