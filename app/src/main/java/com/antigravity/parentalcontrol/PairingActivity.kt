package com.antigravity.parentalcontrol

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.antigravity.parentalcontrol.auth.GoogleAuthHelper
import com.antigravity.parentalcontrol.databinding.ActivityPairingBinding

class PairingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPairingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.hide()

        val mode = AppModeManager.getAppMode(this)

        // Show greeting with Google display name
        val displayName = AppModeManager.getUsername(this)
        if (displayName.isNotEmpty()) {
            binding.tvWelcomeName.text = "Welcome, $displayName!"
        }

        setupUI(mode)

        binding.btnLinkDevice.setOnClickListener {
            handlePairing(mode)
        }

        binding.btnCopyCode.setOnClickListener {
            val code = binding.tvMyCode.text.toString()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Pairing Code", code)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupUI(mode: AppModeManager.Mode) {
        if (mode == AppModeManager.Mode.PARENT) {
            binding.tvPairingTitle.text = "Parent Setup"
            binding.tvPairingSubtitle.text = "Enter your child's mobile ID to pair"
            binding.llParentPairing.visibility = View.VISIBLE
            binding.llChildPairing.visibility = View.GONE
            binding.btnLinkDevice.text = "Pair with Child"
        } else {
            binding.tvPairingTitle.text = "Child Setup"
            binding.tvPairingSubtitle.text = "Share this code with your parent"
            binding.llParentPairing.visibility = View.GONE
            binding.llChildPairing.visibility = View.VISIBLE
            binding.btnLinkDevice.text = "Finish Setup"

            val myCode = AppModeManager.getDeviceId(this)
            binding.tvMyCode.text = myCode
        }
    }

    private fun handlePairing(mode: AppModeManager.Mode) {
        if (mode == AppModeManager.Mode.PARENT) {
            val code = binding.etPairingCode.text.toString().trim()
            if (code.length == 6) {
                AppModeManager.setLinkedChildId(this, code)
                startActivity(Intent(this, ParentDashboardActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "Please enter a 6-digit code", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Mark child setup as complete so pairing screen is not shown again
            AppModeManager.setChildSetupDone(this, true)
            startActivity(Intent(this, ChildDashboardActivity::class.java))
            finish()
        }
    }
}
