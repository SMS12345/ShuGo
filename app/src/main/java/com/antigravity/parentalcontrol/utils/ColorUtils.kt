package com.antigravity.parentalcontrol.utils

import android.graphics.Color
import kotlin.math.absoluteValue

object ColorUtils {
    private val COLORS = arrayOf(
        "#E53935", "#D81B60", "#8E24AA", "#5E35B1", "#3949AB", 
        "#1E88E5", "#039BE5", "#00ACC1", "#00897B", "#43A047", 
        "#7CB342", "#C0CA33", "#FDD835", "#FFB300", "#FB8C00", 
        "#F4511E", "#6D4C41", "#757575", "#546E7A"
    )

    fun getColorForString(input: String): Int {
        val hash = input.hashCode().absoluteValue
        val colorHex = COLORS[hash % COLORS.size]
        return Color.parseColor(colorHex)
    }
}
