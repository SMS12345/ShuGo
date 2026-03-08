package com.antigravity.parentalcontrol.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.antigravity.parentalcontrol.AppModeManager
import com.antigravity.parentalcontrol.models.HistoryEvent
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.antigravity.parentalcontrol.utils.IdCache
import com.antigravity.parentalcontrol.workers.UsageSyncWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy

class UsageMonitoringService : AccessibilityService() {

    private val blockedPackages = mutableSetOf<String>()
    private lateinit var overlayManager: OverlayManager
    private val debounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var blockedListener: com.google.firebase.database.ValueEventListener? = null

    // Event-driven usage sync: debounce to max once per 5 minutes
    private var lastSyncTriggerTime = 0L
    private companion object {
        const val MIN_SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    private var isYoutubePolling = false
    private var lastActivePackage: String = ""
    
    // Bug 5 fix: trailing-edge cooldown for browser scraping
    private var lastBrowserScrapeTime = 0L
    private val BROWSER_SCRAPE_COOLDOWN = 2000L // 2 seconds

    // Bug 4 fix: only scrape known browser packages
    private val KNOWN_BROWSERS = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "com.sec.android.app.sbrowser", // Samsung Internet
        "org.mozilla.firefox", "org.mozilla.firefox_beta",
        "com.microsoft.emmx", // Edge
        "com.brave.browser",
        "com.opera.browser", "com.opera.mini.native",
        "com.UCMobile.intl", // UC Browser
        "com.google.android.googlequicksearchbox", // Google App
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser"
    )

    private val youtubePollRunnable = object : Runnable {
        override fun run() {
            if (AppModeManager.getAppMode(applicationContext) != AppModeManager.Mode.CHILD) return
            try {
                val root = rootInActiveWindow
                if (root?.packageName?.toString() == "com.google.android.youtube") {
                    com.antigravity.parentalcontrol.services.scrapers.YoutubeScraper.scrape(this@UsageMonitoringService)
                    debounceHandler.postDelayed(this, 1500)
                } else {
                    isYoutubePolling = false
                }
                // Bug 2 fix: Don't recycle root here — YoutubeScraper.scrape() manages its own
                // root lifecycle. Recycling here risks invalidating a shared node object.
            } catch (e: Exception) {
                Log.e("UsageMonitoring", "YouTube poll error", e)
                // Bug 1 fix: Retry after delay instead of giving up permanently
                debounceHandler.postDelayed(this, 3000)
            }
        }
    }

