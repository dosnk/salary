package com.salary.manager.feature.statistics.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.salary.core.design.component.GreenTopNavBar
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.AdvanceDataDto
import com.salary.core.network.api.ConstructionPlanDto
import com.salary.core.network.api.PlanTotalDto
import com.salary.core.network.api.SalaryProjectDto
import com.salary.core.network.api.SettlementHistoryDto
import com.salary.core.network.api.SubprojectDto
import com.salary.core.network.dto.ProjectDto
import com.salary.core.ui.state.ListUiState
import com.salary.core.ui.state.UiState

/**
 * 统计面板页面 - 对齐Vue前端Statistics.vue设计
 *
 * 包含：顶部导航栏、4宫格统计卡片、结算单表格区域、结算历史区域
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsDashboardScreen(
    viewModel: StatisticsDashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val settlementSummary by viewModel.settlementSummary.collectAsState()
    val constructionPlans by viewModel.constructionPlans.collectAsState()
    val projectData by viewModel.projectData.collectAsState()
    val settlementHistory by viewModel.settlementHistory.collectAsState()
    val selectedProjectIds by viewModel.selectedProjectIds.collectAsState()
    val calculationResult by viewModel.calculationResult.collectAsState()
    val settling by viewModel.settling.collectAsState()
    val expandedProjects by viewModel.expandedProjects.collectAsState()
    val expandedHistoryProjects by viewModel.expandedHistoryProjects.collectAsState()
    val userNickname by viewModel.userNickname.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val statsProjectListState by viewModel.statsProjectListState.collectAsState()
    val statsPopupTitle by viewModel.statsPopupTitle.collectAsState()
    val exportingId by viewModel.exportingId.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSettleConfirm by remember { mutableStateOf(false) }
    var showStatsProjectList by remember { mutableStateOf(false) }
    var statsFilterType by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 处理错误消息
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    // 处理成功消息
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    // 绿色渐变色（子组件复用）
    val greenGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF84CC16), Color(0xFF65A30D))
    )

    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Green400)
                }
            }
            is UiState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 顶部导航栏
                    item {
                        GreenTopNavBar(
                            title = "统计结算",
                            userNickname = userNickname.ifEmpty { "未登录" },
                            unreadCount = 0
                        )
                    }

                    // 4宫格统计卡片
                    item {
                        StatsGridSection(
                            summary = settlementSummary,
                            formatNumber = { viewModel.formatNumber(it) },
                            onCardClick = { type ->
                                statsFilterType = when (type) {
                                    "未结算工程" -> "unsettled"
                                    "预支总额" -> "advance"
                                    "工程总额" -> "all"
                                    "实付总额" -> "settled"
                                    else -> "unsettled"
                                }
                                if (statsFilterType == "advance") {
                                    // 预支总额点击：显示提示
                                    scope.launch { snackbarHostState.showSnackbar("预支总额为统计汇总数据，请到预支管理页面查看详情") }
                                } else {
                                    viewModel.loadStatsProjectList(statsFilterType)
                                    showStatsProjectList = true
                                }
                            }
                        )
                    }

                    // 结算单区域
                    item {
                        SettlementSheetSection(
                            constructionPlans = constructionPlans,
                            projectData = projectData,
                            selectedProjectIds = selectedProjectIds,
                            expandedProjects = expandedProjects,
                            calculationResult = calculationResult,
                            settling = settling,
                            greenGradient = greenGradient,
                            onToggleSelectAll = { viewModel.toggleSelectAll() },
                            onToggleProjectSelection = { id, selected ->
                                viewModel.toggleProjectSelection(id, selected)
                            },
                            onToggleProjectExpand = { viewModel.toggleProjectExpand(it) },
                            onSettle = { showSettleConfirm = true },
                            getUnitName = { viewModel.getUnitName(it) },
                            formatNumber = { viewModel.formatNumber(it) }
                        )
                    }

                    // 结算历史区域
                    item {
                        SettlementHistorySection(
                            settlementHistory = settlementHistory,
                            constructionPlans = constructionPlans,
                            expandedHistoryProjects = expandedHistoryProjects,
                            greenGradient = greenGradient,
                            exportingId = exportingId,
                            onToggleHistoryProjectExpand = { settlementId, projectId ->
                                viewModel.toggleHistoryProjectExpand(settlementId, projectId)
                            },
                            onExportExcel = { settlementId, settlementNo ->
                                viewModel.exportSettlementExcel(settlementId, settlementNo)
                            },
                            getUnitName = { viewModel.getUnitName(it) },
                            formatNumber = { viewModel.formatNumber(it) }
                        )
                    }

                    // 底部间距
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
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
                            onClick = { viewModel.loadAllData() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400),
                            shape = RoundedCornerShape(8.dp)
                        ) { Text("重新加载", color = Color.White) }
                    }
                }
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // 结算确认对话框
    if (showSettleConfirm) {
        AlertDialog(
            onDismissRequest = { showSettleConfirm = false },
            title = { Text("确认结算") },
            text = {
                Column {
                    Text("已选择 ${selectedProjectIds.size} 个工程")
                    Text("确认后将生成结算单，结算后不可修改", color = AppColors.TextSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSettleConfirm = false
                        viewModel.handleSettle()
                    }
                ) {
                    Text("确认结算", color = AppColors.Green400)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettleConfirm = false }) {
                    Text("取消", color = AppColors.TextSecondary)
                }
            }
        )
    }

    // 统计卡片点击弹窗 - 展示对应条件的工程列表
    if (showStatsProjectList) {
        val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showStatsProjectList = false },
            sheetState = bottomSheetState,
            containerColor = AppColors.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 500.dp)
            ) {
                // 标题栏
                Text(
                    text = statsPopupTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
                // 分割线
                androidx.compose.material3.HorizontalDivider(color = AppColors.Green100, thickness = 1.dp)

                // 工程列表
                when (statsProjectListState) {
                    is ListUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AppColors.Green400)
                        }
                    }
                    is ListUiState.Success -> {
                        val projects = (statsProjectListState as ListUiState.Success<ProjectDto>).items
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(projects, key = { it.id }) { project ->
                                StatsProjectCard(project = project)
                            }
                            // 底部间距
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                        }
                    }
                    is ListUiState.Error -> {
                        val errorMsg = (statsProjectListState as ListUiState.Error).message
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
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
    }
}

// ========== 4宫格统计卡片 ==========

/**
 * 统计卡片数据
 */
