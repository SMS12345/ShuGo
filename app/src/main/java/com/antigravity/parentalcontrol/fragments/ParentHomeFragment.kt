package com.antigravity.parentalcontrol.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.antigravity.parentalcontrol.AppModeManager
import com.antigravity.parentalcontrol.MainActivity
import com.antigravity.parentalcontrol.ParentDashboardActivity
import com.antigravity.parentalcontrol.R
import com.antigravity.parentalcontrol.databinding.FragmentParentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ParentHomeFragment : Fragment() {

    private var _binding: FragmentParentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentParentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? ParentDashboardActivity)?.setCenterTitle("")

        val userName = AppModeManager.getUsername(requireContext())
        binding.tvWelcomeParent.text = "Welcome, $userName"

        setupGrid()
    }

    private fun setupGrid() {
        binding.cardAppControl.setOnClickListener {
            replaceFragment(AppsFragment())
        }

        binding.cardScreenTime.setOnClickListener {
            replaceFragment(UsageFragment())
        }

        binding.cardNotifHistory.visibility = View.VISIBLE
        binding.cardNotifHistory.setOnClickListener {
            replaceFragment(NotificationsFragment())
        }

        binding.cardWebHistory.setOnClickListener {
            replaceFragment(HistoryFragment())
        }

        binding.cardLocation.setOnClickListener {
            replaceFragment(AlertsFragment())
        }

        binding.cardUnpair.setOnClickListener {
            showUnpairDialog()
        }
    }

    private fun showUnpairDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Unpair Device")
            .setMessage("Are you sure you want to unpair this child device? You will need to setup the connection again.")
            .setPositiveButton("Unpair") { _, _ ->
                unpair()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun unpair() {
        context?.let { ctx ->
            // Stop the alert service so it doesn't keep listening
            ctx.stopService(Intent(ctx, com.antigravity.parentalcontrol.services.ParentAlertService::class.java))
            
            // Clear linked ID
            AppModeManager.setLinkedChildId(ctx, "")
            // Reset app mode to re-trigger selection if desired
            AppModeManager.setAppMode(ctx, AppModeManager.Mode.NONE)
            
            val intent = Intent(ctx, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
