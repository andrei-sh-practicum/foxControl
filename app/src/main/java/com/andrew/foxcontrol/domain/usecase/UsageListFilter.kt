package com.andrew.foxcontrol.domain.usecase

import com.andrew.foxcontrol.domain.model.UsageStats

/**
 * Display filter for app lists (Home screen, email report): apps used less than
 * [MIN_APP_USAGE_MS] in the period are hidden from the list. Totals and limit checks
 * always use the full data (bugs_plan.md, B-18).
 */
object UsageListFilter {
    const val MIN_APP_USAGE_MS = 60_000L

    fun visibleApps(apps: List<UsageStats>): List<UsageStats> =
        apps.filter { it.totalDurationMs >= MIN_APP_USAGE_MS }
}
