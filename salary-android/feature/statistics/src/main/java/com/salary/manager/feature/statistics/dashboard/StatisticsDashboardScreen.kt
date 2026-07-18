package com.salary.manager.feature.statistics.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.salary.core.network.dto.ProjectDto
import com.salary.core.ui.state.ListUiState
import com.salary.core.ui.state.UiState
import com.salary.manager.feature.statistics.advance.AdvanceContent
import com.salary.manager.feature.statistics.advance.AdvanceViewModel
import com.salary.manager.feature.statistics.advance.CreateAdvanceDialog

/**
 * 统计面板页面 - 对齐Vue前端Statistics.vue设计
 *
 * 包含：顶部导航栏、Tab切换（统计/预支）、HorizontalPager左右滑动切换内容
 * - 统计Tab：4宫格统计卡片、结算单表格区域、结算历史区域
 * - 预支Tab：预支记录列表+创建预支
 *
 * @param onMessageClick 顶部导航栏消息图标点击回调
 * @param unreadCount 未读消息数（由AppNavHost全局传入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsDashboardScreen(
    viewModel: StatisticsDashboardViewModel = hiltViewModel(),
    advanceViewModel: AdvanceViewModel = hiltViewModel(),
    userNickname: String = "",
    /** 刷新触发器：值变化时静默刷新统计数据（用于Tab切换时刷新） */
    refreshTrigger: Int = 0,
    onMessageClick: (() -> Unit)? = null,
    unreadCount: Int = 0
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settlementSummary by viewModel.settlementSummary.collectAsStateWithLifecycle()
    val constructionPlans by viewModel.constructionPlans.collectAsStateWithLifecycle()
    val projectData by viewModel.projectData.collectAsStateWithLifecycle()
    val settlementHistory by viewModel.settlementHistory.collectAsStateWithLifecycle()
    val selectedProjectIds by viewModel.selectedProjectIds.collectAsStateWithLifecycle()
    val calculationResult by viewModel.calculationResult.collectAsStateWithLifecycle()
    val settling by viewModel.settling.collectAsStateWithLifecycle()
    val expandedProjects by viewModel.expandedProjects.collectAsStateWithLifecycle()
    val expandedHistoryProjects by viewModel.expandedHistoryProjects.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()
    val statsProjectListState by viewModel.statsProjectListState.collectAsStateWithLifecycle()
    val statsPopupTitle by viewModel.statsPopupTitle.collectAsStateWithLifecycle()
    val exportingId by viewModel.exportingId.collectAsStateWithLifecycle()

    // 当前用户角色（资料员/管理员隐藏结算按钮和Checkbox，仅constructor可结算）
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()
    // 是否可结算（仅施工员可操作结算）
    val canSettle = userRole == "constructor"

    // 预支ViewModel状态
    val advanceState by advanceViewModel.state.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var showSettleConfirm by remember { mutableStateOf(false) }
    var showStatsProjectList by remember { mutableStateOf(false) }
    var statsFilterType by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Tab切换时静默刷新统计数据（首次进入refreshTrigger=0不触发，后续切换递增触发）
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            viewModel.silentRefresh()
        }
    }

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

    // 提取稳定的 Lambda（只依赖 viewModel），用 remember 包裹避免每次重组新建
    // 原实现：Lambda 在 item {} 内部声明，每次重组都新建，破坏 Compose skippable 机制
    // 新实现：Lambda 用 remember 包裹，所有 item 共享同一实例，减少重组开销
    val formatNumber: (Double?) -> String = remember(viewModel) { { viewModel.formatNumber(it) } }
    val getUnitName: (String?) -> String = remember(viewModel) { { viewModel.getUnitName(it) } }
    val onToggleSelectAll: () -> Unit = remember(viewModel) { { viewModel.toggleSelectAll() } }
    val onToggleProjectSelection: (Int, Boolean) -> Unit = remember(viewModel) { { id, selected -> viewModel.toggleProjectSelection(id, selected) } }
    val onToggleProjectExpand: (Int) -> Unit = remember(viewModel) { { viewModel.toggleProjectExpand(it) } }
    val onExportCurrentImage: () -> Unit = remember(viewModel) { { viewModel.exportCurrentSettlementImage() } }
    val onToggleHistoryProjectExpand: (Int, Int) -> Unit = remember(viewModel) { { settlementId, projectId -> viewModel.toggleHistoryProjectExpand(settlementId, projectId) } }
    val onExportSettlementExcel: (Int, String) -> Unit = remember(viewModel) { { settlementId, settlementNo -> viewModel.exportSettlementExcel(settlementId, settlementNo) } }
    val onExportSettlementImage: (SettlementHistoryDto) -> Unit = remember(viewModel) { { settlement -> viewModel.exportSettlementImage(settlement) } }

    // 顶部Tab：统计 / 预支，默认统计页面
    val statisticsTabs = listOf("统计", "预支")
    val pagerState = rememberPagerState(initialPage = 0) { statisticsTabs.size }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Green400)
                }
            }
            is UiState.Success -> {
                // 顶部导航栏固定不动，不随列表滚动
                Column(modifier = Modifier.fillMaxSize()) {
                    GreenTopNavBar(
                        title = "统计结算",
                        userNickname = userNickname.ifEmpty { "未登录" },
                        unreadCount = unreadCount,
                        onMessageClick = onMessageClick
                    )

                    // 顶部Tab菜单：统计 / 预支
                    TabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.White,
                        contentColor = AppColors.Green400,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier
                                    .tabIndicatorOffset(tabPositions[pagerState.currentPage])
                                    .padding(horizontal = 24.dp),
                                height = 3.dp,
                                color = AppColors.Green400
                            )
                        }
                    ) {
                        statisticsTabs.forEachIndexed { index, title ->
                            Tab(
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                                modifier = Modifier.height(40.dp),
                                text = {
                                    Text(
                                        title,
                                        fontSize = 14.sp,
                                        fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                                        color = if (pagerState.currentPage == index) AppColors.Green400 else AppColors.TextSecondary
                                    )
                                }
                            )
                        }
                    }

                    // HorizontalPager：左右滑动切换统计/预支内容
                    // beyondViewportPageCount = 0：不预加载相邻页面，减少不必要的渲染开销
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0
                    ) { page ->
                        when (page) {
                            0 -> {
                                // 统计内容区域
                                // 表格各列采用 weight 自适应填满容器宽度，无需横向滚动

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    // 4宫格统计卡片
                                    item(key = "stats_grid") {
                                        StatsGridSection(
                                            summary = settlementSummary,
                                            formatNumber = formatNumber,
                                            onCardClick = { type ->
                                                statsFilterType = when (type) {
                                                    "待结算工程" -> "settling"
                                                    "预支金额" -> "advance"
                                                    "今年工程量" -> "settled"
                                                    "月均收入" -> "settled"
                                                    else -> "settling"
                                                }
                                                if (statsFilterType == "advance") {
                                                    // 预支金额点击：切换到预支Tab
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(1)
                                                    }
                                                } else {
                                                    viewModel.loadStatsProjectList(statsFilterType)
                                                    showStatsProjectList = true
                                                }
                                            }
                                        )
                                    }

                                    // 结算单标题栏 + 已选择工程数提示（不需要横向滚动）
                                    item(key = "settlement_header") {
                                        SettlementSheetHeader(
                                            greenGradient = greenGradient,
                                            selectedProjectIds = selectedProjectIds,
                                            settling = settling,
                                            canSettle = canSettle,
                                            onSettle = { showSettleConfirm = true },
                                            onExportImage = onExportCurrentImage
                                        )
                                    }

                                    // 结算单表格内容 - 拆分为多个 item 支持懒加载
                                    // 原实现：单个 item 内 forEach 渲染所有工程行，工程多时导致卡顿
                                    // 新实现：表头/每个工程行/尾部各自独立 item，按需渲染
                                    if (projectData.isEmpty()) {
                                        item(key = "settlement_empty_projects") {
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
                                        }
                                    } else if (constructionPlans.isEmpty()) {
                                        item(key = "settlement_empty_plans") {
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
                                        }
                                    } else {
                                        val allSelected = selectedProjectIds.size == projectData.size && projectData.isNotEmpty()

                                        // 表头行
                                        item(key = "settlement_table_header") {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White)
                                            ) {
                                                TableHeaderRow(
                                                    constructionPlans = constructionPlans,
                                                    allSelected = allSelected,
                                                    canSettle = canSettle,
                                                    onToggleSelectAll = onToggleSelectAll
                                                )
                                            }
                                        }

                                        // 工程数据行 - 每个工程作为独立 item，支持懒加载
                                        itemsIndexed(
                                            items = projectData,
                                            key = { _, project -> "settlement_project_${project.id}" }
                                        ) { index, project ->
                                            val isSelected = selectedProjectIds.contains(project.id)
                                            val isExpanded = expandedProjects.contains(project.id)
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White)
                                            ) {
                                                ProjectDataRow(
                                                    index = index,
                                                    project = project,
                                                    constructionPlans = constructionPlans,
                                                    isSelected = isSelected,
                                                    isExpanded = isExpanded,
                                                    canSettle = canSettle,
                                                    onToggleSelection = { onToggleProjectSelection(project.id, it) },
                                                    onToggleExpand = { onToggleProjectExpand(project.id) },
                                                    getUnitName = getUnitName,
                                                    formatNumber = formatNumber
                                                )
                                                // 展开的子项目明细行
                                                AnimatedVisibility(visible = isExpanded) {
                                                    Column {
                                                        project.subprojects.forEach { sub ->
                                                            SubprojectRow(
                                                                subproject = sub,
                                                                constructionPlans = constructionPlans,
                                                                canSettle = canSettle,
                                                                getUnitName = getUnitName
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 尾部行（合计/单价/总计/预支/总额）
                                        item(key = "settlement_table_footer") {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                            ) {
                                                TotalRow(
                                                    constructionPlans = constructionPlans,
                                                    planTotals = calculationResult.planTotals,
                                                    canSettle = canSettle,
                                                    getUnitName = getUnitName,
                                                    formatNumber = formatNumber
                                                )
                                                PriceRow(
                                                    constructionPlans = constructionPlans,
                                                    canSettle = canSettle,
                                                    getUnitName = getUnitName
                                                )
                                                GrandTotalRow(
                                                    constructionPlans = constructionPlans,
                                                    planTotals = calculationResult.planTotals,
                                                    grandTotal = calculationResult.grandTotal,
                                                    canSettle = canSettle,
                                                    formatNumber = formatNumber
                                                )
                                                calculationResult.advances.forEach { advance ->
                                                    AdvanceRow(
                                                        advance = advance,
                                                        planCount = constructionPlans.size,
                                                        canSettle = canSettle,
                                                        formatNumber = formatNumber
                                                    )
                                                }
                                                FinalTotalRow(
                                                    finalTotal = calculationResult.finalTotal,
                                                    planCount = constructionPlans.size,
                                                    canSettle = canSettle,
                                                    formatNumber = formatNumber
                                                )
                                            }
                                        }
                                    }

                                    // 结算历史区域 - 拆分为多个 item 以支持懒加载
                                    // 原实现：单个 item 内部 forEach 渲染全部历史结算单，LazyColumn 懒加载失效
                                    // 新实现：每个历史结算单作为独立 item，按需渲染
                                    if (settlementHistory.isEmpty()) {
                                        // 空状态：标题 + 空提示作为单独 item
                                        item(key = "history_empty") {
                                            SettlementHistoryEmptySection(
                                                greenGradient = greenGradient
                                            )
                                        }
                                    } else {
                                        // 顶部间距（替代原 Column 的 vertical=16.dp 顶部 padding）
                                        item(key = "history_top_spacer") {
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                        // 每个历史结算单作为独立 item，支持懒加载
                                        // 子项目>30个默认折叠，≤30个默认展开
                                        items(
                                            items = settlementHistory,
                                            key = { settlement -> "history_${settlement.settlementId}" }
                                        ) { settlement ->
                                            SettlementHistoryItem(
                                                settlement = settlement,
                                                constructionPlans = constructionPlans,
                                                expandedHistoryProjects = expandedHistoryProjects,
                                                greenGradient = greenGradient,
                                                exportingId = exportingId,
                                                onToggleHistoryProjectExpand = onToggleHistoryProjectExpand,
                                                onExportExcel = onExportSettlementExcel,
                                                onExportImage = onExportSettlementImage,
                                                getUnitName = getUnitName,
                                                formatNumber = formatNumber
                                            )
                                        }
                                        // 底部间距（替代原 Column 的 vertical=16.dp 底部 padding）
                                        item(key = "history_bottom_spacer") {
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }

                                    // 底部间距
                                    item(key = "footer_spacer") {
                                        Spacer(modifier = Modifier.height(80.dp))
                                    }
                                }
                            }
                            1 -> {
                                // 预支内容区域（左右滑动切到的第二页）
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val constructors by advanceViewModel.constructors.collectAsStateWithLifecycle()
                                    val selectedAdvanceUserId by advanceViewModel.selectedUserId.collectAsStateWithLifecycle()
                                    AdvanceContent(
                                        state = advanceState,
                                        onRetry = { advanceViewModel.loadAdvances() },
                                        showFilter = advanceViewModel.canFilterByUser(),
                                        constructors = constructors,
                                        selectedUserId = selectedAdvanceUserId,
                                        onSelectUser = { advanceViewModel.setSelectedUserId(it) }
                                    )
                                    // 预支相关状态
                                    var showCreateAdvanceDialog by remember { mutableStateOf(false) }
                                    val createAdvanceError by advanceViewModel.createErrorMessage.collectAsStateWithLifecycle()
                                    val isCreatingAdvance by advanceViewModel.isCreating.collectAsStateWithLifecycle()

                                    // 仅施工员可创建预支，其他角色不显示FAB按钮（对齐后端权限规则）
                                    if (advanceViewModel.canCreateAdvance()) {
                                        FloatingActionButton(
                                            onClick = { showCreateAdvanceDialog = true },
                                            containerColor = AppColors.Green400,
                                            contentColor = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(16.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Add,
                                                contentDescription = "创建预支"
                                            )
                                        }
                                    }
                                    if (showCreateAdvanceDialog) {
                                        CreateAdvanceDialog(
                                            onDismiss = { showCreateAdvanceDialog = false },
                                            onConfirm = { amount, advanceDate, remark ->
                                                advanceViewModel.createAdvance(amount, advanceDate, remark)
                                                showCreateAdvanceDialog = false
                                            },
                                            errorMessage = createAdvanceError,
                                            isCreating = isCreatingAdvance
                                        )
                                    }
                                }
                            }
                        }
                    }
                } // Column闭合
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
 * @param count 份数显示文本（支持小数，如"1.5"）
 * @param amount 金额
 * @param amountLabel 金额标签（如"总额："、"应收："、"月均："）
 * @param countSuffix 份数后辍，默认"份"
 */
