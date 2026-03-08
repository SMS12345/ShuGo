package com.antigravity.parentalcontrol.services

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import com.antigravity.parentalcontrol.R

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun showOverlay(onClose: () -> Unit) {
        if (overlayView?.isAttachedToWindow == true) return // Already showing and attached
        
        // Safety: If for some reason overlayView exists but isn't attached, clean it up
        if (overlayView != null) {
            hideOverlay()
        }

        val inflater = LayoutInflater.from(context)
        overlayView = inflater.inflate(R.layout.overlay_app_blocked, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        overlayView?.findViewById<Button>(R.id.btn_close_overlay)?.setOnClickListener {
            hideOverlay()
            onClose()
        }

        try {
            if (Settings.canDrawOverlays(context)) {
                windowManager.addView(overlayView, params)
            } else {
                Log.e("OverlayManager", "Missing OVERLAY permission")
                overlayView = null
            }
        } catch (e: Exception) {
            Log.e("OverlayManager", "AddView failed: ${e.message}")
            overlayView = null
        }
    }

    fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            overlayView = null
        }
    }

    fun isShowing(): Boolean = overlayView?.isAttachedToWindow == true
}
