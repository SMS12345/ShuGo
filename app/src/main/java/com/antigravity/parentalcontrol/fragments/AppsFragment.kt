package com.antigravity.parentalcontrol.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.antigravity.parentalcontrol.adapters.AppListAdapter
import com.antigravity.parentalcontrol.databinding.FragmentAppsBinding
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.google.firebase.database.ValueEventListener
import androidx.core.widget.doOnTextChanged

class AppsFragment : Fragment() {
    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!
    private lateinit var appAdapter: AppListAdapter
    private var appsListener: ValueEventListener? = null
    private var blockedListener: ValueEventListener? = null
    private var defaultBlockListener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? com.antigravity.parentalcontrol.ParentDashboardActivity)?.setCenterTitle("App Controls")
        setupRecyclerView()
        setupSearchView()
        setupDefaultBlockSwitch()
        observeData()
    }

    private fun setupDefaultBlockSwitch() {
        binding.switchBlockNewApps.setOnCheckedChangeListener { _, isChecked ->
            FirebaseRepository.setBlockNewAppsDefault(isChecked)
        }
    }

    private fun setupSearchView() {
        binding.etSearchApps.doOnTextChanged { text, _, _, _ ->
            appAdapter.filter(text?.toString() ?: "")
        }
    }

    private fun setupRecyclerView() {
        appAdapter = AppListAdapter { packageName, blocked ->
            FirebaseRepository.setAppBlocked(packageName, blocked)
        }
        binding.rvInstalledApps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvInstalledApps.adapter = appAdapter
    }

    private fun observeData() {
        // App list listener (Real-time updates to apps)
        appsListener = FirebaseRepository.listenForInstalledApps { apps ->
            if (_binding == null) return@listenForInstalledApps
            updateUI(apps, appAdapter.getBlockedSet())
        }
        
        // Blocked apps listener (Real-time updates to block status)
        blockedListener = FirebaseRepository.listenForBlockedApps { blockedSet ->
            if (_binding == null) return@listenForBlockedApps
            updateUI(appAdapter.getAppsList(), blockedSet)
        }
        
        // Default app block settings listener
        defaultBlockListener = FirebaseRepository.listenForBlockNewAppsDefault { isEnabled ->
            if (_binding == null) return@listenForBlockNewAppsDefault
            binding.switchBlockNewApps.setOnCheckedChangeListener(null)
            binding.switchBlockNewApps.isChecked = isEnabled
            setupDefaultBlockSwitch()
        }
    }

    private fun updateUI(apps: List<com.antigravity.parentalcontrol.models.AppInfo>, blockedSet: Set<String>) {
        appAdapter.updateData(apps, blockedSet)
        binding.rvInstalledApps.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        appsListener?.let { FirebaseRepository.stopListening("installed_apps", it) }
        blockedListener?.let { FirebaseRepository.stopListening("blocked_apps", it) }
        defaultBlockListener?.let { FirebaseRepository.stopListening("settings/block_new_apps", it) }
        _binding = null
    }
}
