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

        // Use IST timezone for day boundaries (same as Digital Wellbeing)
        val istZone = TimeZone.getTimeZone("Asia/Kolkata")
        val calendar = Calendar.getInstance(istZone)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // Bug 5 fix: use queryEvents() instead of queryUsageStats(INTERVAL_BEST).
        // INTERVAL_BEST returns pre-aggregated buckets that can bleed yesterday's data
        // into today. queryEvents() gives raw move-to-foreground/background events so
        // we can sum only the foreground intervals that fall within today's IST window.
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = android.app.usage.UsageEvents.Event()

        // Map: safePackageName → accumulated foreground ms
        val dailyMap = mutableMapOf<String, Long>()
        // Map: safePackageName → timestamp when it moved to foreground
        val foregroundStartMap = mutableMapOf<String, Long>()

        val pm = applicationContext.packageManager

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue

            // Only count user-facing apps with a launcher intent
            if (pkg == applicationContext.packageName ||
                pkg == "com.android.systemui" ||
                pkg.contains("launcher")) continue

            when (event.eventType) {
                android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    foregroundStartMap[pkg] = event.timeStamp
                }
                android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val fgStart = foregroundStartMap.remove(pkg) ?: continue
                    val duration = event.timeStamp - fgStart
                    if (duration > 0) {
                        val safePkg = pkg.replace(".", "_")
                        dailyMap[safePkg] = (dailyMap[safePkg] ?: 0L) + duration
                    }
                }
            }
        }

        // Any app still in foreground at query time — count time up to now
        val now = System.currentTimeMillis()
        for ((pkg, fgStart) in foregroundStartMap) {
            val duration = now - fgStart
            if (duration > 0) {
                val safePkg = pkg.replace(".", "_")
                dailyMap[safePkg] = (dailyMap[safePkg] ?: 0L) + duration
            }
        }

        // Filter: only keep apps that have a launcher intent and usage >= 1 second
        val filtered = dailyMap.filter { (safePkg, ms) ->
            if (ms < 1000L) return@filter false
            val realPkg = safePkg.replace("_", ".")
            try {
                pm.getLaunchIntentForPackage(realPkg) != null
            } catch (e: Exception) {
                false
            }
        }

        if (filtered.isNotEmpty()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.timeZone = istZone
            val dateString = sdf.format(Date())
            FirebaseRepository.uploadUsageStats(dateString, filtered)
            Log.d("UsageSyncWorker", "Uploaded stats for ${filtered.size} valid applications.")
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
