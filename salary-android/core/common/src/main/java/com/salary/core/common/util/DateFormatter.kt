package com.salary.core.common.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日期格式化工具
 */
object DateFormatter {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val monthFormat = SimpleDateFormat("yyyy年M月", Locale.CHINA)

    /** 格式化ISO日期字符串为 yyyy-MM-dd */
    fun formatDate(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA).parse(isoString)
            date?.let { dateFormat.format(it) } ?: isoString.substring(0, 10)
        } catch (e: Exception) {
            isoString.substring(0, minOf(10, isoString.length))
        }
    }

    /** 格式化ISO日期字符串为 yyyy-MM-dd HH:mm */
    fun formatDateTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA).parse(isoString)
            date?.let { dateTimeFormat.format(it) } ?: isoString.substring(0, 16)
        } catch (e: Exception) {
            isoString
        }
    }

    /** 获取当前月份显示：2026年6月 */
    fun currentMonth(): String = monthFormat.format(Date())
}
