package com.salary.manager.feature.home.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.design.component.GreenTopNavBar
import com.salary.core.design.theme.AppColors
import com.salary.core.ui.state.ListUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 工程列表页面 - 对齐Vue前端Projects.vue设计
 * 包含：搜索栏+高级筛选、筛选标签、按月分组的工程卡片列表、下拉刷新+上拉加载
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProjectListScreen(
    onNavigateToProject: (Int) -> Unit = {},
    viewModel: ProjectListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val advancedFilter by viewModel.advancedFilter.collectAsState()
    val userNickname by viewModel.userNickname.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var searchKeyword by remember { mutableStateOf("") }
    var showAdvancedFilter by remember { mutableStateOf(false) }
    var confirmProject by remember { mutableStateOf<ProjectUiModel?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 搜索防抖Job
    var searchDebounceJob by remember { mutableStateOf<Job?>(null) }

    // 处理成功消息
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    // 处理错误消息
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // 顶部导航栏
        GreenTopNavBar(
            title = "工程管理",
            userNickname = userNickname.ifBlank { "未登录" },
            unreadCount = 0
        )

        // 搜索栏 + 高级筛选按钮
        SearchRow(
            searchKeyword = searchKeyword,
            onSearchKeywordChange = {
                searchKeyword = it
                // 300ms防抖搜索
                searchDebounceJob?.cancel()
                searchDebounceJob = scope.launch {
                    delay(300)
                    viewModel.updateKeyword(it)
                }
            },
            onSearch = {
                // 直接搜索（回车触发）
                searchDebounceJob?.cancel()
                viewModel.updateKeyword(searchKeyword)
            },
            onOpenAdvancedFilter = { showAdvancedFilter = true }
        )

        // 筛选标签行
        if (advancedFilter.hasActiveFilters) {
            FilterTagsRow(
                filter = advancedFilter,
                onClearFilter = { viewModel.clearFilter(it) },
                onClearAll = { viewModel.clearAllFilters() }
            )
        }

        // 工程列表
        when (state) {
            is ListUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.Green400)
                }
            }
            is ListUiState.Success -> {
                val items = (state as ListUiState.Success<ProjectUiModel>).items
                val hasMore = (state as ListUiState.Success<ProjectUiModel>).hasMore

                if (items.isEmpty()) {
                    EmptyProjectList()
                } else {
                    ProjectList(
                        projects = items,
                        hasMore = hasMore,
                        onLoadMore = { viewModel.loadMore() },
                        onRefresh = { viewModel.refresh() },
                        onNavigateToProject = onNavigateToProject,
                        onConfirmComplete = { project -> confirmProject = project },
                        onSettlingClick = { scope.launch { snackbarHostState.showSnackbar("该工程正在统计中，请到统计页面查看统计结果") } },
                        onSettledClick = { scope.launch { snackbarHostState.showSnackbar("该工程已结算完成，如需查看详情请点击\"查看详情\"按钮") } }
                    )
                }
            }
            is ListUiState.Error -> {
                val errorMsg = (state as ListUiState.Error).message
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMsg,
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请检查网络连接或服务器配置后重试",
                            color = AppColors.TextTertiary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.refresh() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.Green400
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("重新加载")
                        }
                    }
                }
            }
        }
    }

    // 确认完工对话框
    confirmProject?.let { project ->
        AlertDialog(
            onDismissRequest = { confirmProject = null },
            title = { Text("确认完工") },
            text = {
                Column {
                    Text("工程名称：${project.name}")
                    Text("当前状态：施工中")
                    Text("新状态：已完工")
                    Text("确认后工程将进入统计结算流程", color = AppColors.TextSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmProjectComplete(project.id)
                        confirmProject = null
                    }
                ) {
                    Text("确认完工", color = AppColors.Green400)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmProject = null }) {
                    Text("取消", color = AppColors.TextSecondary)
                }
            }
        )
    }

    // Snackbar
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 80.dp)
    )
    }

    // 高级筛选弹窗
    if (showAdvancedFilter) {
        AdvancedFilterSheet(
            currentFilter = advancedFilter,
            onApply = { filter ->
                viewModel.updateAdvancedFilter(filter)
                showAdvancedFilter = false
            },
            onReset = {
                viewModel.clearAllFilters()
                showAdvancedFilter = false
            },
            onDismiss = { showAdvancedFilter = false }
        )
    }
}

