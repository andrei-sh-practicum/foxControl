package com.andrew.foxcontrol.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "report_send_log")
data class ReportSendLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String, // YYYY-MM-DD
    val status: String, // SUCCESS, FAILED, PENDING
    val errorMessage: String? = null,
    val recipientCount: Int = 0,
    val sentAt: Long = System.currentTimeMillis()
)
