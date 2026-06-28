package com.salary.manager.feature.statistics.settlement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.salary.core.design.theme.AppColors
import com.salary.core.ui.state.ListUiState

/**
 * 结算单列表页面 - 展示待结算和已结算的工程列表
 */
@Composable
fun SettlementListScreen(
    onPreviewSettlement: (List<Int>) -> Unit = {},
    onHistoryClick: () -> Unit = {},
    viewModel: SettlementListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("结算管理", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onHistoryClick) {
                Text("结算历史", color = AppColors.Green400)
            }
        }

        when (state) {
            is ListUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Green400)
                }
            }
            is ListUiState.Success -> {
                val items = (state as ListUiState.Success<SettlementProjectItem>).items
                if (items.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无待结算工程", color = AppColors.TextTertiary)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items) { item ->
                            SettlementProjectCard(
                                item = item,
                                onSettle = { onPreviewSettlement(listOf(item.id)) }
                            )
                        }

                        // 批量结算按钮
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { onPreviewSettlement(items.map { it.id }) },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = MaterialTheme.shapes.large,
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400)
                            ) {
                                Text("批量结算 (${items.size}个工程)")
                            }
                        }
                    }
                }
            }
            is ListUiState.Error -> {
                val errorMsg = (state as ListUiState.Error).message
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMsg, color = AppColors.TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("请检查网络连接后重试", color = AppColors.TextTertiary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadSettlements() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("重新加载", color = Color.White) }
                    }
                }
            }
        }
    }
}

/**
 * 结算工程卡片 - 展示单个待结算工程信息
 */
@Composable
fun SettlementProjectCard(
    item: SettlementProjectItem,
    onSettle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    AmountFormatter.format(item.amount),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Green400
                )
            }
            OutlinedButton(
                onClick = onSettle,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Green400)
            ) {
                Text("结算")
            }
        }
    }
}
