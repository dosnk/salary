package com.salary.manager.feature.statistics.settlement

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.common.util.AmountFormatter
import com.salary.core.design.theme.AppColors

/**
 * 结算确认弹窗 - 二次确认结算操作
 * @param totalAmount 工程总额
 * @param advanceAmount 预支扣除
 * @param actualAmount 实发金额
 * @param onConfirm 确认回调
 * @param onDismiss 取消回调
 */
@Composable
fun SettlementConfirmDialog(
    totalAmount: String,
    advanceAmount: String,
    actualAmount: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("确认结算", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("请确认以下结算信息：", fontSize = 14.sp, color = AppColors.TextSecondary)

                HorizontalDivider()

                // 金额明细
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("工程总额", fontSize = 14.sp)
                    Text(AmountFormatter.format(totalAmount), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("预支扣除", fontSize = 14.sp)
                    Text("- ${AmountFormatter.format(advanceAmount)}", fontSize = 14.sp, color = AppColors.Error)
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("实发金额", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        AmountFormatter.format(actualAmount),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Green400
                    )
                }

                Text(
                    "确认后将生成结算单，不可撤销。",
                    fontSize = 12.sp,
                    color = AppColors.Warning
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400)
            ) {
                Text("确认结算")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
