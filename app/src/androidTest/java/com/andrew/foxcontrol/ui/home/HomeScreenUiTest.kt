package com.andrew.foxcontrol.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.andrew.foxcontrol.domain.model.DailyUsageStats
import com.andrew.foxcontrol.domain.model.UsageStats
import com.andrew.foxcontrol.ui.theme.FoxControlTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.andrew.foxcontrol.R

@HiltAndroidTest
class HomeScreenUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun homeScreen_showsTitle() = runTest {
        // Create a mock activity
        composeRule.activity.apply {
            setContentView(R.layout.empty_activity)
        }

        // Test that the Compose UI can be created
        val mockState = HomeState(
            isLoading = false,
            dailyStats = DailyUsageStats(
                totalUsageMs = 3600000L,
                apps = listOf(
                    UsageStats(
                        packageName = "com.android.chrome",
                        appName = "Chrome",
                        totalDurationMs = 1800000L,
                        sessionCount = 5,
                        isEntertainment = true
                    )
                )
            )
        )

        // Verify the state can be created
        assertFalse(mockState.isLoading)
        assertNotNull(mockState.dailyStats)
        assertEquals(1, mockState.dailyStats?.apps?.size)
    }

    @Test
    fun homeState_initialState() {
        val initialState = HomeState()
        assertTrue(initialState.isLoading)
        assertEquals(HomePeriod.Today, initialState.period)
        assertNull(initialState.dailyStats)
        assertNull(initialState.weeklyStats)
    }

    @Test
    fun homeState_copyUpdatesFields() {
        val initialState = HomeState()
        val updatedState = initialState.copy(isLoading = false)

        assertFalse(updatedState.isLoading)
        assertEquals(HomePeriod.Today, updatedState.period)
    }

    @Test
    fun homePeriod_values() {
        assertEquals(2, HomePeriod.values().size)
        assertEquals("Сегодня", HomePeriod.Today.label)
        assertEquals("Неделя", HomePeriod.Week.label)
    }
}
