package com.salary.manager.feature.home.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.design.theme.AppColors

/**
 * 施工人员数据模型
 */
data class WorkerSelectModel(
    val userId: Int,
    val nickname: String,
    val isSelected: Boolean = false
)

/**
 * 施工人员选择器弹窗 - 支持多选
 * @param workers 可选施工人员列表
 * @param selectedIds 已选中的用户ID集合
 * @param onConfirm 确认回调，返回选中的用户ID列表
 * @param onDismiss 取消回调
 */
@Composable
fun WorkerSelectDialog(
    workers: List<WorkerSelectModel>,
    selectedIds: Set<Int>,
    onConfirm: (List<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    val localSelectedIds = remember(selectedIds) { mutableStateOf(selectedIds.toMutableSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择施工人员", fontWeight = FontWeight.SemiBold)
        },
        text = {
            if (workers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无可选施工人员", color = AppColors.TextTertiary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(workers) { worker ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = localSelectedIds.value.contains(worker.userId),
                                onCheckedChange = { checked ->
                                    localSelectedIds.value = if (checked) {
                                        localSelectedIds.value.plus(worker.userId)
                                    } else {
                                        localSelectedIds.value.minus(worker.userId)
                                    }.toMutableSet()
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AppColors.Green400
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(worker.nickname, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(localSelectedIds.value.toList()) },
                enabled = localSelectedIds.value.isNotEmpty()
            ) {
                Text("确定 (${localSelectedIds.value.size})", color = AppColors.Green400)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
