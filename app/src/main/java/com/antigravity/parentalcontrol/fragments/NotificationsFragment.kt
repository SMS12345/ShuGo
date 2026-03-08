package com.antigravity.parentalcontrol.fragments

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.antigravity.parentalcontrol.databinding.FragmentNotificationsBinding
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.google.firebase.database.ValueEventListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.antigravity.parentalcontrol.adapters.NotificationAdapter
import androidx.core.widget.doOnTextChanged

class NotificationsFragment : Fragment() {
    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!
    private lateinit var notificationAdapter: NotificationAdapter
    private var notificationsListener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? com.antigravity.parentalcontrol.ParentDashboardActivity)?.setCenterTitle("Notifications")
        
        notificationAdapter = NotificationAdapter()
        binding.rvNotificationLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvNotificationLogs.adapter = notificationAdapter

        setupSearchView()

        binding.swipeRefresh.setOnRefreshListener {
            loadNotifications()
        }

        // Continuous listener for real-time updates (Lifecycle Aware)
        notificationsListener = FirebaseRepository.listenForNotifications { events ->
            if (_binding == null) return@listenForNotifications
            updateUI(events)
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun loadNotifications() {
        binding.swipeRefresh.isRefreshing = true
        FirebaseRepository.getNotificationsOnce { events ->
            if (_binding == null) return@getNotificationsOnce
            updateUI(events)
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun updateUI(events: List<com.antigravity.parentalcontrol.models.NotificationEvent>) {
        notificationAdapter.updateData(events)
        binding.rvNotificationLogs.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        notificationsListener?.let { FirebaseRepository.stopListening("notifications", it) }
        _binding = null
    }

    private fun setupSearchView() {
        binding.etSearchNotifications.doOnTextChanged { text, _, _, _ ->
            notificationAdapter.filter(text?.toString() ?: "")
        }
    }
}
