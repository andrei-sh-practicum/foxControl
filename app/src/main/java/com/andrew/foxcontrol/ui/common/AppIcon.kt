package com.andrew.foxcontrol.ui.common

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.andrew.foxcontrol.core.util.IconCache
import com.andrew.foxcontrol.core.util.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

/**
 * Loads and renders an app icon by package name.
 * Uses an in-memory cache to avoid redundant PackageManager calls.
 * Falls back to a Settings icon if the app cannot be found or the icon cannot be rendered.
 */
@Composable
fun AppIcon(
    packageName: String,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var bitmap by remember(packageName) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(packageName) {
        // Check cache first
        IconCache.get(packageName)?.let { cached ->
            bitmap = cached
            return@LaunchedEffect
        }

        val result = withContext(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val appIcon = pm.getApplicationIcon(packageName)
                appIcon.toBitmap()
            } catch (e: Exception) {
                Log.e("AppIcon", "Failed to load icon for $packageName: ${e.message}")
                null
            }
        }

        result?.let {
            IconCache.put(packageName, it)
            bitmap = it
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(size)
        )
    } else {
        Icon(
            Icons.Default.Settings,
            contentDescription = null,
            modifier = modifier.size(size)
        )
    }
}
