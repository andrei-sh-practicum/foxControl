package com.andrew.foxcontrol.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.andrew.foxcontrol.core.permissions.PermissionHelper
import com.andrew.foxcontrol.core.tracking.TrackingForegroundService
import com.andrew.foxcontrol.ui.navigation.AppNavGraph
import com.andrew.foxcontrol.ui.theme.FoxControlTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure tracking service is running if critical permissions are granted
        ensureTrackingServiceIsRunning()

        setContent {
            FoxControlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavGraph(navController = navController)
                }
            }
        }
    }

    /**
     * Ensure the tracking foreground service is running when critical permissions are granted.
     * This handles the case where the system killed the process and the user reopens the app.
     */
    private fun ensureTrackingServiceIsRunning() {
        if (PermissionHelper.areCriticalPermissionsGranted(this)) {
            val intent = Intent(this, TrackingForegroundService::class.java)
            ContextCompat.startForegroundService(this, intent)
        }
    }
}
