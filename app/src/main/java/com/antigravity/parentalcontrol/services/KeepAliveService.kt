package com.antigravity.parentalcontrol.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.antigravity.parentalcontrol.AppModeManager
import com.antigravity.parentalcontrol.R
import com.antigravity.parentalcontrol.models.HistoryEvent
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.antigravity.parentalcontrol.utils.isAccessibilityServiceEnabled
import com.antigravity.parentalcontrol.utils.isNotificationListenerEnabled
import android.service.notification.NotificationListenerService
import java.util.Timer
import java.util.TimerTask

class KeepAliveService : Service() {

    private var permissionTimer: Timer? = null
    private var isPermissionGuardStarted = false

    companion object {
        private const val CHANNEL_ID = "monitoring_channel"
        private const val NOTIFICATION_ID = 1001

        private var lastAlertStateStr: String? = null
        @Volatile var isNotificationListenerConnected = false

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Catches ForegroundServiceStartNotAllowedException on Android 12+
                // Service may not start, but the app won't crash.
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            context.stopService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (AppModeManager.getAppMode(this) != AppModeManager.Mode.CHILD) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ShuGo Service Active")
            .setContentText("Protecting your device")
            .setSmallIcon(R.drawable.ic_parental_shield)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)

        startPermissionGuard()

        return START_STICKY
    }

    private fun startPermissionGuard() {
        if (isPermissionGuardStarted) return
        isPermissionGuardStarted = true

        permissionTimer?.cancel()
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                checkAndAlertPermissions()
                syncNotificationListener()
            }
        }, 10000, 60000)
        permissionTimer = timer
    }

    private fun syncNotificationListener() {
        if (AppModeManager.getAppMode(this@KeepAliveService) != AppModeManager.Mode.CHILD) return
        
        // Fix #7: Only rebind if listener is enabled but NOT currently connected
        if (isNotificationListenerEnabled(this@KeepAliveService) && !isNotificationListenerConnected) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    NotificationListenerService.requestRebind(
                        ComponentName(this@KeepAliveService, NotificationCollectorService::class.java)
                    )
                }
            } catch (e: Exception) {
            }
        }
    }


    private fun checkAndAlertPermissions() {
        if (AppModeManager.getAppMode(this) != AppModeManager.Mode.CHILD) return

        val isAccessibilityEnabled = isAccessibilityServiceEnabled(this)
        val isNotificationEnabled = isNotificationListenerEnabled(this)
        
        val pm = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pm.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            pm.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        val isUsageEnabled = mode == android.app.AppOpsManager.MODE_ALLOWED
        
        val isOverlayEnabled = Settings.canDrawOverlays(this)
        
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminName = ComponentName(this, com.antigravity.parentalcontrol.receivers.ParentalDeviceAdmin::class.java)
        val isDeviceAdminEnabled = dpm.isAdminActive(adminName)

        val missing = mutableListOf<String>()
        if (!isAccessibilityEnabled) missing.add("Accessibility")
        if (!isNotificationEnabled) missing.add("Notifications")
        if (!isUsageEnabled) missing.add("Usage")
        if (!isOverlayEnabled) missing.add("Overlay")
        if (!isDeviceAdminEnabled) missing.add("Device Admin")

        val alertMessage = if (missing.isNotEmpty()) {
            "Missing permissions: ${missing.joinToString(", ")}"
        } else {
            "All permissions are allowed"
        }

        // Always alert on missing permissions so the parent gets a push notification immediately
        if (missing.isNotEmpty() || lastAlertStateStr != alertMessage) {
            lastAlertStateStr = alertMessage
            val alert = com.antigravity.parentalcontrol.models.AlertEvent(message = alertMessage)
            FirebaseRepository.uploadAlert(alert)
        }
    }

    // Helper functions removed and moved to ServiceUtils.kt


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ShuGo Service"
            val descriptionText = "Ensures device protection remains active."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        permissionTimer?.cancel()
        permissionTimer = null
        isPermissionGuardStarted = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
