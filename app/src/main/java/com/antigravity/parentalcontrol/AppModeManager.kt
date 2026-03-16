package com.antigravity.parentalcontrol

import android.content.Context
import android.content.SharedPreferences

object AppModeManager {
    private const val PREFS_NAME = "parental_control_prefs"
    private const val KEY_MODE = "app_mode"
    private const val KEY_LINKED_ID = "linked_child_id"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_USERNAME = "user_name"
    private const val KEY_CHILD_SETUP_DONE = "child_setup_done"

    // Fix #10: Cache mode to avoid hitting SharedPreferences on every accessibility event
    @Volatile
    private var cachedMode: Mode? = null

    enum class Mode {
        PARENT, CHILD, NONE
    }

    fun setAppMode(context: Context, mode: Mode) {
        cachedMode = mode
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun getAppMode(context: Context): Mode {
        cachedMode?.let { return it }
        val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeStr = prefs.getString(KEY_MODE, Mode.NONE.name)
        val mode = try {
            Mode.valueOf(modeStr ?: Mode.NONE.name)
        } catch (e: Exception) {
            Mode.NONE
        }
        cachedMode = mode
        return mode
    }

    fun setLinkedChildId(context: Context, id: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LINKED_ID, id).apply()
    }

    fun getLinkedChildId(context: Context): String? {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LINKED_ID, null)
    }

    fun getDeviceId(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = (100000..999999).random().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun setUsername(context: Context, name: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_USERNAME, name).apply()
    }

    fun getUsername(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USERNAME, "") ?: ""
    }

    fun setChildSetupDone(context: Context, done: Boolean) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_CHILD_SETUP_DONE, done).apply()
    }

    fun isChildSetupDone(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_CHILD_SETUP_DONE, false)
    }

    /**
     * Clears all SharedPreferences data on sign-out
     */
    fun clearAll(context: Context) {
        cachedMode = null
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
