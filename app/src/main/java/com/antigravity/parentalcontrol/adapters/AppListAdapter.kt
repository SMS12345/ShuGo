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
import com.antigravity.parentalcontrol.databinding.ItemAppCardBinding
import com.antigravity.parentalcontrol.models.AppInfo
import com.antigravity.parentalcontrol.utils.IconFetcher

class AppListAdapter(
    private val onBlockToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private var apps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()
    private var blockedApps: Set<String> = emptySet()
    private var currentFilter: String = ""

    fun updateData(newApps: List<AppInfo>, newBlocked: Set<String>) {
        apps = newApps
        blockedApps = newBlocked
        applyFilter(currentFilter)
    }

    fun filter(query: String) {
        currentFilter = query
        applyFilter(query)
    }

    private fun applyFilter(query: String) {
        val oldList = filteredApps
        filteredApps = if (query.isEmpty()) {
            apps
        } else {
            apps.filter { 
                it.appName.contains(query, ignoreCase = true) || 
                it.packageName.contains(query, ignoreCase = true) 
            }
        }
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size
            override fun getNewListSize() = filteredApps.size
            override fun areItemsTheSame(o: Int, n: Int) = oldList[o].packageName == filteredApps[n].packageName
            override fun areContentsTheSame(o: Int, n: Int) = oldList[o] == filteredApps[n]
        })
        diff.dispatchUpdatesTo(this)
    }

    fun getAppsList(): List<AppInfo> = apps
    fun getBlockedSet(): Set<String> = blockedApps

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(filteredApps[position])
    }

    override fun getItemCount(): Int = filteredApps.size

    inner class AppViewHolder(private val binding: ItemAppCardBinding) : RecyclerView.ViewHolder(binding.root) {
        private var iconJob: Job? = null

        fun bind(app: AppInfo) {
            iconJob?.cancel()
            binding.tvAppName.text = app.appName
            binding.tvPackageName.text = app.packageName
            
            // Initial State: Text Avatar 
            binding.ivAppIcon.visibility = View.GONE
            binding.tvAvatarLetter.visibility = View.VISIBLE
            
            val firstLetter = if (app.appName.isNotEmpty()) app.appName.substring(0, 1).uppercase() else "?"
            binding.tvAvatarLetter.text = firstLetter
            val bgColor = com.antigravity.parentalcontrol.utils.ColorUtils.getColorForString(app.packageName)
            binding.flAvatarBackground.setBackgroundResource(com.antigravity.parentalcontrol.R.drawable.bg_circle)
            binding.flAvatarBackground.background.setTint(bgColor)
            
            // Icon fetching via Coroutine
            iconJob = CoroutineScope(Dispatchers.Main).launch {
                // 1. Try Local PackageManager first
                try {
                    val pm = binding.root.context.packageManager
                    val icon = pm.getApplicationIcon(app.packageName)
                    binding.ivAppIcon.visibility = View.VISIBLE
                    binding.tvAvatarLetter.visibility = View.GONE
                    Glide.with(binding.root.context)
                        .load(icon)
                        .transform(CircleCrop())
                        .into(binding.ivAppIcon)
                    binding.flAvatarBackground.background = null // Remove the "round layer" background
                    return@launch 
                } catch (e: Exception) {
                    // Not found locally
                }

                // 2. Fallback to Scraper (Play Store or System Defaults)
                val iconUrl = IconFetcher.getIconUrl(app.packageName)
                if (iconUrl != null && binding.tvPackageName.text == app.packageName) {
                    binding.ivAppIcon.visibility = View.VISIBLE
                    binding.tvAvatarLetter.visibility = View.GONE
                    Glide.with(binding.root.context)
                        .load(iconUrl)
                        .transform(CircleCrop())
                        .into(binding.ivAppIcon)
                    binding.flAvatarBackground.background = null // Remove the "round layer" background
                }
            }

            val isBlocked = blockedApps.contains(app.packageName.replace(".", "_"))
            
            binding.switchBlock.setOnCheckedChangeListener(null) // Prevent recursive calls
            binding.switchBlock.isChecked = isBlocked
            
            binding.switchBlock.setOnCheckedChangeListener { _, checked ->
                onBlockToggle(app.packageName, checked)
            }
            
            // Visual feedback for blocked state
            binding.root.alpha = if (isBlocked) 0.6f else 1.0f
        }
    }
}
