package com.antigravity.parentalcontrol.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.antigravity.parentalcontrol.databinding.ItemNotificationCardBinding
import com.antigravity.parentalcontrol.models.AlertEvent
import com.antigravity.parentalcontrol.utils.TimeUtils

class AlertAdapter : RecyclerView.Adapter<AlertAdapter.AlertViewHolder>() {

    private var events: List<AlertEvent> = emptyList()
    private var filteredEvents: List<AlertEvent> = emptyList()
    private var currentFilter: String = ""

    fun updateData(newEvents: List<AlertEvent>) {
        events = newEvents
        applyFilter(currentFilter)
    }

    fun filter(query: String) {
        currentFilter = query
        applyFilter(query)
    }

    private fun applyFilter(query: String) {
        val oldList = filteredEvents
        filteredEvents = if (query.isEmpty()) {
            events
        } else {
            events.filter { 
                it.message.contains(query, ignoreCase = true)
            }
        }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = filteredEvents.size
            override fun areItemsTheSame(o: Int, n: Int) = oldList[o].timestamp == filteredEvents[n].timestamp
            override fun areContentsTheSame(o: Int, n: Int) = oldList[o] == filteredEvents[n]
        })
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val binding = ItemNotificationCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AlertViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(filteredEvents[position])
    }

    override fun getItemCount(): Int = filteredEvents.size

    inner class AlertViewHolder(private val binding: ItemNotificationCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: AlertEvent) {
            binding.tvNotifAppName.text = "Alert"
            binding.tvNotifTitle.text = if (event.message == "All permissions are allowed") "Service Active" else "Permission Issue"
            binding.tvNotifText.text = event.message
            binding.tvNotifTime.text = TimeUtils.getRelativeTime(event.timestamp)

            // Avatar logic
            binding.ivAppIcon.visibility = View.GONE
            binding.tvNotifAvatarLetter.visibility = View.VISIBLE
            
            binding.tvNotifAvatarLetter.text = "!"
            val bgColor = android.graphics.Color.parseColor(if (event.message == "All permissions are allowed") "#4CAF50" else "#F44336")
            binding.flNotifAvatarBg.setBackgroundResource(com.antigravity.parentalcontrol.R.drawable.bg_circle)
            binding.flNotifAvatarBg.background.setTint(bgColor)
        }
    }
}
