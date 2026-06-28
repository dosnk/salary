package com.salary.manager.feature.statistics.monthly

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.salary.core.ui.state.UiState
import com.salary.manager.feature.statistics.charts.BarChart
import com.salary.manager.feature.statistics.charts.BarChartData

/**
 * 月度统计页面
 */
@Composable
fun MonthlyStatsScreen(
    viewModel: MonthlyStatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 月份选择器
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { viewModel.previousMonth() }) {
                Text("< 上月")
            }
            Text(
                viewModel.currentMonth.collectAsState().value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = { viewModel.nextMonth() }) {
                Text("下月 >")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (state) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Green400)
                }
            }
            is UiState.Success -> {
                val data = (state as UiState.Success<MonthlyStatsData>).data
                MonthlyStatsContent(data)
            }
            is UiState.Error -> {
                val errorMsg = (state as UiState.Error).message
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMsg, color = AppColors.TextSecondary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("请检查网络连接后重试", color = AppColors.TextTertiary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadStats() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("重新加载", color = Color.White) }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyStatsContent(data: MonthlyStatsData) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 收入柱状图
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("月度收入", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(12.dp))
                    BarChart(data = data.incomeChart)
                }
            }
        }

        // 统计摘要
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatSummaryCard("工程数", "${data.projectCount}", Modifier.weight(1f))
                StatSummaryCard("完工数", "${data.completedCount}", Modifier.weight(1f))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatSummaryCard("总收入", AmountFormatter.formatPlain(data.totalIncome), Modifier.weight(1f))
                StatSummaryCard("已预支", AmountFormatter.formatPlain(data.advanceTotal), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatSummaryCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = AppColors.SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, color = AppColors.TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Green500)
        }
    }
}

data class MonthlyStatsData(
    val projectCount: Int,
    val completedCount: Int,
    val totalIncome: String,
    val advanceTotal: String,
    val incomeChart: List<BarChartData>
)
