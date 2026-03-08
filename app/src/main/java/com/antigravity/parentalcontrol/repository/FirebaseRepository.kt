package com.antigravity.parentalcontrol.repository

import android.util.Log
import com.antigravity.parentalcontrol.models.HistoryEvent
import com.antigravity.parentalcontrol.models.NotificationEvent
import com.antigravity.parentalcontrol.models.AppInfo
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.Query

object FirebaseRepository {
    const val DB_URL = "https://parental-control-f6ee0-default-rtdb.firebaseio.com/"
    private var database: DatabaseReference? = null
    private var deviceId: String = "unknown_device"

    private fun getDatabase(): DatabaseReference {
        return database ?: synchronized(this) {
            database ?: FirebaseDatabase.getInstance(DB_URL).reference.also { database = it }
        }
    }

    private var isSynced = false

    fun init(id: String) {
        if (deviceId == id && database != null) return
        deviceId = id
        Log.d("Firebase", "Initializing Repository with ID: $deviceId at $DB_URL")
        
        val db = getDatabase()
        
        // 3. Stability Fix: Only keep the CRITICAL 'blocked_apps' node synced.
        // Query-based keepSynced (like limitToLast) is prone to SDK internal NPEs.
        db.child(getPath()).child("blocked_apps").keepSynced(true)
        db.child(getPath()).child("history").keepSynced(true)
        db.child(getPath()).child("notifications").keepSynced(true)
        db.child(getPath()).child("alerts").keepSynced(true)
    }

    private fun getPath() = "devices/$deviceId"

    fun cleanupOldData(daysThreshold: Int = 7) {
        val cutoffTimestamp = System.currentTimeMillis() - (daysThreshold * 24 * 60 * 60 * 1000L)
        
        // Cleanup History
        getDatabase().child(getPath()).child("history").orderByChild("timestamp").endAt(cutoffTimestamp.toDouble())
            .get().addOnSuccessListener { snapshot ->
                snapshot.children.forEach { it.ref.removeValue() }
                Log.d("Firebase", "Cleaned up old history entries")
            }

        // Cleanup Notifications
        getDatabase().child(getPath()).child("notifications").orderByChild("timestamp").endAt(cutoffTimestamp.toDouble())
            .get().addOnSuccessListener { snapshot ->
                snapshot.children.forEach { it.ref.removeValue() }
                Log.d("Firebase", "Cleaned up old notification entries")
            }

        // Cleanup Alerts
        getDatabase().child(getPath()).child("alerts").orderByChild("timestamp").endAt(cutoffTimestamp.toDouble())
            .get().addOnSuccessListener { snapshot ->
                snapshot.children.forEach { it.ref.removeValue() }
                Log.d("Firebase", "Cleaned up old alert entries")
            }

        // Cleanup Usage
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        val cutoffDate = sdf.format(java.util.Date(cutoffTimestamp))
        getDatabase().child(getPath()).child("usage").get().addOnSuccessListener { snapshot ->
            snapshot.children.forEach { daySnapshot ->
                val dateString = daySnapshot.key
                if (dateString != null && dateString < cutoffDate) {
                    daySnapshot.ref.removeValue()
                }
            }
            Log.d("Firebase", "Cleaned up old usage entries")
        }
    }

    // Child Data Uploads
    fun uploadUsageStats(dateString: String, usageMap: Map<String, Long>) {
        getDatabase().child(getPath()).child("usage").child(dateString).setValue(usageMap)
            .addOnSuccessListener { Log.d("Firebase", "Usage stats synced for $dateString") }
            .addOnFailureListener { Log.e("Firebase", "Failed to upload usage stats: ${it.message}") }
    }

    fun uploadNotification(event: NotificationEvent) {
        val key = getDatabase().child(getPath()).child("notifications").push().key ?: return
        getDatabase().child(getPath()).child("notifications").child(key).setValue(event)
            .addOnSuccessListener { Log.d("Firebase", "Notification synced successfully") }
            .addOnFailureListener { Log.e("Firebase", "Failed to upload notification: ${it.message}") }
    }

    fun uploadAlert(event: com.antigravity.parentalcontrol.models.AlertEvent) {
        val ref = getDatabase().child(getPath()).child("alerts").push()
        val key = ref.key ?: return
        val completeEvent = event.copy(id = key)
        ref.setValue(completeEvent)
            .addOnSuccessListener { Log.d("Firebase", "Alert synced successfully") }
            .addOnFailureListener { Log.e("Firebase", "Failed to upload alert: ${it.message}") }
    }

