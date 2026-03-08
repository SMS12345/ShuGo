package com.antigravity.parentalcontrol.models

data class AppUsageItem(
    val packageName: String,
    val appName: String,
    val timeMs: Long,
    val maxTimeMs: Long
)