data class StatCardData(
    val title: String,
    val count: Int,
    val amount: Double,
    val iconColor: Color
)

/**
 * 4宫格统计卡片区域
 * 数据来源：/v1/salary-sheet/projects 返回的汇总数据
 */
@Composable
fun StatsGridSection(
    summary: SettlementSummary,
    formatNumber: (Double?) -> String,
    onCardClick: (String) -> Unit = {}
) {
    val cards = listOf(
        StatCardData(
            title = "未结算工程",
            count = summary.totalProjects,
            amount = summary.grandTotal,
            iconColor = Color(0xFFE6A23C) // 橙色
        ),
        StatCardData(
            title = "预支总额",
            count = 0,
            amount = summary.totalAdvance,
            iconColor = Color(0xFF409EFF) // 蓝色
        ),
        StatCardData(
            title = "工程总额",
            count = 0,
            amount = summary.grandTotal,
            iconColor = Color(0xFF84CC16) // 绿色
        ),
        StatCardData(
            title = "实付总额",
            count = 0,
            amount = summary.finalTotal,
            iconColor = Color(0xFF9333EA) // 紫色
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cards.take(2).forEach { card ->
                StatCardItem(
                    card = card,
                    formatNumber = formatNumber,
                    modifier = Modifier.weight(1f),
                    onClick = { onCardClick(card.title) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            cards.drop(2).forEach { card ->
                StatCardItem(
                    card = card,
                    formatNumber = formatNumber,
                    modifier = Modifier.weight(1f),
                    onClick = { onCardClick(card.title) }
                )
            }
        }
    }
}

/**
 * 单个统计卡片 - 白色背景+1dp阴影+8dp圆角+绿色边框
 */
@Composable
fun StatCardItem(
    card: StatCardData,
    formatNumber: (Double?) -> String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE6F4D0), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // 上部：图标 + 标题
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标圆角方块
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(card.iconColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (card.title) {
                            "未结算工程" -> "⏱"
                            "预支总额" -> "💰"
                            "工程总额" -> "✓"
                            else -> "📅"
                        },
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${card.count} 份${card.title}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333842),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 下部：总额
            Row(
                modifier = Modifier.padding(start = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "总额：",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "¥${formatNumber(card.amount)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333842)
                )
            }
        }
    }
}

