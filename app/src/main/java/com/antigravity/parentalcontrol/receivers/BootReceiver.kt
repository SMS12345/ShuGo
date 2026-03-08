package com.antigravity.parentalcontrol.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antigravity.parentalcontrol.AppModeManager
import com.antigravity.parentalcontrol.services.KeepAliveService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            Log.d("BootReceiver", "Service Recovery Triggered: $action")
            
            // Checking if CHILD mode to restart background monitoring
            if (context != null && AppModeManager.getAppMode(context) == AppModeManager.Mode.CHILD) {
                KeepAliveService.start(context)
                // Background services will auto-restart if configured correctly in Manifest
                // For Accessibility and Notification Listener, the system handles restart
            }
        }
    }
}
