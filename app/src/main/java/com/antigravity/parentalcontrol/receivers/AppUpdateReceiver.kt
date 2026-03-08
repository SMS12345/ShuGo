package com.antigravity.parentalcontrol.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antigravity.parentalcontrol.AppModeManager
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.antigravity.parentalcontrol.utils.AppListProvider
import com.antigravity.parentalcontrol.utils.IdCache

class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_ADDED || 
            intent.action == Intent.ACTION_PACKAGE_REMOVED ||
            intent.action == Intent.ACTION_PACKAGE_REPLACED) {
            
            // Only sync if in CHILD mode
            if (AppModeManager.getAppMode(context) == AppModeManager.Mode.CHILD) {
                Log.d("AppUpdateReceiver", "App change detected: ${intent.action}. Triggering sync...")
                
                val pendingResult = goAsync()
                Thread {
                    try {
                        val deviceId = IdCache.getDeviceId(context)
                        FirebaseRepository.init(deviceId)
                        // Re-sync installed apps first
                        val apps = AppListProvider.getInstalledApps(context)
                        FirebaseRepository.uploadInstalledApps(apps) { success ->
                            Log.d("AppUpdateReceiver", "Sync status after package change: $success")
                            
                            // Check if this was a fresh install and if default blocking is enabled
                            if (intent.action == Intent.ACTION_PACKAGE_ADDED && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                                val newPackageName = intent.data?.schemeSpecificPart
                                if (newPackageName != null) {
                                    FirebaseRepository.getBlockNewAppsDefaultOnce { shouldBlock ->
                                        if (shouldBlock) {
                                            Log.d("AppUpdateReceiver", "Auto-blocking newly installed app: $newPackageName")
                                            FirebaseRepository.setAppBlocked(newPackageName, true)
                                        }
                                        pendingResult.finish()
                                    }
                                    return@uploadInstalledApps
                                }
                            }
                            pendingResult.finish()
                        }
                    } catch (e: Exception) {
                        Log.e("AppUpdateReceiver", "Background sync failed", e)
                        pendingResult.finish()
                    }
                }.start()
            }
        }
    }
}
