package com.antigravity.parentalcontrol.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.antigravity.parentalcontrol.ParentDashboardActivity
import com.antigravity.parentalcontrol.R
import com.antigravity.parentalcontrol.models.AlertEvent
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.google.firebase.database.ValueEventListener

class ParentAlertService : Service() {

    private var alertsListener: ValueEventListener? = null
    private var serviceStartTime: Long = 0

    companion object {
        private const val CHANNEL_ID = "parent_alerts_channel"
        private const val FOREGROUND_ID = 2001
        private var lastAlertText: String? = null
        private var lastSpamAlertTime: Long = 0
    }

    override fun onCreate() {
        super.onCreate()
        serviceStartTime = System.currentTimeMillis()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ShuGo Alerts Active")
            .setContentText("Listening for child device alerts...")
            .setSmallIcon(R.drawable.ic_notifications)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(FOREGROUND_ID, notification)
        
        startListening()
        
        return START_STICKY
    }

    private fun startListening() {
        if (alertsListener != null) return
        
        alertsListener = FirebaseRepository.listenForAlerts { events ->
            if (events.isEmpty()) return@listenForAlerts
            
            val latestAlert = events.first()
            
            // Only fire push notification for new alerts after service started
            // We notify the parent if a permission is actively missing (even if it's a repeated warning)
            // but we throttle repeated missing permission warnings to once every 30 minutes.
            if (latestAlert.timestamp > serviceStartTime) {
                val isMissingPermissions = latestAlert.message.startsWith("Missing permissions:")
                val timeSinceLastSpam = System.currentTimeMillis() - lastSpamAlertTime
                
                if (latestAlert.message != lastAlertText || (isMissingPermissions && timeSinceLastSpam > 30 * 60 * 1000L)) {
                    lastAlertText = latestAlert.message
                    if (isMissingPermissions) {
                        lastSpamAlertTime = System.currentTimeMillis()
                    }
                    firePushNotification(latestAlert)
                }
            }
        }
    }

    private fun firePushNotification(alert: AlertEvent) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(this, ParentDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Child Device Alert")
            .setContentText(alert.message)
            .setSmallIcon(R.drawable.ic_warning)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(alert.timestamp.hashCode(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Parent Alerts"
            val descriptionText = "Notifications for missing permissions and alerts"
            val importance = NotificationManager.IMPORTANCE_HIGH
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
        alertsListener?.let { FirebaseRepository.stopListening("alerts", it) }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
