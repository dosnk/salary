package com.salary.core.common.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日期格式化工具
 *
 * 性能注意：
 * - ISO 解析格式 "yyyy-MM-dd'T'HH:mm:ss" 使用 ThreadLocal 缓存 SimpleDateFormat 实例，
 *   避免每次调用都新建（SimpleDateFormat 构造非轻量，且非线程安全）
 * - 顶层展示格式 dateFormat/dateTimeFormat/monthFormat 为单例，仅在主线程读取，无需 ThreadLocal
 */
object DateFormatter {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val monthFormat = SimpleDateFormat("yyyy年M月", Locale.CHINA)

    // ISO 解析格式用 ThreadLocal 缓存，避免每次调用都 new SimpleDateFormat
    // 原因：SimpleDateFormat 构造要解析 pattern、新建 Calendar，是非轻量操作
    // 线程安全：SimpleDateFormat 本身非线程安全，用 ThreadLocal 隔离
    private val isoParserThreadLocal = ThreadLocal<SimpleDateFormat>()

    private fun getIsoParser(): SimpleDateFormat {
        var fmt = isoParserThreadLocal.get()
        if (fmt == null) {
            fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA)
            isoParserThreadLocal.set(fmt)
        }
        return fmt
    }

    /** 格式化ISO日期字符串为 yyyy-MM-dd */
    fun formatDate(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val date = getIsoParser().parse(isoString)
            date?.let { dateFormat.format(it) } ?: isoString.substring(0, 10)
        } catch (e: Exception) {
            isoString.substring(0, minOf(10, isoString.length))
        }
    }

    /** 格式化ISO日期字符串为 yyyy-MM-dd HH:mm */
    fun formatDateTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        return try {
            val date = getIsoParser().parse(isoString)
            date?.let { dateTimeFormat.format(it) } ?: isoString.substring(0, 16)
        } catch (e: Exception) {
            isoString
        }
    }

    /** 获取当前月份显示：2026年6月 */
    fun currentMonth(): String = monthFormat.format(Date())
}