// ========== 结算单区域 ==========

/**
 * 结算单区域 - 绿色渐变标题栏 + 表格
 */
@Composable
fun SettlementSheetSection(
    constructionPlans: List<ConstructionPlanDto>,
    projectData: List<SalaryProjectDto>,
    selectedProjectIds: List<Int>,
    expandedProjects: Set<Int>,
    calculationResult: com.salary.core.network.api.CalculateResultDto,
    settling: Boolean,
    greenGradient: Brush,
    onToggleSelectAll: () -> Unit,
    onToggleProjectSelection: (Int, Boolean) -> Unit,
    onToggleProjectExpand: (Int) -> Unit,
    onSettle: () -> Unit,
    getUnitName: (String?) -> String,
    formatNumber: (Double?) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // 绿色渐变标题栏
        SettlementTitleBar(
            title = "📋 结算单",
            greenGradient = greenGradient,
            rightContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "已选择 ${selectedProjectIds.size} 个工程",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                    Button(
                        onClick = onSettle,
                        enabled = selectedProjectIds.isNotEmpty() && !settling,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF65A30D),
                            disabledContainerColor = Color(0xCCFFFFFF),
                            disabledContentColor = Color(0xFF999999)
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (settling) "结算中..." else
                                if (selectedProjectIds.isNotEmpty()) "结算(${selectedProjectIds.size}个)" else "结算",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        )

        // 表格内容
        if (projectData.isEmpty()) {
            // 空数据提示
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无统计工程，请先在工程管理中确认工程完工",
                    fontSize = 13.sp,
                    color = AppColors.TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        } else if (constructionPlans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无施工方案数据",
                    fontSize = 13.sp,
                    color = AppColors.TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            SettlementTable(
                constructionPlans = constructionPlans,
                projectData = projectData,
                selectedProjectIds = selectedProjectIds,
                expandedProjects = expandedProjects,
                calculationResult = calculationResult,
                onToggleSelectAll = onToggleSelectAll,
                onToggleProjectSelection = onToggleProjectSelection,
                onToggleProjectExpand = onToggleProjectExpand,
                getUnitName = getUnitName,
                formatNumber = formatNumber
            )
        }
    }
}

/**
 * 结算单/历史标题栏 - 绿色渐变背景
 */
@Composable
fun SettlementTitleBar(
    title: String,
    greenGradient: Brush,
    rightContent: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(greenGradient, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            rightContent()
        }
    }
}

/**
 * 结算单表格 - 用LazyColumn实现（Android不适合HTML table）
 */
