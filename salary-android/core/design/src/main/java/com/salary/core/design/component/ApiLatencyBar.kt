package com.salary.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.design.theme.AppColors

/**
 * 全局API状态栏组件
 *
 * 显示在所有页面底部，实时展示后端连接状态和API延迟：
 * - 在线：显示延迟值，颜色按速度区分（绿/橙/红）
 * - 离线：显示离线状态和错误详情
 *
 * @param isOnline 后端是否在线
 * @param latencyMs 最近一次API请求的网络耗时（毫秒）
 * @param lastError 最近一次连接错误信息
 * @param modifier 修饰符
 */
@Composable
fun ApiLatencyBar(
    isOnline: Boolean,
    latencyMs: Long,
    lastError: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：连接状态
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isOnline) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = if (isOnline) "在线" else "离线",
                modifier = Modifier.size(14.dp),
                tint = if (isOnline) Color(0xFF4CAF50) else AppColors.Error
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isOnline) "服务器在线" else "服务器离线",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = if (isOnline) Color(0xFF4CAF50) else AppColors.Error
            )
        }

        // 右侧：延迟值或错误信息
        if (isOnline) {
            Text(
                text = if (latencyMs > 0) "API时延：${latencyMs}ms" else "API时延：--",
                fontSize = 11.sp,
                color = when {
                    latencyMs <= 0 -> AppColors.TextTertiary
                    latencyMs < 200 -> Color(0xFF4CAF50)   // 绿色：<200ms 快速
                    latencyMs < 500 -> Color(0xFFFF9800)   // 橙色：200-500ms 一般
                    else -> Color(0xFFF44336)               // 红色：>500ms 慢
                }
            )
        } else {
            Text(
                text = lastError ?: "连接失败",
                fontSize = 11.sp,
                color = AppColors.Error
            )
        }
    }
}
