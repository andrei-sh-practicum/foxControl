package com.andrew.foxcontrol.core.tracking

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object CategoryResolver {

    /**
     * Maps Android's ApplicationInfo.category int constant to a human-readable Russian string.
     * Returns "" for CATEGORY_UNDEFINED or any unknown value.
     */
    fun resolve(packageManager: PackageManager, packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            mapCategory(appInfo.category)
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        }
    }

    /**
     * Maps the int category constant to a localized display name.
     * This is NOT an enum — it's a one-way translation of Android SDK constants.
     * The resulting string is stored as plain text and displayed as-is.
     */
    fun mapCategory(category: Int): String {
        return when (category) {
            ApplicationInfo.CATEGORY_GAME -> "Игры"
            ApplicationInfo.CATEGORY_AUDIO -> "Аудио"
            ApplicationInfo.CATEGORY_VIDEO -> "Видео"
            ApplicationInfo.CATEGORY_IMAGE -> "Фото и видео"
            ApplicationInfo.CATEGORY_SOCIAL -> "Соцсети"
            ApplicationInfo.CATEGORY_NEWS -> "Новости"
            ApplicationInfo.CATEGORY_MAPS -> "Карты и навигация"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Продуктивность"
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Спец. возможности"
            else -> "" // CATEGORY_UNDEFINED (-1) and anything else
        }
    }
}
