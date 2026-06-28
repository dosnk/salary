package com.salary.manager.feature.home.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.common.util.NetworkUtil
import com.salary.core.common.util.AmountFormatter
import com.salary.core.data.local.DashboardCache
import com.salary.core.data.local.UserStorage
import com.salary.core.network.api.DictionaryApi
import com.salary.core.network.api.DictionaryItemDto
import com.salary.core.network.api.MessageApi
import com.salary.core.network.api.ProjectApi
import com.salary.core.network.api.UserApi
import com.salary.core.network.api.UserDto
import com.salary.core.network.dto.ConstructorItem
import com.salary.core.network.dto.CreateProjectRequest
import com.salary.core.network.dto.ProjectDetailDto
import com.salary.core.network.dto.SubprojectDto
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 施工方案详情（含单价和计量单位）
 */
data class SchemeInfo(
    val id: Int,
    val name: String,
    /** 计量单位：area=面积, perimeter=周长, length=长度 */
    val unit: String = "area",
    /** 单价 */
    val price: Double = 0.0
)

/**
 * 工程历史UI模型
 */
data class ProjectHistoryUiModel(
    val id: Int,
    val name: String,
    val totalAmount: String,
    val workerNames: List<String>,
    val fileCount: Int,
    val createdAt: String,
    val updatedAt: String,
    val subprojects: List<SubprojectUiModel>
)

/**
 * 子项目UI模型
 */
data class SubprojectUiModel(
    val id: Int,
    val spaceTypeName: String,
    val constructionPlanName: String,
    val length: Double,
    val width: Double,
    val quantity: Double,
    val amount: String,
    /** 计量单位 */
    val unit: String = "area"
)

/**
 * 工作台UI状态
 */
data class DashboardUiState(
    /** 是否正在加载初始数据 */
    val isLoading: Boolean = false,
    /** 是否正在保存工程 */
    val isSaving: Boolean = false,
    /** 错误消息 */
    val errorMessage: String? = null,
    /** 成功消息 */
    val successMessage: String? = null,

    // ===== 字典数据 =====
    /** 空间类型列表 */
    val spaceTypes: List<DictionaryItemDto> = emptyList(),
    /** 施工方案列表（含单价和计量单位） */
    val constructionPlans: List<SchemeInfo> = emptyList(),
    /** 施工人员列表 */
    val constructors: List<UserDto> = emptyList(),

    // ===== 表单数据 =====
    /** 客户地址 */
    val customerAddress: String = "",
    /** 选中的空间类型名称 */
    val selectedSpaceType: String = "",
    /** 选中的施工方案名称 */
    val selectedScheme: String = "",
    /** 长度（厘米） */
    val lengthCm: String = "",
    /** 宽度（厘米） */
    val widthCm: String = "",
    /** 分配方式：average=平均, work_days=按工日 */
    val salaryDistribution: String = "average",
    /** 已选中的施工人员ID集合 */
    val selectedConstructorIds: Set<Int> = emptySet(),
    /** 工程备注 */
    val remark: String = "",

    // ===== 计算预览 =====
    /** 单价 */
    val unitPrice: Double = 0.0,
    /** 数量 */
    val quantity: Double = 0.0,
    /** 总金额 */
    val totalAmount: Double = 0.0,
    /** 计算公式文本 */
    val calculationFormula: String = "",

    // ===== 工程历史 =====
    /** 工程历史列表 */
    val projects: List<ProjectHistoryUiModel> = emptyList(),
    /** 选中的年月（格式：yyyy-MM） */
    val selectedYearMonth: String = SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date()),
    /** 是否正在加载工程历史 */
    val isLoadingProjects: Boolean = false,

    // ===== 用户信息 =====
    /** 用户昵称 */
    val userNickname: String = "",
    /** 未读消息数 */
    val unreadCount: Int = 0
)

