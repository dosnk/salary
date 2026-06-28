package com.salary.manager.feature.statistics.advance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.common.util.AmountFormatter
import com.salary.core.common.util.DateFormatter
import com.salary.core.design.theme.AppColors
import com.salary.core.ui.state.ListUiState

/**
 * 预支管理页面 - 预支记录列表+创建预支
 */
@Composable
fun AdvanceScreen(
    viewModel: AdvanceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = AppColors.Green400,
                contentColor = androidx.compose.ui.graphics.Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "创建预支")
            }
        }
    ) { padding ->
        when (state) {
            is ListUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.Green400)
                }
            }
            is ListUiState.Success -> {
                val items = (state as ListUiState.Success<AdvanceItem>).items
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无预支记录", color = AppColors.TextTertiary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 预支总额卡片
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = AppColors.Green50)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("预支总额", fontSize = 13.sp, color = AppColors.TextSecondary)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        AmountFormatter.format(items.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }.toString()),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.Green400
                                    )
                                }
                            }
                        }

                        items(items) { item ->
                            AdvanceCard(item)
                        }
                    }
                }
            }
            is ListUiState.Error -> {
                val errorMsg = (state as ListUiState.Error).message
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMsg, color = AppColors.TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("请检查网络连接后重试", color = AppColors.TextTertiary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadAdvances() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("重新加载", color = Color.White) }
                    }
                }
            }
        }
    }

    // 创建预支弹窗
    if (showCreateDialog) {
        CreateAdvanceDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { amount, remark ->
                viewModel.createAdvance(amount, remark)
                showCreateDialog = false
            }
        )
    }
}

/**
 * 预支记录卡片 - 展示单条预支信息
 */
@Composable
fun AdvanceCard(item: AdvanceItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    AmountFormatter.format(item.amount),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Green400
                )
                item.remark?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(it, fontSize = 13.sp, color = AppColors.TextSecondary)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    DateFormatter.formatDate(item.createdAt),
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary
                )
                if (item.settled) {
                    Text("已结算", fontSize = 12.sp, color = AppColors.Success)
                } else {
                    Text("未结算", fontSize = 12.sp, color = AppColors.Warning)
                }
            }
        }
    }
}

/**
 * 创建预支弹窗 - 输入金额和备注
 */
@Composable
fun CreateAdvanceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建预支", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("预支金额") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注（选填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(amount, remark.ifBlank { null }) },
                enabled = amount.toDoubleOrNull()?.let { it > 0 } == true,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400)
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 预支记录数据项
 */
data class AdvanceItem(
    val id: Int,
    val amount: String,
    val remark: String?,
    val createdAt: String,
    val settled: Boolean
)
