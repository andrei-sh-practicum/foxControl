package com.andrew.foxcontrol.core.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache

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
 * In-memory cache for icon bitmaps keyed by package name.
 * Bounded by memory (1/16 of the app heap, sized in KB); least recently used icons
 * are evicted and simply reloaded on the next request. LruCache is thread-safe.
 */
object IconCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
    }

    fun get(packageName: String): Bitmap? = cache.get(packageName)

    fun put(packageName: String, bitmap: Bitmap) {
        cache.put(packageName, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }
}
