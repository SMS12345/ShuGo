package com.antigravity.parentalcontrol

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.app.AppOpsManager
import android.os.Process
import android.view.View
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.antigravity.parentalcontrol.databinding.ActivityChildDashboardBinding
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.antigravity.parentalcontrol.receivers.ParentalDeviceAdmin
import com.antigravity.parentalcontrol.utils.AppListProvider
import com.antigravity.parentalcontrol.utils.isAccessibilityServiceEnabled
import com.antigravity.parentalcontrol.utils.isNotificationListenerEnabled
import com.antigravity.parentalcontrol.workers.UsageSyncWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.ExistingWorkPolicy

class ChildDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChildDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        if (AppModeManager.getAppMode(this) != AppModeManager.Mode.CHILD) {
            finish()
            return
        }

        val deviceId = AppModeManager.getDeviceId(this)
        val userName = AppModeManager.getUsername(this)
        binding.tvWelcomeChild.text = "Welcome, $userName"
        binding.tvChildDeviceIdFooter.text = "Device ID: $deviceId"

        // Initialize Firebase with the 6-digit ID
        FirebaseRepository.init(deviceId)

        // Initial Sync (Background Thread)
        val syncThread = Thread {
            try {
                val context = applicationContext
                val installedApps = AppListProvider.getInstalledApps(context)
                FirebaseRepository.uploadInstalledApps(installedApps) { success ->
                    if (success) {
                        Log.d("Sync", "Initial app sync complete")
                    } else {
                        Log.e("Sync", "Initial app sync failed - check Firebase Rules")
                    }
                }
            } catch (e: Exception) {
                Log.e("Sync", "Launch sync exception", e)
            }
        }
        syncThread.name = "initial-sync-thread"
        syncThread.start()
        
        FirebaseRepository.cleanupOldData(7) // Prune logs older than 7 days
        
        // Schedule Daily Usage Sync & Pruning Routine
        val syncWorkRequest = PeriodicWorkRequestBuilder<UsageSyncWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UsageSyncWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            syncWorkRequest
        )

        checkPermissions()
        checkBatteryOptimization()
        checkDeviceAdmin()

        binding.btnFixPermissions.setOnClickListener {
            checkAndFixPermissions()
        }
    }

    private fun checkAndFixPermissions() {
        if (!isAccessibilityServiceEnabled(this)) {
            val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            builder.setTitle("Enable Accessibility")
            builder.setMessage("This app requires Accessibility to monitor app usage and block apps.\n\nOn Android 13+, this setting might be restricted. If it's grayed out, choose 'Open App Info' to enable 'Allow restricted settings' first.")
            builder.setPositiveButton("Open Accessibility") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                builder.setNeutralButton("Open App Info") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
            }
            builder.setNegativeButton("Cancel", null)
            builder.show()
        } else if (!isNotificationListenerEnabled(this)) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } else if (!hasUsageStatsPermission()) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } else if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        } else {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminName = ComponentName(this, ParentalDeviceAdmin::class.java)
            if (!dpm.isAdminActive(adminName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Protects device and prevents uninstallation.")
                }
                startActivity(intent)
            } else {
                checkPermissions()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatusButtons()
        
        // Trigger a sync if permission was just granted
        if (hasUsageStatsPermission()) {
            triggerOneTimeUsageSync()
        }
    }

    private fun updateServiceStatusButtons() {
        val isAccessibilityEnabled = isAccessibilityServiceEnabled(this)
        val isNotificationEnabled = isNotificationListenerEnabled(this)
        val isUsageEnabled = hasUsageStatsPermission()
        val isOverlayEnabled = Settings.canDrawOverlays(this)
        
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminName = ComponentName(this, ParentalDeviceAdmin::class.java)
        val isDeviceAdminEnabled = dpm.isAdminActive(adminName)

        // Update UI Status (Text & Color)
        setStatusUI(binding.statusAccessibility, isAccessibilityEnabled)
        setStatusUI(binding.statusUsage, isUsageEnabled)
        setStatusUI(binding.statusOverlay, isOverlayEnabled)
        setStatusUI(binding.statusDeviceAdmin, isDeviceAdminEnabled)
        
        // Ensure binding has statusNotifications (we'll add it to layout next)
        // setStatusUI(binding.statusNotifications, isNotificationEnabled)

        val allGranted = isAccessibilityEnabled && isNotificationEnabled && isUsageEnabled && isOverlayEnabled && isDeviceAdminEnabled
        if (allGranted) {
            binding.tvMonitoringStatus.text = "Monitoring: Active"
            binding.tvMonitoringStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            binding.btnFixPermissions.visibility = View.GONE
            binding.ivChildStatusIcon.setImageResource(R.drawable.ic_parental_shield)
        } else {
            binding.tvMonitoringStatus.text = "Monitoring: Setup Required"
            binding.tvMonitoringStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
            binding.btnFixPermissions.visibility = View.VISIBLE
            binding.ivChildStatusIcon.setImageResource(R.drawable.ic_warning)
        }
    }

    private fun setStatusUI(textView: android.widget.TextView, granted: Boolean) {
        if (granted) {
            textView.text = "Granted"
            textView.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        } else {
            textView.text = "Denied"
            textView.setTextColor(android.graphics.Color.parseColor("#F44336"))
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkDeviceAdmin() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminName = ComponentName(this, ParentalDeviceAdmin::class.java)
        if (!dpm.isAdminActive(adminName)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "ShuGo needs this to protect your device and prevent accidental uninstallation.")
            }
            startActivity(intent)
        }
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Please disable battery optimization manually.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun checkPermissions() {
        // Request Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
            Toast.makeText(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show()
        }

        // Request Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQ_CODE)
            }
        }
    }

    private fun triggerOneTimeUsageSync() {
        val uniqueWorkName = "OneTimeUsageSync"
        val workRequest = OneTimeWorkRequestBuilder<UsageSyncWorker>().build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.KEEP, // Don't restart if already running
            workRequest
        )
        Log.d("Sync", "Enqueued one-time usage sync")
    }

    companion object {
        private const val OVERLAY_PERMISSION_REQ_CODE = 1234
        private const val NOTIFICATION_PERMISSION_REQ_CODE = 5678
    }
}