@Composable
fun SettlementTable(
    constructionPlans: List<ConstructionPlanDto>,
    projectData: List<SalaryProjectDto>,
    selectedProjectIds: List<Int>,
    expandedProjects: Set<Int>,
    calculationResult: com.salary.core.network.api.CalculateResultDto,
    onToggleSelectAll: () -> Unit,
    onToggleProjectSelection: (Int, Boolean) -> Unit,
    onToggleProjectExpand: (Int) -> Unit,
    getUnitName: (String?) -> String,
    formatNumber: (Double?) -> String
) {
    val scrollState = rememberScrollState()
    val allSelected = selectedProjectIds.size == projectData.size && projectData.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
    ) {
        // 横向可滚动的表格
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            // 表头行
            TableHeaderRow(
                constructionPlans = constructionPlans,
                allSelected = allSelected,
                onToggleSelectAll = onToggleSelectAll
            )

            // 工程数据行
            projectData.forEachIndexed { index, project ->
                val isSelected = selectedProjectIds.contains(project.id)
                val isExpanded = expandedProjects.contains(project.id)

                // 工程主行
                ProjectDataRow(
                    index = index,
                    project = project,
                    constructionPlans = constructionPlans,
                    isSelected = isSelected,
                    isExpanded = isExpanded,
                    onToggleSelection = { onToggleProjectSelection(project.id, it) },
                    onToggleExpand = { onToggleProjectExpand(project.id) },
                    getUnitName = getUnitName,
                    formatNumber = formatNumber
                )

                // 展开的子项目明细行
                AnimatedVisibility(visible = isExpanded) {
                    project.subprojects.forEach { sub ->
                        SubprojectRow(
                            subproject = sub,
                            constructionPlans = constructionPlans,
                            getUnitName = getUnitName
                        )
                    }
                }
            }

            // 单价行（灰色背景）
            PriceRow(
                constructionPlans = constructionPlans,
                getUnitName = getUnitName
            )

            // 合计行（蓝色渐变背景）
            TotalRow(
                constructionPlans = constructionPlans,
                planTotals = calculationResult.planTotals,
                getUnitName = getUnitName,
                formatNumber = formatNumber
            )

            // 总计行（绿色渐变背景）
            GrandTotalRow(
                constructionPlans = constructionPlans,
                planTotals = calculationResult.planTotals,
                grandTotal = calculationResult.grandTotal,
                formatNumber = formatNumber
            )

            // 预支行（黄色渐变背景）
            calculationResult.advances.forEach { advance ->
                AdvanceRow(
                    advance = advance,
                    planCount = constructionPlans.size,
                    formatNumber = formatNumber
                )
            }

            // 总额行（粉色渐变背景）
            FinalTotalRow(
                finalTotal = calculationResult.finalTotal,
                planCount = constructionPlans.size,
                formatNumber = formatNumber
            )
        }
    }
}

/**
 * 表头行
 */
@Composable
fun TableHeaderRow(
    constructionPlans: List<ConstructionPlanDto>,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit
) {
    val headerGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFF9FAFB), Color(0xFFF3F4F6))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerGradient)
            .border(width = 0.5.dp, color = Color(0xFF9CA3AF))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 选择列
        Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            Checkbox(
                checked = allSelected,
                onCheckedChange = { onToggleSelectAll() },
                colors = CheckboxDefaults.colors(checkedColor = AppColors.Green400),
                modifier = Modifier.size(24.dp)
            )
        }
        // 序号
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Text("序号", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
        }
        // 工程名称
        Box(modifier = Modifier.width(100.dp), contentAlignment = Alignment.Center) {
            Text("工程名称", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
        }
        // 各施工方案列
        constructionPlans.forEach { plan ->
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                Text(
                    plan.name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF374151),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        // 总额
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text("总额", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
        }
    }
}

/**
 * 工程数据行
 */
@Composable
fun ProjectDataRow(
    index: Int,
    project: SalaryProjectDto,
    constructionPlans: List<ConstructionPlanDto>,
    isSelected: Boolean,
    isExpanded: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    onToggleExpand: () -> Unit,
    getUnitName: (String?) -> String,
    formatNumber: (Double?) -> String
) {
    val bgColor = if (!isSelected) Color(0xFFF5F5F5) else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .border(width = 0.5.dp, color = Color(0xFF9CA3AF))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 选择列
        Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggleSelection,
                colors = CheckboxDefaults.colors(checkedColor = AppColors.Green400),
                modifier = Modifier.size(24.dp)
            )
        }
        // 序号
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Text("${index + 1}", fontSize = 11.sp, color = Color(0xFF333333))
        }
        // 工程名称（可展开）
        Box(
            modifier = Modifier
                .width(100.dp)
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    project.project_name,
                    fontSize = 11.sp,
                    color = Color(0xFF333333),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 各施工方案列
        constructionPlans.forEach { plan ->
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                val planQty = project.planQuantities[plan.id.toString()]
                if (planQty != null && planQty.totalQuantity > 0) {
                    Text(
                        "${formatNumber(planQty.totalQuantity)}${getUnitName(plan.unit)}",
                        fontSize = 10.sp,
                        color = Color(0xFF333333),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", fontSize = 10.sp, color = Color(0xFF999999))
                }
            }
        }
        // 总额
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text("-", fontSize = 10.sp, color = Color(0xFF999999))
        }
    }
}

/**
 * 子项目明细行
 */
