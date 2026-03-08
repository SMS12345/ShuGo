package com.antigravity.parentalcontrol.services.scrapers

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.antigravity.parentalcontrol.models.HistoryEvent
import com.antigravity.parentalcontrol.repository.FirebaseRepository

object YoutubeScraper {

    private var lastYoutubeTitle: String? = null

    fun reset() {
        lastYoutubeTitle = null
    }

    fun scrape(service: AccessibilityService) {
        val rootNode = service.rootInActiveWindow ?: return
        try {
            val title = findVideoTitle(rootNode)
            
            if (title != null && title != lastYoutubeTitle && title.length > 1) {
                // Heuristic filtering for non-titles
                val lowerTitle = title.lowercase()
                val isStandaloneMetadata = lowerTitle.matches(Regex("^[\\d.,kmbt]+$")) ||
                                           lowerTitle.matches(Regex("^[\\d.,kmbt]+\\s+(view|views|review|reviews)$")) || 
                                           lowerTitle.matches(Regex("^\\d+\\s+(second|seconds|minute|minutes|hour|hours|day|days|week|weeks|month|months|year|years)\\s+ago$"))
                
                if (!isStandaloneMetadata) {
                    lastYoutubeTitle = title
                    val history = HistoryEvent(type = "YOUTUBE", title = title)
                    FirebaseRepository.uploadHistory(history)
                    Log.d("UsageMonitoring", "Captured YouTube Title: $title")
                }
            }
        } finally {
            rootNode.recycle()
        }
    }

    private fun checkAndRecycle(root: AccessibilityNodeInfo, id: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(id)
        if (nodes.isNotEmpty()) {
            nodes.forEach { it.recycle() }
            return true
        }
        return false
    }

    private fun findVideoTitle(root: AccessibilityNodeInfo): String? {
        // Quick check: does the screen have a player container? 
        // If not, it's highly unlikely we are watching a video (e.g. just scrolling feed without inline playback)
        var hasPlayer = checkAndRecycle(root, "com.google.android.youtube:id/watch_player") ||
                        checkAndRecycle(root, "com.google.android.youtube:id/player_view") ||
                        checkAndRecycle(root, "com.google.android.youtube:id/reel_player_overlay_container") ||
                        checkAndRecycle(root, "com.google.android.youtube:id/player_fragment_container") ||
                        checkAndRecycle(root, "com.google.android.youtube:id/time_bar_view")
        
        if (!hasPlayer) return null

        // 1. Specific Target: Look for common title IDs directly
        val targetIds = listOf(
            "com.google.android.youtube:id/title",
            "com.google.android.youtube:id/video_title",
            "com.google.android.youtube:id/shorts_player_title",
            "com.google.android.youtube:id/reel_title"
        )

        for (id in targetIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            for (node in nodes) {
                val text = node.text?.toString()?.trim()
                val isVisible = node.isVisibleToUser
                node.recycle()
                
                if (isVisible && !text.isNullOrEmpty() && text.length > 1) {
                    return cleanTitle(text)
                }
            }
        }

        // Return immediately if the direct ID scan didn't find the title.
        // We removed the recursive visual text scraper here because it accidentally
        // scrapes Playlist titles and ad text when playing from a playlist menu.
        return null
    }

    private fun extractAllVisibleTexts(node: AccessibilityNodeInfo, list: MutableList<String>, depth: Int) {
        if (depth > 20 || !node.isVisibleToUser) return
        val text = node.text?.toString()
        if (!text.isNullOrEmpty()) {
            list.add(text)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                extractAllVisibleTexts(child, list, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    private fun cleanTitle(t: String): String {
        var cleanTitle = t
        if (cleanTitle.contains(" by ") && cleanTitle.contains(" views")) {
            cleanTitle = cleanTitle.substringBefore(" by ").substringBefore(" views").trim()
        } else {
            val parts = cleanTitle.split(" - ")
            var titleEndIdx = parts.size
            for (i in parts.indices) {
                val p = parts[i]
                if (p.contains(" seconds") || p.contains(" minutes") || p.contains(" hours") || p.contains("Go to channel")) {
                    titleEndIdx = i
                    break
                }
            }
            if (titleEndIdx > 0 && titleEndIdx < parts.size) {
                cleanTitle = parts.take(titleEndIdx).joinToString(" - ").trim()
            }
        }
        return cleanTitle
    }
}
