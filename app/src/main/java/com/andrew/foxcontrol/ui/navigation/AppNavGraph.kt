package com.andrew.foxcontrol.ui.navigation

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.andrew.foxcontrol.core.tracking.TrackingForegroundService
import com.andrew.foxcontrol.ui.onboarding.OnboardingScreen
import com.andrew.foxcontrol.ui.home.HomeScreen
import com.andrew.foxcontrol.ui.settings.SettingsScreen
import com.andrew.foxcontrol.ui.settings.PrivateSettingsScreen
import com.andrew.foxcontrol.ui.settings.EmailSettingsScreen
import com.andrew.foxcontrol.ui.settings.EmailRecipientsScreen
import com.andrew.foxcontrol.ui.vendor.VendorInstructionsScreen
import com.andrew.foxcontrol.ui.debug.DebugScreen
import com.andrew.foxcontrol.ui.appdetail.AppDetailScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Onboarding.route
) {
    val context = LocalContext.current
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onNavigationCompleted = {
                    // Start tracking service after onboarding completes
                    val intent = Intent(context, TrackingForegroundService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onAppDetailClick = { packageName ->
                    navController.navigate(Screen.AppDetail.createRoute(packageName))
                }
            )
        }

        composable(
            route = Screen.AppDetail.ROUTE_PATTERN,
            arguments = listOf(
                navArgument(Screen.AppDetail.ARG_PACKAGE_NAME) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString(Screen.AppDetail.ARG_PACKAGE_NAME) ?: return@composable
            AppDetailScreen(
                packageName = packageName,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = { navController.popBackStack() },
                onPrivateSettingsClick = {
                    navController.navigate(Screen.PrivateSettings.route)
                },
                onDebugClick = {
                    navController.navigate(Screen.Debug.route)
                }
            )
        }

        composable(Screen.PrivateSettings.route) {
            PrivateSettingsScreen(
                onBackClick = { navController.popBackStack() },
                onEmailSettingsClick = {
                    navController.navigate(Screen.EmailSettings.route)
                },
                onEmailRecipientsClick = {
                    navController.navigate(Screen.EmailRecipients.route)
                },
                onVendorInstructionsClick = {
                    navController.navigate(Screen.VendorInstructions.route)
                }
            )
        }

        composable(Screen.EmailSettings.route) {
            EmailSettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.EmailRecipients.route) {
            EmailRecipientsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.VendorInstructions.route) {
            VendorInstructionsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(Screen.Debug.route) {
            DebugScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
