package com.salary.core.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.design.theme.AppColors

/**
 * 浮动API状态芯片
 *
 * 浮动在页面右下角，合并显示服务器在线状态和API延迟：
 * - 在线：绿色圆点 + "服务器在线：100ms"
 * - 离线：红色圆点 + "服务器离线：服务器错误（502）"
 * - 根据服务器在线状态自动缩进，右下角浮动显示
 * - 带自动动画过渡
 *
 * 使用方式：在页面Box的右下角放置此组件
 * ```kotlin
 * Box(modifier = Modifier.fillMaxSize()) {
 *     // 页面内容
 *     ApiLatencyChip(
 *         isOnline = isOnline,
 *         latencyMs = latencyMs,
 *         lastError = lastError,
 *         modifier = Modifier.align(Alignment.BottomEnd)
 *     )
 * }
 * ```
 *
 * @param isOnline 后端是否在线
 * @param latencyMs 最近一次API请求的网络耗时（毫秒）
 * @param lastError 最近一次连接错误信息
 * @param modifier 修饰符（通常用于定位）
 */
@Composable
fun ApiLatencyChip(
    isOnline: Boolean,
    latencyMs: Long,
    lastError: String?,
    modifier: Modifier = Modifier
) {
    // 监听在线状态变化，实现动画过渡
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .shadow(4.dp, RoundedCornerShape(20.dp))
                .background(
                    color = if (isOnline) Color(0xFFF1F8E9) else Color(0xFFFFF3F0),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // 状态指示点
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isOnline) Color(0xFF4CAF50) else AppColors.Error,
                        shape = RoundedCornerShape(4.dp)
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            // 状态文字
            if (isOnline) {
                // 在线：显示时延
                Text(
                    text = if (latencyMs > 0) "服务器在线：${latencyMs}ms" else "服务器在线",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        latencyMs <= 0 -> Color(0xFF4CAF50)
                        latencyMs < 200 -> Color(0xFF4CAF50)
                        latencyMs < 500 -> Color(0xFFFF9800)
                        else -> Color(0xFFFF5722)
                    }
                )
            } else {
                // 离线：显示错误信息
                val errorText = when {
                    lastError != null -> lastError
                    else -> "连接失败"
                }
                Text(
                    text = "服务器离线：$errorText",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.Error,
                    maxLines = 1
                )
            }
        }
    }
}