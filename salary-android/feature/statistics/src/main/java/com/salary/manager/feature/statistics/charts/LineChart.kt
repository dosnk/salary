package com.salary.manager.feature.statistics.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.design.theme.AppColors

/**
 * 折线图数据项
 */
data class LineChartData(
    val label: String,
    val value: Float
)

/**
 * 折线图组件 - 使用Compose Canvas绘制
 * @param data 数据点列表
 * @param lineColor 折线颜色
 * @param modifier 修饰符
 */
@Composable
fun LineChart(
    data: List<LineChartData>,
    lineColor: Color = AppColors.Green400,
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

    val maxValue = data.maxOfOrNull { it.value }?.coerceAtLeast(1f) ?: 1f

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val padding = 20f

            // 绘制网格线
            for (i in 0..4) {
                val y = padding + (canvasHeight - 2 * padding) * i / 4
                drawLine(
                    color = Color(0xFFE0E0E0),
                    start = Offset(padding, y),
                    end = Offset(canvasWidth - padding, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                )
            }

            // 绘制折线
            if (data.size >= 2) {
                val path = Path()
                val stepX = (canvasWidth - 2 * padding) / (data.size - 1)

                data.forEachIndexed { index, item ->
                    val x = padding + index * stepX
                    val y = canvasHeight - padding - (item.value / maxValue) * (canvasHeight - 2 * padding)

                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)

                    // 绘制数据点
                    drawCircle(
                        color = lineColor,
                        radius = 4f,
                        center = Offset(x, y)
                    )
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 2.5f)
                )
            } else if (data.size == 1) {
                val x = canvasWidth / 2
                val y = canvasHeight - padding - (data[0].value / maxValue) * (canvasHeight - 2 * padding)
                drawCircle(color = lineColor, radius = 6f, center = Offset(x, y))
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