@Composable
fun SubprojectRow(
    subproject: SubprojectDto,
    constructionPlans: List<ConstructionPlanDto>,
    getUnitName: (String?) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC))
            .border(width = 0.5.dp, color = Color(0xFF9CA3AF))
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 空选择列
        Box(modifier = Modifier.width(48.dp))
        // 空序号
        Box(modifier = Modifier.width(36.dp))
        // 子项目名称
        Box(modifier = Modifier.width(100.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                "${subproject.space_type_name} - ${subproject.plan_name}",
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(start = 20.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 各施工方案列
        constructionPlans.forEach { plan ->
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                if (subproject.plan_id == plan.id) {
                    Text(
                        "${String.format("%.2f", subproject.user_quantity)}${getUnitName(plan.unit)}",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", fontSize = 10.sp, color = Color(0xFFCCCCCC))
                }
            }
        }
        // 总额
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text("-", fontSize = 10.sp, color = Color(0xFFCCCCCC))
        }
    }
}

/**
 * 单价行 - 灰色背景
 */
@Composable
fun PriceRow(
    constructionPlans: List<ConstructionPlanDto>,
    getUnitName: (String?) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .border(width = 0.5.dp, color = Color(0xFF9CA3AF))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 合并前3列
        Box(modifier = Modifier.width(184.dp), contentAlignment = Alignment.CenterStart) {
            Text("单价", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF666666))
        }
        // 各方案单价
        constructionPlans.forEach { plan ->
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                Text(
                    "¥${plan.price ?: 0}/${getUnitName(plan.unit)}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center
                )
            }
        }
        // 总额
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text("-", fontSize = 10.sp, color = Color(0xFF999999))
        }
    }
}

/**
 * 合计行 - 蓝色渐变背景
 */
@Composable
fun TotalRow(
    constructionPlans: List<ConstructionPlanDto>,
    planTotals: Map<String, PlanTotalDto>,
    getUnitName: (String?) -> String,
    formatNumber: (Double?) -> String
) {
    val blueGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFDBEAFE), Color(0xFFBFDBFE))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(blueGradient)
            .border(width = 0.5.dp, color = Color(0xFF9CA3AF))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 合并前3列
        Box(modifier = Modifier.width(184.dp), contentAlignment = Alignment.CenterStart) {
            Text("合计", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
        }
        // 各方案合计
        constructionPlans.forEach { plan ->
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                val total = planTotals[plan.id.toString()]
                if (total != null && total.totalQuantity > 0) {
                    Text(
                        "${formatNumber(total.totalQuantity)}${getUnitName(plan.unit)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E40AF),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", fontSize = 10.sp, color = Color(0xFF1E40AF))
                }
            }
        }
        // 总额
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text("-", fontSize = 10.sp, color = Color(0xFF1E40AF))
        }
    }
}

/**
 * 总计行 - 绿色渐变背景
 */
@Composable
fun GrandTotalRow(
    constructionPlans: List<ConstructionPlanDto>,
    planTotals: Map<String, PlanTotalDto>,
    grandTotal: Double,
    formatNumber: (Double?) -> String
) {
    val greenGradientRow = Brush.horizontalGradient(
        colors = listOf(Color(0xFFD4EDDA), Color(0xFFC3E6CB))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(greenGradientRow)
            .border(width = 0.5.dp, color = Color(0xFF9CA3AF))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 合并前3列
        Box(modifier = Modifier.width(184.dp), contentAlignment = Alignment.CenterStart) {
            Text("总计", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
        }
        // 各方案总计金额
        constructionPlans.forEach { plan ->
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                val total = planTotals[plan.id.toString()]
                if (total != null && total.totalAmount > 0) {
                    Text(
                        "¥${formatNumber(total.totalAmount)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E40AF),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", fontSize = 10.sp, color = Color(0xFF1E40AF))
                }
            }
        }
        // 总额
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text(
                "¥${formatNumber(grandTotal)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E40AF)
            )
        }
    }
}

/**
 * 预支行 - 黄色渐变背景
 */
@Composable
fun AdvanceRow(
    advance: AdvanceDataDto,
    planCount: Int,
    formatNumber: (Double?) -> String
) {
    val yellowGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(yellowGradient)
            .border(width = 0.5.dp, color = Color(0xFF9CA3AF))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 日期+预支标签
        Box(modifier = Modifier.width(184.dp), contentAlignment = Alignment.CenterStart) {
            Text(
                "${advance.advance_date} 预支",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF92400E)
            )
        }
        // 各方案列（显示-）
        repeat(planCount) {
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                Text("-", fontSize = 10.sp, color = Color(0xFF92400E))
            }
        }
        // 预支金额
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text(
                "¥${formatNumber(advance.advance_amount)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF92400E)
            )
        }
    }
}

