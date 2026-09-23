package com.andrew.foxcontrol.data.local.model

/** Result rows of aggregate queries in UsageSessionDao (not entities). */
data class DateRange(
    val minDate: String?,
    val maxDate: String?
)

data class UsageStatsSummary(
    val packageName: String,
    val appName: String,
    val totalMs: Long,
    val sessionCount: Int
)
