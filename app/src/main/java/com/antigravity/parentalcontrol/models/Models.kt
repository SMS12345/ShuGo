package com.antigravity.parentalcontrol.models

data class NotificationEvent(
    val id: String = "",
    val packageName: String = "",
    val title: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class AlertEvent(
    val id: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class HistoryEvent(
    val type: String = "", // "YOUTUBE" or "WEB"
    val title: String? = null,
    val url: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class AppInfo(
    val packageName: String = "",
    val appName: String = "",
    val isSystemApp: Boolean = false
)

data class BlockedApp(
    val packageName: String = "",
    val isBlocked: Boolean = false
)
