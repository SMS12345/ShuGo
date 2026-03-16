package com.antigravity.parentalcontrol.utils

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.antigravity.parentalcontrol.services.UsageMonitoringService

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedId = ComponentName(context, UsageMonitoringService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabledServices?.split(':')?.contains(expectedId) == true
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    // NotificationManagerCompat.getEnabledListenerPackages() is the correct public API.
    // Reading Settings.Secure "enabled_notification_listeners" directly can return null
    // on Android 12+ without READ_SECURE_SETTINGS, causing a false "denied" result.
    return NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
}