data class StatCardData(
    val title: String,
    val count: String,
    val amount: Double,
    val iconColor: Color,
    val amountLabel: String = "总额：",
    val countSuffix: String = "份"
)

/**
 * 4宫格统计卡片区域
 * 4个卡片定义：
 * - 待结算工程：已完工未结算工程份数（工程级）+ 应收总额（个人级）
 * - 预支金额：未结算预支份数+总预支（个人维度）
 * - 今年工程量：今年所有状态工程份数+工程总额（工程级）
 * - 月均收入：今年已结算工程月均份数（工程级）+ 月均金额（个人级）
 */
@Composable
fun StatsGridSection(
    summary: SettlementSummary,
    formatNumber: (Double?) -> String,
    onCardClick: (String) -> Unit = {}
) {
    val cards = listOf(
        StatCardData(
            title = "待结算工程",
            count = summary.totalProjects.toString(),
            amount = summary.grandTotal,
            iconColor = Color(0xFFE6A23C), // 橙色
            amountLabel = "应收："
        ),
        StatCardData(
            title = "预支金额",
            count = summary.advanceCount.toString(),
            amount = summary.totalAdvance,
            iconColor = Color(0xFF409EFF), // 蓝色
            amountLabel = "总额："
        ),
        StatCardData(
            title = "今年工程量",
            count = summary.settledProjectCount.toString(),
            amount = summary.settledProjectTotalAmount,
            iconColor = Color(0xFF84CC16), // 绿色
            amountLabel = "总额："
        ),
        StatCardData(
            title = "月均收入",
            count = summary.monthlyAvgCount.toString(),
            amount = summary.monthlyAvgAmount,
            iconColor = Color(0xFF9333EA), // 紫色
            amountLabel = "月均："
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
 * 布局结构（三行）：
 *   第1行：图标 + 标题
 *   第2行：份数（缩进对齐图标右侧）
 *   第3行：金额标签 + 金额数（缩进对齐图标右侧）
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
            // 第1行：图标 + 标题
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
                            "待结算工程" -> "⏱"
                            "预支金额" -> "💰"
                            "今年工程量" -> "✓"
                            "月均收入" -> "📅"
                            else -> "📅"
                        },
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = card.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333842),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            // 第2行：份数（缩进40dp，与图标右侧对齐）
            Text(
                text = "${card.count}${card.countSuffix}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333842),
                modifier = Modifier.padding(start = 40.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            // 第3行：金额标签 + 金额数（缩进40dp，与图标右侧对齐）
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(40.dp))
                Text(
                    text = card.amountLabel,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Text(
                    text = "¥${formatNumber(card.amount)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = card.iconColor
                )
            }
        }
    }
}

