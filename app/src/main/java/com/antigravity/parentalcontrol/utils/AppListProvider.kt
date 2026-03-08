package com.antigravity.parentalcontrol.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.antigravity.parentalcontrol.models.AppInfo

object AppListProvider {

    // Fix #11: Blocklist of truly unwanted system entries instead of hiding all system apps
    private val BLOCKLIST = setOf(
        "com.android.settings",
        "com.android.systemui",
        "com.android.providers.settings",
        "com.android.inputmethod.latin",
        "com.google.android.inputmethod.latin",
        "com.android.shell"
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
