package com.salary.manager.feature.home.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.common.util.NetworkUtil
import com.salary.core.common.util.AmountFormatter
import com.salary.core.data.local.DashboardCache
import com.salary.core.data.local.ServerConfig
import com.salary.core.data.local.UserStorage
import com.salary.core.network.api.DictionaryApi
import com.salary.core.network.api.DictionaryItemDto
import com.salary.core.network.api.MessageApi
import com.salary.core.network.api.ProjectApi
import com.salary.core.network.api.UploadManager
import com.salary.core.network.api.UploadOutcome
import com.salary.core.network.api.UploadProgress
import com.salary.core.network.api.UserApi
import com.salary.core.network.api.UserDto
import com.salary.core.network.dto.ConstructorItem
import com.salary.core.network.dto.CreateProjectRequest
import com.salary.core.network.dto.FileDto
import com.salary.core.network.dto.ProjectDto
import com.salary.core.network.dto.SubprojectDto
import com.salary.core.network.dto.WorkerWorkdayItem
import android.content.Context
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val subprojects: List<SubprojectUiModel>,
    /** 工程备注（null或空白表示无备注，卡片中不显示备注预览行） */
    val remark: String? = null
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
    val unit: String = "area",
    /** 计量单位的显示名称（预计算，避免在Composable中调用ViewModel） */
    val unitDisplayName: String = "㎡"
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
    /**
     * 各施工人员的工日数（按工日分配时使用）
     * key: userId, value: 工日数（默认1.0）
     * 仅当salaryDistribution="work_days"时启用
     */
    val workerWorkdays: Map<Int, String> = emptyMap(),
    /**
     * 总工日校验值（按工日分配时使用）
     * 为空时不校验；有值时校验各施工人员工日之和是否等于此值
     */
    val totalWorkdaysInput: String = "",
    /** 总工日校验结果提示（空字符串表示无提示） */
    val workdaysValidationHint: String = "",
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
    /** 是否还有更多工程可加载（分页加载） */
    val hasMoreProjects: Boolean = false,
    /** 是否正在加载更多工程 */
    val isLoadingMoreProjects: Boolean = false,

    // ===== 用户信息 =====
    /** 用户昵称 */
    val userNickname: String = "",
    /** 未读消息数 */
    val unreadCount: Int = 0,

    // ===== 文件上传 =====
    /** 当前要上传附件的工程ID（触发UI层打开文件选择器） */
    val pendingUploadProjectId: Int? = null,
    /** 当前要上传附件的工程名称（传给后端用于生成存储路径） */
    val pendingUploadProjectName: String = "",
    /** 是否正在上传附件 */
    val isUploading: Boolean = false,
    /** 上传进度信息（非null时展示进度弹窗，null时隐藏） */
    val uploadProgress: UploadProgress? = null,

    // ===== 查看附件 =====
    /** 当前正在查看附件的工程ID（非null时弹出附件列表弹窗） */
    val viewingFilesProjectId: Int? = null,
    /** 当前正在查看附件的工程名称（弹窗标题用） */
    val viewingFilesProjectName: String = "",
    /** 附件列表 */
    val viewingFiles: List<FileDto> = emptyList(),
    /** 是否正在加载附件列表 */
    val isLoadingFiles: Boolean = false
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
    private val uploadManager: UploadManager,
    private val userStorage: UserStorage,
    private val dashboardCache: DashboardCache,
    private val serverConfig: ServerConfig,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /** 当前用户角色（用于UI层按角色控制元素显示） */
    val userRole: StateFlow<String> = userStorage.roleFlow

    private companion object {
        const val TAG = "DashboardViewModel"
    }

    /** 数字格式化工具 */
    private val numberFormat = DecimalFormat("0.00")

    /** 客户地址与施工人员的映射缓存（内存副本，定期同步到DataStore） */
    // 使用LinkedHashMap保持插入顺序，便于实现LRU淘汰
    private var addressConstructorMap: LinkedHashMap<String, List<Int>> = linkedMapOf()

    /** 地址映射缓存最大数量，超过时淘汰最旧记录（LRU） */
    private val maxAddressCacheSize = 50

    /** 防抖保存表单的Job */
    private var saveFormJob: kotlinx.coroutines.Job? = null

    /** 工程列表分页：当前页码 */
    private var projectsCurrentPage = 1
    /** 工程列表分页：每页数量 */
    private val projectsPageSize = 20
    /** 工程列表分页：是否正在加载更多（防止重复触发） */
    private var isLoadingMoreProjects = false

    init {
        loadInitialData()
    }

    /**
     * 加载初始数据：字典数据、用户信息、未读消息数、工程历史
     * 同时从缓存恢复地址映射和表单数据
     *
     * 优化：工程历史加载与字典数据并行执行，不等待字典加载完成
     * （工程列表在施工方案加载完成后即可启动，无需等待全部字典数据）
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                coroutineScope {
                    val spaceTypesDeferred = async { loadSpaceTypes() }
                    val plansDeferred = async { loadConstructionPlans() }
                    val constructorsDeferred = async { loadConstructors() }
                    val userInfoDeferred = async { loadUserInfo() }
                    val unreadDeferred = async { loadUnreadCount() }

                    // 等待施工方案加载完成（工程列表映射需要方案单位）
                    plansDeferred.await()

                    // 施工方案就绪后立即并行加载工程历史，不等待其他字典数据
                    val projectsDeferred = async { loadProjectsSuspend() }

                    // 等待其余数据加载完成
                    spaceTypesDeferred.await()
                    constructorsDeferred.await()
                    userInfoDeferred.await()
                    unreadDeferred.await()
                    projectsDeferred.await()
                }

                // 从缓存恢复地址→施工人员映射（转为LinkedHashMap保持插入顺序）
                addressConstructorMap = linkedMapOf<String, List<Int>>().apply {
                    putAll(dashboardCache.loadAddressMap())
                }

                // 从缓存恢复表单数据
                restoreFormCache()

                _uiState.value = _uiState.value.copy(
                    isLoading = false
                )
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
                workerWorkdays = cache.workerWorkdays,
                remark = cache.remark
            )
            // 恢复施工方案对应的单价
            if (cache.selectedScheme.isNotBlank()) {
                val scheme = _uiState.value.constructionPlans.find { it.name == cache.selectedScheme }
                if (scheme != null) {
                    _uiState.value = _uiState.value.copy(unitPrice = scheme.price)
                }
            }
            // 无论是否恢复方案，只要长度或宽度非空就重算预览
            // 避免：用户上次仅修改了长度/宽度而未选方案，恢复后预览公式为空的不一致问题
            if (cache.lengthCm.isNotBlank() || cache.widthCm.isNotBlank()) {
                recalculate()
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
     * 加载工程历史列表（按月筛选，第一页）
     * 列表接口已聚合返回 sub_projects，无需再发起N+1详情请求
     */
    fun loadProjects() {
        viewModelScope.launch { loadProjectsSuspend() }
    }

    /**
     * 加载更多工程历史（分页加载，滚动到底部时触发）
     */
    fun loadMoreProjects() {
        if (isLoadingMoreProjects || !_uiState.value.hasMoreProjects) return
        viewModelScope.launch {
            isLoadingMoreProjects = true
            _uiState.value = _uiState.value.copy(isLoadingMoreProjects = true)
            try {
                val nextPage = projectsCurrentPage + 1
                val response = projectApi.getProjects(
                    page = nextPage,
                    size = projectsPageSize,
                    yearMonth = _uiState.value.selectedYearMonth
                )
                if (response.code == 200) {
                    val pageData = response.data
                    if (pageData != null && pageData.list.isNotEmpty()) {
                        val newProjects = pageData.list.map { mapProjectDtoToUiModel(it) }
                        projectsCurrentPage = nextPage
                        _uiState.value = _uiState.value.copy(
                            projects = _uiState.value.projects + newProjects,
                            hasMoreProjects = pageData.list.size >= projectsPageSize,
                            isLoadingMoreProjects = false
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            hasMoreProjects = false,
                            isLoadingMoreProjects = false
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(isLoadingMoreProjects = false)
                }
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMoreProjects = false)
            } finally {
                isLoadingMoreProjects = false
            }
        }
    }

    /**
     * 加载工程历史列表的suspend实现（供并行调用，加载第一页）
     */
    private suspend fun loadProjectsSuspend() {
        _uiState.value = _uiState.value.copy(isLoadingProjects = true)
        projectsCurrentPage = 1

        try {
            val response = projectApi.getProjects(
                page = 1,
                size = projectsPageSize,
                yearMonth = _uiState.value.selectedYearMonth
            )

            if (response.code == 200) {
                val pageData = response.data
                if (pageData == null) {
                    _uiState.value = _uiState.value.copy(
                        projects = emptyList(),
                        isLoadingProjects = false,
                        hasMoreProjects = false
                    )
                    return
                }
                // 直接使用列表接口返回的数据（含subprojects），无需再发起N+1详情请求
                val projects = pageData.list.map { mapProjectDtoToUiModel(it) }
                _uiState.value = _uiState.value.copy(
                    projects = projects,
                    isLoadingProjects = false,
                    hasMoreProjects = pageData.list.size >= projectsPageSize
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    projects = emptyList(),
                    isLoadingProjects = false,
                    hasMoreProjects = false,
                    errorMessage = NetworkErrorHandler.translateServerError(response.msg, "加载工程历史失败")
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                projects = emptyList(),
                isLoadingProjects = false,
                hasMoreProjects = false,
                errorMessage = NetworkErrorHandler.translate(e, "加载工程历史失败")
            )
        }
    }

    /**
     * 将工程列表DTO转换为UI模型
     * 列表接口已返回subprojects，无需再发起详情请求
     */
    private fun mapProjectDtoToUiModel(dto: ProjectDto): ProjectHistoryUiModel {
        return ProjectHistoryUiModel(
            id = dto.id,
            name = dto.name,
            totalAmount = AmountFormatter.format(dto.totalAmount),
            workerNames = dto.workers.map { it.nickname },
            fileCount = dto.filesCount,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            subprojects = dto.subprojects.map { mapToSubprojectModel(it) },
            remark = dto.remark
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
            unit = unit,
            unitDisplayName = getUnitDisplayName(unit)
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
                updateAddressMapping(oldAddress, currentIds)
                saveAddressMapAsync()
            }

            // 2. 从缓存中加载新地址对应的施工人员
            val newIds = addressConstructorMap[newAddress]
            if (newIds != null && newIds.isNotEmpty()) {
                // 新地址有缓存，恢复施工人员
                // 同时刷新LRU访问顺序：将命中项移到末尾（最近使用），避免被过早淘汰
                _uiState.value = _uiState.value.copy(selectedConstructorIds = newIds.toSet())
                updateAddressMapping(newAddress, newIds)  // LRU访问更新：移到末尾
                saveAddressMapAsync()
            } else {
                // 新地址无缓存，清空施工人员勾选
                // 不同地址对应不同客户，施工人员可能完全不同，不应沿用旧地址的选择
                _uiState.value = _uiState.value.copy(selectedConstructorIds = emptySet())
            }
        }
        // 注：oldAddress.isBlank()（首次输入地址）时不触发人员关联切换
        // 设计取舍：首次输入视为新客户首次录入，保留当前已勾选施工人员便于继续操作
        // 此时即便新地址在缓存中有映射也不自动恢复，避免覆盖用户当前的选择意图

        // 防抖保存表单数据
        saveFormDebounced()
    }

    /**
     * 选择空间类型
     * 触发防抖保存：用户单独修改空间类型后退出App也不丢失
     */
    fun selectSpaceType(spaceType: String) {
        _uiState.value = _uiState.value.copy(selectedSpaceType = spaceType)
        saveFormDebounced()
    }

    /**
     * 选择施工方案，同时更新单价和重新计算
     * 触发防抖保存：用户单独修改施工方案后退出App也不丢失
     */
    fun selectScheme(schemeName: String) {
        val scheme = _uiState.value.constructionPlans.find { it.name == schemeName }
        _uiState.value = _uiState.value.copy(
            selectedScheme = schemeName,
            unitPrice = scheme?.price ?: 0.0
        )
        recalculate()
        saveFormDebounced()
    }

    /**
     * 更新长度（厘米）
     * 触发防抖保存：用户单独修改长度后退出App也不丢失
     */
    fun updateLength(length: String) {
        _uiState.value = _uiState.value.copy(lengthCm = length)
        recalculate()
        saveFormDebounced()
    }

    /**
     * 更新宽度（厘米）
     * 触发防抖保存：用户单独修改宽度后退出App也不丢失
     */
    fun updateWidth(width: String) {
        _uiState.value = _uiState.value.copy(widthCm = width)
        recalculate()
        saveFormDebounced()
    }

    /**
     * 更新分配方式
     * 切换为"按工日"时自动为已选施工人员初始化工日映射（空值，由placeholder提示"1"）
     * 切换回"平均"时清空工日映射，避免冗余数据
     * 触发防抖保存：用户单独修改分配方式后退出App也不丢失
     */
    fun updateSalaryDistribution(distribution: String) {
        val currentIds = _uiState.value.selectedConstructorIds
        val newWorkdays = when (distribution) {
            "work_days" -> {
                // 切换为按工日：为已选施工人员初始化工日映射（保留已有输入，新项置空）
                val existing = _uiState.value.workerWorkdays
                currentIds.associateWith { id -> existing[id] ?: "" }
            }
            else -> {
                // 切换为平均：清空工日映射
                emptyMap()
            }
        }
        _uiState.value = _uiState.value.copy(
            salaryDistribution = distribution,
            workerWorkdays = newWorkdays
        )
        recalculate()
        saveFormDebounced()
    }

    /**
     * 更新指定施工人员的工日数
     * 仅在按工日分配模式下有效，自动触发重新计算预览
     */
    fun updateWorkerWorkdays(userId: Int, workdays: String) {
        if (_uiState.value.salaryDistribution != "work_days") return
        // 过滤非法输入：允许空字符串（用户清空时），但解析为数字时必须>0
        val filtered = workdays.filter { it.isDigit() || it == '.' }
        val newMap = _uiState.value.workerWorkdays.toMutableMap()
        newMap[userId] = filtered
        _uiState.value = _uiState.value.copy(workerWorkdays = newMap)
        validateWorkdays()
        recalculate()
        saveFormDebounced()
    }

    /**
     * 更新总工日校验输入值
     * 为空时不校验；有值时校验各施工人员工日之和是否等于此值
     */
    fun updateTotalWorkdaysInput(value: String) {
        if (_uiState.value.salaryDistribution != "work_days") return
        // 过滤非法输入：仅允许数字和小数点
        val filtered = value.filter { it.isDigit() || it == '.' }
        _uiState.value = _uiState.value.copy(totalWorkdaysInput = filtered)
        validateWorkdays()
        saveFormDebounced()
    }

    /**
     * 校验各施工人员工日之和与总工日输入是否一致
     * - 总工日输入为空时不校验，清空提示
     * - 总工日输入有值时：空值工日按1计算，比较合计与输入值
     */
    private fun validateWorkdays() {
        val state = _uiState.value
        if (state.salaryDistribution != "work_days") {
            _uiState.value = state.copy(workdaysValidationHint = "")
            return
        }
        val input = state.totalWorkdaysInput.trim()
        // 总工日输入为空：不校验
        if (input.isEmpty()) {
            _uiState.value = state.copy(workdaysValidationHint = "")
            return
        }
        val targetTotal = input.toDoubleOrNull()
        if (targetTotal == null || targetTotal <= 0) {
            _uiState.value = state.copy(workdaysValidationHint = "总工日输入无效")
            return
        }
        // 计算各施工人员工日之和（空值按1计算）
        val selectedIds = state.selectedConstructorIds
        if (selectedIds.isEmpty()) {
            _uiState.value = state.copy(workdaysValidationHint = "")
            return
        }
        val sum = selectedIds.sumOf { id ->
            val v = state.workerWorkdays[id]?.trim()
            val parsed = v?.toDoubleOrNull()
            if (parsed != null && parsed > 0) parsed else 1.0
        }
        // 允许0.01的浮点误差
        val diff = kotlin.math.abs(sum - targetTotal)
        // 使用 String.format 格式化数字（保留2位小数），避免依赖文件级私有函数
        val sumStr = String.format("%.2f", sum)
        val targetStr = String.format("%.2f", targetTotal)
        _uiState.value = state.copy(
            workdaysValidationHint = if (diff > 0.01) {
                "工日合计 $sumStr 与总工日 $targetStr 不一致"
            } else {
                "工日合计 $sumStr 与总工日一致 ✓"
            }
        )
    }

    /**
     * 切换施工人员选中状态
     * 参考Vue前端：施工人员变化时立即保存到当前地址的缓存
     * 按工日分配模式下：勾选时初始化工日为空（placeholder提示"1"），取消勾选时移除工日记录
     */
    fun toggleConstructor(userId: Int) {
        val current = _uiState.value.selectedConstructorIds
        val newSet = if (current.contains(userId)) {
            current - userId
        } else {
            current + userId
        }

        // 按工日分配模式下同步更新工日映射
        val newWorkdays = if (_uiState.value.salaryDistribution == "work_days") {
            val mutable = _uiState.value.workerWorkdays.toMutableMap()
            if (newSet.contains(userId)) {
                // 勾选：初始化工日为空字符串（由UI placeholder提示"1"）
                if (!mutable.containsKey(userId)) mutable[userId] = ""
            } else {
                // 取消勾选：移除工日记录
                mutable.remove(userId)
            }
            mutable.toMap()
        } else {
            _uiState.value.workerWorkdays
        }

        _uiState.value = _uiState.value.copy(
            selectedConstructorIds = newSet,
            workerWorkdays = newWorkdays
        )

        // 如果当前有地址，保存施工人员到当前地址的缓存
        val address = _uiState.value.customerAddress
        if (address.isNotBlank()) {
            updateAddressMapping(address, newSet.toList())
            saveAddressMapAsync()
        }

        // 防抖保存表单数据
        saveFormDebounced()
    }

    /**
     * 更新工程备注
     * 同步触发防抖保存，确保险备注输入被持久化到DataStore
     */
    fun updateRemark(remark: String) {
        _uiState.value = _uiState.value.copy(remark = remark)
        // 防抖保存表单数据（含备注）
        saveFormDebounced()
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
     * 按工日分配模式下额外显示每人分摊金额（按工日比例）
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

        // 生成计算公式文本（含按工日分配的每人分摊明细）
        val formula = buildCalculationFormula(
            scheme, quantity, unitPrice, totalAmount, lengthCm, widthCm,
            salaryDistribution = state.salaryDistribution,
            workerWorkdays = state.workerWorkdays,
            selectedConstructorIds = state.selectedConstructorIds,
            constructors = state.constructors
        )

        _uiState.value = state.copy(
            quantity = quantity,
            totalAmount = totalAmount,
            calculationFormula = formula
        )
    }

    /**
     * 构建计算公式文本
     * 参考Vue前端的 calculationFormula computed 逻辑
     * 按工日分配模式下追加显示每人分摊金额
     */
    private fun buildCalculationFormula(
        scheme: SchemeInfo?,
        quantity: Double,
        unitPrice: Double,
        totalAmount: Double,
        lengthCm: Double,
        widthCm: Double,
        salaryDistribution: String = "average",
        workerWorkdays: Map<Int, String> = emptyMap(),
        selectedConstructorIds: Set<Int> = emptySet(),
        constructors: List<UserDto> = emptyList()
    ): String {
        val q = numberFormat.format(quantity)
        val p = numberFormat.format(unitPrice)
        val t = numberFormat.format(totalAmount)

        // 基础公式：数量 × 单价 = 总额
        val baseFormula = when (scheme?.unit) {
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

        // 平均分配：仅显示基础公式，不再追加人均工费（用户要求）
        // 按工日分配：追加显示每人按工日比例分摊金额
        if (salaryDistribution == "work_days" && selectedConstructorIds.isNotEmpty() && totalAmount > 0) {
            // 解析每人工日数：空值按默认1工日处理（与UI placeholder一致）
            val workdayPairs = selectedConstructorIds.map { id ->
                val days = workerWorkdays[id]?.trim()?.let {
                    if (it.isEmpty()) 1.0 else it.toDoubleOrNull() ?: 1.0
                } ?: 1.0
                id to days
            }
            val totalWorkdays = workdayPairs.sumOf { it.second }

            if (totalWorkdays > 0) {
                val details = workdayPairs.map { (id, days) ->
                    val worker = constructors.find { it.id == id }
                    val name = worker?.nickname ?: "用户$id"
                    val ratio = days / totalWorkdays
                    val amount = totalAmount * ratio
                    "$name ${numberFormat.format(days)}工日 ¥${numberFormat.format(amount)}"
                }
                return "$baseFormula\n总工日：${numberFormat.format(totalWorkdays)}\n${details.joinToString("\n")}"
            }
        }

        return baseFormula
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

        // 按工日分配模式校验：每人工日数必须>0（空值按默认1处理，无需用户必须输入）
        if (state.salaryDistribution == "work_days") {
            val invalidWorkdays = state.selectedConstructorIds.any { id ->
                val raw = state.workerWorkdays[id]?.trim() ?: ""
                // 空值视为1.0（有效）；非空时解析必须>0
                if (raw.isEmpty()) false
                else (raw.toDoubleOrNull() ?: 0.0) <= 0
            }
            if (invalidWorkdays) {
                _uiState.value = state.copy(errorMessage = "按工日分配模式下，每位施工人员的工日数必须大于0")
                return
            }
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                // 按工日分配模式下构建workerWorkDays列表：空值按默认1工日处理
                val workerWorkDays = if (state.salaryDistribution == "work_days") {
                    state.selectedConstructorIds.map { id ->
                        val raw = state.workerWorkdays[id]?.trim() ?: ""
                        val days = if (raw.isEmpty()) 1.0 else raw.toDoubleOrNull() ?: 1.0
                        WorkerWorkdayItem(id, days)
                    }
                } else null

                val request = CreateProjectRequest(
                    name = state.customerAddress,
                    spaceType = state.selectedSpaceType,
                    constructionScheme = state.selectedScheme,
                    length = length,
                    width = width ?: 0.0,
                    salaryDistribution = state.salaryDistribution,
                    constructors = state.selectedConstructorIds.map { ConstructorItem(it) },
                    remark = state.remark.ifBlank { null },
                    workerWorkDays = workerWorkDays
                )

                val response = projectApi.createProject(request)
                if (response.code == 200) {
                    // 检查是否为已有工程（追加子项目）
                    val isExisting = state.projects.any { it.name == state.customerAddress }
                    val message = if (isExisting) "已添加为子项目" else "工程创建成功"

                    // 保存当前地址和施工人员到映射缓存（保留关联）
                    if (state.customerAddress.isNotBlank()) {
                        updateAddressMapping(state.customerAddress, state.selectedConstructorIds.toList())
                        saveAddressMapAsync()
                    }

                    // 重置表单（保留客户地址和施工人员，清空工日映射因工程已保存）
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
                        calculationFormula = "",
                        workerWorkdays = emptyMap()
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

    // ===== 文件上传 =====

    /**
     * 触发文件选择器：记录目标工程ID和名称，UI层监听pendingUploadProjectId变化启动选择器
     */
    fun openFilePickerForProject(projectId: Int) {
        val project = _uiState.value.projects.find { it.id == projectId }
        _uiState.value = _uiState.value.copy(
            pendingUploadProjectId = projectId,
            pendingUploadProjectName = project?.name ?: "salary"
        )
    }

    /**
     * 上传选中的多个文件到后端（串行执行，两步式：上传文件→写入数据库关联工程）
     * @param uris 文件Uri列表（由UI层从文件选择器获取，支持多选）
     * 成功后刷新工程历史以更新附件数量
     */
    fun uploadAttachments(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) {
            cancelUpload()
            return
        }
        val projectId = _uiState.value.pendingUploadProjectId ?: return
        val projectName = _uiState.value.pendingUploadProjectName.ifBlank { "salary" }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, uploadProgress = null)
            // 委托 UploadManager 完成批量上传，进度回调更新UI状态
            val result = uploadManager.uploadAttachments(uris, projectId, projectName) { progress ->
                _uiState.value = _uiState.value.copy(uploadProgress = progress)
            }

            // 上传完成，组装提示消息
            // 全部失败时展示具体失败原因（如"不支持的文件类型"），部分失败时展示失败文件名
            val message = when {
                result.isAllSuccess -> "成功上传 ${result.successCount} 个附件"
                result.successCount > 0 -> {
                    // 部分成功：列出失败文件名
                    val failedNames = result.failedDetails.joinToString("、") { it.fileName }
                    "上传完成：成功 ${result.successCount} 个，失败 ${result.failedCount} 个（$failedNames）"
                }
                else -> {
                    // 全部失败：展示具体失败原因（取第一个文件的错误原因）
                    val firstError = result.failedDetails.firstOrNull()?.error ?: "上传失败"
                    if (result.failedCount == 1) firstError
                    else "$firstError（共 ${result.failedCount} 个文件失败）"
                }
            }

            _uiState.value = _uiState.value.copy(
                isUploading = false,
                uploadProgress = null,
                pendingUploadProjectId = null,
                pendingUploadProjectName = "",
                successMessage = if (result.successCount > 0) message else null,
                errorMessage = if (result.successCount == 0) message else null
            )

            // 有任一文件上传成功则刷新工程历史以更新附件数量
            if (result.successCount > 0) {
                loadProjects()
            }
        }
    }

    /**
     * 取消上传（用户未选择文件时重置状态）
     */
    fun cancelUpload() {
        _uiState.value = _uiState.value.copy(
            pendingUploadProjectId = null,
            pendingUploadProjectName = "",
            uploadProgress = null
        )
    }

    // ===== 查看附件 =====

    /**
     * 打开附件列表弹窗：记录目标工程并拉取附件列表
     * 附件列表来自工程详情接口 GET /v1/projects/:id 的 files 字段
     */
    fun openAttachmentList(projectId: Int) {
        val project = _uiState.value.projects.find { it.id == projectId }
        _uiState.value = _uiState.value.copy(
            viewingFilesProjectId = projectId,
            viewingFilesProjectName = project?.name ?: "",
            viewingFiles = emptyList(),
            isLoadingFiles = true
        )
        loadProjectFiles(projectId)
    }

    /**
     * 关闭附件列表弹窗
     */
    fun closeAttachmentList() {
        _uiState.value = _uiState.value.copy(
            viewingFilesProjectId = null,
            viewingFilesProjectName = "",
            viewingFiles = emptyList(),
            isLoadingFiles = false
        )
    }

    /**
     * 加载指定工程的附件列表
     * 通过工程详情接口获取 files 字段
     */
    private fun loadProjectFiles(projectId: Int) {
        viewModelScope.launch {
            try {
                val response = projectApi.getProjectDetail(projectId)
                val detail = response.data
                if (response.code == 200 && detail != null) {
                    _uiState.value = _uiState.value.copy(
                        viewingFiles = detail.files,
                        isLoadingFiles = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        viewingFiles = emptyList(),
                        isLoadingFiles = false,
                        errorMessage = NetworkErrorHandler.translateServerError(response.msg, "加载附件列表失败")
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "加载附件列表失败: projectId=$projectId, ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    viewingFiles = emptyList(),
                    isLoadingFiles = false,
                    errorMessage = NetworkErrorHandler.translate(e, "加载附件列表失败")
                )
            }
        }
    }

    /**
     * 拼接附件完整访问URL
     * 后端 path 字段为相对路径（如 /upload/202512/salary/xxx.jpg），需拼接服务器地址
     * @param relativePath 后端返回的相对路径
     * @return 完整URL；若服务器地址未配置则返回相对路径
     */
    suspend fun buildFileUrl(relativePath: String): String {
        if (relativePath.isEmpty()) return relativePath
        // 已经是完整URL则直接返回
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath
        }
        val baseUrl = serverConfig.getServerUrl().trimEnd('/')
        if (baseUrl.isEmpty()) return relativePath
        return baseUrl + relativePath
    }

    // ===== 缓存辅助方法 =====

    /**
     * 更新地址→施工人员映射，并执行LRU淘汰
     * 先移除已存在的同名key（保证更新后位于末尾，即最近使用），
     * 再插入新值，超过上限时淘汰头部（最旧）记录。
     *
     * @param address 客户地址
     * @param ids 施工人员ID列表
     */
    private fun updateAddressMapping(address: String, ids: List<Int>) {
        addressConstructorMap.remove(address)  // 先移除，确保重新插入到末尾
        addressConstructorMap[address] = ids
        // LRU淘汰：超过上限时移除最旧（头部）记录
        while (addressConstructorMap.size > maxAddressCacheSize) {
            val oldestKey = addressConstructorMap.keys.first()
            addressConstructorMap.remove(oldestKey)
        }
    }

    /**
     * 异步保存地址映射到DataStore
     *
     * 并发保护：先在主线程拷贝快照（避免IO线程序列化时主线程修改Map导致ConcurrentModificationException）
     * NonCancellable：确保ViewModel销毁时落盘操作不被取消，避免数据丢失
     */
    private fun saveAddressMapAsync() {
        // 主线程立即拷贝快照，避免后续IO线程序列化时发生并发修改
        val snapshot = addressConstructorMap.toMap()
        viewModelScope.launch {
            withContext(NonCancellable) {
                dashboardCache.saveAddressMap(snapshot)
            }
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
            withContext(NonCancellable) {
                dashboardCache.saveFormCache(
                    DashboardCache.FormCache(
                        customerAddress = state.customerAddress,
                        selectedSpaceType = state.selectedSpaceType,
                        selectedScheme = state.selectedScheme,
                        lengthCm = state.lengthCm,
                        widthCm = state.widthCm,
                        salaryDistribution = state.salaryDistribution,
                        selectedConstructorIds = state.selectedConstructorIds.toList(),
                        workerWorkdays = state.workerWorkdays,
                        remark = state.remark
                    )
                )
            }
        }
    }

    /**
     * ViewModel销毁时兜底保存
     *
     * 场景：用户修改表单后800ms内退出App，防抖Job未执行就被取消，导致最后一次修改丢失。
     *
     * 实现说明：
     * - 使用NonCancellable + GlobalScope.launch异步保存，避免在主线程执行IO操作
     * - NonCancellable确保即使ViewModel的viewModelScope已取消，保存操作仍能完成
     * - GlobalScope生命周期独立于ViewModel，App进程未退出时保存任务可继续执行
     * - 相比runBlocking：不阻塞主线程，避免ANR风险
     */
    override fun onCleared() {
        super.onCleared()
        // 取消未执行的防抖Job（避免重复保存）
        saveFormJob?.cancel()
        // 异步兜底保存表单快照（防抖未完成时强制落盘）
        // 使用NonCancellable确保保存操作不被取消
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.NonCancellable) {
            try {
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
                        workerWorkdays = state.workerWorkdays,
                        remark = state.remark
                    )
                )
                // 地址映射兜底落盘
                dashboardCache.saveAddressMap(addressConstructorMap.toMap())
            } catch (_: Exception) {
                // 静默处理，销毁阶段无法向用户报错
            }
        }
    }
}
