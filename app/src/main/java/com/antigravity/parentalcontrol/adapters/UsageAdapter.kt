package com.antigravity.parentalcontrol.adapters

import android.content.pm.PackageManager
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.antigravity.parentalcontrol.databinding.ItemUsageCardBinding
import com.antigravity.parentalcontrol.models.AppUsageItem
import com.antigravity.parentalcontrol.utils.IconFetcher
import com.antigravity.parentalcontrol.utils.TimeUtils
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class UsageAdapter : ListAdapter<AppUsageItem, UsageAdapter.UsageViewHolder>(DiffCallback()) {

    private var fullList: List<AppUsageItem> = emptyList()
    private var currentFilter: String = ""

    fun updateFullList(newList: List<AppUsageItem>) {
        fullList = newList
        applyFilter(currentFilter)
    }

    fun filter(query: String) {
        currentFilter = query
        applyFilter(query)
    }

    private fun applyFilter(query: String) {
        val filtered = if (query.isEmpty()) {
            fullList
        } else {
            fullList.filter { it.appName.contains(query, ignoreCase = true) }
        }
        submitList(filtered)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsageViewHolder {
        val binding = ItemUsageCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return UsageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UsageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UsageViewHolder(private val binding: ItemUsageCardBinding) : RecyclerView.ViewHolder(binding.root) {
        private var iconJob: Job? = null

        fun bind(item: AppUsageItem) {
            iconJob?.cancel()
            binding.tvUsageAppName.text = item.appName
            binding.tvUsageTime.text = TimeUtils.formatDuration(item.timeMs)
            
            val progress = if (item.maxTimeMs > 0) ((item.timeMs.toFloat() / item.maxTimeMs.toFloat()) * 100).toInt() else 0
            binding.pbUsageBar.progress = progress
            
            // Set Avatar letter
            val initial = item.appName.firstOrNull()?.uppercase() ?: "?"
            binding.tvUsageAvatarLetter.text = initial
            
            // Set Avatar Color
            val colors = listOf("#E57373", "#F06292", "#BA68C8", "#9575CD", "#7986CB", "#64B5F6", "#4FC3F7", "#4DD0E1", "#4DB6AC", "#81C784")
            val colorIndex = Math.abs(item.packageName.hashCode()) % colors.size
            binding.cvUsageIconContainer.setCardBackgroundColor(Color.parseColor(colors[colorIndex]))
            binding.cvUsageIconContainer.cardElevation = 2f // Restore elevation if it was removed
            
            // Reset ImageView
            binding.ivUsageAppIcon.visibility = View.GONE
            binding.tvUsageAvatarLetter.visibility = View.VISIBLE
            
            iconJob = CoroutineScope(Dispatchers.Main).launch {
                // 1. Try Local PackageManager first (Common for system apps)
                try {
                    val pm = binding.root.context.packageManager
                    val icon = pm.getApplicationIcon(item.packageName)
                    binding.ivUsageAppIcon.visibility = View.VISIBLE
                    binding.tvUsageAvatarLetter.visibility = View.GONE
                    Glide.with(binding.root.context)
                        .load(icon)
                        .transform(CircleCrop())
                        .into(binding.ivUsageAppIcon)
                    binding.cvUsageIconContainer.setCardBackgroundColor(Color.TRANSPARENT)
                    binding.cvUsageIconContainer.cardElevation = 0f
                    return@launch 
                } catch (e: Exception) {
                    // Not found locally, proceed to scraper
                }

                // 2. Fallback to Scraper (Play Store or System Defaults)
                val iconUrl = IconFetcher.getIconUrl(item.packageName)
                if (iconUrl != null && binding.tvUsageAppName.text == item.appName) {
                    binding.ivUsageAppIcon.visibility = View.VISIBLE
                    binding.tvUsageAvatarLetter.visibility = View.GONE
                    Glide.with(binding.root.context)
                        .load(iconUrl)
                        .transform(CircleCrop())
                        .into(binding.ivUsageAppIcon)
                    binding.cvUsageIconContainer.setCardBackgroundColor(Color.TRANSPARENT)
                    binding.cvUsageIconContainer.cardElevation = 0f
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<AppUsageItem>() {
        override fun areItemsTheSame(oldItem: AppUsageItem, newItem: AppUsageItem): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppUsageItem, newItem: AppUsageItem): Boolean {
            return oldItem == newItem
        }
    }
}