/**
 * 工作台ViewModel
 * 管理工作台页面的所有状态和业务逻辑，包括：
 * - 字典数据加载（空间类型、施工方案、施工人员）
 * - 工程创建表单管理
 * - 计算预览公式
 * - 工程历史加载
 * - 消息未读数
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dictionaryApi: DictionaryApi,
    private val userApi: UserApi,
    private val projectApi: ProjectApi,
    private val messageApi: MessageApi,
    private val userStorage: UserStorage,
    private val dashboardCache: DashboardCache,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** 数字格式化工具 */
    private val numberFormat = DecimalFormat("0.00")

    /** 客户地址与施工人员的映射缓存（内存副本，定期同步到DataStore） */
    private var addressConstructorMap: Map<String, List<Int>> = emptyMap()

    /** 防抖保存表单的Job */
    private var saveFormJob: kotlinx.coroutines.Job? = null

    init {
        loadInitialData()
    }

    /**
     * 加载初始数据：字典数据、用户信息、未读消息数、工程历史
     * 同时从缓存恢复地址映射和表单数据
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                coroutineScope {
                    // 并行加载字典数据和用户信息
                    val spaceTypesDeferred = async { loadSpaceTypes() }
                    val plansDeferred = async { loadConstructionPlans() }
                    val constructorsDeferred = async { loadConstructors() }
                    val userInfoDeferred = async { loadUserInfo() }
                    val unreadDeferred = async { loadUnreadCount() }

                    // 等待所有加载完成
                    spaceTypesDeferred.await()
                    plansDeferred.await()
                    constructorsDeferred.await()
                    userInfoDeferred.await()
                    unreadDeferred.await()
                }

                // 从缓存恢复地址→施工人员映射
                addressConstructorMap = dashboardCache.loadAddressMap()

                // 从缓存恢复表单数据
                restoreFormCache()

                _uiState.value = _uiState.value.copy(
                    isLoading = false
                )

                // 加载工程历史
                loadProjects()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = NetworkErrorHandler.translate(e, "加载数据失败")
                )
            }
        }
    }

    /**
     * 从缓存恢复表单数据
     */
    private suspend fun restoreFormCache() {
        try {
            val cache = dashboardCache.loadFormCache() ?: return
            _uiState.value = _uiState.value.copy(
                customerAddress = cache.customerAddress,
                selectedSpaceType = cache.selectedSpaceType,
                selectedScheme = cache.selectedScheme,
                lengthCm = cache.lengthCm,
                widthCm = cache.widthCm,
                salaryDistribution = cache.salaryDistribution,
                selectedConstructorIds = cache.selectedConstructorIds.toSet(),
                remark = cache.remark
            )
            // 恢复施工方案对应的单价
            if (cache.selectedScheme.isNotBlank()) {
                val scheme = _uiState.value.constructionPlans.find { it.name == cache.selectedScheme }
                if (scheme != null) {
                    _uiState.value = _uiState.value.copy(unitPrice = scheme.price)
                    recalculate()
                }
            }
        } catch (_: Exception) {
            // 静默处理
        }
    }

    /**
     * 加载空间类型列表
     */
    private suspend fun loadSpaceTypes() {
        try {
            val response = dictionaryApi.getSpaceTypes()
            if (response.code == 200) {
                _uiState.value = _uiState.value.copy(
                    spaceTypes = response.data ?: emptyList()
                )
            }
        } catch (_: Exception) {
            // 静默处理，不影响其他数据加载
        }
    }

    /**
     * 加载施工方案列表（含单价和计量单位）
     */
    private suspend fun loadConstructionPlans() {
        try {
            val response = dictionaryApi.getConstructionPlans()
            if (response.code == 200) {
                val plans = response.data ?: emptyList()
                val schemeInfos = plans.map { dto ->
                    SchemeInfo(
                        id = dto.id,
                        name = dto.name,
                        unit = dto.unit ?: "area",
                        price = dto.price ?: 0.0
                    )
                }
                _uiState.value = _uiState.value.copy(
                    constructionPlans = schemeInfos
                )
            }
        } catch (_: Exception) {
            // 静默处理
        }
    }

    /**
     * 加载施工人员列表
     */
    private suspend fun loadConstructors() {
        try {
            val response = userApi.getConstructors()
            if (response.code == 200) {
                _uiState.value = _uiState.value.copy(
                    constructors = response.data ?: emptyList()
                )
            }
        } catch (_: Exception) {
            // 静默处理
        }
    }

    /**
     * 加载用户信息（昵称）
     */
    private suspend fun loadUserInfo() {
        try {
            val nickname = userStorage.getNickname() ?: ""
            _uiState.value = _uiState.value.copy(userNickname = nickname)
        } catch (_: Exception) {
            // 静默处理
        }
    }

    /**
     * 加载未读消息数
     */
    private suspend fun loadUnreadCount() {
        try {
            val response = messageApi.getUnreadCount()
            if (response.code == 200) {
                _uiState.value = _uiState.value.copy(
                    unreadCount = response.data?.count ?: 0
                )
            }
        } catch (_: Exception) {
            // 静默处理
        }
    }

    /**
     * 加载工程历史列表（按月筛选）
     * 采用N+1查询：先获取列表，再逐个获取详情（子项目和附件）
     */
    fun loadProjects() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingProjects = true)

            try {
                val response = projectApi.getProjects(
                    page = 1,
                    size = 50,
                    yearMonth = _uiState.value.selectedYearMonth
                )

                if (response.code == 200) {
                    val pageData = response.data
                    if (pageData == null) {
                        _uiState.value = _uiState.value.copy(
                            projects = emptyList(),
                            isLoadingProjects = false
                        )
                        return@launch
                    }

                    // 并行获取每个工程的详情，失败时回退到列表数据
                    val detailResults = coroutineScope {
                        pageData.list.map { project ->
                            async {
                                loadProjectDetailSafely(project.id)
                                    ?: ProjectHistoryUiModel(
                                        id = project.id,
                                        name = project.name,
                                        totalAmount = String.format("%.2f", project.totalAmount),
                                        workerNames = project.workers.map { it.nickname },
                                        fileCount = project.filesCount,
                                        createdAt = project.createdAt,
                                        updatedAt = project.updatedAt,
                                        subprojects = emptyList()
                                    )
                            }
                        }.map { it.await() }
                    }

                    _uiState.value = _uiState.value.copy(
                        projects = detailResults.filterNotNull(),
                        isLoadingProjects = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        projects = emptyList(),
                        isLoadingProjects = false,
                        errorMessage = NetworkErrorHandler.translateServerError(response.msg, "加载工程历史失败")
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    projects = emptyList(),
                    isLoadingProjects = false,
                    errorMessage = NetworkErrorHandler.translate(e, "加载工程历史失败")
                )
            }
        }
    }

    /**
     * 安全加载工程详情，失败时返回null
     */
    private suspend fun loadProjectDetailSafely(projectId: Int): ProjectHistoryUiModel? {
        return try {
            val detailResponse = projectApi.getProjectDetail(projectId)
            val detail = detailResponse.data
            if (detailResponse.code == 200 && detail != null) {
                mapToHistoryModel(detail)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 将工程详情DTO映射为UI模型
     */
    private fun mapToHistoryModel(detail: ProjectDetailDto): ProjectHistoryUiModel {
        return ProjectHistoryUiModel(
            id = detail.id,
            name = detail.name,
            totalAmount = String.format("%.2f", detail.totalAmount),
            workerNames = detail.workers.map { it.nickname },
            fileCount = detail.files.size,
            createdAt = detail.createdAt,
            updatedAt = detail.updatedAt,
            subprojects = detail.subprojects.map { mapToSubprojectModel(it) }
        )
    }

    /**
     * 将子项目DTO映射为UI模型
     */
    private fun mapToSubprojectModel(dto: SubprojectDto): SubprojectUiModel {
        // 根据施工方案名称查找计量单位
        val unit = _uiState.value.constructionPlans
            .find { it.name == dto.constructionPlanName }?.unit ?: "area"

        return SubprojectUiModel(
            id = dto.id,
            spaceTypeName = dto.spaceTypeName,
            constructionPlanName = dto.constructionPlanName,
            length = dto.length ?: 0.0,
            width = dto.width ?: 0.0,
            quantity = dto.quantity ?: 0.0,
            amount = AmountFormatter.format(dto.amount),
            unit = unit
        )
    }

    // ===== 表单操作 =====

    /**
     * 更新客户地址
     * 参考Vue前端 handleProjectNameInput 逻辑：
     * 1. 保存旧地址的施工人员到缓存
     * 2. 从缓存恢复新地址对应的施工人员
     * 3. 保存地址映射到DataStore
     */
    fun updateCustomerAddress(newAddress: String) {
        val oldAddress = _uiState.value.customerAddress
        _uiState.value = _uiState.value.copy(customerAddress = newAddress)

        // 只有当地址真正改变时才更新施工人员关联
        if (oldAddress.isNotBlank() && oldAddress != newAddress) {
            // 1. 保存当前施工人员到旧地址的缓存
            val currentIds = _uiState.value.selectedConstructorIds.toList()
            if (currentIds.isNotEmpty()) {
                addressConstructorMap = addressConstructorMap.toMutableMap().apply {
                    this[oldAddress] = currentIds
                }
            }

            // 2. 从缓存中加载新地址对应的施工人员
            val newIds = addressConstructorMap[newAddress]
            if (newIds != null) {
                // 新地址有缓存，恢复施工人员
                _uiState.value = _uiState.value.copy(selectedConstructorIds = newIds.toSet())
            }
            // 如果新地址不在缓存中，不清空施工人员，让用户自己选择（与Vue一致）

            // 3. 保存新地址和当前施工人员到缓存
            addressConstructorMap = addressConstructorMap.toMutableMap().apply {
                this[newAddress] = _uiState.value.selectedConstructorIds.toList()
            }

            // 4. 异步持久化到DataStore
            saveAddressMapAsync()
        }

        // 防抖保存表单数据
        saveFormDebounced()
    }

    /**
     * 选择空间类型
     */
    fun selectSpaceType(spaceType: String) {
        _uiState.value = _uiState.value.copy(selectedSpaceType = spaceType)
    }

    /**
     * 选择施工方案，同时更新单价和重新计算
     */
    fun selectScheme(schemeName: String) {
        val scheme = _uiState.value.constructionPlans.find { it.name == schemeName }
        _uiState.value = _uiState.value.copy(
            selectedScheme = schemeName,
            unitPrice = scheme?.price ?: 0.0
        )
        recalculate()
    }

    /**
     * 更新长度（厘米）
     */
    fun updateLength(length: String) {
        _uiState.value = _uiState.value.copy(lengthCm = length)
        recalculate()
    }

    /**
     * 更新宽度（厘米）
     */
    fun updateWidth(width: String) {
        _uiState.value = _uiState.value.copy(widthCm = width)
        recalculate()
    }

    /**
     * 更新分配方式
     */
    fun updateSalaryDistribution(distribution: String) {
        _uiState.value = _uiState.value.copy(salaryDistribution = distribution)
    }

    /**
     * 切换施工人员选中状态
     * 参考Vue前端：施工人员变化时立即保存到当前地址的缓存
     */
    fun toggleConstructor(userId: Int) {
        val current = _uiState.value.selectedConstructorIds
        val newSet = if (current.contains(userId)) {
            current - userId
        } else {
            current + userId
        }
        _uiState.value = _uiState.value.copy(selectedConstructorIds = newSet)

        // 如果当前有地址，保存施工人员到当前地址的缓存
        val address = _uiState.value.customerAddress
        if (address.isNotBlank()) {
            addressConstructorMap = addressConstructorMap.toMutableMap().apply {
                this[address] = newSet.toList()
            }
            saveAddressMapAsync()
        }

        // 防抖保存表单数据
        saveFormDebounced()
    }

    /**
     * 更新工程备注
     */
    fun updateRemark(remark: String) {
        _uiState.value = _uiState.value.copy(remark = remark)
    }

    /**
     * 选择年月并重新加载工程历史
     */
    fun selectYearMonth(yearMonth: String) {
        _uiState.value = _uiState.value.copy(selectedYearMonth = yearMonth)
        loadProjects()
    }

    /**
     * 重新计算预览公式
     * 根据施工方案的计量单位计算数量和金额
     */
    private fun recalculate() {
        val state = _uiState.value
        val scheme = state.constructionPlans.find { it.name == state.selectedScheme }
        val lengthCm = state.lengthCm.toDoubleOrNull() ?: 0.0
        val widthCm = state.widthCm.toDoubleOrNull() ?: 0.0
        val unitPrice = state.unitPrice

        // 根据计量单位计算数量
        val quantity = when (scheme?.unit) {
            "area" -> (lengthCm * widthCm) / 10000       // cm² → m²
            "perimeter" -> (lengthCm + widthCm) * 2 / 100 // cm → m
            "length" -> lengthCm / 100                     // cm → m
            else -> (lengthCm * widthCm) / 10000          // 默认按面积
        }

        val totalAmount = quantity * unitPrice

        // 生成计算公式文本
        val formula = buildCalculationFormula(scheme, quantity, unitPrice, totalAmount, lengthCm, widthCm)

        _uiState.value = state.copy(
            quantity = quantity,
            totalAmount = totalAmount,
            calculationFormula = formula
        )
    }

    /**
     * 构建计算公式文本
     * 参考Vue前端的 calculationFormula computed 逻辑
     */
    private fun buildCalculationFormula(
        scheme: SchemeInfo?,
        quantity: Double,
        unitPrice: Double,
        totalAmount: Double,
        lengthCm: Double,
        widthCm: Double
    ): String {
        val q = numberFormat.format(quantity)
        val p = numberFormat.format(unitPrice)
        val t = numberFormat.format(totalAmount)

        return when (scheme?.unit) {
            "area" -> "$q m² × ¥$p/m² = ¥$t"
            "perimeter" -> {
                val perimeter = (lengthCm + widthCm) * 2 / 100
                "${numberFormat.format(perimeter)} m × ¥$p/m = ¥$t"
            }
            "length" -> {
                val lengthM = lengthCm / 100
                "${numberFormat.format(lengthM)} m × ¥$p/m = ¥$t"
            }
            else -> "$q m² × ¥$p/m² = ¥$t"
        }
    }

    /**
     * 获取当前施工方案的计量单位
     */
    fun currentSchemeUnit(): String {
        val scheme = _uiState.value.constructionPlans
            .find { it.name == _uiState.value.selectedScheme }
        return scheme?.unit ?: "area"
    }

    /**
     * 保存工程（创建新工程或添加子项目）
     * 后端根据客户地址判断是新建还是追加子项目
     */
    fun saveProject() {
        val state = _uiState.value

        // 网络状态检查
        if (!NetworkUtil.isNetworkAvailable(context)) {
            _uiState.value = state.copy(errorMessage = "网络连接已断开，请检查网络后重试")
            return
        }

        // 表单验证
        if (state.customerAddress.isBlank()) {
            _uiState.value = state.copy(errorMessage = "请输入客户地址")
            return
        }
        if (state.selectedSpaceType.isBlank()) {
            _uiState.value = state.copy(errorMessage = "请选择空间类型")
            return
        }
        if (state.selectedScheme.isBlank()) {
            _uiState.value = state.copy(errorMessage = "请选择施工方案")
            return
        }
        val length = state.lengthCm.toDoubleOrNull()
        if (length == null || length <= 0) {
            _uiState.value = state.copy(errorMessage = "请输入有效的长度")
            return
        }
        val schemeUnit = currentSchemeUnit()
        val width = state.widthCm.toDoubleOrNull()
        if (schemeUnit != "length" && (width == null || width <= 0)) {
            _uiState.value = state.copy(errorMessage = "请输入有效的宽度")
            return
        }
        if (state.selectedConstructorIds.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "请选择施工人员")
            return
        }
        if (state.unitPrice <= 0) {
            _uiState.value = state.copy(errorMessage = "单价无效，请重新选择施工方案")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                val request = CreateProjectRequest(
                    name = state.customerAddress,
                    spaceType = state.selectedSpaceType,
                    constructionScheme = state.selectedScheme,
                    length = length,
                    width = width ?: 0.0,
                    salaryDistribution = state.salaryDistribution,
                    constructors = state.selectedConstructorIds.map { ConstructorItem(it) },
                    remark = state.remark.ifBlank { null }
                )

                val response = projectApi.createProject(request)
                if (response.code == 200) {
                    // 检查是否为已有工程（追加子项目）
                    val isExisting = state.projects.any { it.name == state.customerAddress }
                    val message = if (isExisting) "已添加为子项目" else "工程创建成功"

                    // 保存当前地址和施工人员到映射缓存（保留关联）
                    if (state.customerAddress.isNotBlank()) {
                        addressConstructorMap = addressConstructorMap.toMutableMap().apply {
                            this[state.customerAddress] = state.selectedConstructorIds.toList()
                        }
                        saveAddressMapAsync()
                    }

                    // 重置表单（保留客户地址和施工人员）
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        successMessage = message,
                        selectedSpaceType = "",
                        selectedScheme = "",
                        lengthCm = "",
                        widthCm = "",
                        remark = "",
                        unitPrice = 0.0,
                        quantity = 0.0,
                        totalAmount = 0.0,
                        calculationFormula = ""
                    )

                    // 清除表单缓存（保留地址映射）
                    viewModelScope.launch { dashboardCache.clearFormCache() }

                    // 刷新工程历史
                    loadProjects()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = NetworkErrorHandler.translateServerError(response.msg, "保存工程失败，请稍后重试")
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = NetworkErrorHandler.translate(e, "网络连接失败，请检查网络后重试")
                )
            }
        }
    }

    /**
     * 刷新未读消息数
     */
    fun refreshUnreadCount() {
        viewModelScope.launch {
            try {
                val response = messageApi.getUnreadCount()
                if (response.code == 200) {
                    _uiState.value = _uiState.value.copy(
                        unreadCount = response.data?.count ?: 0
                    )
                }
            } catch (_: Exception) {
                // 静默处理
            }
        }
    }

    /**
     * 清除消息提示（错误/成功）
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    /**
     * 获取计量单位的中文显示名
     */
    fun getUnitDisplayName(unit: String): String {
        return when (unit) {
            "area" -> "㎡"
            "perimeter" -> "米"
            "length" -> "米"
            else -> unit
        }
    }

    // ===== 缓存辅助方法 =====

    /**
     * 异步保存地址映射到DataStore
     */
    private fun saveAddressMapAsync() {
        viewModelScope.launch {
            dashboardCache.saveAddressMap(addressConstructorMap)
        }
    }

    /**
     * 防抖保存表单数据到DataStore
     * 参考Vue前端的 saveFormDataDebounced（800ms防抖）
     */
    private fun saveFormDebounced() {
        saveFormJob?.cancel()
        saveFormJob = viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            val state = _uiState.value
            dashboardCache.saveFormCache(
                DashboardCache.FormCache(
                    customerAddress = state.customerAddress,
                    selectedSpaceType = state.selectedSpaceType,
                    selectedScheme = state.selectedScheme,
                    lengthCm = state.lengthCm,
                    widthCm = state.widthCm,
                    salaryDistribution = state.salaryDistribution,
                    selectedConstructorIds = state.selectedConstructorIds.toList(),
                    remark = state.remark
                )
            )
        }
    }
}
