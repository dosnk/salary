package com.salary.manager.feature.statistics.dashboard

import android.content.Context
import android.os.Environment
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
import com.salary.core.network.dto.ProjectDto
import com.salary.core.ui.state.ListUiState
import com.salary.core.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * 统计面板ViewModel - 管理结算统计、结算单数据、结算历史和工程选择逻辑
 *
 * 结算统计数据从 /v1/salary-sheet/projects 返回的数据中提取，
 * 不再调用不存在的 /v1/statistics/settlements 接口
 */
@HiltViewModel
class StatisticsDashboardViewModel @Inject constructor(
    private val salarySheetApi: SalarySheetApi,
    private val projectApi: ProjectApi,
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

    /** 用户昵称 */
    private val _userNickname = MutableStateFlow("")
    val userNickname: StateFlow<String> = _userNickname.asStateFlow()

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

    /** 正在导出的结算单ID，null表示未在导出 */
    private val _exportingId = MutableStateFlow<Int?>(null)
    val exportingId: StateFlow<Int?> = _exportingId.asStateFlow()

    init {
        loadAllData()
    }

    /** 加载所有数据 */
    fun loadAllData() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                // 并行加载所有数据
                kotlinx.coroutines.coroutineScope {
                    val sheetJob = launch { loadSalarySheetData() }
                    val historyJob = launch { loadSettlementHistory() }
                    val userJob = launch { loadUserNickname() }
                    sheetJob.join()
                    historyJob.join()
                    userJob.join()
                }
                _state.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _state.value = UiState.Error(NetworkErrorHandler.translate(e, "加载统计数据失败"))
            }
        }
    }

    /**
     * 加载结算单数据（施工方案 + 工程列表）
     * 同时从工程数据中提取结算统计信息
     */
    private suspend fun loadSalarySheetData() {
        try {
            // 并行加载施工方案和工程数据
            kotlinx.coroutines.coroutineScope {
                val plansJob = launch {
                    try {
                        val plansResponse = salarySheetApi.getConstructionPlans()
                        if (plansResponse.code == 200 && plansResponse.data != null) {
                            _constructionPlans.value = plansResponse.data!!
                        }
                    } catch (_: Exception) { }
                }
                val projectsJob = launch {
                    try {
                        val projectsResponse = salarySheetApi.getProjects()
                        if (projectsResponse.code == 200 && projectsResponse.data != null) {
                            val data = projectsResponse.data!!
                            _projectData.value = data.projects
                            // 从工程数据中提取结算统计
                            _settlementSummary.value = SettlementSummary(
                                totalProjects = data.projects.size,
                                grandTotal = data.grandTotal,
                                totalAdvance = data.totalAdvance,
                                finalTotal = data.finalTotal
                            )
                        }
                    } catch (_: Exception) { }
                }
                plansJob.join()
                projectsJob.join()
            }
        } catch (_: Exception) { }
    }

    /** 加载结算历史 */
    private suspend fun loadSettlementHistory() {
        try {
            val response = salarySheetApi.getSettlementHistory()
            if (response.code == 200 && response.data != null) {
                _settlementHistory.value = response.data!!
            }
        } catch (_: Exception) { }
    }

    /** 加载用户昵称 */
    private suspend fun loadUserNickname() {
        try {
            val nickname = userStorage.getNickname()
            _userNickname.value = nickname ?: ""
        } catch (_: Exception) { }
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
                _errorMessage.value = "计算失败：${e.message}"
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
                    _errorMessage.value = response.msg.ifEmpty { "结算失败" }
                }
            } catch (e: Exception) {
                _errorMessage.value = "结算失败：${e.message}"
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
     * 加载统计卡片对应的工程列表
     * 对齐Vue前端 showConstructingProjects/showSettlingProjects/showSettledProjects/showThisMonthProjects 逻辑
     * @param filterType 筛选类型：unsettled/settling/settled/thisMonth
     */
    fun loadStatsProjectList(filterType: String) {
        viewModelScope.launch {
            _statsProjectListState.value = ListUiState.Loading
            _statsPopupTitle.value = when (filterType) {
                "unsettled" -> "未结算工程"
                "settling" -> "统计中工程"
                "settled" -> "今年结算工程"
                "thisMonth" -> "本月工程"
                else -> "工程列表"
            }
            try {
                val now = java.time.LocalDate.now()
                val params = when (filterType) {
                    "unsettled" -> mapOf("status" to "constructing", "settlementStatus" to "unsettled")
                    "settling" -> mapOf("status" to "completed", "settlementStatus" to "settling")
                    "settled" -> mapOf("status" to "completed", "settlementStatus" to "settled", "year" to now.year.toString())
                    "thisMonth" -> mapOf("yearMonth" to "${now.year}-${String.format("%02d", now.monthValue)}")
                    else -> emptyMap()
                }
                val response = projectApi.getProjects(
                    page = 1,
                    size = 50,
                    keyword = null,
                    status = params["status"],
                    settlementStatus = params["settlementStatus"],
                    yearMonth = params["yearMonth"]
                )
                if (response.code == 200) {
                    val pageData = response.data
                    if (pageData == null || pageData.list.isEmpty()) {
                        _statsProjectListState.value = ListUiState.Error("暂无符合条件的工程")
                    } else {
                        _statsProjectListState.value = ListUiState.Success(
                            items = pageData.list,
                            hasMore = pageData.hasNext,
                            page = 1
                        )
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
}

/**
 * 结算统计摘要（从 /v1/salary-sheet/projects 数据中提取）
 * 替代原来不存在的 /v1/statistics/settlements 接口
 */
data class SettlementSummary(
    /** 未结算工程数 */
    val totalProjects: Int = 0,
    /** 工程总额 */
    val grandTotal: Double = 0.0,
    /** 预支总额 */
    val totalAdvance: Double = 0.0,
    /** 实付总额 */
    val finalTotal: Double = 0.0
)
