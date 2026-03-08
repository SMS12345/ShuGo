package com.antigravity.parentalcontrol.adapters

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.antigravity.parentalcontrol.R
import com.antigravity.parentalcontrol.databinding.ItemHistoryCardBinding
import com.antigravity.parentalcontrol.models.HistoryEvent
import com.antigravity.parentalcontrol.utils.TimeUtils

class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    private var events: List<HistoryEvent> = emptyList()
    private var filteredEvents: List<HistoryEvent> = emptyList()
    private var currentFilter: String = ""

    fun updateData(newEvents: List<HistoryEvent>) {
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
                it.title?.contains(query, ignoreCase = true) == true || 
                it.url?.contains(query, ignoreCase = true) == true
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(filteredEvents[position])
    }

    override fun getItemCount(): Int = filteredEvents.size

    class HistoryViewHolder(private val binding: ItemHistoryCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(event: HistoryEvent) {
            // Icon
            when (event.type) {
                "WEB" -> binding.ivHistoryIcon.setImageResource(R.drawable.ic_globe)
                "YOUTUBE" -> binding.ivHistoryIcon.setImageResource(R.drawable.ic_play)
                else -> binding.ivHistoryIcon.setImageResource(R.drawable.ic_warning)
            }

            // Texts
            val titleText = event.title
            if (titleText.isNullOrEmpty()) {
                binding.tvHistoryTitle.visibility = View.GONE
            } else {
                binding.tvHistoryTitle.visibility = View.VISIBLE
                binding.tvHistoryTitle.text = titleText
                binding.tvHistoryTitle.textSize = 16f
                binding.tvHistoryTitle.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            binding.tvHistoryTime.text = TimeUtils.getRelativeTime(event.timestamp)

            // URL handling
            val isSearch = event.title?.startsWith("Search: ") == true
            
            if (!event.url.isNullOrEmpty() && !isSearch) {
                binding.tvHistoryUrl.visibility = View.VISIBLE
                binding.tvHistoryUrl.text = event.url
            } else {
                binding.tvHistoryUrl.visibility = View.GONE
            }

            // Click handling
            if (event.type == "YOUTUBE" && !event.title.isNullOrEmpty()) {
                binding.root.setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_SEARCH)
                        intent.setPackage("com.google.android.youtube")
                        intent.putExtra("query", event.title)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        it.context.startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(event.title)}"))
                            it.context.startActivity(intent)
                        } catch (ignored: Exception) {}
                    }
                }
            } else if (!event.url.isNullOrEmpty()) {
                binding.root.setOnClickListener {
                    try {
                        val safeUrl = if (event.url.startsWith("http")) event.url else "https://${event.url}"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))
                        it.context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                binding.root.setOnClickListener(null)
            }
        }
    }
}
