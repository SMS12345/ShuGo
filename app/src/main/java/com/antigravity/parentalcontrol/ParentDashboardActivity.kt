package com.antigravity.parentalcontrol

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.antigravity.parentalcontrol.auth.GoogleAuthHelper
import com.antigravity.parentalcontrol.databinding.ActivityParentDashboardBinding
import com.antigravity.parentalcontrol.fragments.AppsFragment
import com.antigravity.parentalcontrol.fragments.HistoryFragment
import com.antigravity.parentalcontrol.fragments.NotificationsFragment
import com.antigravity.parentalcontrol.fragments.UsageFragment
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.antigravity.parentalcontrol.fragments.ParentHomeFragment
import com.antigravity.parentalcontrol.services.ParentAlertService
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class ParentDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityParentDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityParentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        if (AppModeManager.getAppMode(this) != AppModeManager.Mode.PARENT) {
            finish()
            return
        }

        val linkedId = AppModeManager.getLinkedChildId(this)
        if (linkedId != null && linkedId.isNotEmpty()) {
            initParentSession(linkedId)
        } else {
            Toast.makeText(this, "Device not paired", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, PairingActivity::class.java))
            finish()
        }

        checkNotificationPermission()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_dashboard, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sign_out -> {
                performSignOut()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performSignOut() {
        GoogleAuthHelper.signOut(this) {
            AppModeManager.clearAll(this)
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun initParentSession(childId: String) {
        FirebaseRepository.init(childId)
        
        // Start Alert Push Notification listener gracefully
        try {
            val intent = Intent(this, ParentAlertService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            // Android 13+ will throw SecurityException if POST_NOTIFICATIONS is missing.
            // checkNotificationPermission() will request it, and the service will start next launch.
        }
        
        // Show the Home Fragment (Grid) by default
        replaceFragment(ParentHomeFragment())
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    fun setCenterTitle(title: String) {
        supportActionBar?.title = title
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5678)
            }
        }
    }
}