/**
 * 总额行 - 粉色渐变背景
 */
@Composable
fun FinalTotalRow(
    finalTotal: Double,
    planCount: Int,
    formatNumber: (Double?) -> String
) {
    val pinkGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFFCE4EC), Color(0xFFFADBD8))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(pinkGradient)
            .border(width = 0.5.dp, color = Color(0xFF9CA3AF))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 总额标签
        Box(modifier = Modifier.width(184.dp), contentAlignment = Alignment.CenterStart) {
            Text("总额", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
        }
        // 各方案列（显示-）
        repeat(planCount) {
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                Text("-", fontSize = 10.sp, color = Color(0xFF1E40AF))
            }
        }
        // 最终总额
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text(
                "¥${formatNumber(finalTotal)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E40AF)
            )
        }
    }
}

// ========== 结算历史区域 ==========

/**
 * 结算历史区域
 */
@Composable
fun SettlementHistorySection(
    settlementHistory: List<SettlementHistoryDto>,
    constructionPlans: List<ConstructionPlanDto>,
    expandedHistoryProjects: Set<String>,
    greenGradient: Brush,
    exportingId: Int? = null,
    onToggleHistoryProjectExpand: (Int, Int) -> Unit,
    onExportExcel: (Int, String) -> Unit = { _, _ -> },
    getUnitName: (String?) -> String,
    formatNumber: (Double?) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
        if (settlementHistory.isEmpty()) {
            // 绿色标题栏 + 空提示
            SettlementTitleBar(
                title = "📜 结算历史",
                greenGradient = greenGradient
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无已结算工程",
                    fontSize = 13.sp,
                    color = AppColors.TextTertiary,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            settlementHistory.forEach { settlement ->
                Spacer(modifier = Modifier.height(8.dp))

                // 绿色标题栏（含结算单号和日期）
                SettlementTitleBar(
                    title = "📜 结算历史",
                    greenGradient = greenGradient,
                    rightContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                settlement.settlement_no,
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${settlement.start_month} 至 ${settlement.end_month}",
                                fontSize = 10.sp,
                                color = Color(0xCCFFFFFF)
                            )
                            // 导出Excel按钮
                            val isExporting = exportingId == settlement.settlement_id
                            Button(
                                onClick = { onExportExcel(settlement.settlement_id, settlement.settlement_no) },
                                enabled = !isExporting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF65A30D),
                                    disabledContainerColor = Color(0xCCFFFFFF),
                                    disabledContentColor = Color(0xFF999999)
                                ),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isExporting) "导出中" else "导出Excel",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                )

                // 历史表格
                SettlementHistoryTable(
                    settlement = settlement,
                    constructionPlans = constructionPlans,
                    expandedHistoryProjects = expandedHistoryProjects,
                    onToggleHistoryProjectExpand = onToggleHistoryProjectExpand,
                    getUnitName = getUnitName,
                    formatNumber = formatNumber
                )
            }
        }
    }
}

/**
 * 结算历史表格
 */
