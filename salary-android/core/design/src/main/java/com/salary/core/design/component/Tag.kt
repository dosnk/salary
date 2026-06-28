package com.salary.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.design.theme.AppColors

/**
 * 通用标签组件
 */
@Composable
fun SalaryTag(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        color = textColor,
        fontSize = 12.sp,
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/**
 * 工程状态标签
 */
@Composable
fun ProjectStatusTag(status: String) {
    val (text, color) = when (status) {
        "preparing" -> "备料中" to AppColors.Warning
        "constructing" -> "施工中" to AppColors.Green400
        "completed" -> "已完工" to AppColors.Success
        "canceled" -> "已取消" to AppColors.TextTertiary
        else -> status to AppColors.TextSecondary
    }
    SalaryTag(text = text, backgroundColor = color.copy(alpha = 0.12f), textColor = color)
}

/**
 * 结算状态标签
 */
@Composable
fun SettlementStatusTag(status: String) {
    val (text, color) = when (status) {
        "unsettled" -> "未结算" to AppColors.Warning
        "settling" -> "统计中" to AppColors.Green400
        "settled" -> "已结算" to AppColors.Success
        else -> status to AppColors.TextSecondary
    }
    SalaryTag(text = text, backgroundColor = color.copy(alpha = 0.12f), textColor = color)
}
