package com.andrew.foxcontrol.data.repository

import android.content.pm.PackageManager
import com.andrew.foxcontrol.data.local.dao.AlertLogDao
import com.andrew.foxcontrol.data.local.dao.AppLimitDao
import com.andrew.foxcontrol.data.local.dao.GlobalLimitDao
import com.andrew.foxcontrol.data.local.dao.ServiceHeartbeatDao
import com.andrew.foxcontrol.data.local.dao.TrackedAppDao
import com.andrew.foxcontrol.data.local.dao.UsageSessionDao
import com.andrew.foxcontrol.data.local.entity.AppLimitEntity
import com.andrew.foxcontrol.data.local.entity.GlobalLimitEntity
import com.andrew.foxcontrol.data.local.entity.TrackedAppEntity
import com.andrew.foxcontrol.data.local.entity.UsageSessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    private lateinit var globalLimitDao: GlobalLimitDao
    private lateinit var repository: UsageStatsRepositoryImpl

    @Before
    fun setUp() {
        usageSessionDao = mockk()
        trackedAppDao = mockk()
        appLimitDao = mockk()
        globalLimitDao = mockk()
        repository = UsageStatsRepositoryImpl(
            usageSessionDao = usageSessionDao,
            trackedAppDao = trackedAppDao,
            appLimitDao = appLimitDao,
            globalLimitDao = globalLimitDao,
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
    fun getDailyUsage_keepsShortUseAppsAndCountsThemInTotal() = runBlocking {
        // B-18: short-use apps are hidden only in UI lists, the repository returns everything
        coEvery { usageSessionDao.getSessionsByDateSync(date) } returns listOf(
            session("short", 30_000L),
            session("long", 120_000L)
        )

        val usage = repository.getDailyUsage(date)

        assertEquals(listOf("long", "short"), usage.apps.map { it.packageName })
        assertEquals(150_000L, usage.totalUsageMs)
    }

    @Test
    fun getDailyUsage_sumsSessionsPerApp() = runBlocking {
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
    fun getDailyUsage_renamedAppIsOneEntryWithLatestName() = runBlocking {
        // B-11: the label changed during the day
        coEvery { usageSessionDao.getSessionsByDateSync(date) } returns listOf(
            UsageSessionEntity(packageName = "app", appName = "Old", startTime = 0, endTime = 1_000, durationMs = 40_000L, date = date),
            UsageSessionEntity(packageName = "app", appName = "New", startTime = 2_000, endTime = 3_000, durationMs = 40_000L, date = date)
        )

        val usage = repository.getDailyUsage(date)

        assertEquals(1, usage.apps.size)
        assertEquals("New", usage.apps[0].appName)
        assertEquals(80_000L, usage.apps[0].totalDurationMs)
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
    fun getAppLimits_returnsEnabledLimitsWithoutBlocking() = runBlocking {
        val limits = listOf(AppLimitEntity("app", dailyLimitMinutes = 2))
        coEvery { appLimitDao.getEnabledLimitsSync() } returns limits

        assertEquals(limits, repository.getAppLimits())
    }

    @Test
    fun trackUsageSession_existingAppDoesNotQueryPackageManager() = runBlocking {
        // packageManager is a strict mock: any call to it would fail the test
        coEvery { trackedAppDao.getTrackedApp("app") } returns TrackedAppEntity(packageName = "app", appName = "App")
        coEvery { usageSessionDao.insertSession(any()) } returns Unit
        coEvery { trackedAppDao.updateUsage("app", 60_000L, 1_060_000L) } returns Unit

        repository.trackUsageSession("app", "App", 1_000_000L, 1_060_000L, 60_000L, isEntertainment = false)

        coVerify { trackedAppDao.updateUsage("app", 60_000L, 1_060_000L) }
    }

    @Test
    fun trackUsageSession_excludedAppIsIgnored() = runBlocking {
        coEvery { trackedAppDao.getTrackedApp("app") } returns
            TrackedAppEntity(packageName = "app", appName = "App", isExcluded = true)

        repository.trackUsageSession("app", "App", 1_000_000L, 1_060_000L, 60_000L, isEntertainment = false)

        coVerify(exactly = 0) { usageSessionDao.insertSession(any()) }
    }

    @Test
    fun setGlobalLimit_upsertsRowAndEnablesOnlyForPositiveMinutes() = runBlocking {
        coEvery { globalLimitDao.insertGlobalLimit(any()) } returns Unit

        repository.setGlobalLimit(90)
        coVerify { globalLimitDao.insertGlobalLimit(GlobalLimitEntity(id = 1, dailyLimitMinutes = 90, enabled = true)) }

        repository.setGlobalLimit(0)
        coVerify { globalLimitDao.insertGlobalLimit(GlobalLimitEntity(id = 1, dailyLimitMinutes = 0, enabled = false)) }
    }

    @Test
    fun removeAppLimit_deletesTheRow() = runBlocking {
        coEvery { appLimitDao.deleteLimit("app") } returns Unit

        repository.removeAppLimit("app")

        coVerify { appLimitDao.deleteLimit("app") }
    }
}