@Composable
fun SettlementHistoryTable(
    settlement: SettlementHistoryDto,
    constructionPlans: List<ConstructionPlanDto>,
    expandedHistoryProjects: Set<String>,
    onToggleHistoryProjectExpand: (Int, Int) -> Unit,
    getUnitName: (String?) -> String,
    formatNumber: (Double?) -> String
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            // 历史表头（无选择列）
            HistoryHeaderRow(constructionPlans = constructionPlans)

            // 去重后的工程列表
            val uniqueProjects = settlement.projects.distinctBy { it.id }
            uniqueProjects.forEachIndexed { index, project ->
                val isExpanded = expandedHistoryProjects.contains("${settlement.settlement_id}-${project.id}")

                // 工程行
                HistoryProjectDataRow(
                    index = index,
                    project = project,
                    constructionPlans = constructionPlans,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        onToggleHistoryProjectExpand(settlement.settlement_id, project.id)
                    },
                    getUnitName = getUnitName,
                    formatNumber = formatNumber
                )

                // 展开的子项目明细
                AnimatedVisibility(visible = isExpanded) {
                    project.subprojects.forEach { sub ->
                        SubprojectRow(
                            subproject = sub,
                            constructionPlans = constructionPlans,
                            getUnitName = getUnitName
                        )
                    }
                }
            }

            // 单价行
            PriceRow(
                constructionPlans = constructionPlans,
                getUnitName = getUnitName
            )

            // 合计行
            TotalRow(
                constructionPlans = constructionPlans,
                planTotals = settlement.planTotals,
                getUnitName = getUnitName,
                formatNumber = formatNumber
            )

            // 总计行
            GrandTotalRow(
                constructionPlans = constructionPlans,
                planTotals = settlement.planTotals,
                grandTotal = settlement.grandTotal,
                formatNumber = formatNumber
            )

            // 预支行
            settlement.advances.forEach { advance ->
                AdvanceRow(
                    advance = advance,
                    planCount = constructionPlans.size,
                    formatNumber = formatNumber
                )
            }

            // 总额行
            FinalTotalRow(
                finalTotal = settlement.finalTotal,
                planCount = constructionPlans.size,
                formatNumber = formatNumber
            )
        }
    }
}

/**
 * 历史表头行（无选择列）
 */
@Composable
fun HistoryHeaderRow(
    constructionPlans: List<ConstructionPlanDto>
) {
    val headerGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFF9FAFB), Color(0xFFF3F4F6))
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerGradient)
            .border(width = 0.5.dp, color = Color(0xFF9CA3AF))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 序号
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Text("序号", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
        }
        // 工程名称
        Box(modifier = Modifier.width(120.dp), contentAlignment = Alignment.Center) {
            Text("工程名称", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
        }
        // 各施工方案列
        constructionPlans.forEach { plan ->
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                Text(
                    plan.name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF374151),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        // 总额
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text("总额", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
        }
    }
}

/**
 * 历史工程数据行（无选择列）
 */
@Composable
fun HistoryProjectDataRow(
    index: Int,
    project: SalaryProjectDto,
    constructionPlans: List<ConstructionPlanDto>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    getUnitName: (String?) -> String,
    formatNumber: (Double?) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .border(width = 0.5.dp, color = Color(0xFF9CA3AF))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 序号
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Text("${index + 1}", fontSize = 11.sp, color = Color(0xFF333333))
        }
        // 工程名称（可展开）
        Box(
            modifier = Modifier
                .width(120.dp)
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    project.project_name,
                    fontSize = 11.sp,
                    color = Color(0xFF333333),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 各施工方案列
        constructionPlans.forEach { plan ->
            Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                val planQty = project.planQuantities[plan.id.toString()]
                if (planQty != null && planQty.totalQuantity > 0) {
                    Text(
                        "${formatNumber(planQty.totalQuantity)}${getUnitName(plan.unit)}",
                        fontSize = 10.sp,
                        color = Color(0xFF333333),
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", fontSize = 10.sp, color = Color(0xFF999999))
                }
            }
        }
        // 总额
        Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.Center) {
            Text("-", fontSize = 10.sp, color = Color(0xFF999999))
        }
    }
}

// ========== 统计卡片弹窗工程列表 ==========

/**
 * 统计卡片弹窗中的工程卡片 - 简洁展示
 */
@Composable
private fun StatsProjectCard(project: ProjectDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // 工程名称 + 状态标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // 状态标签
                Text(
                    text = when (project.status) {
                        "preparing" -> "备料中"
                        "constructing" -> "施工中"
                        "completed" -> "已完工"
                        "canceled" -> "已取消"
                        else -> project.status
                    },
                    fontSize = 11.sp,
                    color = when (project.status) {
                        "constructing", "completed" -> AppColors.Green400
                        "preparing" -> Color(0xFFE6A23C)
                        "canceled" -> Color(0xFF999999)
                        else -> AppColors.TextSecondary
                    },
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            // 金额 + 施工人员
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "工费总额：¥${String.format("%.2f", project.totalAmount)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Green400
                )
                if (project.workers.isNotEmpty()) {
                    Text(
                        text = project.workers.joinToString("、") { it.nickname },
                        fontSize = 11.sp,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
