package com.andrew.foxcontrol.data.repository

import android.content.pm.PackageManager
import com.andrew.foxcontrol.data.local.dao.AlertLogDao
import com.andrew.foxcontrol.data.local.dao.AppLimitDao
import com.andrew.foxcontrol.data.local.dao.GlobalLimitDao
import com.andrew.foxcontrol.data.local.dao.ServiceHeartbeatDao
import com.andrew.foxcontrol.data.local.dao.TrackedAppDao
import com.andrew.foxcontrol.data.local.dao.UsageSessionDao
import com.andrew.foxcontrol.data.local.entity.AppLimitEntity
import com.andrew.foxcontrol.data.local.entity.TrackedAppEntity
import com.andrew.foxcontrol.data.local.entity.UsageSessionEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Characterization tests for [UsageStatsRepositoryImpl] — pin down the current behavior
 * before refactoring (see _docs/refactoring_plan.md, stage 0.3).
 */
class UsageStatsRepositoryImplTest {

    private val date = "2026-09-23"

    private lateinit var usageSessionDao: UsageSessionDao
    private lateinit var trackedAppDao: TrackedAppDao
    private lateinit var appLimitDao: AppLimitDao
    private lateinit var repository: UsageStatsRepositoryImpl

    @Before
    fun setUp() {
        usageSessionDao = mockk()
        trackedAppDao = mockk()
        appLimitDao = mockk()
        repository = UsageStatsRepositoryImpl(
            usageSessionDao = usageSessionDao,
            trackedAppDao = trackedAppDao,
            appLimitDao = appLimitDao,
            globalLimitDao = mockk<GlobalLimitDao>(),
            serviceHeartbeatDao = mockk<ServiceHeartbeatDao>(),
            alertLogDao = mockk<AlertLogDao>(),
            packageManager = mockk<PackageManager>()
        )
        coEvery { trackedAppDao.getAllTrackedAppsSync() } returns emptyList()
    }

    private fun session(pkg: String, durationMs: Long, name: String = pkg) = UsageSessionEntity(
        packageName = pkg,
        appName = name,
        startTime = 0L,
        endTime = durationMs,
        durationMs = durationMs,
        date = date
    )

    @Test
    fun getDailyUsage_dropsAppsBelowOneMinute() = runBlocking {
        coEvery { usageSessionDao.getSessionsByDateSync(date) } returns listOf(
            session("short", 59_999L),
            session("exact", 60_000L)
        )

        val usage = repository.getDailyUsage(date)

        assertEquals(listOf("exact"), usage.apps.map { it.packageName })
    }

    @Test
    fun getDailyUsage_thresholdAppliesToDailySumNotToSingleSession() = runBlocking {
        coEvery { usageSessionDao.getSessionsByDateSync(date) } returns listOf(
            session("app", 40_000L),
            session("app", 40_000L)
        )

        val usage = repository.getDailyUsage(date)

        assertEquals(1, usage.apps.size)
        assertEquals(80_000L, usage.apps[0].totalDurationMs)
        assertEquals(2, usage.apps[0].sessionCount)
    }

    @Test
    fun getDailyUsage_totalIsSumOfShownAppsOnly() = runBlocking {
        // Current behavior since bf44269 (see bugs_plan.md, B-18)
        coEvery { usageSessionDao.getSessionsByDateSync(date) } returns listOf(
            session("short", 30_000L),
            session("long", 120_000L)
        )

        val usage = repository.getDailyUsage(date)

        assertEquals(120_000L, usage.totalUsageMs)
    }

    @Test
    fun getDailyUsage_sortedByDurationDesc() = runBlocking {
        coEvery { usageSessionDao.getSessionsByDateSync(date) } returns listOf(
            session("b", 120_000L),
            session("a", 300_000L),
            session("c", 60_000L)
        )

        val usage = repository.getDailyUsage(date)

        assertEquals(listOf("a", "b", "c"), usage.apps.map { it.packageName })
    }

    @Test
    fun getDailyUsage_fillsCategoryFromTrackedApps() = runBlocking {
        coEvery { usageSessionDao.getSessionsByDateSync(date) } returns listOf(
            session("game", 120_000L),
            session("tool", 120_000L)
        )
        coEvery { trackedAppDao.getAllTrackedAppsSync() } returns listOf(
            TrackedAppEntity(packageName = "game", appName = "Game", category = "Игры")
        )

        val usage = repository.getDailyUsage(date).apps.associateBy { it.packageName }

        assertEquals("Игры", usage.getValue("game").category)
        assertEquals("", usage.getValue("tool").category)
    }

    @Test
    fun checkAppLimit_exceededOnlyWhenStrictlyGreater() = runBlocking {
        coEvery { appLimitDao.getLimit("app") } returns AppLimitEntity("app", dailyLimitMinutes = 2)

        coEvery { usageSessionDao.getSessionsByDateSync(any()) } returns listOf(session("app", 120_000L))
        assertFalse(repository.checkAppLimit("app"))

        coEvery { usageSessionDao.getSessionsByDateSync(any()) } returns listOf(session("app", 120_001L))
        assertTrue(repository.checkAppLimit("app"))
    }

    @Test
    fun checkAppLimit_disabledOrMissingLimitIsNeverExceeded() = runBlocking {
        coEvery { usageSessionDao.getSessionsByDateSync(any()) } returns listOf(session("app", 600_000L))

        coEvery { appLimitDao.getLimit("app") } returns AppLimitEntity("app", dailyLimitMinutes = 1, enabled = false)
        assertFalse(repository.checkAppLimit("app"))

        coEvery { appLimitDao.getLimit("app") } returns null
        assertFalse(repository.checkAppLimit("app"))
    }
}
