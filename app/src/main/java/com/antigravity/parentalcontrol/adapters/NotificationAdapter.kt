package com.antigravity.parentalcontrol.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.antigravity.parentalcontrol.databinding.ItemNotificationCardBinding
import com.antigravity.parentalcontrol.models.NotificationEvent
import com.antigravity.parentalcontrol.utils.ColorUtils
import com.antigravity.parentalcontrol.utils.TimeUtils
import com.antigravity.parentalcontrol.utils.IconFetcher

class NotificationAdapter : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private var events: List<NotificationEvent> = emptyList()
    private var filteredEvents: List<NotificationEvent> = emptyList()
    private var currentFilter: String = ""

    fun updateData(newEvents: List<NotificationEvent>) {
        // Filter out system UI notifications entirely as per request: "only show notifications from other apps"
        val nonSystemEvents = newEvents.filter {
            !it.packageName.startsWith("com.android.") && 
            !it.packageName.startsWith("android") &&
            !it.packageName.contains("antigravity")
        }
        events = nonSystemEvents
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
                it.packageName.contains(query, ignoreCase = true) || 
                it.title.contains(query, ignoreCase = true) || 
                it.text.contains(query, ignoreCase = true)
            }
        }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = filteredEvents.size
            override fun areItemsTheSame(o: Int, n: Int) = oldList[o].timestamp == filteredEvents[n].timestamp && oldList[o].packageName == filteredEvents[n].packageName
            override fun areContentsTheSame(o: Int, n: Int) = oldList[o] == filteredEvents[n]
        })
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(filteredEvents[position])
    }

    override fun getItemCount(): Int = filteredEvents.size

    inner class NotificationViewHolder(private val binding: ItemNotificationCardBinding) : RecyclerView.ViewHolder(binding.root) {
        private var iconJob: Job? = null

        fun bind(event: NotificationEvent) {
            iconJob?.cancel()
            binding.tvNotifAppName.text = event.packageName.substringAfterLast(".")
            binding.tvNotifTitle.text = event.title
            binding.tvNotifText.text = event.text
            binding.tvNotifTime.text = TimeUtils.getRelativeTime(event.timestamp)

            // Avatar logic
            binding.ivAppIcon.visibility = View.GONE
            binding.tvNotifAvatarLetter.visibility = View.VISIBLE
            
            val appName = binding.tvNotifAppName.text.toString()
            val firstLetter = if (appName.isNotEmpty()) appName.substring(0, 1).uppercase() else "?"
            binding.tvNotifAvatarLetter.text = firstLetter
            val bgColor = ColorUtils.getColorForString(event.packageName)
            binding.flNotifAvatarBg.setBackgroundResource(com.antigravity.parentalcontrol.R.drawable.bg_circle)
            binding.flNotifAvatarBg.background.setTint(bgColor)
            
            // Icon fetching via Coroutine
            iconJob = CoroutineScope(Dispatchers.Main).launch {
                val iconUrl = IconFetcher.getIconUrl(event.packageName)
                if (iconUrl != null && binding.tvNotifAppName.text.toString() == appName && binding.tvNotifTitle.text == event.title) {
                    binding.ivAppIcon.visibility = View.VISIBLE
                    binding.tvNotifAvatarLetter.visibility = View.GONE
                    Glide.with(binding.root.context)
                        .load(iconUrl)
                        .transform(CircleCrop())
                        .into(binding.ivAppIcon)
                    binding.flNotifAvatarBg.background = null // Remove the "round layer" background
                }
            }
        }
    }
}
