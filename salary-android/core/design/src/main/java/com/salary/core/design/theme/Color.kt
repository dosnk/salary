package com.salary.core.design.theme

import androidx.compose.ui.graphics.Color

/**
 * 应用颜色体系 - 黄绿色系
 */
object AppColors {
    // 绿色主色系
    val Green50 = Color(0xFFF5FBE8)
    val Green100 = Color(0xFFE0F0C0)
    val Green200 = Color(0xFFC4E098)
    val Green300 = Color(0xFF96CC4C)
    val Green400 = Color(0xFF8CC63F)   // 主色
    val Green500 = Color(0xFF7CB034)   // 主色深
    val Green600 = Color(0xFF6B9727)
    val Green700 = Color(0xFF567A1F)
    val Green800 = Color(0xFF415E18)
    val Green900 = Color(0xFF2C4110)

    // 背景色
    val Background = Color(0xFFF9FDF7)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceVariant = Color(0xFFF5FBE8)

    // 文字色
    val TextPrimary = Color(0xFF333333)
    val TextSecondary = Color(0xFF666666)
    val TextTertiary = Color(0xFF999999)
    val TextPlaceholder = Color(0xFFCCCCCC)

    // 语义色
    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val Error = Color(0xFFEF4444)

    // 暗色主题
    val GreenDark = Color(0xFFA0D468)

    /**
     * 亮色主题配色方案
     */
    fun lightColorScheme() = androidx.compose.material3.lightColorScheme(
        primary = Green400,
        onPrimary = Color.White,
        primaryContainer = Green100,
        onPrimaryContainer = Green900,
        secondary = Green500,
        onSecondary = Color.White,
        secondaryContainer = Green50,
        onSecondaryContainer = Green700,
        tertiary = Success,
        background = Background,
        onBackground = TextPrimary,
        surface = Surface,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceVariant,
        onSurfaceVariant = TextSecondary,
        error = Error,
        onError = Color.White,
        outline = Color(0xFFE0E0E0),
        outlineVariant = Color(0xFFF0F0F0),
    )

    /**
     * 暗色主题配色方案
     */
    fun darkColorScheme() = androidx.compose.material3.darkColorScheme(
        primary = GreenDark,
        onPrimary = Color.Black,
        primaryContainer = Green700,
        onPrimaryContainer = Green100,
        secondary = Green300,
        onSecondary = Color.Black,
        background = Color(0xFF1A1A2E),
        onBackground = Color(0xFFE0E0E0),
        surface = Color(0xFF252540),
        onSurface = Color(0xFFE0E0E0),
        error = Color(0xFFCF6679),
    )
}