/**
 * 搜索栏 - 搜索框 + 高级筛选按钮（漏斗图标）
 */
@Composable
private fun SearchRow(
    searchKeyword: String,
    onSearchKeywordChange: (String) -> Unit,
    onSearch: () -> Unit = {},
    onOpenAdvancedFilter: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 搜索框
        OutlinedTextField(
            value = searchKeyword,
            onValueChange = onSearchKeywordChange,
            placeholder = { Text("搜索工程名称、描述或施工员") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = { onSearch() }
            )
        )
        // 高级筛选按钮
        IconButton(
            onClick = onOpenAdvancedFilter,
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(AppColors.Green400, AppColors.Green500)),
                    RoundedCornerShape(12.dp)
                )
                .size(48.dp)
        ) {
            Icon(
                Icons.Default.FilterAlt,
                contentDescription = "高级筛选",
                tint = Color.White
            )
        }
    }
}

/**
 * 筛选标签行 - 已激活的筛选条件显示为可关闭的Tag
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterTagsRow(
    filter: AdvancedFilterState,
    onClearFilter: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.Surface
    ) {
        FlowRow(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 月份标签
            filter.month?.let { month ->
                FilterTag(
                    text = "${filter.year ?: java.time.LocalDate.now().year}年${month}月",
                    onClose = { onClearFilter("month") }
                )
            }
            // 工程状态标签
            filter.status?.let { status ->
                FilterTag(
                    text = statusText(status),
                    onClose = { onClearFilter("status") }
                )
            }
            // 结算状态标签
            filter.settlementStatus?.let { status ->
                FilterTag(
                    text = settlementStatusText(status),
                    onClose = { onClearFilter("settlementStatus") }
                )
            }
            // 日期范围标签
            if (filter.startDate != null || filter.endDate != null) {
                FilterTag(
                    text = "日期: ${filter.startDate ?: "起始"} ~ ${filter.endDate ?: "结束"}",
                    onClose = { onClearFilter("date") }
                )
            }
            // 清空按钮
            TextButton(onClick = onClearAll) {
                Text("清空", fontSize = 12.sp, color = AppColors.TextSecondary)
            }
        }
    }
}

/**
 * 可关闭的筛选标签
 */
@Composable
private fun FilterTag(
    text: String,
    onClose: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = AppColors.Green400.copy(alpha = 0.12f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                color = AppColors.Green400,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(
                onClick = onClose,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.size(16.dp)
            ) {
                Text("×", fontSize = 14.sp, color = AppColors.Green400)
            }
        }
    }
}

