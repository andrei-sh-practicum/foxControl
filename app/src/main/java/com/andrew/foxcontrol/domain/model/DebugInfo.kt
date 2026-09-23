package com.andrew.foxcontrol.domain.model

import com.andrew.foxcontrol.data.local.entity.UsageSessionEntity
import com.andrew.foxcontrol.data.local.model.DateRange

/** Raw DB statistics for the Debug screen. */
data class DebugInfo(
    val sessionCount: Int,
    val uniquePackageCount: Int,
    val dateRange: DateRange?,
    val recentSessions: List<UsageSessionEntity>,
    val heartbeatCount: Int,
    val lastHeartbeatTimestamp: Long?
)