// ========== 结算单区域 ==========

/**
 * 结算单标题栏 + 已选择工程数提示
 * 从 SettlementSheetSection 拆分出来，作为 LazyColumn 的独立 item
 * 不包含表格内容（表格内容已拆为独立的 header/projects/footer items）
 */
@Composable
fun SettlementSheetHeader(
    greenGradient: Brush,
    selectedProjectIds: List<Int>,
    settling: Boolean,
    canSettle: Boolean,
    onSettle: () -> Unit,
    onExportImage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // 绿色渐变标题栏（标题+导出图片按钮+结算按钮）
        SettlementTitleBar(
            title = "📋 结算单",
            greenGradient = greenGradient,
            rightContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 导出图片结算单按钮（在结算按钮左边）
                    Button(
                        onClick = onExportImage,
                        enabled = selectedProjectIds.isNotEmpty() && !settling,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF65A30D),
                            disabledContainerColor = Color(0xCCFFFFFF),
                            disabledContentColor = Color(0xFF999999)
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "导出",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                    // 结算按钮 —— 资料员/管理员不可结算，隐藏按钮
                    if (canSettle) {
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
            }
        )

        // 已选择工程数提示 —— 资料员/管理员不可结算时隐藏提示
        if (canSettle) {
            Text(
                text = "已选择 ${selectedProjectIds.size} 个工程，确认后将生成结算单，结算后不可修改",
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
            )
        }
    }
}

