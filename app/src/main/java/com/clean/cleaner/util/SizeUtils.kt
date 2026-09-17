package com.clean.cleaner.util

import java.text.DecimalFormat

object SizeUtils {
    private val df = DecimalFormat("0.0")

    fun format(size: Long): String {
        if (size <= 0) return "0B"
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${df.format(size / 1024.0)} KB"
            size < 1024 * 1024 * 1024 -> "${df.format(size / (1024.0 * 1024))} MB"
            else -> "${df.format(size / (1024.0 * 1024 * 1024))} GB"
        }
    }

    fun formatPercent(used: Long, total: Long): Int {
        if (total <= 0) return 0
        return ((used.toDouble() / total) * 100).toInt().coerceIn(0, 100)
    }

    /** 紧凑格式：10.94GB / 617.4MB / 38个(4.05GB) 等，用于功能入口标签 */
    fun compact(size: Long): String {
        if (size <= 0) return "0B"
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${df.format(size / 1024.0)}KB"
            size < 1024 * 1024 * 1024 -> "${df.format(size / (1024.0 * 1024))}MB"
            else -> "${df.format(size / (1024.0 * 1024 * 1024))}GB"
        }
    }
}