    fun uploadHistory(event: HistoryEvent): String? {
        val ref = getDatabase().child(getPath()).child("history").push()
        val key = ref.key ?: return null
        ref.setValue(event)
            .addOnSuccessListener { Log.d("Firebase", "History event synced: ${event.title}") }
            .addOnFailureListener { 
                Log.e("Firebase", "Queueing History: ${event.title} (Reason: ${it.message})")
                // With persistence on, this will retry automatically
            }
        return key
    }

    fun updateHistoryTitle(key: String, title: String) {
        getDatabase().child(getPath()).child("history").child(key).child("title").setValue(title)
            .addOnSuccessListener { Log.d("Firebase", "Updated history title for key $key") }
            .addOnFailureListener { Log.e("Firebase", "Failed to update history title: ${it.message}") }
    }

    fun updateHistoryUrlAndTitle(key: String, url: String, title: String) {
        val updates = mapOf<String, Any>(
            "url" to url,
            "title" to title
        )
        getDatabase().child(getPath()).child("history").child(key).updateChildren(updates)
            .addOnSuccessListener { Log.d("Firebase", "Updated history redirect for key $key") }
            .addOnFailureListener { Log.e("Firebase", "Failed to update history redirect: ${it.message}") }
    }

    fun uploadInstalledApps(apps: List<AppInfo>, onResult: ((Boolean) -> Unit)? = null) {
        if (deviceId == "unknown_device") {
            Log.e("Firebase", "Attempted to upload apps with unknown_device ID")
            onResult?.invoke(false)
            return
        }
        Log.d("Firebase", "Uploading ${apps.size} apps to ${getPath()}/installed_apps")
        getDatabase().child(getPath()).child("installed_apps").setValue(apps)
            .addOnSuccessListener { 
                Log.d("Firebase", "App list uploaded successfully to ${getPath()}") 
                onResult?.invoke(true)
            }
            .addOnFailureListener { 
                Log.e("Firebase", "FAILED to upload app list to ${getPath()}: ${it.message}") 
                onResult?.invoke(false)
            }
    }

