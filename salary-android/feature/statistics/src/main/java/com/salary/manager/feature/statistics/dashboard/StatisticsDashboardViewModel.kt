package com.salary.manager.feature.statistics.dashboard

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.data.local.UserStorage
import com.salary.core.network.api.CalculateRequest
import com.salary.core.network.api.CalculateResultDto
import com.salary.core.network.api.ConstructionPlanDto
import com.salary.core.network.api.ProjectApi
import com.salary.core.network.api.SalaryProjectDto
import com.salary.core.network.api.SalarySheetApi
import com.salary.core.network.api.SettleRequest
import com.salary.core.network.api.SettlementHistoryDto
import com.salary.core.network.api.StatisticsApi
import com.salary.core.network.dto.ProjectDto
import com.salary.core.ui.state.ListUiState
import com.salary.core.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * 统计面板ViewModel - 管理结算统计、结算单数据、结算历史和工程选择逻辑
 *
 * 4个卡片统计数据从 /v1/statistics/dashboard 获取（后端统一计算，前端直接展示），
 * 结算中工程列表从 /v1/salary-sheet/projects 获取（用于页面下方展示）。
 */
@HiltViewModel
class StatisticsDashboardViewModel @Inject constructor(
    private val salarySheetApi: SalarySheetApi,
    private val projectApi: ProjectApi,
    private val statisticsApi: StatisticsApi,
    private val userStorage: UserStorage,
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** 页面整体加载状态 */
    private val _state = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val state: StateFlow<UiState<Unit>> = _state.asStateFlow()

    /** 结算统计摘要（从工程数据中计算得出） */
    private val _settlementSummary = MutableStateFlow(SettlementSummary())
    val settlementSummary: StateFlow<SettlementSummary> = _settlementSummary.asStateFlow()

    /** 施工方案列表 */
    private val _constructionPlans = MutableStateFlow<List<ConstructionPlanDto>>(emptyList())
    val constructionPlans: StateFlow<List<ConstructionPlanDto>> = _constructionPlans.asStateFlow()

    /** 结算单工程数据 */
    private val _projectData = MutableStateFlow<List<SalaryProjectDto>>(emptyList())
    val projectData: StateFlow<List<SalaryProjectDto>> = _projectData.asStateFlow()

    /** 结算历史列表 */
    private val _settlementHistory = MutableStateFlow<List<SettlementHistoryDto>>(emptyList())
    val settlementHistory: StateFlow<List<SettlementHistoryDto>> = _settlementHistory.asStateFlow()

    /** 已选择的工程ID列表 */
    private val _selectedProjectIds = MutableStateFlow<List<Int>>(emptyList())
    val selectedProjectIds: StateFlow<List<Int>> = _selectedProjectIds.asStateFlow()

    /** 计算结果 */
    private val _calculationResult = MutableStateFlow(CalculateResultDto())
    val calculationResult: StateFlow<CalculateResultDto> = _calculationResult.asStateFlow()

    /** 结算操作进行中 */
    private val _settling = MutableStateFlow(false)
    val settling: StateFlow<Boolean> = _settling.asStateFlow()

    /** 已展开的工程ID列表 */
    private val _expandedProjects = MutableStateFlow<Set<Int>>(emptySet())
    val expandedProjects: StateFlow<Set<Int>> = _expandedProjects.asStateFlow()

    /** 已展开的历史工程（格式：settlementId-projectId） */
    private val _expandedHistoryProjects = MutableStateFlow<Set<String>>(emptySet())
    val expandedHistoryProjects: StateFlow<Set<String>> = _expandedHistoryProjects.asStateFlow()

    /** 用户昵称（从UserStorage响应式获取） */
    val userNickname: StateFlow<String> = userStorage.nicknameFlow

    /** 当前用户角色（用于UI层按角色控制元素显示，如资料员/管理员隐藏结算按钮和Checkbox） */
    val userRole: StateFlow<String> = userStorage.roleFlow

    /** 错误消息 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** 成功消息 */
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    /** 统计卡片点击弹窗的工程列表加载状态 */
    private val _statsProjectListState = MutableStateFlow<ListUiState<ProjectDto>>(ListUiState.Loading)
    val statsProjectListState: StateFlow<ListUiState<ProjectDto>> = _statsProjectListState.asStateFlow()

    /** 统计卡片点击弹窗的标题 */
    private val _statsPopupTitle = MutableStateFlow("")
    val statsPopupTitle: StateFlow<String> = _statsPopupTitle.asStateFlow()

    // ===== 统计弹窗分页加载状态 =====
    /** 当前弹窗使用的筛选类型（settling/settled/其他），用于 loadMore 时复用 */
    private val _statsFilterType = MutableStateFlow("")
    val statsFilterType: StateFlow<String> = _statsFilterType.asStateFlow()

    /** 是否正在加载下一页（防抖，避免重复触发 loadMore） */
    private val _statsLoadingMore = MutableStateFlow(false)
    val statsLoadingMore: StateFlow<Boolean> = _statsLoadingMore.asStateFlow()

    /** 是否还有更多数据可加载（基于后端 hasNext） */
    private val _statsHasMore = MutableStateFlow(false)
    val statsHasMore: StateFlow<Boolean> = _statsHasMore.asStateFlow()

    /** 正在导出的结算单ID，null表示未在导出 */
    private val _exportingId = MutableStateFlow<Int?>(null)
    val exportingId: StateFlow<Int?> = _exportingId.asStateFlow()

    private companion object {
        const val TAG = "StatisticsDashboardVM"
        /** 统计弹窗每页大小（与后端 /v1/projects 的 size 上限100对齐） */
        const val STATS_PAGE_SIZE = 50
    }

    init {
        loadAllData()
    }

    /** 加载所有数据（渐进式：先加载结算单数据立即显示页面，历史数据异步加载不阻塞） */
    fun loadAllData() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                // 先加载结算单数据（施工方案+工程列表+仪表盘统计），加载完立即显示页面
                loadSalarySheetData()
                _state.value = UiState.Success(Unit)
                // 异步加载结算历史（不阻塞页面显示，加载完自动刷新UI）
                launch { loadSettlementHistory() }
            } catch (e: Exception) {
                _state.value = UiState.Error(NetworkErrorHandler.translate(e, "加载统计数据失败"))
            }
        }
    }

    /**
     * 静默刷新（不显示Loading、不覆盖已有状态），用于Tab切换时后台刷新数据
     * 仅在数据已加载成功后刷新，避免页面闪烁
     */
    fun silentRefresh() {
        viewModelScope.launch {
            try {
                // 先刷新结算单数据
                loadSalarySheetData()
                if (_state.value is UiState.Error) {
                    _state.value = UiState.Success(Unit)
                }
                // 异步刷新历史数据
                launch { loadSettlementHistory() }
            } catch (_: Exception) {
                // 静默模式忽略错误，不覆盖已有状态
            }
        }
    }

    /**
     * 加载结算单数据（施工方案 + 工程列表 + 仪表盘卡片统计）
     *
     * 数据来源分工：
     * - 施工方案列表：salarySheetApi.getConstructionPlans()
     * - 结算中工程列表（页面下方列表展示）：salarySheetApi.getProjects()
     * - 4个卡片统计数据：statisticsApi.getDashboard()（所有计算由后端完成，前端直接展示）
     *
     * 4个卡片数据由后端 /v1/statistics/dashboard 接口一次性返回：
     * - 待结算工程：unsettledProjectCount + unsettledAmount（已完工未结算，份数工程级，金额个人级）
     * - 预支金额：advanceCount + advanceTotal（未结算预支）
     * - 今年工程量：yearProjectCount + yearProjectAmount（今年所有状态工程，工程级总额）
     * - 月均收入：monthlyAvgCount + monthlyAvgAmount（份数工程级，金额个人级）
     */
    private suspend fun loadSalarySheetData() {
        try {
            // 并行加载施工方案、工程列表、仪表盘统计数据
            kotlinx.coroutines.coroutineScope {
                val plansJob = launch {
                    try {
                        val plansResponse = salarySheetApi.getConstructionPlans()
                        if (plansResponse.code == 200 && plansResponse.data != null) {
                            _constructionPlans.value = plansResponse.data!!
                        }
                    } catch (e: Exception) {
                        _errorMessage.value = NetworkErrorHandler.translate(e, "加载施工方案失败")
                    }
                }
                val projectsJob = launch {
                    try {
                        val projectsResponse = salarySheetApi.getProjects()
                        if (projectsResponse.code == 200 && projectsResponse.data != null) {
                            val data = projectsResponse.data!!
                            _projectData.value = data.projects
                            // finalTotal 用于下方列表展示（结算中实付金额）
                            _settlementSummary.update { it.copy(finalTotal = data.finalTotal) }
                        }
                    } catch (e: Exception) {
                        _errorMessage.value = NetworkErrorHandler.translate(e, "加载工程数据失败")
                    }
                }
                // 仪表盘4个卡片数据：所有计算由后端完成
                val dashboardJob = launch {
                    try {
                        loadDashboardStats()
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "加载仪表盘统计失败", e)
                        _errorMessage.value = NetworkErrorHandler.translate(e, "加载统计数据失败")
                    }
                }
                plansJob.join()
                projectsJob.join()
                dashboardJob.join()
            }
        } catch (e: Exception) {
            _errorMessage.value = NetworkErrorHandler.translate(e, "加载数据失败")
        }
    }

    /**
     * 加载仪表盘4个卡片统计数据
     * 调用后端 /v1/statistics/dashboard 接口，所有金额计算由后端SQL聚合完成，前端不做任何计算。
     */
    private suspend fun loadDashboardStats() {
        val response = statisticsApi.getDashboard()
        android.util.Log.d(TAG, "仪表盘统计: code=${response.code}, data=${response.data != null}")
        if (response.code == 200 && response.data != null) {
            val d = response.data!!
            android.util.Log.d(TAG, "仪表盘数据: unsettled=${d.unsettledProjectCount}/${d.unsettledAmount}, " +
                "advance=${d.advanceCount}/${d.advanceTotal}, " +
                "yearProject=${d.yearProjectCount}/${d.yearProjectAmount}, " +
                "monthlyAvg=${d.monthlyAvgCount}/${d.monthlyAvgAmount}")
            _settlementSummary.update { current ->
                current.copy(
                    // 卡片1：待结算工程（份数工程级，金额个人级）
                    totalProjects = d.unsettledProjectCount,
                    grandTotal = d.unsettledAmount,
                    // 卡片2：预支金额
                    totalAdvance = d.advanceTotal,
                    advanceCount = d.advanceCount,
                    // 卡片3：今年工程量（所有状态，工程级总额）
                    settledProjectCount = d.yearProjectCount,
                    settledProjectTotalAmount = d.yearProjectAmount,
                    // 卡片4：月均收入（份数工程级，金额个人级）
                    // settledUserAmount 存储今年个人总额，供其他地方使用
                    settledUserAmount = d.monthlyAvgAmount * java.time.LocalDate.now().monthValue.coerceAtLeast(1),
                    monthlyAvgCount = d.monthlyAvgCount,
                    monthlyAvgAmount = d.monthlyAvgAmount
                )
            }
        }
    }

    /** 加载结算历史 */
    private suspend fun loadSettlementHistory() {
        try {
            val response = salarySheetApi.getSettlementHistory()
            if (response.code == 200 && response.data != null) {
                _settlementHistory.value = response.data!!
            }
        } catch (e: Exception) {
            _errorMessage.value = NetworkErrorHandler.translate(e, "加载结算历史失败")
        }
    }

    /** 切换工程选择状态 */
    fun toggleProjectSelection(projectId: Int, selected: Boolean) {
        val current = _selectedProjectIds.value.toMutableList()
        if (selected && !current.contains(projectId)) {
            current.add(projectId)
        } else if (!selected) {
            current.remove(projectId)
        }
        _selectedProjectIds.value = current
        // 选择变化后重新计算
        calculateSettlement()
    }

    /** 全选/取消全选 */
    fun toggleSelectAll() {
        val currentSelected = _selectedProjectIds.value
        val allIds = _projectData.value.map { it.id }
        if (currentSelected.size == allIds.size && allIds.isNotEmpty()) {
            _selectedProjectIds.value = emptyList()
        } else {
            _selectedProjectIds.value = allIds
        }
        calculateSettlement()
    }

    /** 计算结算金额 */
    private fun calculateSettlement() {
        val selectedIds = _selectedProjectIds.value
        if (selectedIds.isEmpty()) {
            _calculationResult.value = CalculateResultDto()
            return
        }
        viewModelScope.launch {
            try {
                val response = salarySheetApi.calculate(CalculateRequest(projectIds = selectedIds))
                if (response.code == 200 && response.data != null) {
                    _calculationResult.value = response.data!!
                }
            } catch (e: Exception) {
                _errorMessage.value = NetworkErrorHandler.translate(e, "计算结算失败")
            }
        }
    }

    /** 执行结算操作 */
    fun handleSettle() {
        val selectedIds = _selectedProjectIds.value
        if (selectedIds.isEmpty()) {
            _errorMessage.value = "请选择要结算的工程"
            return
        }
        viewModelScope.launch {
            _settling.value = true
            try {
                val response = salarySheetApi.settle(SettleRequest(projectIds = selectedIds))
                if (response.code == 200 && response.data != null) {
                    val data = response.data!!
                    _successMessage.value = "结算成功！单号：${data.settlementNo}"
                    _selectedProjectIds.value = emptyList()
                    _calculationResult.value = CalculateResultDto()
                    // 刷新数据
                    loadSalarySheetData()
                    loadSettlementHistory()
                } else {
                    _errorMessage.value = NetworkErrorHandler.translateServerError(response.msg, "结算失败")
                }
            } catch (e: Exception) {
                _errorMessage.value = NetworkErrorHandler.translate(e, "结算失败")
            } finally {
                _settling.value = false
            }
        }
    }

    /** 切换工程展开/折叠 */
    fun toggleProjectExpand(projectId: Int) {
        val current = _expandedProjects.value.toMutableSet()
        if (current.contains(projectId)) {
            current.remove(projectId)
        } else {
            current.add(projectId)
        }
        _expandedProjects.value = current
    }

    /** 切换历史工程展开/折叠 */
    fun toggleHistoryProjectExpand(settlementId: Int, projectId: Int) {
        val key = "$settlementId-$projectId"
        val current = _expandedHistoryProjects.value.toMutableSet()
        if (current.contains(key)) {
            current.remove(key)
        } else {
            current.add(key)
        }
        _expandedHistoryProjects.value = current
    }

    /** 清除错误消息 */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /** 清除成功消息 */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    /**
     * 加载统计卡片对应的工程列表（首次加载/重置）
     *
     * 卡片与filterType对应关系：
     * - "待结算工程"卡片 → settling → 查询已完工且结算状态为settling的工程（个人维度）
     * - "预支金额"卡片 → advance → 切换到预支Tab（不加载工程列表）
     * - "今年工程量"卡片 → settled → 查询今年已结算工程（工程总额）
     * - "月均收入"卡片 → settled → 查询今年已结算工程（与今年工程量共用）
     *
     * 分页：首次加载第1页，后续滚动到底部由 loadMoreStatsProjects() 追加
     */
    fun loadStatsProjectList(filterType: String) {
        viewModelScope.launch {
            // 重置分页状态
            _statsFilterType.value = filterType
            _statsLoadingMore.value = false
            _statsHasMore.value = false
            _statsProjectListState.value = ListUiState.Loading
            _statsPopupTitle.value = when (filterType) {
                "settling" -> "待结算工程"
                "settled" -> "今年已结算工程"
                else -> "工程列表"
            }
            try {
                val response = fetchStatsPage(filterType, page = 1)
                if (response.code == 200) {
                    val pageData = response.data
                    if (pageData == null || pageData.list.isEmpty()) {
                        _statsProjectListState.value = ListUiState.Error("暂无符合条件的工程")
                    } else {
                        // settled 分支：前端按 createdAt 过滤今年的工程
                        val filtered = filterByCurrentYear(filterType, pageData.list)
                        _statsHasMore.value = pageData.hasNext
                        if (filtered.isEmpty() && !pageData.hasNext) {
                            // 首页过滤后为空且无更多数据，提示无今年工程
                            _statsProjectListState.value = ListUiState.Error("暂无今年的已结算工程")
                        } else {
                            _statsProjectListState.value = ListUiState.Success(
                                items = filtered,
                                hasMore = pageData.hasNext,
                                page = 1
                            )
                            // 首页过滤后为空但后端还有更多数据时，自动加载下一页（避免页面空让用户手动滑）
                            if (filtered.isEmpty() && pageData.hasNext) {
                                loadMoreStatsProjects()
                            }
                        }
                    }
                } else {
                    _statsProjectListState.value = ListUiState.Error(
                        NetworkErrorHandler.translateServerError(response.msg, "加载工程列表失败")
                    )
                }
            } catch (e: Exception) {
                _statsProjectListState.value = ListUiState.Error(
                    NetworkErrorHandler.translate(e, "加载工程列表失败")
                )
            }
        }
    }

    /**
     * 加载下一页工程列表（滚动到底部时自动触发）
     *
     * 仅当 hasMore=true 且当前未在加载时执行，追加到现有列表末尾。
     * settled 分支会按年份过滤今年的工程，过滤后若本页为空但仍有更多数据，自动继续加载下一页。
     */
    fun loadMoreStatsProjects() {
        // 防抖：正在加载或没有更多数据时直接返回
        if (_statsLoadingMore.value || !_statsHasMore.value) return
        // 仅在 Success 状态下追加（避免初始 Loading/Error 状态触发）
        val current = _statsProjectListState.value
        if (current !is ListUiState.Success) return

        viewModelScope.launch {
            _statsLoadingMore.value = true
            try {
                val filterType = _statsFilterType.value
                val nextPage = current.page + 1
                val response = fetchStatsPage(filterType, page = nextPage)
                if (response.code == 200) {
                    val pageData = response.data
                    if (pageData == null || pageData.list.isEmpty()) {
                        // 后端返回空，认为没有更多
                        _statsHasMore.value = false
                        _statsProjectListState.value = current.copy(hasMore = false)
                    } else {
                        val filtered = filterByCurrentYear(filterType, pageData.list)
                        // 累积新数据到现有列表（去重，避免分页边界重复）
                        val existingIds = current.items.map { it.id }.toHashSet()
                        val merged = current.items + filtered.filter { it.id !in existingIds }
                        _statsHasMore.value = pageData.hasNext
                        _statsProjectListState.value = ListUiState.Success(
                            items = merged,
                            hasMore = pageData.hasNext,
                            page = nextPage
                        )
                        // 本页过滤后为空但仍有更多数据时，自动继续加载下一页
                        if (filtered.isEmpty() && pageData.hasNext) {
                            // 递归加载下一页（_statsLoadingMore 会在本次 return 后被重置）
                            // 但需先释放 loadingMore 标志，避免递归调用被防抖拦截
                            _statsLoadingMore.value = false
                            loadMoreStatsProjects()
                            return@launch
                        }
                    }
                } else {
                    // 加载失败：保留已有数据，提示错误（通过 errorMessage 流，不打断列表）
                    _errorMessage.value = NetworkErrorHandler.translateServerError(response.msg, "加载更多失败")
                    // 失败时也保留 hasMore，允许用户再次触发重试
                }
            } catch (e: Exception) {
                _errorMessage.value = NetworkErrorHandler.translate(e, "加载更多失败")
            } finally {
                _statsLoadingMore.value = false
            }
        }
    }

    /**
     * 拉取指定页的工程数据（按 filterType 构造请求参数）
     * @param filterType settling/settled/其他
     * @param page 页码（从1开始）
     */
    private suspend fun fetchStatsPage(
        filterType: String,
        page: Int
    ): com.salary.core.network.dto.ApiResponse<com.salary.core.network.dto.PageResponse<ProjectDto>> {
        return when (filterType) {
            "settling" -> projectApi.getProjects(
                page = page, size = STATS_PAGE_SIZE,
                status = "completed",
                settlementStatus = "settling"
            )
            "settled" -> projectApi.getProjects(
                page = page, size = STATS_PAGE_SIZE,
                status = "completed",
                settlementStatus = "settled"
            )
            else -> projectApi.getProjects(page = page, size = STATS_PAGE_SIZE)
        }
    }

    /**
     * settled 分支按 createdAt 字段过滤今年的工程
     * 其他分支原样返回
     */
    private fun filterByCurrentYear(filterType: String, items: List<ProjectDto>): List<ProjectDto> {
        if (filterType != "settled") return items
        val currentYear = java.time.LocalDate.now().year
        return items.filter { dto ->
            try {
                dto.createdAt.length >= 4 && dto.createdAt.substring(0, 4).toInt() == currentYear
            } catch (_: Exception) {
                false
            }
        }
    }

    /** 获取计量单位中文名 */
    fun getUnitName(unit: String?): String {
        return when (unit) {
            "length", "perimeter" -> "米"
            "area" -> "㎡"
            else -> unit ?: ""
        }
    }

    /** 格式化数字为两位小数 */
    fun formatNumber(num: Double?): String {
        if (num == null) return "0.00"
        return String.format("%.2f", num)
    }

    /**
     * 导出结算历史Excel文件
     * 对齐Vue前端 exportSettlementToExcel 逻辑
     */
    fun exportSettlementExcel(settlementId: Int, settlementNo: String) {
        viewModelScope.launch {
            _exportingId.value = settlementId
            try {
                val responseBody = withContext(Dispatchers.IO) {
                    salarySheetApi.exportSettlementExcel(settlementId)
                }
                // 保存到下载目录
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val fileName = "${settlementNo}.xlsx"
                val file = File(downloadsDir, fileName)
                withContext(Dispatchers.IO) {
                    FileOutputStream(file).use { outputStream ->
                        responseBody.byteStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
                _successMessage.value = "Excel已导出到下载目录：$fileName"
            } catch (e: Exception) {
                _errorMessage.value = "导出失败：${e.message}"
            } finally {
                _exportingId.value = null
            }
        }
    }

    /**
     * 导出结算单图片（PNG格式）
     * 使用SettlementImageGenerator在本地绘制，样式与程序内显示一致
     * 通过MediaStore保存到图库，相册可见
     *
     * @param settlement 结算历史数据
     */
    fun exportSettlementImage(settlement: SettlementHistoryDto) {
        viewModelScope.launch {
            // 防重复点击：已有导出任务进行中时拒绝新请求
            if (_exportingId.value != null) {
                _errorMessage.value = "正在导出中，请稍后再试"
                return@launch
            }
            _exportingId.value = settlement.settlementId
            val bitmaps = mutableListOf<Bitmap>()
            try {
                // 获取用户名（优先使用昵称）
                val userName = userStorage.nicknameFlow.value.ifBlank {
                    userStorage.getNickname() ?: "未知用户"
                }

                // 获取施工方案列表（从已加载的数据中）
                val plans = _constructionPlans.value

                // 从历史展开状态中解析当前结算单的展开工程ID集合
                // key 格式为 "settlementId-projectId"，筛选当前结算单的已展开工程
                val expandedProjectIds = _expandedHistoryProjects.value
                    .mapNotNull { key ->
                        val parts = key.split("-")
                        if (parts.size == 2 && parts[0] == settlement.settlementId.toString()) {
                            parts[1].toIntOrNull()
                        } else null
                    }
                    .toSet()

                // 在IO线程生成图片（自动分页，返回多页Bitmap列表）
                // 根据当前展开状态导出：展开的工程导出明细，未展开的工程仅导出汇总行
                val pageBitmaps = withContext(Dispatchers.IO) {
                    SettlementImageGenerator.generate(
                        settlement = settlement,
                        constructionPlans = plans,
                        userName = userName,
                        expandedProjectIds = expandedProjectIds,
                        getUnitName = { unit -> getUnitName(unit) },
                        formatNumber = { num -> formatNumber(num) }
                    )
                }
                bitmaps.addAll(pageBitmaps)

                // 通过MediaStore保存到图库（相册可见）
                // 多页时文件名追加 _1、_2 等后缀
                val totalPages = bitmaps.size
                val savedFiles = mutableListOf<String>()
                bitmaps.forEachIndexed { index, bmp ->
                    val fileName = if (totalPages > 1) {
                        "${settlement.settlementNo}_${index + 1}.png"
                    } else {
                        "${settlement.settlementNo}.png"
                    }
                    withContext(Dispatchers.IO) {
                        saveBitmapToGallery(bmp, fileName)
                    }
                    savedFiles.add(fileName)
                }

                _successMessage.value = if (totalPages > 1) {
                    "图片已保存到图库（共${totalPages}页）：${savedFiles.first()} 等"
                } else {
                    "图片已保存到图库：${savedFiles.first()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "导出失败：${e.message}"
            } finally {
                // 确保所有 Bitmap 在任何情况下都被回收，避免 native 内存泄漏
                bitmaps.forEach { it.recycle() }
                _exportingId.value = null
            }
        }
    }

    /**
     * 导出当前选中工程的结算单图片（未结算状态）
     * 将当前选中的工程数据、计算结果、预支数据组装为SettlementHistoryDto，复用SettlementImageGenerator生成图片
     */
    fun exportCurrentSettlementImage() {
        viewModelScope.launch {
            // 防重复点击：已有导出任务进行中时拒绝新请求
            if (_exportingId.value != null) {
                _errorMessage.value = "正在导出中，请稍后再试"
                return@launch
            }
            val selectedIds = _selectedProjectIds.value
            if (selectedIds.isEmpty()) {
                _errorMessage.value = "请先选择要导出的工程"
                return@launch
            }

            // 使用一个特殊的ID标记当前结算单导出中（避免与历史结算单ID冲突）
            val currentExportId = -1
            _exportingId.value = currentExportId
            val bitmaps = mutableListOf<Bitmap>()
            try {
                val userName = userStorage.nicknameFlow.value.ifBlank {
                    userStorage.getNickname() ?: "未知用户"
                }
                val plans = _constructionPlans.value
                val calcResult = _calculationResult.value
                val allProjects = _projectData.value
                // 仅导出选中的工程
                val selectedProjects = allProjects.filter { it.id in selectedIds }

                // 组装为SettlementHistoryDto，复用SettlementImageGenerator
                val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date())
                val currentSettlement = SettlementHistoryDto(
                    settlementId = currentExportId,
                    settlementNo = "当前结算单_${today.replace("-", "")}",
                    startMonth = today,
                    endMonth = today,
                    totalAmount = calcResult.grandTotal,
                    advanceAmount = calcResult.totalAdvance,
                    actualAmount = calcResult.finalTotal,
                    confirmed = false,
                    confirmedAt = null,
                    settledBy = 0,
                    settledByUsername = "",
                    settledByNickname = userName,
                    createdAt = now,
                    projects = selectedProjects,
                    advances = calcResult.advances,
                    planTotals = calcResult.planTotals,
                    grandTotal = calcResult.grandTotal,
                    totalAdvance = calcResult.totalAdvance,
                    finalTotal = calcResult.finalTotal
                )

                // 在IO线程生成图片（自动分页，返回多页Bitmap列表）
                // 根据当前展开状态导出：展开的工程导出明细，未展开的工程仅导出汇总行
                val expandedProjectIds = _expandedProjects.value
                val pageBitmaps = withContext(Dispatchers.IO) {
                    SettlementImageGenerator.generate(
                        settlement = currentSettlement,
                        constructionPlans = plans,
                        userName = userName,
                        expandedProjectIds = expandedProjectIds,
                        getUnitName = { unit -> getUnitName(unit) },
                        formatNumber = { num -> formatNumber(num) }
                    )
                }
                bitmaps.addAll(pageBitmaps)

                // 通过MediaStore保存到图库
                // 多页时文件名追加 _1、_2 等后缀
                val totalPages = bitmaps.size
                val savedFiles = mutableListOf<String>()
                bitmaps.forEachIndexed { index, bmp ->
                    val fileName = if (totalPages > 1) {
                        "${currentSettlement.settlementNo}_${index + 1}.png"
                    } else {
                        "${currentSettlement.settlementNo}.png"
                    }
                    withContext(Dispatchers.IO) {
                        saveBitmapToGallery(bmp, fileName)
                    }
                    savedFiles.add(fileName)
                }

                _successMessage.value = if (totalPages > 1) {
                    "图片已保存到图库（共${totalPages}页）：${savedFiles.first()} 等"
                } else {
                    "图片已保存到图库：${savedFiles.first()}"
                }
            } catch (e: Exception) {
                _errorMessage.value = "导出失败：${e.message}"
            } finally {
                // 确保所有 Bitmap 在任何情况下都被回收，避免 native 内存泄漏
                bitmaps.forEach { it.recycle() }
                _exportingId.value = null
            }
        }
    }

    /**
     * 通过MediaStore将Bitmap保存到图库（Pictures目录）
     * 兼容Android 10+（API 29+）的分区存储和旧版本
     *
     * @param bitmap 要保存的图片
     * @param fileName 文件名（含扩展名）
     * @return 保存后的Uri
     */
    private fun saveBitmapToGallery(bitmap: Bitmap, fileName: String): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            // 指定存储路径：Pictures/三人行结算单/
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/三人行结算单")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw RuntimeException("无法创建图库文件")

        resolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        } ?: throw RuntimeException("无法打开输出流")

        // Android 10+需要更新IS_PENDING为0，表示文件已就绪
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }

        return uri
    }
}

