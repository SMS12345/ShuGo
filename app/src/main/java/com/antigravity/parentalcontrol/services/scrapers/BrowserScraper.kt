package com.antigravity.parentalcontrol.services.scrapers

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.net.Uri
import java.net.URLDecoder
import com.antigravity.parentalcontrol.models.HistoryEvent
import com.antigravity.parentalcontrol.repository.FirebaseRepository

object BrowserScraper {

    private var lastUrl: String? = null
    private var lastBrowserTitle: String? = null
    private var lastHistoryKey: String? = null
    private var lastScrapeTimeMillis: Long = 0

    fun reset() {
        lastUrl = null
        lastBrowserTitle = null
        lastHistoryKey = null
        lastScrapeTimeMillis = 0
    }

    private val BROWSER_HINT_STRINGS = listOf(
        "search or type url",
        "search or type web address",
        "search or type address",
        "search or type",
        "search or web address",
        "search...",
        "search…",   // Unicode ellipsis
        "search",
        "http://",
        "https://",
        "about:blank",
        "loading...",
        "loading"
    )

    private fun isValidBrowserText(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        val lowerText = text.lowercase().trim()
        return BROWSER_HINT_STRINGS.none { lowerText == it || lowerText.startsWith(it) && lowerText.length < it.length + 5 }
    }

