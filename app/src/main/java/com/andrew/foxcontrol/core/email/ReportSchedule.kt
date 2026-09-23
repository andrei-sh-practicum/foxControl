package com.andrew.foxcontrol.core.email

import com.andrew.foxcontrol.data.local.entity.ReportSendLogEntity

/** Values of `report_send_log.status`. */
object ReportStatus {
    const val SUCCESS = "SUCCESS"
    const val PARTIAL = "PARTIAL"
    const val FAILED = "FAILED"
}

/**
 * Decides whether the daily report should be sent now (bugs_plan.md, B-10):
 * "the send time has passed today and today's report hasn't been delivered yet".
 * Survives missed timer ticks (Doze, killed process) and process restarts, because the
 * decision is based on `report_send_log`, not on hitting the exact minute.
 */
object ReportSchedule {

    /** Failed attempts per day after which we stop trying until tomorrow. */
    const val MAX_FAILED_ATTEMPTS_PER_DAY = 3

    /** Pause between a failed attempt and the next one. */
    const val RETRY_INTERVAL_MS = 15 * 60_000L

    enum class Decision {
        /** Send time hasn't come yet today. */
        NOT_YET,
        /** Today's report was already delivered (fully or partially). */
        ALREADY_SENT,
        /** Too many failed attempts today. */
        ATTEMPTS_EXHAUSTED,
        /** The last attempt failed recently — wait for the retry interval. */
        RETRY_LATER,
        SEND
    }

    /**
     * @param nowMinuteOfDay  current time as minutes since midnight
     * @param sendMinuteOfDay configured send time as minutes since midnight
     * @param todayLogs       `report_send_log` rows for today's date
     * @param nowMs           current time in ms (for the retry interval)
     */
    fun decide(
        nowMinuteOfDay: Int,
        sendMinuteOfDay: Int,
        todayLogs: List<ReportSendLogEntity>,
        nowMs: Long
    ): Decision {
        if (nowMinuteOfDay < sendMinuteOfDay) return Decision.NOT_YET

        if (todayLogs.any { it.status == ReportStatus.SUCCESS || it.status == ReportStatus.PARTIAL }) {
            return Decision.ALREADY_SENT
        }

        val failed = todayLogs.filter { it.status == ReportStatus.FAILED }
        if (failed.size >= MAX_FAILED_ATTEMPTS_PER_DAY) return Decision.ATTEMPTS_EXHAUSTED

        val lastFailedAt = failed.maxOfOrNull { it.sentAt }
        if (lastFailedAt != null && nowMs - lastFailedAt < RETRY_INTERVAL_MS) return Decision.RETRY_LATER

        return Decision.SEND
    }
}
