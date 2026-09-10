package com.andrew.foxcontrol.ui.common

fun formatDuration(durationMs: Long): String {
    val hours = durationMs / (1000 * 60 * 60)
    val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)
    return if (hours > 0) {
        "${hours}ч ${minutes}м"
    } else {
        "${minutes}м"
    }
}
