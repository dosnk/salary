package com.salary.core.design.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.salary.core.design.theme.AppColors

/**
 * 输入框统一圆角形状（小圆角 8dp）
 *
 * 用于全应用 OutlinedTextField 的 shape 参数，确保各页面输入框圆角一致。
 */
val SalaryTextFieldShape = RoundedCornerShape(8.dp)

/**
 * 输入框次要圆角形状（更小圆角 6dp）
 *
 * 用于工日输入框等紧凑场景，与默认 [SalaryTextFieldShape] 区分。
 */
val SalaryTextFieldShapeSmall = RoundedCornerShape(6.dp)

/**
 * 应用统一OutlinedTextField配色模板
 *
 * 以绿色主色（[AppColors.Green400]）为聚焦边框/标签颜色，调用方通过参数覆盖默认值
 * 以适配禁用态、未聚焦态、容器背景等差异化需求。
 *
 * 使用示例：
 * ```
 * OutlinedTextField(
 *     ...
 *     colors = salaryTextFieldColors(),
 *     shape = SalaryTextFieldShape
 * )
 * ```
 *
 * @param focusedBorderColor 聚焦时边框色，默认主色绿
 * @param focusedLabelColor 聚焦时标签色，默认主色绿
 * @param disabledBorderColor 禁用时边框色，默认主色绿（用于只读但需高亮的字段）
 * @param disabledTextColor 禁用时文字色，默认主文字色（避免禁用后变灰看不清）
 * @param unfocusedBorderColor 未聚焦边框色，默认 [Color.Unspecified] 由 Material 主题决定
 * @param unfocusedContainerColor 未聚焦容器背景色，默认 [Color.Unspecified]
 * @param focusedContainerColor 聚焦容器背景色，默认 [Color.Unspecified]
 */
@Composable
fun salaryTextFieldColors(
    focusedBorderColor: Color = AppColors.Green400,
    focusedLabelColor: Color = AppColors.Green400,
    disabledBorderColor: Color = AppColors.Green400,
    disabledTextColor: Color = AppColors.TextPrimary,
    unfocusedBorderColor: Color = Color.Unspecified,
    unfocusedContainerColor: Color = Color.Unspecified,
    focusedContainerColor: Color = Color.Unspecified
): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = focusedBorderColor,
    focusedLabelColor = focusedLabelColor,
    disabledBorderColor = disabledBorderColor,
    disabledTextColor = disabledTextColor,
    unfocusedBorderColor = unfocusedBorderColor,
    unfocusedContainerColor = unfocusedContainerColor,
    focusedContainerColor = focusedContainerColor
)
