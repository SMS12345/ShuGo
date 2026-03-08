package com.antigravity.parentalcontrol.utils

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.antigravity.parentalcontrol.services.UsageMonitoringService

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedId = ComponentName(context, UsageMonitoringService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabledServices?.split(':')?.contains(expectedId) == true
}

fun isNotificationListenerEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (!flat.isNullOrEmpty()) {
        val names = flat.split(":")
        for (name in names) {
            val componentName = ComponentName.unflattenFromString(name)
            if (componentName != null && componentName.packageName == pkgName) {
                return true
            }
        }
    }
    return false
}
