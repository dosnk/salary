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
import com.salary.core.common.util.DateFormatter
import com.salary.core.design.theme.AppColors
import com.salary.core.ui.state.ListUiState

/**
 * 结算历史页面 - 展示已结算记录列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementHistoryScreen(
    onBack: () -> Unit = {},
    viewModel: SettlementHistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("结算历史") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
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
                val items = (state as ListUiState.Success<SettlementHistoryItem>).items
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无结算记录", color = AppColors.TextTertiary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items) { item ->
                            SettlementHistoryCard(item)
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
                            onClick = { viewModel.loadHistory() },
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
 * 结算历史卡片 - 展示单条结算记录摘要
 */
@Composable
fun SettlementHistoryCard(item: SettlementHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    item.settlementNo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary
                )
                Text(
                    DateFormatter.formatDate(item.settledAt),
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                AmountFormatter.format(item.actualAmount),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Green400
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "总额 ${AmountFormatter.formatPlain(item.totalAmount)} | 预支 ${AmountFormatter.formatPlain(item.advanceAmount)}",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary
                )
                if (item.confirmed) {
                    Text("已确认", fontSize = 12.sp, color = AppColors.Success, fontWeight = FontWeight.Medium)
                } else {
                    Text("待确认", fontSize = 12.sp, color = AppColors.Warning, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * 结算历史数据项
 */
data class SettlementHistoryItem(
    val id: Int,
    val settlementNo: String,
    val totalAmount: String,
    val advanceAmount: String,
    val actualAmount: String,
    val settledAt: String,
    val confirmed: Boolean
)
