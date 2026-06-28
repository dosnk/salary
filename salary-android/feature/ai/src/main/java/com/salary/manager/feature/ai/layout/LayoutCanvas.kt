package com.salary.manager.feature.ai.layout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.SvgDimensionsDto
import com.salary.core.network.api.SvgLayoutDto
import com.salary.core.network.api.SvgPanelDto
import com.salary.core.network.api.SvgRectDto

/**
 * 排料Canvas渲染器
 *
 * 功能:
 * - 渲染房间轮廓
 * - 渲染板材布局（整板/裁切板不同颜色）
 * - 渲染尺寸标注
 * - 支持双指缩放和拖动手势
 */
@Composable
fun LayoutCanvas(
    layoutData: SvgLayoutDto,
    modifier: Modifier = Modifier
) {
    // 缩放和平移状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // 限制缩放范围 0.5x ~ 5x
                    val newScale = (scale * zoom).coerceIn(0.5f, 5f)
                    scale = newScale
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    ) {
        // 应用平移和缩放变换 — 手动计算变换后的坐标
        val canvasWidth = size.width
        val canvasHeight = size.height
        val svgScale = minOf(
            canvasWidth / layoutData.svgWidth,
            canvasHeight / layoutData.svgHeight
        ) * scale

        // 绘制房间轮廓
        drawRoom(layoutData.roomRect, svgScale, offsetX, offsetY)

        // 绘制板材布局
        layoutData.panels.forEach { panel ->
            drawPanel(panel, svgScale, offsetX, offsetY)
        }

        // 绘制尺寸标注
        drawDimensions(layoutData, svgScale, offsetX, offsetY)
    }
}

/**
 * 绘制房间轮廓
 */
private fun DrawScope.drawRoom(roomRect: SvgRectDto, scale: Float, offsetX: Float, offsetY: Float) {
    // 房间背景
    drawRect(
        color = AppColors.Green50,
        topLeft = Offset(roomRect.x.toFloat() * scale + offsetX, roomRect.y.toFloat() * scale + offsetY),
        size = Size(roomRect.w.toFloat() * scale, roomRect.h.toFloat() * scale)
    )

    // 房间边框
    drawRect(
        color = AppColors.TextPrimary,
        topLeft = Offset(roomRect.x.toFloat() * scale + offsetX, roomRect.y.toFloat() * scale + offsetY),
        size = Size(roomRect.w.toFloat() * scale, roomRect.h.toFloat() * scale),
        style = Stroke(width = 2f)
    )
}

/**
 * 绘制单个板材
 */
private fun DrawScope.drawPanel(panel: SvgPanelDto, scale: Float, offsetX: Float, offsetY: Float) {
    val x = panel.x.toFloat() * scale + offsetX
    val y = panel.y.toFloat() * scale + offsetY
    val w = panel.w.toFloat() * scale
    val h = panel.h.toFloat() * scale

    if (panel.isFull) {
        // 整板：绿色填充
        drawRect(
            color = AppColors.Green200.copy(alpha = 0.6f),
            topLeft = Offset(x, y),
            size = Size(w, h)
        )
        // 整板边框
        drawRect(
            color = AppColors.Green400,
            topLeft = Offset(x, y),
            size = Size(w, h),
            style = Stroke(width = 1f)
        )
    } else {
        // 裁切板：黄色填充 + 虚线边框
        drawRect(
            color = Color(0xFFFFF3CD).copy(alpha = 0.6f),
            topLeft = Offset(x, y),
            size = Size(w, h)
        )
        drawRect(
            color = AppColors.Warning,
            topLeft = Offset(x, y),
            size = Size(w, h),
            style = Stroke(
                width = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
            )
        )
    }
}

/**
 * 绘制尺寸标注
 */
private fun DrawScope.drawDimensions(layoutData: SvgLayoutDto, scale: Float, offsetX: Float, offsetY: Float) {
    val dims = layoutData.dimensions
    val room = layoutData.roomRect
    val padding = layoutData.padding.toFloat() * scale

    // 使用 nativeCanvas 绘制文字
    val paint = android.graphics.Paint().apply {
        textSize = 24f
        color = AppColors.TextSecondary.hashCode()
        isAntiAlias = true
    }

    // 底部标注：房间长度
    val lengthText = "${dims.roomLength.toInt()}cm"
    val centerX = (room.x.toFloat() + room.w.toFloat() / 2) * scale + offsetX
    val bottomY = (room.y.toFloat() + room.h.toFloat()) * scale + padding * 0.6f + offsetY
    drawContext.canvas.nativeCanvas.drawText(
        lengthText,
        centerX - paint.measureText(lengthText) / 2,
        bottomY,
        paint
    )

    // 右侧标注：房间宽度
    val widthText = "${dims.roomWidth.toInt()}cm"
    val rightX = (room.x.toFloat() + room.w.toFloat()) * scale + padding * 0.3f + offsetX
    val centerY = (room.y.toFloat() + room.h.toFloat() / 2) * scale + offsetY
    drawContext.canvas.nativeCanvas.drawText(
        widthText,
        rightX,
        centerY + paint.textSize / 3,
        paint
    )

    // 板材尺寸标注
    val panelPaint = android.graphics.Paint().apply {
        textSize = 18f
        color = AppColors.TextTertiary.hashCode()
        isAntiAlias = true
    }
    val panelInfo = "${dims.panelLength.toInt()}\u00D7${dims.panelWidth.toInt()}cm"
    if (layoutData.panels.isNotEmpty()) {
        val firstPanel = layoutData.panels[0]
        val px = (firstPanel.x.toFloat() + firstPanel.w.toFloat() / 2) * scale + offsetX
        val py = (firstPanel.y.toFloat() + firstPanel.h.toFloat() / 2) * scale + offsetY
        drawContext.canvas.nativeCanvas.drawText(
            panelInfo,
            px - panelPaint.measureText(panelInfo) / 2,
            py + panelPaint.textSize / 3,
            panelPaint
        )
    }
}
