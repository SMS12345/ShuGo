package com.antigravity.parentalcontrol.utils

import android.content.Context
import com.antigravity.parentalcontrol.AppModeManager

object IdCache {
    @Volatile
    private var cachedDeviceId: String? = null

    fun getDeviceId(context: Context): String {
        return cachedDeviceId ?: synchronized(this) {
            cachedDeviceId ?: AppModeManager.getDeviceId(context).also { cachedDeviceId = it }
        }
    }
}
