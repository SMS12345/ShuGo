package com.antigravity.parentalcontrol

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.antigravity.parentalcontrol.auth.GoogleAuthHelper
import com.antigravity.parentalcontrol.databinding.ActivityMainBinding
import com.antigravity.parentalcontrol.services.KeepAliveService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auth guard: redirect to login if not signed in
        if (!GoogleAuthHelper.isSignedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        // Check if mode is already selected
        val currentMode = AppModeManager.getAppMode(this)
        if (currentMode != AppModeManager.Mode.NONE) {
            navigateToDashboard(currentMode)
            return
        }

        binding.cardParentMode.setOnClickListener {
            AppModeManager.setAppMode(this, AppModeManager.Mode.PARENT)
            navigateToDashboard(AppModeManager.Mode.PARENT)
        }

        binding.cardChildMode.setOnClickListener {
            AppModeManager.setAppMode(this, AppModeManager.Mode.CHILD)
            navigateToDashboard(AppModeManager.Mode.CHILD)
        }
    }

    private fun navigateToDashboard(mode: AppModeManager.Mode) {
        if (mode == AppModeManager.Mode.PARENT) {
            val linkedId = AppModeManager.getLinkedChildId(this)
            if (linkedId == null) {
                startActivity(Intent(this, PairingActivity::class.java))
            } else {
                startActivity(Intent(this, ParentDashboardActivity::class.java))
            }
        } else {
            val username = AppModeManager.getUsername(this)
            if (username.isEmpty()) {
                startActivity(Intent(this, PairingActivity::class.java))
            } else {
                KeepAliveService.start(this)
                startActivity(Intent(this, ChildDashboardActivity::class.java))
            }
        }
        finish()
    }
}
