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
}
