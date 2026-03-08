package com.antigravity.parentalcontrol.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.antigravity.parentalcontrol.models.HistoryEvent
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.antigravity.parentalcontrol.utils.IdCache

class ParentalDeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Anti-Tamper Protection Enabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "Anti-Tamper Protection Disabled!", Toast.LENGTH_LONG).show()

        val deviceId = IdCache.getDeviceId(context)
        FirebaseRepository.init(deviceId)
        
        val alert = HistoryEvent(
            type = "SECURITY_ALERT",
            title = "CRITICAL: ANTI-TAMPER DISABLED",
            url = "Child removed Device Administrator privileges! The app can now be uninstalled."
        )
        FirebaseRepository.uploadHistory(alert)
    }
}
