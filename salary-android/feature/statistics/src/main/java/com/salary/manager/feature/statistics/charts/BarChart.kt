package com.salary.manager.feature.statistics.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.design.theme.AppColors

/**
 * 柱状图数据项
 */
data class BarChartData(
    val label: String,
    val value: Float,
    val color: Color = AppColors.Green400
)

/**
 * 柱状图组件 - 使用Compose Canvas绘制
 * @param data 数据列表
 * @param maxValue Y轴最大值
 * @param modifier 修饰符
 */
@Composable
fun BarChart(
    data: List<BarChartData>,
    maxValue: Float = data.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier.height(200.dp).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无数据", color = AppColors.TextTertiary, fontSize = 14.sp)
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val barCount = data.size
            val barWidth = (canvasWidth / barCount) * 0.6f
            val gap = (canvasWidth / barCount) * 0.4f

            data.forEachIndexed { index, item ->
                val barHeight = (item.value / maxValue) * (canvasHeight - 40f)
                val x = index * (barWidth + gap) + gap / 2
                val y = canvasHeight - barHeight - 20f

                // 绘制柱体
                drawRect(
                    color = item.color,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, barHeight)
                )
            }
        }

        // X轴标签
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            data.forEach { item ->
                Text(
                    item.label,
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