    private val browserScrapeRunnable = Runnable {
        try {
            com.antigravity.parentalcontrol.services.scrapers.BrowserScraper.scrape(this@UsageMonitoringService)
        } catch (e: Exception) {
            Log.e("UsageMonitoring", "Browser scrape error", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        overlayManager = OverlayManager(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (AppModeManager.getAppMode(applicationContext) != AppModeManager.Mode.CHILD) return

        val packageName = event.packageName?.toString() ?: ""
        
        // We rely on onServiceConnected and Application class for FirebaseRepository.init
        // to avoid expensive redundant calls here.
        // If the current window is ShuGo itself, do NOT automatically hide the overlay.
        // The overlay is part of ShuGo, so showing it triggers a window change to ShuGo.
        if (packageName == applicationContext.packageName) {
            return
        }

        val sanitizedPackageName = packageName.replace(".", "_")

        // 1. App Blocking (Priority: High)
        // Check on every relevant event to prevent bypass via Recent Apps/Split Screen
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            if (blockedPackages.contains(sanitizedPackageName)) {
                if (!overlayManager.isShowing()) {
                    overlayManager.showOverlay {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }
                Log.d("UsageMonitoring", "Blocked access to $packageName")
            } else if (overlayManager.isShowing() && packageName.isNotEmpty()) {
                // hideOverlay only if we are MOVING AWAY from a blocked app 
                // to a known non-blocked app. Ignore systemui updates.
                if (!packageName.startsWith("com.android.systemui")) {
                    overlayManager.hideOverlay()
                }
            }
        }

        // 2. History Tracking (YouTube & Web)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            
            if (packageName == "com.google.android.youtube") {
                if (!isYoutubePolling) {
                    isYoutubePolling = true
                    debounceHandler.postDelayed(youtubePollRunnable, 500)
                }
            } else if (KNOWN_BROWSERS.contains(packageName)) {
                // Bug 4 fix: Only scrape known browsers instead of all apps
                // Bug 5 fix: Trailing-edge cooldown to prevent flood starvation
                val now = System.currentTimeMillis()
                if (now - lastBrowserScrapeTime > BROWSER_SCRAPE_COOLDOWN) {
                    lastBrowserScrapeTime = now
                    debounceHandler.removeCallbacks(browserScrapeRunnable)
                    debounceHandler.postDelayed(browserScrapeRunnable, 500)
                }
            }
        }

        // Bug 3 fix: Reset scraper state when leaving YouTube or browser
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            packageName.isNotEmpty() && packageName != lastActivePackage) {
            if (lastActivePackage == "com.google.android.youtube" && packageName != "com.google.android.youtube") {
                com.antigravity.parentalcontrol.services.scrapers.YoutubeScraper.reset()
            }
            if (KNOWN_BROWSERS.contains(lastActivePackage) && !KNOWN_BROWSERS.contains(packageName)) {
                com.antigravity.parentalcontrol.services.scrapers.BrowserScraper.reset()
            }
            lastActivePackage = packageName
        }

        // 3. Event-driven usage sync on app switch (debounced)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            packageName.isNotEmpty() &&
            !packageName.startsWith("com.android.systemui") &&
            packageName != applicationContext.packageName) {
            triggerDebouncedUsageSync()
        }

    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (AppModeManager.getAppMode(applicationContext) != AppModeManager.Mode.CHILD) {
            disableSelf()
            return
        }

        // Ensure Repository is initialized with the persisted ID
        val deviceId = IdCache.getDeviceId(applicationContext)
        FirebaseRepository.init(deviceId)

        // Listen for blocked packages from Firebase
        blockedListener = FirebaseRepository.listenForBlockedApps { packages ->
            blockedPackages.clear()
            blockedPackages.addAll(packages)
            checkCurrentAppAndOverlay()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        blockedListener?.let { FirebaseRepository.stopListening("blocked_apps", it) }
        debounceHandler.removeCallbacksAndMessages(null)
    }

    private fun triggerDebouncedUsageSync() {
        val now = System.currentTimeMillis()
        if (now - lastSyncTriggerTime < MIN_SYNC_INTERVAL_MS) return
        lastSyncTriggerTime = now

        try {
            val workRequest = OneTimeWorkRequestBuilder<UsageSyncWorker>().build()
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                "EventDrivenUsageSync",
                ExistingWorkPolicy.KEEP, // Don't restart if already running
                workRequest
            )
            Log.d("UsageMonitoring", "Triggered event-driven usage sync")
        } catch (e: Exception) {
            Log.e("UsageMonitoring", "Failed to trigger usage sync", e)
        }
    }

    private fun checkCurrentAppAndOverlay() {
        val root = rootInActiveWindow ?: return
        val currentPackage = root.packageName?.toString() ?: ""
        root.recycle()
        if (currentPackage.isEmpty() || currentPackage == applicationContext.packageName) return
        
        // Never block the user's home launcher (prevents overlay loops on "Go Home")
        val isLauncher = try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply { 
                addCategory(android.content.Intent.CATEGORY_HOME) 
            }
            val resolveInfo = packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == currentPackage
        } catch (e: Exception) { false }
        
        if (isLauncher) {
            // Un-show if showing since we are safely home
            if (overlayManager.isShowing()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    overlayManager.hideOverlay()
                }
            }
            return
        }
        
        val sanitized = currentPackage.replace(".", "_")
        if (blockedPackages.contains(sanitized)) {
            if (!overlayManager.isShowing()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (!overlayManager.isShowing()) {
                        overlayManager.showOverlay {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                    }
                }
            }
        } else {
            if (overlayManager.isShowing()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    overlayManager.hideOverlay()
                }
            }
        }
    }
}
