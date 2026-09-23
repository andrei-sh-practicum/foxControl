package com.andrew.foxcontrol.core.email

import com.andrew.foxcontrol.core.email.ReportSchedule.Decision
import com.andrew.foxcontrol.data.local.entity.ReportSendLogEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportScheduleTest {

    private val send = 20 * 60          // 20:00
    private val now = 1_000_000_000L

    private fun log(status: String, minutesAgo: Long = 60) = ReportSendLogEntity(
        date = "2026-09-23",
        status = status,
        sentAt = now - minutesAgo * 60_000L
    )

    @Test
    fun `before send time`() {
        assertEquals(Decision.NOT_YET, ReportSchedule.decide(send - 1, send, emptyList(), now))
    }

    @Test
    fun `exactly at send time and later — send if nothing was sent`() {
        assertEquals(Decision.SEND, ReportSchedule.decide(send, send, emptyList(), now))
        // missed the exact minute (Doze / killed process) — still sent later the same day
        assertEquals(Decision.SEND, ReportSchedule.decide(send + 95, send, emptyList(), now))
    }

    @Test
    fun `already delivered today`() {
        assertEquals(Decision.ALREADY_SENT, ReportSchedule.decide(send + 5, send, listOf(log(ReportStatus.SUCCESS)), now))
        assertEquals(Decision.ALREADY_SENT, ReportSchedule.decide(send + 5, send, listOf(log(ReportStatus.PARTIAL)), now))
    }

    @Test
    fun `failed attempt is retried after the interval`() {
        val recent = listOf(log(ReportStatus.FAILED, minutesAgo = 5))
        assertEquals(Decision.RETRY_LATER, ReportSchedule.decide(send + 5, send, recent, now))

        val old = listOf(log(ReportStatus.FAILED, minutesAgo = 15))
        assertEquals(Decision.SEND, ReportSchedule.decide(send + 15, send, old, now))
    }

    @Test
    fun `stop after max failed attempts`() {
        val logs = List(ReportSchedule.MAX_FAILED_ATTEMPTS_PER_DAY) { log(ReportStatus.FAILED, minutesAgo = 60L + it) }
        assertEquals(Decision.ATTEMPTS_EXHAUSTED, ReportSchedule.decide(send + 120, send, logs, now))
    }

    @Test
    fun `success after failures wins`() {
        val logs = listOf(log(ReportStatus.FAILED, 30), log(ReportStatus.SUCCESS, 10))
        assertEquals(Decision.ALREADY_SENT, ReportSchedule.decide(send + 40, send, logs, now))
    }
}
