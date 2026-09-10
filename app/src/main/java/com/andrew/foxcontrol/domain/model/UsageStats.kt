package com.andrew.foxcontrol.domain.model

data class UsageStats(
    val packageName: String,
    val appName: String,
    val totalDurationMs: Long,
    val sessionCount: Int,
    val isEntertainment: Boolean,
    val category: String = ""
)

data class DailyUsageStats(
    val date: String,
    val totalUsageMs: Long,
    val apps: List<UsageStats>
)

data class WeeklyUsageStats(
    val startDate: String,
    val endDate: String,
    val totalUsageMs: Long,
    val apps: List<UsageStats>
)
