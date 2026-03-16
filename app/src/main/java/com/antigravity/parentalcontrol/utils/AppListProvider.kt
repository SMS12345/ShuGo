package com.antigravity.parentalcontrol.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.antigravity.parentalcontrol.models.AppInfo

object AppListProvider {

    // Blocklist of system entries that have launcher intents but are not meaningful to block
    private val BLOCKLIST = setOf(
        // Core system noise (original)
        "com.android.settings",
        "com.android.systemui",
        "com.android.providers.settings",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.android.shell",
        // Bug 4: Additional unwanted system apps
        "com.android.vending",                        // Play Store
        "com.google.android.apps.safetyhub",          // Personal Safety
        "com.android.stk",                            // SIM Toolkit
        "com.android.switchaccess",                   // Switch Access
        "com.google.android.marvin.talkback",         // TalkBack / Switch Access
        "com.android.traceur",                        // System Tracing
        "com.samsung.android.app.switchwidget",       // Samsung Switch
        "com.android.server.telecom",                 // Android Switch / Telecom
        "com.google.android.apps.restore",            // Backup & Restore
        "com.android.nfc",                            // NFC service
        "com.android.soundrecorder",                  // Sound Recorder (OEM)
        "com.android.cellbroadcastreceiver",          // Emergency alerts
        "com.android.carrierconfig"                   // Carrier services UI
    )

    fun getInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        
        return apps.mapNotNull { appInfo ->
            val pkgName = appInfo.packageName
            val launchIntent = pm.getLaunchIntentForPackage(pkgName)
            
            // Include any app that has a launch intent and isn't in the blocklist
            if (launchIntent != null && !BLOCKLIST.contains(pkgName) && pkgName != context.packageName) {
                AppInfo(
                    packageName = pkgName,
                    appName = pm.getApplicationLabel(appInfo).toString(),
                    isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            } else {
                null
            }
        }
    }
}