/**
 * 结算统计摘要
 * 数据来源：
 * - 待结算工程/预支：/v1/salary-sheet/projects（个人维度）
 * - 今年工程量：/v1/projects?settlementStatus=settled（工程总额）
 * - 月均收入：/v1/salary-sheet/settled-projects（个人分摊金额）
 */
data class SettlementSummary(
    // ===== 待结算工程（份数工程级，金额个人级） =====
    /** 待结算工程份数（settling状态，工程级） */
    val totalProjects: Int = 0,
    /** 应收总额（个人分摊金额，个人级） */
    val grandTotal: Double = 0.0,
    // ===== 预支金额（个人维度） =====
    /** 未结算预支总额 */
    val totalAdvance: Double = 0.0,
    /** 未结算预支份数 */
    val advanceCount: Int = 0,
    // ===== 实付总额（个人维度，保留用于其他展示） =====
    /** 实付总额 */
    val finalTotal: Double = 0.0,
    // ===== 今年工程量（所有状态，工程级总额） =====
    /** 今年创建的所有工程份数（不限结算状态） */
    val settledProjectCount: Int = 0,
    /** 今年创建的所有工程总额（工程级 total_amount 合计） */
    val settledProjectTotalAmount: Double = 0.0,
    // ===== 月均收入（份数工程级，金额个人级） =====
    /** 今年已结算工程个人分摊总额 */
    val settledUserAmount: Double = 0.0,
    /** 月均份数（工程级整数，今年已结算工程总数） */
    val monthlyAvgCount: Int = 0,
    /** 月均金额（个人级，今年个人已结算工资 / 当前月份） */
    val monthlyAvgAmount: Double = 0.0
)
