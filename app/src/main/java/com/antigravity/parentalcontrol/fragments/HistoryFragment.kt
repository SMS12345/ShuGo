package com.antigravity.parentalcontrol.fragments

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.antigravity.parentalcontrol.databinding.FragmentHistoryBinding
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.google.firebase.database.ValueEventListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.antigravity.parentalcontrol.adapters.HistoryAdapter
import androidx.core.widget.doOnTextChanged

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var historyAdapter: HistoryAdapter
    private var historyListener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? com.antigravity.parentalcontrol.ParentDashboardActivity)?.setCenterTitle("Web History")
        
        historyAdapter = HistoryAdapter()
        binding.rvHistoryLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistoryLogs.adapter = historyAdapter
        
        setupSearchView()
        
        binding.swipeRefresh.setOnRefreshListener {
            loadHistory()
        }

        // Continuous listener for real-time updates (Lifecycle Aware)
        historyListener = FirebaseRepository.listenForHistory { events ->
            if (_binding == null) return@listenForHistory
            updateUI(events)
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun loadHistory() {
        // swipeRefresh logic stays for manual override if needed
        binding.swipeRefresh.isRefreshing = true
        FirebaseRepository.getHistoryOnce { events ->
            if (_binding == null) return@getHistoryOnce
            updateUI(events)
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun updateUI(events: List<com.antigravity.parentalcontrol.models.HistoryEvent>) {
        historyAdapter.updateData(events)
        if (events.isEmpty()) {
            binding.rvHistoryLogs.visibility = View.GONE
        } else {
            binding.rvHistoryLogs.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        historyListener?.let { FirebaseRepository.stopListening("history", it) }
        _binding = null
    }

    private fun setupSearchView() {
        binding.etSearchHistory.doOnTextChanged { text, _, _, _ ->
            historyAdapter.filter(text?.toString() ?: "")
        }
    }
}