    fun scrape(service: AccessibilityService) {
        val rootNode = service.rootInActiveWindow ?: return

        try {
            var url: String? = null
            var title: String? = null
            
            // Optimization: Try direct ID lookup for major browsers
            val urlNodeIds = arrayOf(
                "com.android.chrome:id/url_bar", 
                "com.android.chrome:id/location_bar",
                "com.google.android.googlequicksearchbox:id/googleapp_search_box",
                "com.google.android.googlequicksearchbox:id/search_box",
                "com.google.android.googlequicksearchbox:id/search_box_text",
                "com.google.android.googlequicksearchbox:id/search_query_text",
                "com.google.android.googlequicksearchbox:id/search_edit_text",
                "com.sec.android.app.sbrowser:id/location_bar_text", // Samsung
                "org.mozilla.firefox:id/url_bar_title",               // Firefox
                "com.microsoft.emmx:id/url_bar",                      // Edge
                "com.brave.browser:id/url_bar"                        // Brave
            )
            for (id in urlNodeIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    val firstNode = nodes[0]
                    // CRITICAL: If the URL bar is focused, the user is likely typing.
                    // Skip scraping to avoid noise. EXCEPTION: Google Search App often 
                    // holds focus even when showing results.
                    if (firstNode.isFocused && !id.contains("googlequicksearchbox")) {
                        nodes.forEach { it.recycle() }
                        return 
                    }
                    val text = firstNode.text?.toString()
                    nodes.forEach { it.recycle() }
                    if (isValidBrowserText(text)) {
                        url = text
                        break
                    }
                }
            }

            val titleNodeIds = arrayOf(
                "com.android.chrome:id/title",
                "com.google.android.googlequicksearchbox:id/title"
            )
            for (id in titleNodeIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (!nodes.isNullOrEmpty()) {
                    val text = nodes[0].text?.toString()
                    nodes.forEach { it.recycle() }
                    if (!text.isNullOrEmpty()) {
                        title = text
                        break
                    }
                }
            }

            // Fallback to recursive search for any app (Handles any WebView or custom address bar)
            if (url == null || title == null) {
                val map = mutableMapOf<String, String>()
                findBrowserData(rootNode, 0, map)
                if (url == null) url = map["url"]
                if (title == null) title = map["title"]
            }

            if (url != null) {
                // Determine the best title and type for History
                var finalTitle = title
                var type = "WEB"
                
                // 1. Check for Google Search
                val googleQuery = extractGoogleQuery(url)
                if (googleQuery != null) {
                    finalTitle = "Search: $googleQuery"
                } else if (url.contains("youtube.com") || url.contains("m.youtube.com")) {
                    // 2. Check for YouTube in browser
                    type = "YOUTUBE"
                    if (finalTitle == null || finalTitle.contains("youtube", ignoreCase = true)) {
                        finalTitle = "YouTube Web"
                    }
                }
                
                // 3. Skip if title indicates loading
                if (finalTitle != null && (finalTitle.lowercase().contains("loading...") || finalTitle.lowercase() == "loading")) {
                    return
                }
                
                // 4. Amazon 'Delivering to' edge case
                if (finalTitle != null && url.contains("amazon.") && finalTitle.lowercase().contains("delivering to")) {
                    finalTitle = "Amazon Web"
                }

                // Final safety: if title is still missing or just a URL, use a placeholder
                if (finalTitle.isNullOrEmpty() || finalTitle.contains(".") || finalTitle.startsWith("http")) {
                   if (type == "YOUTUBE") finalTitle = "YouTube Web"
                   else if (googleQuery == null) finalTitle = "Web Page"
                }

                val currentTime = System.currentTimeMillis()
                val isRedirect = (currentTime - lastScrapeTimeMillis < 2500) // 2.5 seconds threshold for redirect

                if (url != lastUrl) {
                    lastUrl = url
                    lastBrowserTitle = finalTitle
                    lastScrapeTimeMillis = currentTime
                    
                    if (isRedirect && lastHistoryKey != null) {
                        FirebaseRepository.updateHistoryUrlAndTitle(lastHistoryKey!!, url, finalTitle ?: "Web Page")
                        Log.d("UsageMonitoring", "Redirect Detected. Updated History: $url | Title: $finalTitle")
                    } else {
                        val history = HistoryEvent(type = type, url = url, title = finalTitle)
                        lastHistoryKey = FirebaseRepository.uploadHistory(history)
                        Log.d("UsageMonitoring", "Captured $type History: $url | Title: $finalTitle")
                    }
                } else if (finalTitle != lastBrowserTitle && finalTitle != "Web Page") {
                    lastBrowserTitle = finalTitle
                    lastScrapeTimeMillis = currentTime
                    lastHistoryKey?.let { key ->
                        FirebaseRepository.updateHistoryTitle(key, finalTitle ?: "Web Page")
                        Log.d("UsageMonitoring", "Updated $type History Title: $url | New Title: $finalTitle")
                    }
                }
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun extractGoogleQuery(url: String): String? {
        if (!url.contains("google.") || (!url.contains("/search") && !url.contains("q="))) return null
        return try {
            val fullUrl = if (url.startsWith("http")) url else "https://$url"
            val uri = Uri.parse(fullUrl)
            
            // Try common search parameters
            val query = uri.getQueryParameter("q") ?: 
                        uri.getQueryParameter("as_q") ?: 
                        uri.getQueryParameter("query")
            
            if (query != null) return query

            // Regex fallback if query parameter isn't easily parsed
            val regex = Regex("[?&](?:q|as_q|query)=([^&]+)")
            regex.find(url)?.groupValues?.get(1)?.let { 
                return URLDecoder.decode(it, "UTF-8")
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun findBrowserData(node: AccessibilityNodeInfo, depth: Int, result: MutableMap<String, String>) {
        if (depth > 30 || (result.containsKey("url") && result.containsKey("title"))) return
        if (!node.isVisibleToUser) return

        val id = node.viewIdResourceName ?: ""
        val text = node.text?.toString()
        val className = node.className?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString()

        // 1. Check for URL/Search Bar patterns (Generic for any browser)
        val lowerId = id.lowercase()
        if (className.contains("EditText") || className.contains("TextView") || className.contains("View")) {
            if (lowerId.contains("url") || lowerId.contains("location") || lowerId.contains("search") || lowerId.contains("address")) {
                // SKIP if actively being typed in
                if (node.isFocused) return 
                
                if (isValidBrowserText(text)) {
                    if (text!!.contains(".") || text.contains("http") || text.contains("/")) {
                        result["url"] = text
                    } else if (text.length >= 2) {
                        result["url"] = "google.com/search?q=${text.replace(" ", "+")}"
                        result["title"] = "Search: $text"
                    }
                }
            }
        }
        
        // 2. Check for WebView components (Universal History)
        if (className.contains("WebView", ignoreCase = true)) {
            result["webview_found"] = "true"
            if (!contentDesc.isNullOrEmpty()) {
                result["title"] = contentDesc
            } else if (!text.isNullOrEmpty()) {
                result["title"] = text
            }
        } else if (id.endsWith(":id/title") && !text.isNullOrEmpty()) {
            result["title"] = text
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                findBrowserData(child, depth + 1, result)
            } finally {
                child.recycle()
            }
        }
    }
}
