package com.antigravity.parentalcontrol.workers

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.antigravity.parentalcontrol.AppModeManager
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import android.app.AppOpsManager
import android.os.Process
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class UsageSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        Log.d("UsageSyncWorker", "Starting daily usage sync and global data pruning...")
        
        try {
            // Ensure repository is initialized with the correct device ID in background process
            val deviceId = AppModeManager.getDeviceId(applicationContext)
            FirebaseRepository.init(deviceId)

            // Check if usage stats permission is actually granted
            if (!hasUsageStatsPermission()) {
                Log.w("UsageSyncWorker", "Skipping sync: PACKAGE_USAGE_STATS permission not granted.")
                return Result.failure()
            }

            syncTodayUsage()
            
            // Trigger global data pruning inside Firebase Repository
            FirebaseRepository.cleanupOldData(7) // Prune all data older than 7 days
            
            return Result.success()
        } catch (e: Exception) {
            Log.e("UsageSyncWorker", "Error during usage sync: ${e.message}")
            return Result.retry()
        }
    }

    private fun syncTodayUsage() {
        val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        // Use IST timezone explicitly for day boundaries (Digital Wellbeing style)
        val istZone = TimeZone.getTimeZone("Asia/Kolkata")
        val calendar = Calendar.getInstance(istZone)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // Use INTERVAL_BEST for accurate aggregation within IST day boundaries
        val usageStats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
        
        val dailyMap = mutableMapOf<String, Long>()
        
        val pm = applicationContext.packageManager
        
        for (stat in usageStats) {
            val packageName = stat.packageName
            val timeInForeground = stat.totalTimeInForeground
            
            // Filter: Only capture usage if:
            // 1. Time > 0
            // 2. The app is a user-facing app (has a launcher intent in the package manager)
            // 3. It's not our own app or the system UI
            if (timeInForeground > 0 && 
                packageName != applicationContext.packageName &&
                packageName != "com.android.systemui" &&
                !packageName.contains("launcher")) {
                
                val launchIntent = pm.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    val safePackageName = packageName.replace(".", "_")
                    val existingTime = dailyMap[safePackageName] ?: 0L
                    dailyMap[safePackageName] = existingTime + timeInForeground
                }
            }
        }
        
        if (dailyMap.isNotEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.timeZone = istZone
            val dateString = sdf.format(Date())
            FirebaseRepository.uploadUsageStats(dateString, dailyMap)
            Log.d("UsageSyncWorker", "Uploaded stats for ${dailyMap.size} valid applications.")
        } else {
            Log.d("UsageSyncWorker", "No usage data found to upload for today.")
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = applicationContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), applicationContext.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), applicationContext.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
