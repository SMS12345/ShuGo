package com.antigravity.parentalcontrol.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object IconFetcher {
    // Fix #4: Bounded LRU cache (max 100 entries) instead of unbounded map
    private val cache = object : android.util.LruCache<String, String?>(100) {}
    
    // Limit concurrent Play Store requests to avoid rate limiting
    private val semaphore = java.util.concurrent.Semaphore(3)
    private val systemIconMap = mapOf(
        "com.android.settings" to "https://raw.githubusercontent.com/google/material-design-icons/master/png/action/settings/materialicons/48dp/2x/baseline_settings_black_48dp.png",
        "com.google.android.settings" to "https://raw.githubusercontent.com/google/material-design-icons/master/png/action/settings/materialicons/48dp/2x/baseline_settings_black_48dp.png",
        "com.android.calendar" to "https://raw.githubusercontent.com/google/material-design-icons/master/png/action/event/materialicons/48dp/2x/baseline_event_black_48dp.png",
        "com.google.android.calendar" to "https://raw.githubusercontent.com/google/material-design-icons/master/png/action/event/materialicons/48dp/2x/baseline_event_black_48dp.png",
        "com.android.camera" to "https://raw.githubusercontent.com/google/material-design-icons/master/png/image/photo_camera/materialicons/48dp/2x/baseline_photo_camera_black_48dp.png",
        "com.android.gallery" to "https://raw.githubusercontent.com/google/material-design-icons/master/png/image/image/materialicons/48dp/2x/baseline_image_black_48dp.png",
        "com.android.contacts" to "https://raw.githubusercontent.com/google/material-design-icons/master/png/social/person/materialicons/48dp/2x/baseline_person_black_48dp.png",
        "com.android.phone" to "https://raw.githubusercontent.com/google/material-design-icons/master/png/communication/call/materialicons/48dp/2x/baseline_call_black_48dp.png",
        "com.android.dialer" to "https://raw.githubusercontent.com/google/material-design-icons/master/png/communication/call/materialicons/48dp/2x/baseline_call_black_48dp.png"
    )

    private const val NULL_SENTINEL = "__NULL__"

    suspend fun getIconUrl(packageName: String): String? {
        val cached = cache.get(packageName)
        if (cached != null) {
            return if (cached == NULL_SENTINEL) null else cached
        }

        // 1. Check for hardcoded system app icons
        systemIconMap[packageName]?.let {
            cache.put(packageName, it)
            return it
        }

        return withContext(Dispatchers.IO) {
            try {
                semaphore.acquire()
                try {
                    val url = "https://play.google.com/store/apps/details?id=$packageName"
                    
                    val document = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .timeout(5000)
                        .get()
                        
                    val metaTags = document.getElementsByTag("meta")
                    var iconUrl: String? = null
                    
                    for (meta in metaTags) {
                        if (meta.attr("property") == "og:image") {
                            iconUrl = meta.attr("content")
                            if (!iconUrl.contains("favicon")) {
                                break
                            } else {
                                iconUrl = null
                            }
                        }
                    }
                    
                    cache.put(packageName, iconUrl ?: NULL_SENTINEL)
                    iconUrl
                } finally {
                    semaphore.release()
                }
            } catch (e: Exception) {
                Log.e("IconFetcher", "Failed to get icon from Play Store for $packageName: ${e.message}")
                cache.put(packageName, NULL_SENTINEL)
                null
            }
        }
    }
}
