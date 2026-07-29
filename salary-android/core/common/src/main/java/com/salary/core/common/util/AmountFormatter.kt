package com.salary.core.common.util

import java.text.DecimalFormat

/**
 * 金额格式化工具
 */
object AmountFormatter {
    private val format = DecimalFormat("#,##0.00")

    /** 格式化金额为人民币显示格式：¥12,345.00 */
    fun format(amount: String?): String {
        if (amount.isNullOrBlank()) return "¥0.00"
        return try {
            "¥${format.format(amount.toDouble())}"
        } catch (e: NumberFormatException) {
            "¥0.00"
        }
    }

    /** 格式化Double金额为人民币显示格式：¥12,345.00 */
    fun format(amount: Double?): String {
        if (amount == null) return "¥0.00"
        return "¥${format.format(amount)}"
    }

    /** 格式化金额（无符号）：12,345.00 */
    fun formatPlain(amount: String?): String {
        if (amount.isNullOrBlank()) return "0.00"
        return try {
            format.format(amount.toDouble())
        } catch (e: NumberFormatException) {
            "0.00"
        }
    }

    /** 格式化Double金额（无符号）：12,345.00 */
    fun formatPlain(amount: Double?): String {
        if (amount == null) return "0.00"
        return format.format(amount)
    }

    /**
     * 格式化为两位小数（无千分位）：12345.60
     * 支持 Double / String / Int / Float，null 或异常返回 "0.00"
     */
    fun format2f(value: Any?): String {
        if (value == null) return "0.00"
        return when (value) {
            is Double -> if (value == 0.0) "0.00" else String.format("%.2f", value)
            is Float -> if (value == 0.0f) "0.00" else String.format("%.2f", value.toDouble())
            is String -> if (value.isBlank()) "0.00" else try { String.format("%.2f", value.toDouble()) } catch (_: NumberFormatException) { "0.00" }
            is Int -> String.format("%.2f", value.toDouble())
            else -> "0.00"
        }
    }
}
