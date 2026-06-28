package com.salary.core.design.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 应用形状系统
 * 圆角: 大24dp / 中16dp / 小8dp
 */
val AppShapes = Shapes(
    extraLarge = RoundedCornerShape(24.dp),  // 弹窗/大卡片
    large = RoundedCornerShape(16.dp),        // 按钮/卡片
    medium = RoundedCornerShape(12.dp),       // 通用组件
    small = RoundedCornerShape(8.dp),         // 输入框/标签
    extraSmall = RoundedCornerShape(4.dp)     // 小徽章
)
