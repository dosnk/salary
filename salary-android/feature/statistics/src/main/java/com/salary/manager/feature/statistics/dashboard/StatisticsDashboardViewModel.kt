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

    /** 用户昵称（从UserStorage响应式获取） */
    val userNickname: StateFlow<String> = userStorage.nicknameFlow

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
                    sheetJob.join()
                    historyJob.join()
                }
                _state.value = UiState.Success(Unit)
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
                kotlinx.coroutines.coroutineScope {
                    val sheetJob = launch { loadSalarySheetData() }
                    val historyJob = launch { loadSettlementHistory() }
                    sheetJob.join()
                    historyJob.join()
                }
                // 静默刷新成功后，仅当之前是Error状态时才更新为Success
                if (_state.value is UiState.Error) {
                    _state.value = UiState.Success(Unit)
                }
            } catch (_: Exception) {
                // 静默模式忽略错误，不覆盖已有状态
            }
        }
    }

    /**
     * 加载结算单数据（施工方案 + 工程列表 + 已结算工程）
     * 同时从工程数据中提取4个卡片的统计信息：
     * - 待结算工程：settling状态工程份数+个人应收总额
     * - 预支金额：未结算预支份数+总额（个人维度）
     * - 今年工量：今年已结算工程份数+工程总额（非个人分摊）
     * - 月均工资：今年已结算工程月均份数+月均金额（个人分摊）
     */
    private suspend fun loadSalarySheetData() {
        try {
            // 并行加载施工方案、工程数据、已结算工程数据
            kotlinx.coroutines.coroutineScope {
                val plansJob = launch {
                    try {
                        val plansResponse = salarySheetApi.getConstructionPlans()
                        if (plansResponse.code == 200 && plansResponse.data != null) {
                            _constructionPlans.value = plansResponse.data!!
                        }
                    } catch (e: Exception) {
                        // 记录错误但不中断其他数据加载
                        _errorMessage.value = NetworkErrorHandler.translate(e, "加载施工方案失败")
                    }
                }
                val projectsJob = launch {
                    try {
                        val projectsResponse = salarySheetApi.getProjects()
                        if (projectsResponse.code == 200 && projectsResponse.data != null) {
                            val data = projectsResponse.data!!
                            _projectData.value = data.projects
                            // 更新待结算工程和预支金额数据（个人维度）
                            _settlementSummary.value = _settlementSummary.value.copy(
                                totalProjects = data.projects.size,
                                grandTotal = data.grandTotal,
                                totalAdvance = data.totalAdvance,
                                advanceCount = data.advances.size,
                                finalTotal = data.finalTotal
                            )
                        }
                    } catch (e: Exception) {
                        _errorMessage.value = NetworkErrorHandler.translate(e, "加载工程数据失败")
                    }
                }
                // 加载已结算工程数据（用于今年工量和月均工资）
                val settledJob = launch {
                    try {
                        loadSettledProjectStats()
                    } catch (e: Exception) {
                        _errorMessage.value = NetworkErrorHandler.translate(e, "加载已结算工程数据失败")
                    }
                }
                plansJob.join()
                projectsJob.join()
                settledJob.join()
            }
        } catch (e: Exception) {
            _errorMessage.value = NetworkErrorHandler.translate(e, "加载数据失败")
        }
    }

    /**
     * 加载已结算工程统计（用于"今年工量"和"月均工资"卡片）
     * - 今年工量：通过 projectApi 获取已结算工程列表，累加 totalAmount（工程总额）
     * - 月均工资：通过 salarySheetApi.getSettledProjects 获取个人分摊金额，按月平均
     */
    private suspend fun loadSettledProjectStats() {
        val currentYear = java.time.LocalDate.now().year
        val currentMonth = java.time.LocalDate.now().monthValue

        // ===== 今年工量：工程总额（非个人分摊） =====
        var settledCount = 0
        var settledTotalAmount = 0.0
        try {
            val response = projectApi.getProjects(
                page = 1,
                size = 200,
                status = "completed",
                settlementStatus = "settled"
            )
            if (response.code == 200 && response.data != null) {
                val pageData = response.data!!
                // 按 createdAt 年份过滤出今年的已结算工程
                val thisYearProjects = pageData.list.filter { dto ->
                    try {
                        dto.createdAt.substring(0, 4).toInt() == currentYear
                    } catch (_: Exception) {
                        false
                    }
                }
                settledCount = thisYearProjects.size
                settledTotalAmount = thisYearProjects.sumOf { it.totalAmount }
            }
        } catch (_: Exception) {
            // 静默处理，不影响其他数据
        }

        // ===== 月均工资：个人分摊金额 =====
        var settledUserAmount = 0.0
        try {
            val settledResponse = salarySheetApi.getSettledProjects()
            if (settledResponse.code == 200 && settledResponse.data != null) {
                val settledProjects = settledResponse.data!!
                // 按 createdAt 年份过滤出今年的已结算工程
                val thisYearSettled = settledProjects.filter { project ->
                    try {
                        project.createdAt.substring(0, 4).toInt() == currentYear
                    } catch (_: Exception) {
                        false
                    }
                }
                // 累加个人分摊金额（subprojects.userAmount）
                settledUserAmount = thisYearSettled.sumOf { project ->
                    project.subprojects.sumOf { it.userAmount }
                }
            }
        } catch (_: Exception) {
            // 静默处理
        }

        // 月均计算：总额 / 当前月份数（至少为1避免除零）
        val monthDivisor = currentMonth.coerceAtLeast(1)
        _settlementSummary.value = _settlementSummary.value.copy(
            settledProjectCount = settledCount,
            settledProjectTotalAmount = settledTotalAmount,
            settledUserAmount = settledUserAmount,
            monthlyAvgCount = settledCount.toDouble() / monthDivisor,
            monthlyAvgAmount = settledUserAmount / monthDivisor
        )
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
     * 加载统计卡片对应的工程列表
     * @param filterType 筛选类型：settling/advance/settled
     *
     * 卡片与filterType对应关系：
     * - "待结算工程"卡片 → settling → 查询已完工且结算状态为settling的工程（个人维度）
     * - "预支金额"卡片 → advance → 切换到预支Tab（不加载工程列表）
     * - "今年工量"卡片 → settled → 查询今年已结算工程（工程总额）
     * - "月均工资"卡片 → settled → 查询今年已结算工程（与今年工量共用）
     */
    fun loadStatsProjectList(filterType: String) {
        viewModelScope.launch {
            _statsProjectListState.value = ListUiState.Loading
            _statsPopupTitle.value = when (filterType) {
                "settling" -> "待结算工程"
                "settled" -> "今年已结算工程"
                else -> "工程列表"
            }
            try {
                val now = java.time.LocalDate.now()
                val response = when (filterType) {
                    "settling" -> projectApi.getProjects(
                        page = 1, size = 50,
                        status = "completed",
                        settlementStatus = "settling"
                    )
                    "settled" -> projectApi.getProjects(
                        page = 1, size = 200,
                        status = "completed",
                        settlementStatus = "settled"
                    )
                    else -> projectApi.getProjects(page = 1, size = 50)
                }
                if (response.code == 200) {
                    val pageData = response.data
                    if (pageData == null || pageData.list.isEmpty()) {
                        _statsProjectListState.value = ListUiState.Error("暂无符合条件的工程")
                    } else {
                        // 已结算工程按年份过滤今年的
                        val filtered = if (filterType == "settled") {
                            pageData.list.filter { dto ->
                                try {
                                    dto.createdAt.substring(0, 4).toInt() == now.year
                                } catch (_: Exception) {
                                    false
                                }
                            }
                        } else {
                            pageData.list
                        }
                        if (filtered.isEmpty()) {
                            _statsProjectListState.value = ListUiState.Error("暂无今年的已结算工程")
                        } else {
                            _statsProjectListState.value = ListUiState.Success(
                                items = filtered,
                                hasMore = false,
                                page = 1
                            )
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
            _exportingId.value = settlement.settlementId
            try {
                // 获取用户名（优先使用昵称）
                val userName = userStorage.nicknameFlow.value.ifBlank {
                    userStorage.getNickname() ?: "未知用户"
                }

                // 获取施工方案列表（从已加载的数据中）
                val plans = _constructionPlans.value

                // 在IO线程生成图片
                val bitmap = withContext(Dispatchers.IO) {
                    SettlementImageGenerator.generate(
                        settlement = settlement,
                        constructionPlans = plans,
                        userName = userName,
                        getUnitName = { unit -> getUnitName(unit) },
                        formatNumber = { num -> formatNumber(num) }
                    )
                }

                // 通过MediaStore保存到图库（相册可见）
                val fileName = "${settlement.settlementNo}.png"
                val savedUri = withContext(Dispatchers.IO) {
                    saveBitmapToGallery(bitmap, fileName)
                }
                bitmap.recycle()

                _successMessage.value = "图片已保存到图库：$fileName"
            } catch (e: Exception) {
                _errorMessage.value = "导出失败：${e.message}"
            } finally {
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
            val selectedIds = _selectedProjectIds.value
            if (selectedIds.isEmpty()) {
                _errorMessage.value = "请先选择要导出的工程"
                return@launch
            }

            // 使用一个特殊的ID标记当前结算单导出中（避免与历史结算单ID冲突）
            val currentExportId = -1
            _exportingId.value = currentExportId
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

                // 在IO线程生成图片
                val bitmap = withContext(Dispatchers.IO) {
                    SettlementImageGenerator.generate(
                        settlement = currentSettlement,
                        constructionPlans = plans,
                        userName = userName,
                        getUnitName = { unit -> getUnitName(unit) },
                        formatNumber = { num -> formatNumber(num) }
                    )
                }

                // 通过MediaStore保存到图库
                val fileName = "${currentSettlement.settlementNo}.png"
                val savedUri = withContext(Dispatchers.IO) {
                    saveBitmapToGallery(bitmap, fileName)
                }
                bitmap.recycle()

                _successMessage.value = "图片已保存到图库：$fileName"
            } catch (e: Exception) {
                _errorMessage.value = "导出失败：${e.message}"
            } finally {
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
 * - 今年工量：/v1/projects?settlementStatus=settled（工程总额）
 * - 月均工资：/v1/salary-sheet/settled-projects（个人分摊金额）
 */
data class SettlementSummary(
    // ===== 待结算工程（个人维度） =====
    /** 待结算工程份数（settling状态） */
    val totalProjects: Int = 0,
    /** 应收总额（个人分摊金额） */
    val grandTotal: Double = 0.0,
    // ===== 预支金额（个人维度） =====
    /** 未结算预支总额 */
    val totalAdvance: Double = 0.0,
    /** 未结算预支份数 */
    val advanceCount: Int = 0,
    // ===== 实付总额（个人维度，保留用于其他展示） =====
    /** 实付总额 */
    val finalTotal: Double = 0.0,
    // ===== 今年工量（工程总额，非个人分摊） =====
    /** 今年已结算工程份数 */
    val settledProjectCount: Int = 0,
    /** 今年已结算工程总额（工程原始总额，非个人分摊） */
    val settledProjectTotalAmount: Double = 0.0,
    // ===== 月均工资（个人维度） =====
    /** 今年已结算工程个人分摊总额 */
    val settledUserAmount: Double = 0.0,
    /** 月均份数 */
    val monthlyAvgCount: Double = 0.0,
    /** 月均金额（个人分摊） */
    val monthlyAvgAmount: Double = 0.0
)
