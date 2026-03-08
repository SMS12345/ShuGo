package com.antigravity.parentalcontrol.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun getRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        val minute = 60 * 1000L
        val hour = 60 * minute
        val day = 24 * hour

        return when {
            diff < minute -> "Just now"
            diff < 2 * minute -> "a minute ago"
            diff < 50 * minute -> "${diff / minute} minutes ago"
            diff < 90 * minute -> "an hour ago"
            diff < 24 * hour -> "${diff / hour} hours ago"
            diff < 48 * hour -> "yesterday"
            else -> "${diff / day} days ago"
        }
    }
    
    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDuration(timeMs: Long): String {
        if (timeMs <= 0) return "0m"
        val minutes = (timeMs / (1000 * 60)) % 60
        val hours = (timeMs / (1000 * 60 * 60))
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
}