/**
 * 高级筛选弹窗 - 底部弹出
 * 包含：月份选择、工程状态选择、结算状态选择、开始/结束日期
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFilterSheet(
    currentFilter: AdvancedFilterState,
    onApply: (AdvancedFilterState) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var editFilter by remember(currentFilter) { mutableStateOf(currentFilter) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("取消", color = AppColors.TextSecondary)
                }
                Text("高级筛选", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                TextButton(onClick = { onApply(editFilter) }) {
                    Text("应用", color = AppColors.Green400, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 月份选择
            FilterOptionRow(label = "月份") {
                var showMonthPicker by remember { mutableStateOf(false) }
                TextButton(onClick = { showMonthPicker = true }) {
                    Text(
                        if (editFilter.month != null) "${editFilter.year ?: java.time.LocalDate.now().year}年${editFilter.month}月"
                        else "请选择月份",
                        color = if (editFilter.month != null) AppColors.Green400 else AppColors.TextPlaceholder
                    )
                }
                if (showMonthPicker) {
                    MonthPickerSheet(
                        currentYear = editFilter.year ?: java.time.LocalDate.now().year,
                        currentMonth = editFilter.month ?: java.time.LocalDate.now().monthValue,
                        onConfirm = { year, month ->
                            editFilter = editFilter.copy(year = year, month = month)
                            showMonthPicker = false
                        },
                        onDismiss = { showMonthPicker = false }
                    )
                }
            }

            // 工程状态选择
            FilterOptionRow(label = "工程状态") {
                var showStatusPicker by remember { mutableStateOf(false) }
                TextButton(onClick = { showStatusPicker = true }) {
                    Text(
                        editFilter.status?.let { statusText(it) } ?: "请选择状态",
                        color = if (editFilter.status != null) AppColors.Green400 else AppColors.TextPlaceholder
                    )
                }
                if (showStatusPicker) {
                    StatusPickerSheet(
                        options = statusOptions,
                        current = editFilter.status,
                        onConfirm = { editFilter = editFilter.copy(status = it); showStatusPicker = false },
                        onDismiss = { showStatusPicker = false }
                    )
                }
            }

            // 结算状态选择
            FilterOptionRow(label = "结算状态") {
                var showSettlementPicker by remember { mutableStateOf(false) }
                TextButton(onClick = { showSettlementPicker = true }) {
                    Text(
                        editFilter.settlementStatus?.let { settlementStatusText(it) } ?: "请选择结算状态",
                        color = if (editFilter.settlementStatus != null) AppColors.Green400 else AppColors.TextPlaceholder
                    )
                }
                if (showSettlementPicker) {
                    StatusPickerSheet(
                        options = settlementStatusOptions,
                        current = editFilter.settlementStatus,
                        onConfirm = { editFilter = editFilter.copy(settlementStatus = it); showSettlementPicker = false },
                        onDismiss = { showSettlementPicker = false }
                    )
                }
            }

            // 开始日期
            FilterOptionRow(label = "开始日期") {
                var dateValue by remember { mutableStateOf(editFilter.startDate ?: "") }
                OutlinedTextField(
                    value = dateValue,
                    onValueChange = {
                        dateValue = it
                        editFilter = editFilter.copy(startDate = it.ifBlank { null })
                    },
                    placeholder = { Text("yyyy-MM-dd", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.width(160.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
            }

            // 结束日期
            FilterOptionRow(label = "结束日期") {
                var dateValue by remember { mutableStateOf(editFilter.endDate ?: "") }
                OutlinedTextField(
                    value = dateValue,
                    onValueChange = {
                        dateValue = it
                        editFilter = editFilter.copy(endDate = it.ifBlank { null })
                    },
                    placeholder = { Text("yyyy-MM-dd", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.width(160.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 重置按钮
            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Surface,
                    contentColor = AppColors.TextPrimary
                )
            ) {
                Text("重置")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 筛选选项行 - 标签 + 选择器
 */
@Composable
private fun FilterOptionRow(
    label: String,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = AppColors.TextPrimary)
        content()
    }
}

/**
 * 月份选择弹窗
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MonthPickerSheet(
    currentYear: Int,
    currentMonth: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedYear by remember { mutableStateOf(currentYear) }
    var selectedMonth by remember { mutableStateOf(currentMonth) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("选择月份", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))

            // 年份选择
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectedYear-- }) { Text("<") }
                Text("${selectedYear}年", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                TextButton(onClick = { selectedYear++ }) { Text(">") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 月份网格
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (1..12).forEach { month ->
                    val isSelected = month == selectedMonth
                    TextButton(
                        onClick = { selectedMonth = month },
                        modifier = Modifier
                            .background(
                                if (isSelected) AppColors.Green400 else AppColors.Green50,
                                RoundedCornerShape(8.dp)
                            )
                            .width(56.dp)
                    ) {
                        Text(
                            "${month}月",
                            color = if (isSelected) Color.White else AppColors.TextPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 确认按钮
            Button(
                onClick = { onConfirm(selectedYear, selectedMonth) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400)
            ) {
                Text("确定", color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 状态选择弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPickerSheet(
    options: List<Pair<String?, String>>,
    current: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == current
                TextButton(
                    onClick = { onConfirm(value) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        label,
                        color = if (isSelected) AppColors.Green400 else AppColors.TextPrimary,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 工程列表 - 按月分组显示，支持下拉刷新和上拉加载
 */
