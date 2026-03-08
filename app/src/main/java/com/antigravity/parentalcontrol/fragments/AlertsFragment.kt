package com.antigravity.parentalcontrol.fragments

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.antigravity.parentalcontrol.databinding.FragmentAlertsBinding
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.google.firebase.database.ValueEventListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.antigravity.parentalcontrol.adapters.AlertAdapter
import androidx.core.widget.doOnTextChanged

class AlertsFragment : Fragment() {
    private var _binding: FragmentAlertsBinding? = null
    private val binding get() = _binding!!
    private lateinit var alertAdapter: AlertAdapter
    private var alertsListener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAlertsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? com.antigravity.parentalcontrol.ParentDashboardActivity)?.setCenterTitle("Alerts")
        
        alertAdapter = AlertAdapter()
        binding.rvAlertLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvAlertLogs.adapter = alertAdapter

        setupSearchView()

        binding.swipeRefreshAlerts.setOnRefreshListener {
            loadAlerts()
        }

        alertsListener = FirebaseRepository.listenForAlerts { events ->
            if (_binding == null) return@listenForAlerts
            updateUI(events)
            binding.swipeRefreshAlerts.isRefreshing = false
        }
    }

    private fun loadAlerts() {
        binding.swipeRefreshAlerts.isRefreshing = true
        FirebaseRepository.getAlertsOnce { events ->
            if (_binding == null) return@getAlertsOnce
            updateUI(events)
            binding.swipeRefreshAlerts.isRefreshing = false
        }
    }

    private fun updateUI(events: List<com.antigravity.parentalcontrol.models.AlertEvent>) {
        alertAdapter.updateData(events)
        binding.rvAlertLogs.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        alertsListener?.let { FirebaseRepository.stopListening("alerts", it) }
        _binding = null
    }

    private fun setupSearchView() {
        binding.etSearchAlerts.doOnTextChanged { text, _, _, _ ->
            alertAdapter.filter(text?.toString() ?: "")
        }
    }
}
