package com.salary.core.design.component

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 通用滑动返回布局
 *
 * 从屏幕左边缘（80dp 范围内）起手，向右滑动超过 100dp 时触发 [onBack] 回调。
 * 同时配合 [androidx.activity.compose.BackHandler] 拦截系统返回手势/按键，
 * 确保 Android 10+ 系统手势导航不会直接退出 Activity。
 *
 * 使用方式：
 * ```
 * SwipeBackLayout(onBack = { xxxSubPage = 0 }) {
 *     SomeScreen(...)
 * }
 * ```
 *
 * @param onBack 触发返回时的回调
 * @param enabled 是否启用滑动返回，默认 true
 * @param content 子内容
 */
@Composable
fun SwipeBackLayout(
    onBack: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }

    val density = LocalDensity.current
    // 起始触摸点x需小于该值才算左边缘：80dp
    val edgeWidthPx = with(density) { 80.dp.toPx() }
    // 触发返回所需的最小水平滑动距离：100dp
    val backThresholdPx = with(density) { 100.dp.toPx() }

    // 记录手势起始x坐标和累计滑动量
    var startX by remember { mutableStateOf(0f) }
    var totalDelta by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        totalDelta = 0f
                    },
                    onDragEnd = {
                        // 手势结束：累计滑动距离超过阈值且起始位置在左边缘 → 返回上一级
                        if (startX <= edgeWidthPx && totalDelta >= backThresholdPx) {
                            onBack()
                        }
                        totalDelta = 0f
                    },
                    onDragCancel = {
                        totalDelta = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        // 累计水平滑动量（向右为正）
                        totalDelta += dragAmount
                    }
                )
            }
    ) {
        content()
    }
}
