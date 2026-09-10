package com.andrew.foxcontrol.core.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

/**
 * Converts any [Drawable] (BitmapDrawable, AdaptiveIconDrawable, VectorDrawable, etc.)
 * into a [Bitmap] suitable for rendering via Compose Image API.
 *
 * Returns null if the drawable is null, has invalid dimensions, or fails to draw.
 */
fun Drawable?.toBitmap(): Bitmap? {
    this ?: return null

    if (this is BitmapDrawable) {
        bitmap?.let { return it }
    }

    val width = intrinsicWidth.takeIf { it > 0 } ?: return null
    val height = intrinsicHeight.takeIf { it > 0 } ?: return null

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, width, height)
    draw(canvas)
    return bitmap
}

/**
 * Simple in-memory cache for icon bitmaps keyed by package name.
 * Thread-safe via ConcurrentHashMap.
 */
object IconCache {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun get(packageName: String): Bitmap? = cache[packageName]

    fun put(packageName: String, bitmap: Bitmap) {
        cache[packageName] = bitmap
    }

    fun clear() {
        cache.clear()
    }
}