/**
 * 单元格右边框（用于形成表格内竖线）
 *
 * 配合 Modifier.weight(1f) 自适应宽度使用，在单元格右侧画竖线，
 * 多个单元格组合形成完整的内边框分隔效果。
 *
 * @param width 边框宽度（默认0.5dp）
 * @param color 边框颜色（默认浅灰，与外边框区分）
 */
fun Modifier.rightBorder(
    width: Dp = 0.5.dp,
    color: Color = Color(0xFFE5E7EB)
): Modifier = this.drawBehind {
    val borderWidth = width.toPx()
    drawLine(
        color = color,
        start = Offset(size.width, 0f),
        end = Offset(size.width, size.height),
        strokeWidth = borderWidth
    )
}

/**
 * 结算单/历史标题栏 - 绿色渐变背景
 * 注意：左侧标题使用weight(1f)占满剩余空间并单行省略，
 *       右侧内容保持紧凑单行，避免内容过多时换行导致标题栏超高
 */
@Composable
fun SettlementTitleBar(
    title: String,
    greenGradient: Brush,
    rightContent: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(greenGradient, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧标题：占满剩余空间，单行省略，避免换行
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 右侧内容：不占weight，保持紧凑
        rightContent()
    }
}

/**
 * 表头行
 */
@Composable
fun TableHeaderRow(
    constructionPlans: List<ConstructionPlanDto>,
    allSelected: Boolean,
    canSettle: Boolean,
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
        // 选择列 —— 资料员/管理员不可结算时隐藏 Checkbox
        if (canSettle) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { onToggleSelectAll() },
                    colors = CheckboxDefaults.colors(checkedColor = AppColors.Green400),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        // 序号
        Box(
            modifier = Modifier
                .weight(1f)
                .rightBorder(),
            contentAlignment = Alignment.Center
        ) {
            Text("序号", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
        }
        // 工程名称
        Box(
            modifier = Modifier
                .weight(2f)
                .rightBorder(),
            contentAlignment = Alignment.Center
        ) {
            Text("工程名称", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
        }
        // 各施工方案列（最后一个方案列不画右边框，与总额列之间由总额列画线）
        constructionPlans.forEachIndexed { index, plan ->
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    plan.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF374151),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        // 总额（最后一列，不画右边框避免与外边框重叠）
        Box(
            modifier = Modifier.weight(1.5f),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "总额",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF374151),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
    canSettle: Boolean,
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
        // 选择列 —— 资料员/管理员不可结算时隐藏 Checkbox
        if (canSettle) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onToggleSelection,
                    colors = CheckboxDefaults.colors(checkedColor = AppColors.Green400),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        // 序号
        Box(
            modifier = Modifier
                .weight(1f)
                .rightBorder(),
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", fontSize = 12.sp, color = Color(0xFF333333))
        }
        // 工程名称（可展开，单行省略避免换行撑高行高）
        Box(
            modifier = Modifier
                .weight(2f)
                .rightBorder()
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    project.projectName,
                    fontSize = 12.sp,
                    color = Color(0xFF333333),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 各施工方案列
        // 子项目未展开时显示该工程在该方案下的合计数量，展开时显示"-"（明细行已显示各自数量）
        constructionPlans.forEach { plan ->
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                if (!isExpanded) {
                    val planQty = project.planQuantities[plan.id.toString()]
                    if (planQty != null && planQty.totalQuantity > 0) {
                        Text(
                            "${formatNumber(planQty.totalQuantity)}${getUnitName(plan.unit)}",
                            fontSize = 12.sp,
                            color = Color(0xFF333333),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text("-", fontSize = 12.sp, color = Color(0xFF999999))
                    }
                } else {
                    Text("-", fontSize = 12.sp, color = Color(0xFF999999))
                }
            }
        }
        // 总额
        // 子项目未展开时显示该工程的合计金额（各方案金额之和），展开时显示"-"（明细行已显示金额）
        Box(
            modifier = Modifier.weight(1.5f),
            contentAlignment = Alignment.Center
        ) {
            if (!isExpanded) {
                val projectTotalAmount = project.planQuantities.values.sumOf { it.totalAmount }
                if (projectTotalAmount > 0) {
                    Text(
                        "¥${formatNumber(projectTotalAmount)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E40AF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", fontSize = 12.sp, color = Color(0xFF999999))
                }
            } else {
                Text("-", fontSize = 12.sp, color = Color(0xFF999999))
            }
        }
    }
}

/**
 * 子项目明细行
 * 名称列单行省略显示，保持表格行高一致
 * @param canSettle 是否可结算（施工员才有选择列，资料员/管理员无选择列需动态调整列宽对齐）
 */
@Composable
fun SubprojectRow(
    subproject: SubprojectDto,
    constructionPlans: List<ConstructionPlanDto>,
    canSettle: Boolean = true,
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
        // 空选择列 —— 资料员/管理员不可结算时无此列
        if (canSettle) {
            Box(modifier = Modifier.weight(1f).rightBorder())
        }
        // 空序号
        Box(modifier = Modifier.weight(1f).rightBorder())
        // 子项目名称（单行省略，保持行高一致）
        Box(
            modifier = Modifier.weight(2f).rightBorder(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "${subproject.spaceTypeName} - ${subproject.planName}",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(start = 20.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 各施工方案列
        constructionPlans.forEach { plan ->
            Box(
                modifier = Modifier.weight(1.5f).rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                if (subproject.planId == plan.id) {
                    Text(
                        "${String.format("%.2f", subproject.userQuantity)}${getUnitName(plan.unit)}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", fontSize = 11.sp, color = Color(0xFFCCCCCC))
                }
            }
        }
        // 总额
        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
            Text("-", fontSize = 11.sp, color = Color(0xFFCCCCCC))
        }
    }
}

/**
 * 单价行 - 灰色背景
 * @param canSettle 是否可结算（控制合并列宽度：施工员含选择列184dp，资料员/管理员无选择列136dp）
 */
@Composable
fun PriceRow(
    constructionPlans: List<ConstructionPlanDto>,
    canSettle: Boolean = true,
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
        // 合并前3列（选择+序号+工程名，weight=1+1+2=4）
        Box(
            modifier = Modifier.weight(4f).rightBorder(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("单价", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF666666))
        }
        // 各方案单价
        constructionPlans.forEach { plan ->
            Box(
                modifier = Modifier.weight(1.5f).rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "¥${plan.price ?: 0}/${getUnitName(plan.unit)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF666666),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        // 总额
        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
            Text("-", fontSize = 11.sp, color = Color(0xFF999999))
        }
    }
}

/**
 * 合计行 - 蓝色渐变背景
 * @param canSettle 是否可结算（控制合并列宽度：施工员含选择列184dp，资料员/管理员无选择列136dp）
 */
@Composable
fun TotalRow(
    constructionPlans: List<ConstructionPlanDto>,
    planTotals: Map<String, PlanTotalDto>,
    canSettle: Boolean = true,
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
        // 合并前3列（选择+序号+工程名，weight=1+1+2=4）
        Box(
            modifier = Modifier.weight(4f).rightBorder(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("合计", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
        }
        // 各方案合计
        constructionPlans.forEach { plan ->
            Box(
                modifier = Modifier.weight(1.5f).rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                val total = planTotals[plan.id.toString()]
                if (total != null && total.totalQuantity > 0) {
                    Text(
                        "${formatNumber(total.totalQuantity)}${getUnitName(plan.unit)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E40AF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", fontSize = 12.sp, color = Color(0xFF1E40AF))
                }
            }
        }
        // 总额
        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
            Text("-", fontSize = 12.sp, color = Color(0xFF1E40AF))
        }
    }
}

/**
 * 总计行 - 绿色渐变背景
 * @param canSettle 是否可结算（控制合并列宽度：施工员含选择列184dp，资料员/管理员无选择列136dp）
 */
@Composable
fun GrandTotalRow(
    constructionPlans: List<ConstructionPlanDto>,
    planTotals: Map<String, PlanTotalDto>,
    grandTotal: Double,
    canSettle: Boolean = true,
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
        // 合并前3列（选择+序号+工程名，weight=1+1+2=4）
        Box(
            modifier = Modifier.weight(4f).rightBorder(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("总计", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
        }
        // 各方案总计金额
        constructionPlans.forEach { plan ->
            Box(
                modifier = Modifier.weight(1.5f).rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                val total = planTotals[plan.id.toString()]
                if (total != null && total.totalAmount > 0) {
                    Text(
                        "¥${formatNumber(total.totalAmount)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E40AF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", fontSize = 12.sp, color = Color(0xFF1E40AF))
                }
            }
        }
        // 总额
        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
            Text(
                "¥${formatNumber(grandTotal)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E40AF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 预支行 - 黄色渐变背景
 * @param canSettle 是否可结算（控制合并列宽度：施工员含选择列184dp，资料员/管理员无选择列136dp）
 */
@Composable
fun AdvanceRow(
    advance: AdvanceDataDto,
    planCount: Int,
    canSettle: Boolean = true,
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
        // 合并前3列（选择+序号+工程名，weight=1+1+2=4）
        Box(
            modifier = Modifier.weight(4f).rightBorder(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "${formatAdvanceDate(advance.advanceDate)}预支",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF92400E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 各方案列（显示-）
        repeat(planCount) {
            Box(
                modifier = Modifier.weight(1.5f).rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                Text("-", fontSize = 12.sp, color = Color(0xFF92400E))
            }
        }
        // 预支金额
        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
            Text(
                "¥${formatNumber(advance.advanceAmount)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF92400E),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 总额行 - 粉色渐变背景
 * @param canSettle 是否可结算（控制合并列宽度：施工员含选择列184dp，资料员/管理员无选择列136dp）
 */
@Composable
fun FinalTotalRow(
    finalTotal: Double,
    planCount: Int,
    canSettle: Boolean = true,
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
        // 总额标签（合并前3列：选择+序号+工程名，weight=1+1+2=4）
        Box(
            modifier = Modifier.weight(4f).rightBorder(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("总额", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
        }
        // 各方案列（显示-）
        repeat(planCount) {
            Box(
                modifier = Modifier.weight(1.5f).rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                Text("-", fontSize = 12.sp, color = Color(0xFF1E40AF))
            }
        }
        // 最终总额
        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
            Text(
                "¥${formatNumber(finalTotal)}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E40AF),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ========== 结算历史区域 ==========

/**
 * 结算历史空状态区域（无已结算工程时显示）
 * 作为外层 LazyColumn 的独立 item
 */
@Composable
fun SettlementHistoryEmptySection(
    greenGradient: Brush
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 16.dp)
    ) {
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
    }
}

/**
 * 单个历史结算单项（标题栏 + 可折叠表格）
 * 作为外层 LazyColumn 的独立 item，支持懒加载
 * 默认折叠（只渲染标题栏），点击标题栏才展开表格，避免大量历史数据一次性渲染
 *
 * @param settlement 单个历史结算单数据
 * @param constructionPlans 施工方案列表
 * @param expandedHistoryProjects 展开的历史工程集合
 * @param isSettlementExpanded 当前结算单是否展开
 * @param greenGradient 绿色渐变背景
 * @param exportingId 当前正在导出的结算单ID
 * @param onToggleSettlementExpand 切换结算单展开/折叠回调
 * @param onToggleHistoryProjectExpand 切换历史工程展开回调
 * @param onExportExcel 导出表格回调
 * @param onExportImage 导出图片回调
 * @param getUnitName 获取单位名称回调
 * @param formatNumber 格式化数字回调
 */
@Composable
fun SettlementHistoryItem(
    settlement: SettlementHistoryDto,
    constructionPlans: List<ConstructionPlanDto>,
    expandedHistoryProjects: Set<String>,
    greenGradient: Brush,
    exportingId: Int? = null,
    onToggleHistoryProjectExpand: (Int, Int) -> Unit,
    onExportExcel: (Int, String) -> Unit = { _, _ -> },
    onExportImage: (SettlementHistoryDto) -> Unit = {},
    getUnitName: (String?) -> String,
    formatNumber: (Double?) -> String
) {
    // 展开/折叠状态：子项目总数≤30个默认展开，>30个默认折叠
    val totalSubprojects = remember(settlement.settlementId) {
        settlement.projects.sumOf { it.subprojects.size }
    }
    var isExpanded by remember(settlement.settlementId) {
        mutableStateOf(totalSubprojects <= 30)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // 顶部间距（保持与原 forEach 内 Spacer(8.dp) 一致的视觉间距）
        Spacer(modifier = Modifier.height(8.dp))

        // 绿色标题栏：左侧两行（结算时间 + 结算时间段），右侧导出按钮
        // 点击标题栏切换表格展开/折叠
        SettlementHistoryTitleBar(
            settlement = settlement,
            greenGradient = greenGradient,
            exportingId = exportingId,
            isExpanded = isExpanded,
            onToggleExpand = { isExpanded = !isExpanded },
            onExportExcel = { onExportExcel(settlement.settlementId, settlement.settlementNo) },
            onExportImage = { onExportImage(settlement) }
        )

        // 历史表格（仅在展开时渲染，折叠时不渲染表格内容，大幅减少渲染量）
        AnimatedVisibility(visible = isExpanded) {
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

/**
 * 结算历史专用标题栏
 * 左侧两行（向左对齐）：
 *   第一行 - 结算时间：YYYY-MM-DD
 *   第二行 - 结算时间段：YYYY-MM-DD 到 YYYY-MM-DD
 * 右侧：展开/折叠指示器 + 导出按钮（点击弹出选择：导出表格文件 / 导出图片结算单）
 * 点击标题栏左侧区域切换表格展开/折叠（子项目>30个默认折叠，≤30个默认展开）
 *
 * @param settlement 结算历史数据
 * @param greenGradient 绿色渐变背景
 * @param exportingId 当前正在导出的结算单ID
 * @param isExpanded 当前是否展开
 * @param onToggleExpand 切换展开/折叠回调
 * @param onExportExcel 点击导出表格回调
 * @param onExportImage 点击导出图片回调
 */
@Composable
private fun SettlementHistoryTitleBar(
    settlement: SettlementHistoryDto,
    greenGradient: Brush,
    exportingId: Int?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onExportExcel: () -> Unit,
    onExportImage: () -> Unit
) {
    // 格式化结算时间：从 settled_at（后端实际返回的字段，ISO格式）解析为 YYYY-MM-DD
    val settledDateText = remember(settlement.settledAt) {
        formatChineseDate(settlement.settledAt)
    }
    // 格式化结算时间段：YYYY-MM-DD 到 YYYY-MM-DD
    val periodText = remember(settlement.startMonth, settlement.endMonth) {
        buildPeriodString(settlement.startMonth, settlement.endMonth)
    }
    val isExporting = exportingId == settlement.settlementId
    // 导出类型选择弹窗状态
    var showExportDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(greenGradient, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：两行文本 + 展开/折叠箭头，点击切换展开状态
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onToggleExpand() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 展开/折叠指示箭头
            Text(
                text = if (isExpanded) "▼" else "▶",
                fontSize = 12.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 第一行：结算时间
                Text(
                    text = "结算时间：$settledDateText",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
                // 第二行：结算时间段
                Text(
                    text = "结算时间段：$periodText",
                    fontSize = 12.sp,
                    color = Color(0xCCFFFFFF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
            }
        }
        // 右侧：导出按钮（点击弹出选择弹窗）
        Button(
            onClick = { showExportDialog = true },
            enabled = !isExporting,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF65A30D),
                disabledContainerColor = Color(0xCCFFFFFF),
                disabledContentColor = Color(0xFF999999)
            ),
            shape = RoundedCornerShape(4.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isExporting) "导出中" else "导出",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }

    // 导出类型选择弹窗
    if (showExportDialog) {
        ExportTypeDialog(
            onDismiss = { showExportDialog = false },
            onExportExcel = {
                showExportDialog = false
                onExportExcel()
            },
            onExportImage = {
                showExportDialog = false
                onExportImage()
            }
        )
    }
}

/**
 * 导出类型选择弹窗
 * 提供两种导出选项：导出表格文件（Excel）、导出图片结算单（PNG）
 */
@Composable
private fun ExportTypeDialog(
    onDismiss: () -> Unit,
    onExportExcel: () -> Unit,
    onExportImage: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择导出方式", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 导出表格文件
                Surface(
                    onClick = onExportExcel,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0FDF4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Green100)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("📊", fontSize = 20.sp)
                        Column {
                            Text(
                                "导出表格文件",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.TextPrimary
                            )
                            Text(
                                "Excel格式，可编辑",
                                fontSize = 11.sp,
                                color = AppColors.TextTertiary
                            )
                        }
                    }
                }
                // 导出图片结算单
                Surface(
                    onClick = onExportImage,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF0FDF4),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Green100)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🖼️", fontSize = 20.sp)
                        Column {
                            Text(
                                "导出图片结算单",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppColors.TextPrimary
                            )
                            Text(
                                "PNG格式，样式与程序一致",
                                fontSize = 11.sp,
                                color = AppColors.TextTertiary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = AppColors.TextSecondary)
            }
        }
    )
}

/**
 * 将ISO日期字符串格式化为年-月-日（不含具体时间点）
 * 支持两种输入格式：ISO标准(yyyy-MM-dd'T'HH:mm:ss) 和 后端实际(yyyy-MM-dd HH:mm)
 */
private fun formatChineseDate(isoString: String): String {
    if (isoString.isBlank()) return "未知"
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA).parse(isoString)
            ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).parse(isoString)
            ?: Date()
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(date)
    } catch (e: Exception) {
        try {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).parse(isoString) ?: Date()
            SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(date)
        } catch (e2: Exception) {
            isoString.substring(0, minOf(10, isoString.length))
        }
    }
}

/**
 * 格式化预支日期为 2026.06.22 格式
 * 支持解析多种输入格式：ISO(yyyy-MM-dd'T'HH:mm:ss)、yyyy-MM-dd、yyyy-MM-dd HH:mm
 */
private fun formatAdvanceDate(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    return try {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA).parse(dateStr)
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(dateStr)
            ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).parse(dateStr)
            ?: return dateStr
        SimpleDateFormat("yyyy.MM.dd", Locale.CHINA).format(date)
    } catch (e: Exception) {
        // 解析失败时，尝试将连字符替换为点号
        dateStr.replace("-", ".").substring(0, minOf(10, dateStr.length))
    }
}

/**
 * 构建结算时间段字符串：YYYY-MM-DD 到 YYYY-MM-DD
 * 后端 start_month/end_month 为 DATE 类型，返回 yyyy-MM-dd 格式（如 2026-06-01）
 * 兼容处理：若返回带时间的 ISO 格式（如 2026-06-01T00:00:00），截取日期部分
 * 例如：startMonth="2026-06-01", endMonth="2026-06-30" → "2026-06-01 到 2026-06-30"
 */
private fun buildPeriodString(startMonth: String, endMonth: String): String {
    // 截取日期部分（取前10位 yyyy-MM-dd，兼容带T的ISO格式）
    val start = startMonth.takeIf { it.isNotBlank() }
        ?.let { it.substringBefore("T").take(10) }
        ?: "未知"
    val end = endMonth.takeIf { it.isNotBlank() }
        ?.let { it.substringBefore("T").take(10) }
        ?: "未知"
    return "$start 到 $end"
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
    ) {
        // 表格各列采用 weight 自适应填满容器宽度，无需横向滚动
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // 历史表头（无选择列）
            HistoryHeaderRow(constructionPlans = constructionPlans)

            // 去重后的工程列表（用 remember 包裹避免每次重组重复去重）
            val uniqueProjects = remember(settlement.projects) { settlement.projects.distinctBy { it.id } }
            uniqueProjects.forEachIndexed { index, project ->
                val isExpanded = expandedHistoryProjects.contains("${settlement.settlementId}-${project.id}")

                // 工程行
                HistoryProjectDataRow(
                    index = index,
                    project = project,
                    constructionPlans = constructionPlans,
                    isExpanded = isExpanded,
                    onToggleExpand = {
                        onToggleHistoryProjectExpand(settlement.settlementId, project.id)
                    },
                    getUnitName = getUnitName,
                    formatNumber = formatNumber
                )

                // 展开的子项目明细
                // 注意：AnimatedVisibility 的 content lambda 要求单一根组件，
                //       不能直接 forEach，否则只会渲染其中一个子项，需用 Column 包裹
                AnimatedVisibility(visible = isExpanded) {
                    Column {
                        project.subprojects.forEach { sub ->
                            HistorySubprojectRow(
                                subproject = sub,
                                constructionPlans = constructionPlans,
                                getUnitName = getUnitName
                            )
                        }
                    }
                }
            }

            // 合计行
            TotalRow(
                constructionPlans = constructionPlans,
                planTotals = settlement.planTotals,
                canSettle = false,
                getUnitName = getUnitName,
                formatNumber = formatNumber
            )

            // 单价行
            PriceRow(
                constructionPlans = constructionPlans,
                canSettle = false,
                getUnitName = getUnitName
            )

            // 总计行
            GrandTotalRow(
                constructionPlans = constructionPlans,
                planTotals = settlement.planTotals,
                grandTotal = settlement.grandTotal,
                canSettle = false,
                formatNumber = formatNumber
            )

            // 预支行
            settlement.advances.forEach { advance ->
                AdvanceRow(
                    advance = advance,
                    planCount = constructionPlans.size,
                    canSettle = false,
                    formatNumber = formatNumber
                )
            }

            // 总额行
            FinalTotalRow(
                finalTotal = settlement.finalTotal,
                planCount = constructionPlans.size,
                canSettle = false,
                formatNumber = formatNumber
            )
        }
    }
}

/**
 * 历史表头行（无选择列）
 * 注意：前两列总宽 = 36 + 148 = 184dp，与 TotalRow/GrandTotalRow/PriceRow 的"合并前3列"宽度一致，确保列对齐
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
        Box(
            modifier = Modifier.weight(1f).rightBorder(),
            contentAlignment = Alignment.Center
        ) {
            Text("序号", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
        }
        // 工程名称（weight=3，与TotalRow等合并列weight=4对齐：1+3=4）
        Box(
            modifier = Modifier.weight(3f).rightBorder(),
            contentAlignment = Alignment.Center
        ) {
            Text("工程名称", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
        }
        // 各施工方案列
        constructionPlans.forEach { plan ->
            Box(
                modifier = Modifier.weight(1.5f).rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    plan.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF374151),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        // 总额
        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
            Text(
                "总额",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF374151),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 历史工程数据行（无选择列）
 * 注意：工程名称宽度148dp，前两列总宽184dp与合计/总计行对齐
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
        Box(
            modifier = Modifier.weight(1f).rightBorder(),
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", fontSize = 12.sp, color = Color(0xFF333333))
        }
        // 工程名称（可展开，weight=3与表头对齐，单行省略避免换行撑高行高）
        Box(
            modifier = Modifier
                .weight(3f)
                .rightBorder()
                .clickable { onToggleExpand() },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isExpanded) "▼" else "▶",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    project.projectName,
                    fontSize = 12.sp,
                    color = Color(0xFF333333),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 各施工方案列
        // 子项目未展开时显示该工程在该方案下的合计数量，展开时显示"-"（明细行已显示各自数量）
        constructionPlans.forEach { plan ->
            Box(
                modifier = Modifier.weight(1.5f).rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                if (!isExpanded) {
                    val planQty = project.planQuantities[plan.id.toString()]
                    if (planQty != null && planQty.totalQuantity > 0) {
                        Text(
                            "${formatNumber(planQty.totalQuantity)}${getUnitName(plan.unit)}",
                            fontSize = 12.sp,
                            color = Color(0xFF333333),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text("-", fontSize = 12.sp, color = Color(0xFF999999))
                    }
                } else {
                    Text("-", fontSize = 12.sp, color = Color(0xFF999999))
                }
            }
        }
        // 总额
        // 子项目未展开时显示该工程的合计金额（各方案金额之和），展开时显示"-"（明细行已显示金额）
        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
            if (!isExpanded) {
                val projectTotalAmount = project.planQuantities.values.sumOf { it.totalAmount }
                if (projectTotalAmount > 0) {
                    Text(
                        "¥${formatNumber(projectTotalAmount)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E40AF),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", fontSize = 12.sp, color = Color(0xFF999999))
                }
            } else {
                Text("-", fontSize = 12.sp, color = Color(0xFF999999))
            }
        }
    }
}

/**
 * 历史子项目明细行（无选择列）
 * 前2列合并宽度 = 36 + 148 = 184dp，与 TotalRow/GrandTotalRow/PriceRow 对齐
 * 名称列单行省略显示，保持表格行高一致
 */
@Composable
fun HistorySubprojectRow(
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
        // 空序号
        Box(modifier = Modifier.weight(1f).rightBorder())
        // 子项目名称（weight=3与工程名称列对齐；单行省略，保持行高一致）
        Box(
            modifier = Modifier.weight(3f).rightBorder(),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                "${subproject.spaceTypeName} - ${subproject.planName}",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(start = 20.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 各施工方案列
        constructionPlans.forEach { plan ->
            Box(
                modifier = Modifier.weight(1.5f).rightBorder(),
                contentAlignment = Alignment.Center
            ) {
                if (subproject.planId == plan.id) {
                    Text(
                        "${String.format("%.2f", subproject.userQuantity)}${getUnitName(plan.unit)}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text("-", fontSize = 11.sp, color = Color(0xFFCCCCCC))
                }
            }
        }
        // 总额
        Box(modifier = Modifier.weight(1.5f), contentAlignment = Alignment.Center) {
            Text("-", fontSize = 11.sp, color = Color(0xFFCCCCCC))
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
