package com.salary.manager.feature.home.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
 *
 * 弹窗规范：使用 Dialog + usePlatformDefaultWidth=false，
 * 宽度按屏幕宽度的 92% 自适应，避免窄屏截断、宽屏过窄。
 *
 * 当前状态：暂无调用方，预留给工程创建/编辑页选择施工人员使用
 * （见 docs/development-roadmap.md 4.1.7 施工人员选择器）。
 * 保留在 list 目录因与工程列表数据模型同源，后续接入工程创建流程时可直接复用。
 *
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "选择施工人员",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

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
                        modifier = Modifier
                            .fillMaxWidth()
                            // 限制最大高度，避免施工人员过多时弹窗撑满屏幕
                            .heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(workers, key = { it.userId }) { worker ->
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { onConfirm(localSelectedIds.value.toList()) },
                        enabled = localSelectedIds.value.isNotEmpty()
                    ) {
                        Text("确定 (${localSelectedIds.value.size})", color = AppColors.Green400)
                    }
                }
            }
        }
    }
}
