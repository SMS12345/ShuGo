package com.antigravity.parentalcontrol.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.antigravity.parentalcontrol.AppModeManager
import com.antigravity.parentalcontrol.models.NotificationEvent
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.antigravity.parentalcontrol.utils.IdCache
import com.antigravity.parentalcontrol.utils.isNotificationListenerEnabled
import android.os.Handler
import android.os.Looper

class NotificationCollectorService : NotificationListenerService() {

    private var lastNotificationText: String? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        KeepAliveService.isNotificationListenerConnected = true
        if (AppModeManager.getAppMode(applicationContext) != AppModeManager.Mode.CHILD) {
            Handler(Looper.getMainLooper()).postDelayed({
                try { requestUnbind() } catch (e: Exception) {}
            }, 1000)
            return
        }
        
        val deviceId = IdCache.getDeviceId(applicationContext)
        FirebaseRepository.init(deviceId)
        Log.d("NotificationCollector", "Service Connected & Initialized")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        KeepAliveService.isNotificationListenerConnected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Only monitor if in CHILD mode
        if (AppModeManager.getAppMode(applicationContext) != AppModeManager.Mode.CHILD) return

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if (title.isNotEmpty() || text.isNotEmpty()) {
            val combinedText = "[$packageName] $title: $text"
            if (combinedText != lastNotificationText) {
                lastNotificationText = combinedText
                val event = NotificationEvent(
                    packageName = packageName,
                    title = title,
                    text = text
                )
                Log.d("NotificationCollector", "Posted: $combinedText")
                FirebaseRepository.uploadNotification(event)
            }
        }
    }
}
