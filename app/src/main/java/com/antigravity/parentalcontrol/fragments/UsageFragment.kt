package com.antigravity.parentalcontrol.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.antigravity.parentalcontrol.adapters.UsageAdapter
import com.antigravity.parentalcontrol.databinding.FragmentUsageBinding
import com.antigravity.parentalcontrol.models.AppInfo
import com.antigravity.parentalcontrol.models.AppUsageItem
import com.antigravity.parentalcontrol.repository.FirebaseRepository
import com.antigravity.parentalcontrol.utils.TimeUtils
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class UsageFragment : Fragment() {

    private var _binding: FragmentUsageBinding? = null
    private val binding get() = _binding!!

    private lateinit var usageAdapter: UsageAdapter
    
    // Data Caches
    private var allUsageData: Map<String, Map<String, Long>> = emptyMap()
    private var installedAppsMap: Map<String, String> = emptyMap()
    
    private var currentDateOffset = 0 // 0 = today, -1 = yesterday, max -6

    private var usageListener: ValueEventListener? = null
    private var appsListener: ValueEventListener? = null

    // Palette for Pie Chart
    private val chartColors = listOf(
        Color.parseColor("#E57373"), // Red
        Color.parseColor("#F06292"), // Pink
        Color.parseColor("#BA68C8"), // Purple
        Color.parseColor("#9575CD"), // Deep Purple
        Color.parseColor("#7986CB"), // Indigo
        Color.parseColor("#64B5F6"), // Blue
        Color.parseColor("#4FC3F7")  // Light Blue
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentUsageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as? com.antigravity.parentalcontrol.ParentDashboardActivity)?.setCenterTitle("Screen Time")

        setupRecyclerView()
        setupPieChart()
        setupDateNavigation()

        binding.pbLoading.visibility = View.VISIBLE
        
        // Fetch Apps (For readable names)
        appsListener = FirebaseRepository.listenForInstalledApps { apps ->
            installedAppsMap = apps.associate { it.packageName to it.appName }
            refreshUsageUI()
        }

        // Fetch Usage Data
        usageListener = FirebaseRepository.listenForUsageStats { data ->
            allUsageData = data
            binding.pbLoading.visibility = View.GONE
            refreshUsageUI()
        }
    }

    private fun setupRecyclerView() {
        usageAdapter = UsageAdapter()
        binding.rvUsageList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = usageAdapter
        }
    }

    private fun setupPieChart() {
        binding.usagePieChart.apply {
            description.isEnabled = false
            setUsePercentValues(true)
            isDrawHoleEnabled = true
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.TRANSPARENT)
            setTransparentCircleAlpha(0)
            holeRadius = 80f // Restored to 80f
            setDrawCenterText(true)
            setCenterTextSize(26f)
            setCenterTextColor(Color.WHITE)
            setDrawEntryLabels(true) 
            setEntryLabelColor(Color.WHITE)
            setEntryLabelTextSize(13f) // Increased for better readability
            rotationAngle = 0f
            isRotationEnabled = true
            isHighlightPerTapEnabled = true
            legend.isEnabled = false
            
            // Adjust offsets to balance the chart size
            setExtraOffsets(25f, 10f, 25f, 10f)
        }
    }

    private fun setupDateNavigation() {
        updateDateText()
        
        binding.btnPrevDay.setOnClickListener {
            if (currentDateOffset > -6) {
                currentDateOffset--
                updateDateText()
                refreshUsageUI()
            }
        }
        
        binding.btnNextDay.setOnClickListener {
            if (currentDateOffset < 0) {
                currentDateOffset++
                updateDateText()
                refreshUsageUI()
            }
        }
    }

    private fun updateDateText() {
        binding.btnNextDay.isEnabled = currentDateOffset < 0
        binding.btnPrevDay.isEnabled = currentDateOffset > -6
        
        if (currentDateOffset == 0) {
            binding.tvCurrentDate.text = "Today"
        } else if (currentDateOffset == -1) {
            binding.tvCurrentDate.text = "Yesterday"
        } else {
            val istZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
            val calendar = Calendar.getInstance(istZone)
            calendar.add(Calendar.DAY_OF_YEAR, currentDateOffset)
            val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
            sdf.timeZone = istZone
            binding.tvCurrentDate.text = sdf.format(calendar.time)
        }
        
        // Disable alpha to visually show state
        binding.btnNextDay.alpha = if (binding.btnNextDay.isEnabled) 1.0f else 0.3f
        binding.btnPrevDay.alpha = if (binding.btnPrevDay.isEnabled) 1.0f else 0.3f
    }

    private fun refreshUsageUI() {
        val istZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
        val calendar = Calendar.getInstance(istZone)
        calendar.add(Calendar.DAY_OF_YEAR, currentDateOffset)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = istZone
        val dateString = sdf.format(calendar.time)

        val dailyUsage = allUsageData[dateString]

        if (dailyUsage.isNullOrEmpty()) {
            binding.rvUsageList.visibility = View.GONE
            binding.usagePieChart.visibility = View.INVISIBLE
            binding.tvNoData.visibility = View.VISIBLE
            usageAdapter.updateFullList(emptyList())
            return
        }

        binding.rvUsageList.visibility = View.VISIBLE
        binding.usagePieChart.visibility = View.VISIBLE
        binding.tvNoData.visibility = View.GONE

        // Process data
        var totalTimeMs = 0L
        val listItems = mutableListOf<AppUsageItem>()

        for ((pkg, timeMs) in dailyUsage) {
            // Filter: Don't show the app if its usage is less than 1 minute (60,000ms)
            if (timeMs < 60000) continue

            totalTimeMs += timeMs
            val cleanPkg = pkg.replace("_", ".")
            val appName = getReadableAppName(cleanPkg)
            listItems.add(AppUsageItem(cleanPkg, appName, timeMs, 0))
        }

        // Check if after filtering we still have data
        if (listItems.isEmpty()) {
            binding.rvUsageList.visibility = View.GONE
            binding.usagePieChart.visibility = View.INVISIBLE
            binding.tvNoData.visibility = View.VISIBLE
            usageAdapter.updateFullList(emptyList())
            return
        }

        // Sort descending
        listItems.sortByDescending { it.timeMs }
        
        val pieEntries = ArrayList<PieEntry>()
        val maxItemTimeMs = listItems.first().timeMs
        
        // Build top 5 for pie chart
        val top5 = listItems.take(5)
        top5.forEach { topApp ->
            pieEntries.add(PieEntry(topApp.timeMs.toFloat(), topApp.appName))
        }
        
        if (listItems.size > 5) {
            val otherTime = listItems.drop(5).sumOf { it.timeMs }
            pieEntries.add(PieEntry(otherTime.toFloat(), "Other"))
        }

        // Update List with highest max time
        val updatedList = listItems.map { it.copy(maxTimeMs = maxItemTimeMs) }
        usageAdapter.updateFullList(updatedList)

        // Draw Pie Chart
        val dataSet = PieDataSet(pieEntries, "Daily Usage")
        dataSet.colors = chartColors
        dataSet.sliceSpace = 3f
        dataSet.selectionShift = 3f 
        
        // Use external labels but hide the lines
        dataSet.xValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        dataSet.yValuePosition = PieDataSet.ValuePosition.OUTSIDE_SLICE
        dataSet.valueLinePart1OffsetPercentage = 100f // Start right at the edge
        dataSet.valueLinePart1Length = 0.1f // Very short length
        dataSet.valueLinePart2Length = 0.1f // Very short length
        dataSet.valueLineWidth = 0f // Hide the line
        dataSet.valueLineColor = Color.TRANSPARENT
        
        dataSet.setDrawValues(false) // Don't show percentages, just names

        val pieData = PieData(dataSet)
        binding.usagePieChart.data = pieData
        
        // Set Total Time inside center
        binding.usagePieChart.centerText = TimeUtils.formatDuration(totalTimeMs)
        binding.usagePieChart.invalidate()
    }

    private fun getReadableAppName(packageName: String): String {
        // 1. Try Firebase map
        installedAppsMap[packageName]?.let { return it }
        
        // 2. Try Local Package Manager (for common apps installed on both)
        try {
            val pm = requireContext().packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            return pm.getApplicationLabel(info).toString()
        } catch (e: Exception) { }

        // 3. Hardcoded Fallbacks for common system apps that might be missing labels
        return when {
            packageName.contains("calendar") -> "Calendar"
            packageName.contains("calculator") -> "Calculator"
            packageName.contains("camera") -> "Camera"
            packageName.contains("gallery") || packageName.contains("photos") -> "Gallery"
            packageName.contains("contacts") -> "Contacts"
            packageName.contains("dialer") || packageName.contains("telecom") -> "Phone"
            packageName.contains("vending") -> "Play Store"
            packageName.contains("gm") && packageName.contains("mail") -> "Gmail"
            else -> packageName
        }
    }

    private fun updateUI(events: List<AppUsageItem>) {
       usageAdapter.updateFullList(events)
       binding.rvUsageList.visibility = if (events.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        usageListener?.let { FirebaseRepository.stopListening("usage", it) }
        appsListener?.let { FirebaseRepository.stopListening("installed_apps", it) }
        _binding = null
    }

}