@Composable
private fun ProjectList(
    projects: List<ProjectUiModel>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onNavigateToProject: (Int) -> Unit,
    onConfirmComplete: (ProjectUiModel) -> Unit,
    onSettlingClick: () -> Unit,
    onSettledClick: () -> Unit
) {
    val listState = rememberLazyListState()

    // 检测是否滚动到底部，触发加载更多
    LaunchedEffect(listState, hasMore) {
        snapshotFlow {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= listState.layoutInfo.totalItemsCount - 2
        }.collect { isAtEnd ->
            if (isAtEnd && hasMore) {
                onLoadMore()
            }
        }
    }

    // 按月分组
    val groupedProjects = remember(projects) { groupProjectsByMonth(projects) }

    LazyColumn(
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        groupedProjects.forEach { (yearMonth, monthProjects) ->
            // 月份分组标题（绿色渐变背景，sticky）
            item(key = "header_$yearMonth") {
                MonthGroupHeader(yearMonth = yearMonth)
            }
            // 该月下的工程卡片
            items(monthProjects, key = { it.id }) { project ->
                ProjectCard(
                    project = project,
                    onNavigateToProject = { onNavigateToProject(project.id) },
                    onConfirmComplete = { onConfirmComplete(project) },
                    onSettlingClick = onSettlingClick,
                    onSettledClick = onSettledClick
                )
            }
        }

        // 加载更多指示器
        if (hasMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = AppColors.Green400,
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

/**
 * 月份分组标题 - 绿色渐变背景
 */
@Composable
private fun MonthGroupHeader(yearMonth: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(AppColors.Green400, AppColors.Green500)
                ),
                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = yearMonth,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

/**
 * 空状态占位 - 友好提示
 */
@Composable
private fun EmptyProjectList() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("暂无工程数据", fontSize = 16.sp, color = AppColors.TextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("您可以在工作台页面创建新工程", fontSize = 12.sp, color = AppColors.TextTertiary)
        }
    }
}

// ==================== 工具函数 ====================

/** 工程状态选项列表 */
private val statusOptions = listOf(
    null to "全部状态",
    "preparing" to "备料中",
    "constructing" to "施工中",
    "completed" to "已完工",
    "canceled" to "已取消"
)

/** 结算状态选项列表 */
private val settlementStatusOptions = listOf(
    null to "全部结算状态",
    "unsettled" to "未结算",
    "settling" to "统计中",
    "settled" to "已结算"
)

/** 工程状态文本映射 */
private fun statusText(status: String): String = when (status) {
    "preparing" -> "备料中"
    "constructing" -> "施工中"
    "completed" -> "已完工"
    "canceled" -> "已取消"
    else -> status
}

/** 结算状态文本映射 */
private fun settlementStatusText(status: String): String = when (status) {
    "unsettled" -> "未结算"
    "settling" -> "统计中"
    "settled" -> "已结算"
    else -> status
}

/** 按月分组工程列表 */
private fun groupProjectsByMonth(projects: List<ProjectUiModel>): Map<String, List<ProjectUiModel>> {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA)
    val formatter = SimpleDateFormat("yyyy年M月", Locale.CHINA)
    return projects
        .groupBy { project ->
            try {
                val date = parser.parse(project.createdAt)
                date?.let { formatter.format(it) } ?: "未知月份"
            } catch (e: Exception) {
                "未知月份"
            }
        }
}
