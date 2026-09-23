package com.andrew.foxcontrol.domain.usecase

import com.andrew.foxcontrol.data.local.entity.AppLimitEntity
import com.andrew.foxcontrol.domain.model.UsageStats

/**
 * Daily limit checks.
 *
 * Note: two rules exist and are kept as they were:
 * - [isExceeded] (alerts) compares milliseconds: 30 min 30 s against a 30 min limit IS exceeded;
 * - [exceededApps] (Home screen, email report) compares whole minutes (floored):
 *   30 min 30 s against a 30 min limit is NOT exceeded.
 */
object LimitCalculator {

    data class ExceededApp(
        val packageName: String,
        val appName: String,
        val totalMinutes: Int,
        val limitMinutes: Int,
        val overMinutes: Int
    )

    /** Alert rule: strictly more than the limit, in milliseconds. */
    fun isExceeded(usedMs: Long, limitMinutes: Int): Boolean =
        usedMs > limitMinutes * 60L * 1000L

    /**
     * Apps whose floored used minutes are strictly above their limit, most exceeded first
     * (stable for equal overuse: keeps the order of [apps]).
     */
    fun exceededApps(apps: List<UsageStats>, limits: List<AppLimitEntity>): List<ExceededApp> {
        val limitMap = limits.associate { it.packageName to it.dailyLimitMinutes }
        return apps.mapNotNull { app ->
            val limitMinutes = limitMap[app.packageName] ?: return@mapNotNull null
            val totalMinutes = (app.totalDurationMs / (1000 * 60)).toInt()
            if (totalMinutes > limitMinutes) {
                ExceededApp(
                    packageName = app.packageName,
                    appName = app.appName,
                    totalMinutes = totalMinutes,
                    limitMinutes = limitMinutes,
                    overMinutes = totalMinutes - limitMinutes
                )
            } else null
        }.sortedByDescending { it.overMinutes }
    }
}