    fun listenForHistory(callback: (List<HistoryEvent>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val events = mutableListOf<HistoryEvent>()
                for (child in snapshot.children) {
                    val event = child.getValue(HistoryEvent::class.java)
                    if (event != null) events.add(event)
                }
                callback(events.reversed())
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to listen for history: ${error.message}")
            }
        }
        getDatabase().child(getPath()).child("history").limitToLast(100).addValueEventListener(listener)
        return listener
    }

    fun getHistoryOnce(callback: (List<HistoryEvent>) -> Unit) {
        getDatabase().child(getPath()).child("history").limitToLast(50).get().addOnSuccessListener { snapshot ->
            val events = mutableListOf<HistoryEvent>()
            for (child in snapshot.children) {
                val event = child.getValue(HistoryEvent::class.java)
                if (event != null) events.add(event)
            }
            callback(events.reversed())
        }.addOnFailureListener {
            Log.e("Firebase", "Failed to fetch history once: ${it.message}")
        }
    }

    fun listenForNotifications(callback: (List<NotificationEvent>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val events = mutableListOf<NotificationEvent>()
                for (child in snapshot.children) {
                    val event = child.getValue(NotificationEvent::class.java)
                    if (event != null) events.add(event)
                }
                callback(events.reversed())
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to listen for notifications: ${error.message}")
            }
        }
        getDatabase().child(getPath()).child("notifications").limitToLast(100).addValueEventListener(listener)
        return listener
    }

    fun getNotificationsOnce(callback: (List<NotificationEvent>) -> Unit) {
        getDatabase().child(getPath()).child("notifications").limitToLast(50).get().addOnSuccessListener { snapshot ->
            val events = mutableListOf<NotificationEvent>()
            for (child in snapshot.children) {
                val event = child.getValue(NotificationEvent::class.java)
                if (event != null) events.add(event)
            }
            callback(events.reversed())
        }.addOnFailureListener {
            Log.e("Firebase", "Failed to fetch notifications once: ${it.message}")
        }
    }

    fun listenForAlerts(callback: (List<com.antigravity.parentalcontrol.models.AlertEvent>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val alerts = mutableListOf<com.antigravity.parentalcontrol.models.AlertEvent>()
                for (child in snapshot.children) {
                    val alert = child.getValue(com.antigravity.parentalcontrol.models.AlertEvent::class.java)
                    if (alert != null) alerts.add(alert)
                }
                callback(alerts.reversed())
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to listen for alerts: ${error.message}")
            }
        }
        getDatabase().child(getPath()).child("alerts").limitToLast(100).addValueEventListener(listener)
        return listener
    }

    fun getAlertsOnce(callback: (List<com.antigravity.parentalcontrol.models.AlertEvent>) -> Unit) {
        getDatabase().child(getPath()).child("alerts").limitToLast(50).get().addOnSuccessListener { snapshot ->
            val alerts = mutableListOf<com.antigravity.parentalcontrol.models.AlertEvent>()
            for (child in snapshot.children) {
                val alert = child.getValue(com.antigravity.parentalcontrol.models.AlertEvent::class.java)
                if (alert != null) alerts.add(alert)
            }
            callback(alerts.reversed())
        }.addOnFailureListener {
            Log.e("Firebase", "Failed to fetch alerts once: ${it.message}")
        }
    }

    fun listenForInstalledApps(callback: (List<AppInfo>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val apps = mutableListOf<AppInfo>()
                for (child in snapshot.children) {
                    val app = child.getValue(AppInfo::class.java)
                    if (app != null) apps.add(app)
                }
                callback(apps.sortedBy { it.appName })
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to listen for installed apps: ${error.message}")
            }
        }
        getDatabase().child(getPath()).child("installed_apps").addValueEventListener(listener)
        return listener
    }

    fun listenForUsageStats(callback: (Map<String, Map<String, Long>>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val usageData = mutableMapOf<String, Map<String, Long>>()
                for (dateSnapshot in snapshot.children) {
                    val dateStr = dateSnapshot.key ?: continue
                    val dailyMap = mutableMapOf<String, Long>()
                    for (appSnapshot in dateSnapshot.children) {
                        val packageName = appSnapshot.key?.replace("_", ".") ?: continue
                        val timeMs = appSnapshot.getValue(Long::class.java) ?: 0L
                        dailyMap[packageName] = timeMs
                    }
                    usageData[dateStr] = dailyMap
                }
                callback(usageData)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to listen for usage stats: ${error.message}")
            }
        }
        getDatabase().child(getPath()).child("usage").addValueEventListener(listener)
        return listener
    }

    // Parent Command Listeners
    fun listenForBlockedApps(callback: (Set<String>) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val blockedSet = mutableSetOf<String>()
                for (child in snapshot.children) {
                    val packageName = child.key ?: continue
                    val isBlocked = child.getValue(Boolean::class.java) ?: false
                    if (isBlocked) blockedSet.add(packageName)
                }
                callback(blockedSet)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to listen for blocked apps: ${error.message}")
            }
        }
        getDatabase().child(getPath()).child("blocked_apps").addValueEventListener(listener)
        return listener
    }

    fun stopListening(pathSuffix: String, listener: ValueEventListener) {
        getDatabase().child(getPath()).child(pathSuffix).removeEventListener(listener)
    }

    // Parent Action: Block an App (any package name)
    fun setAppBlocked(packageName: String, blocked: Boolean) {
        val sanitizedPath = packageName.replace(".", "_")
        getDatabase().child(getPath()).child("blocked_apps").child(sanitizedPath).setValue(blocked)
    }

    // Default App Blocking Settings
    fun setBlockNewAppsDefault(enabled: Boolean) {
        getDatabase().child(getPath()).child("settings").child("block_new_apps").setValue(enabled)
            .addOnSuccessListener { Log.d("Firebase", "Updated block_new_apps to $enabled") }
            .addOnFailureListener { Log.e("Firebase", "Failed to update block_new_apps: ${it.message}") }
    }

    fun listenForBlockNewAppsDefault(callback: (Boolean) -> Unit): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isEnabled = snapshot.getValue(Boolean::class.java) ?: false
                callback(isEnabled)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Failed to listen for block_new_apps: ${error.message}")
            }
        }
        getDatabase().child(getPath()).child("settings").child("block_new_apps").addValueEventListener(listener)
        return listener
    }

    fun getBlockNewAppsDefaultOnce(callback: (Boolean) -> Unit) {
        getDatabase().child(getPath()).child("settings").child("block_new_apps").get()
            .addOnSuccessListener { snapshot ->
                val isEnabled = snapshot.getValue(Boolean::class.java) ?: false
                callback(isEnabled)
            }
            .addOnFailureListener {
                Log.e("Firebase", "Failed to fetch block_new_apps once: ${it.message}")
                callback(false) // Default to false on error
            }
    }

    // Sync Mechanism: 
    // This uses Firebase Realtime Database with Persistent Listeners.
    // 1. Parent writes a 'Boolean' to the 'blocked_apps' node.
    // 2. Firebase Server pushes the update immediately to the child app via a long-lived Socket.
    // 3. Child app's 'UsageMonitoringService' receives the 'onDataChange' event and updates its local 'blockedPackages' set.
}
