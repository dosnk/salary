package com.salary.manager.feature.statistics.settlement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.common.util.AmountFormatter
import com.salary.core.design.theme.AppColors
import com.salary.core.ui.state.UiState

/**
 * 结算预览页面 - 展示待结算工程列表、金额汇总、预支扣除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementPreviewScreen(
    onBack: () -> Unit = {},
    onConfirm: () -> Unit = {},
    viewModel: SettlementPreviewViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("结算预览") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = androidx.compose.ui.graphics.Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("实发金额", fontSize = 12.sp, color = AppColors.TextSecondary)
                        val actualAmount = when (state) {
                            is UiState.Success -> AmountFormatter.format((state as UiState.Success<SettlementPreviewData>).data.actualAmount)
                            else -> "¥0.00"
                        }
                        Text(actualAmount, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.Green400)
                    }
                    Button(
                        onClick = onConfirm,
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400)
                    ) {
                        Text("确认结算", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    ) { padding ->
        when (state) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Green400)
                }
            }
            is UiState.Success -> {
                val data = (state as UiState.Success<SettlementPreviewData>).data
                SettlementPreviewContent(data, modifier = Modifier.padding(padding))
            }
            is UiState.Error -> {
                val errorMsg = (state as UiState.Error).message
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMsg, color = AppColors.TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("请检查网络连接后重试", color = AppColors.TextTertiary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SettlementPreviewContent(data: SettlementPreviewData, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 金额汇总
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = AppColors.Green50)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SummaryRow("工程总额", AmountFormatter.format(data.totalAmount))
                    SummaryRow("预支扣除", "- ${AmountFormatter.format(data.advanceAmount)}")
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SummaryRow("实发金额", AmountFormatter.format(data.actualAmount), isBold = true)
                }
            }
        }

        // 工程列表
        item {
            Text("包含工程", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        items(data.projects) { project ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        project.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        AmountFormatter.format(project.amount),
                        fontSize = 14.sp,
                        color = AppColors.Green500,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String, isBold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = AppColors.TextSecondary,
            maxLines = 1
        )
        Text(
            value,
            fontSize = if (isBold) 18.sp else 14.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) AppColors.Green400 else AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

data class SettlementPreviewData(
    val totalAmount: String,
    val advanceAmount: String,
    val actualAmount: String,
    val projects: List<SettlementProjectItem>
)

data class SettlementProjectItem(
    val id: Int,
    val name: String,
    val amount: String
)
