package com.antigravity.parentalcontrol

import android.app.Application
import android.provider.Settings
import android.util.Log
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.antigravity.parentalcontrol.utils.IdCache
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Logger

class ParentalControlApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // 1. CRITICAL: Persistence must be set BEFORE any other Firebase call to avoid RunLoop NPEs
        try {
            FirebaseDatabase.getInstance(FirebaseRepository.DB_URL).setPersistenceEnabled(true)
        } catch (e: Exception) {
            // Already set or error
        }

        // 2. Logging and other configs
        FirebaseDatabase.getInstance(FirebaseRepository.DB_URL).setLogLevel(Logger.Level.WARN)

        // 1. Initialize Firebase Repository with the persisted 6-digit ID
        val deviceId = IdCache.getDeviceId(this)
        FirebaseRepository.init(deviceId)
        
        // 2. Ultra-Stability: Global Exception Handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("UltraStability", "Uncaught Exception in thread ${thread.name}: ${throwable.message}")
            throwable.printStackTrace()
            
            // Pass to original handler so the OS can kill the process properly 
            // instead of leaving a frozen zombie app.
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
