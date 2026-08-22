package com.salary.manager.feature.home.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.common.util.NetworkUtil
import com.salary.core.common.util.AmountFormatter
import com.salary.core.common.util.AppLog
import com.salary.core.common.util.DateFormatter
import com.salary.core.common.util.WorkdaysValidator
import com.salary.core.data.local.DashboardCache
import com.salary.core.data.local.ServerConfig
import com.salary.core.data.local.UserStorage
import com.salary.core.network.api.DictionaryApi
import com.salary.core.network.api.DictionaryItemDto
import com.salary.core.network.api.MessageApi
import com.salary.core.network.api.ProjectApi
import com.salary.core.network.api.UploadManager
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.text.DecimalFormat
import java.util.concurrent.ConcurrentHashMap
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
 *
 * @Immutable 标注：告知 Compose 编译器该类型所有字段不可变，
 * 当实例未变时可安全跳过依赖该参数的 Composable 重组，减少主页滑动时的不必要重组
 *
 * @Serializable 标注：支持将工程列表序列化为 JSON 缓存到 DataStore，
 * 冷启动时可用缓存立即渲染，再后台拉取最新数据覆盖（stale-while-revalidate）
 */
@Immutable
@kotlinx.serialization.Serializable
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
 *
 * @Immutable 标注：与 ProjectHistoryUiModel 配合，让 SubprojectTable 在参数未变时能被跳过
 *
 * @Serializable 标注：随 ProjectHistoryUiModel 一起序列化缓存
 */
@Immutable
@kotlinx.serialization.Serializable
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
    val unitDisplayName: String = "㎡",
    /** 子项目备注（null或空字符串表示无备注） */
    val remark: String? = null,
    /** 实测数量（非null表示使用现场实测值覆盖按长宽计算的quantity） */
    val measuredQuantity: Double? = null,
    /** 实测备注（记录实测方式或现场说明） */
    val measuredNote: String? = null
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
    /**
     * 高度（厘米字符串，仅梯形等需要三维参数的形状使用，空字符串表示未输入）
     * 语义随空间形状变化：
     * - rectangle：不使用
     * - right_triangle：不使用（length=底, width=高）
     * - trapezoid：梯形的高（length=上底, width=下底, height=高）
     * - circle：不使用（length=直径）
     */
    val heightCm: String = "",
    /** 实测数量（异形空间现场实测值，空字符串表示不使用实测，按长宽计算） */
    val measuredQuantity: String = "",
    /** 实测备注（记录实测方式或现场说明，可选） */
    val measuredNote: String = "",
    /**
     * 实测信息区展开状态（平时少用，默认折叠；已填写实测数据时默认展开）
     * 初始值在缓存加载时根据 measuredQuantity/measuredNote 是否非空计算
     */
    val isMeasuredSectionExpanded: Boolean = false,
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
    /**
     * 工日校验是否一致（仅当 workdaysValidationHint 非空时有意义）
     *
     * UI 层根据此 Boolean 决定颜色/动画样式，避免通过字符串 contains("不一致") 匹配，
     * 防止后续文案调整导致 UI 判断失效。
     */
    val isWorkdaysConsistent: Boolean = false,
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
    val selectedYearMonth: String = DateFormatter.currentYearMonth(),
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

        /**
         * 施工人员列表进程内单例缓存
         *
         * 施工人员变更频率极低（新增/改名/删除均罕见），进程内可长期复用。
         * 首次冷启动从磁盘 DashboardCache 加载填充；后续 ViewModel 重建（如返回、
         * Tab 切换）直接命中此内存缓存，无需再走 DataStore 读取和 JSON 反序列化，
         * 达到"打开即可见、不再卡顿"的效果。
         */
        @Volatile
        private var cachedConstructors: List<UserDto>? = null

        /** 施工人员缓存的 JSON 编解码器（忽略未知字段，兼容后端字段扩展） */
        private val CONSTRUCTORS_JSON = Json { ignoreUnknownKeys = true }

        /**
         * 工程历史列表进程内缓存（按月份区分）
         *
         * key: yearMonth(yyyy-MM)，value: 该月份第一页工程列表
         * ViewModel 重建（Tab 切换、返回主页）时直接命中内存缓存立即渲染，
         * 无需再走 DataStore IO 和 JSON 反序列化。
         * 网络刷新成功后覆盖对应月份的缓存。
         *
         * 使用 [ConcurrentHashMap] 保证多协程并发读写月份缓存时的线程安全，
         * 替代原先的 @Volatile + mutableMapOf（后者只保证引用可见性，不保证内部操作原子性）。
         */
        private val cachedProjectsByMonth: MutableMap<String, List<ProjectHistoryUiModel>> = ConcurrentHashMap()

        /** 工程列表缓存的 JSON 编解码器（忽略未知字段，兼容模型扩展） */
        private val PROJECTS_JSON = Json { ignoreUnknownKeys = true }
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

    /**
     * 表单是否有未落盘的用户修改
     *
     * true  = 用户修改了表单但800ms防抖保存尚未完成
     * false = 无未落盘修改（刚恢复缓存 / 防抖已落盘 / 保存工程成功已重置）
     *
     * 用途：onCleared 兜底保存仅在 formDirty=true 时执行，
     * 防止"启动后表单尚未恢复就退出"时空表单覆盖磁盘上的有效缓存
     */
    @Volatile
    private var formDirty = false

    /** 工程列表分页：当前页码 */
    private var projectsCurrentPage = 1
    /** 工程列表分页：每页数量 */
    private val projectsPageSize = 20

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

            // ========== 表单缓存优先恢复（必须在网络请求之前） ==========
            // 若放在网络加载之后，冷启动时表单会先显示为空（网络请求耗时数秒），
            // 用户若在恢复完成前退出App，onCleared兜底保存的空表单会覆盖磁盘上的
            // 有效缓存，导致"上次输入的内容下次启动丢失"。
            // DataStore本地读取毫秒级完成，提前执行不影响加载速度。
            restoreFormCache()

            // ========== 施工人员缓存优先注入 ==========
            // 施工人员数据变更频率极低，先用内存/磁盘缓存立即渲染，避免冷启动等待网络。
            // 后续 loadConstructors() 会在后台异步向服务端拉取最新列表并覆盖 UI 与缓存。
            primeConstructorsFromCache()

            // ========== 工程列表缓存优先注入 ==========
            // 先用内存/磁盘缓存渲染工程历史，避免冷启动白屏等待网络。
            // 后续 loadProjectsSuspend() 会从网络拉取最新数据覆盖。
            primeProjectsFromCache()

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
     *
     * 在网络请求之前执行（DataStore本地读取，毫秒级），确保冷启动首帧即显示
     * 上次未保存的输入内容。施工方案的单价依赖方案列表（网络加载），
     * 由 [loadConstructionPlans] 完成后调用 [applySchemePricing] 补齐。
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
                heightCm = cache.heightCm,
                salaryDistribution = cache.salaryDistribution,
                selectedConstructorIds = cache.selectedConstructorIds.toSet(),
                workerWorkdays = cache.workerWorkdays,
                remark = cache.remark,
                measuredQuantity = cache.measuredQuantity,
                measuredNote = cache.measuredNote,
                // 已填写实测数据时默认展开，否则折叠（实测字段平时少用）
                isMeasuredSectionExpanded = cache.measuredQuantity.isNotBlank() || cache.measuredNote.isNotBlank()
            )
            // 恢复的内容已与磁盘缓存一致，不算"未落盘的修改"
            formDirty = false
            // 无论是否恢复方案，只要长度/宽度/高度/实测数量非空就重算预览
            // 避免：用户上次仅修改了长度/宽度而未选方案，恢复后预览公式为空的不一致问题
            // （此时方案列表可能尚未加载，单价由 applySchemePricing 在方案就绪后补齐）
            if (cache.lengthCm.isNotBlank() || cache.widthCm.isNotBlank() || cache.heightCm.isNotBlank() || cache.measuredQuantity.isNotBlank()) {
                recalculate()
            }
        } catch (_: Exception) {
            // 静默处理
        }
    }

    /**
     * 施工方案列表加载完成后，为已恢复的表单补充方案单价并重算预览
     *
     * restoreFormCache 在网络请求之前执行，此时方案列表尚未从服务端加载，
     * 无法查到已恢复方案名对应的单价。本函数在 loadConstructionPlans 成功后调用，
     * 保证恢复表单后单价和金额预览正确。
     */
    private fun applySchemePricing() {
        val state = _uiState.value
        if (state.selectedScheme.isBlank()) return
        val scheme = state.constructionPlans.find { it.name == state.selectedScheme } ?: return
        _uiState.value = state.copy(unitPrice = scheme.price)
        // 单价就绪后重算预览，覆盖恢复时单价为0的计算结果
        recalculate()
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
                // 方案列表就绪后，为恢复的表单补充单价并重算预览
                // （restoreFormCache 在网络请求前执行，当时查不到方案单价）
                applySchemePricing()
            }
        } catch (_: Exception) {
            // 静默处理，不影响其他数据加载
        }
    }

    /**
     * 冷启动前用缓存立即填充施工人员列表
     *
     * 优先级：进程内存缓存 > 磁盘 DataStore 缓存
     * 目的：让"施工人员选择"控件在首屏渲染时就有数据可显示，避免等待网络请求。
     * 已有内存缓存时跳过磁盘读，减少 IO 开销。
     */
    private suspend fun primeConstructorsFromCache() {
        // 内存缓存命中：直接注入并返回
        cachedConstructors?.let { mem ->
            if (mem.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(constructors = mem)
                return
            }
        }
        // 磁盘缓存回填：JSON 反序列化在 IO 线程执行，避免阻塞主线程
        try {
            val jsonStr = dashboardCache.loadConstructorsJson()
            if (jsonStr.isNotBlank()) {
                val list = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    CONSTRUCTORS_JSON.decodeFromString(
                        ListSerializer(UserDto.serializer()),
                        jsonStr
                    )
                }
                if (list.isNotEmpty()) {
                    cachedConstructors = list
                    _uiState.value = _uiState.value.copy(constructors = list)
                }
            }
        } catch (_: Exception) {
            // 缓存损坏时静默忽略，等 loadConstructors 从网络补齐
        }
    }

    /**
     * 加载施工人员列表（stale-while-revalidate）
     *
     * 走网络刷新最新数据，成功后：
     * 1. 更新 UI 状态覆盖旧缓存渲染的内容
     * 2. 写入内存缓存（供同一进程后续 ViewModel 复用）
     * 3. 序列化 JSON 写入磁盘缓存（供下次冷启动立即命中）
     *
     * 网络失败时保持当前 UI 状态（可能是从缓存注入的旧数据），不打断用户操作。
     */
    private suspend fun loadConstructors() {
        try {
            val response = userApi.getConstructors()
            if (response.code == 200) {
                val list = response.data ?: emptyList()
                _uiState.value = _uiState.value.copy(constructors = list)

                // 与已缓存内容不同才写盘，减少 DataStore 无效写入
                if (list != cachedConstructors) {
                    cachedConstructors = list
                    // 磁盘写入放到 IO 线程，避免序列化占用主线程
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            val jsonStr = CONSTRUCTORS_JSON.encodeToString(
                                ListSerializer(UserDto.serializer()),
                                list
                            )
                            dashboardCache.saveConstructorsJson(jsonStr)
                        } catch (_: Exception) {
                            // 静默：写盘失败不影响业务
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 静默处理：保留缓存已渲染的旧数据
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
     * 强制从网络刷新工程历史（下拉刷新时调用）
     *
     * 与 [loadProjects] 的区别：
     * - 不清空已有列表（避免下拉时列表闪烁消失）
     * - 网络失败时若已有缓存数据则不弹错误提示，仅提示"网络异常，显示缓存数据"
     */
    fun forceRefreshProjects() {
        viewModelScope.launch {
            val yearMonth = _uiState.value.selectedYearMonth
            // 标记加载中但不清空列表，让用户看到当前内容上有刷新指示器
            _uiState.value = _uiState.value.copy(isLoadingProjects = true)
            projectsCurrentPage = 1
            try {
                val response = projectApi.getProjects(
                    page = 1,
                    size = projectsPageSize,
                    yearMonth = yearMonth
                )
                if (response.code == 200) {
                    val pageData = response.data
                    if (pageData == null) {
                        _uiState.value = _uiState.value.copy(
                            projects = emptyList(),
                            isLoadingProjects = false,
                            hasMoreProjects = false
                        )
                        // 清空对应月份缓存
                        cachedProjectsByMonth.remove(yearMonth)
                        withContext(kotlinx.coroutines.Dispatchers.IO) {
                            dashboardCache.clearProjectsCache(yearMonth)
                        }
                        return@launch
                    }
                    val projects = pageData.list.map { mapProjectDtoToUiModel(it) }
                    _uiState.value = _uiState.value.copy(
                        projects = projects,
                        isLoadingProjects = false,
                        hasMoreProjects = pageData.list.size >= projectsPageSize
                    )
                    // 网络成功 → 回写内存缓存和磁盘缓存
                    saveProjectsToCache(projects, yearMonth)
                } else {
                    // 服务器返回错误：若已有缓存数据则保留，仅提示
                    val hasCache = _uiState.value.projects.isNotEmpty()
                    _uiState.value = _uiState.value.copy(
                        isLoadingProjects = false,
                        errorMessage = if (hasCache) {
                            "网络异常，显示缓存数据"
                        } else {
                            NetworkErrorHandler.translateServerError(response.msg, "加载工程历史失败")
                        }
                    )
                }
            } catch (e: Exception) {
                val hasCache = _uiState.value.projects.isNotEmpty()
                _uiState.value = _uiState.value.copy(
                    isLoadingProjects = false,
                    errorMessage = if (hasCache) {
                        "网络异常，显示缓存数据"
                    } else {
                        NetworkErrorHandler.translate(e, "加载工程历史失败")
                    }
                )
            }
        }
    }

    /**
     * 加载更多工程历史（分页加载，滚动到底部时触发）
     *
     * 防重复触发直接读取 [DashboardUiState.isLoadingMoreProjects]，
     * 与 UI 层订阅的状态字段统一，避免双源状态不一致。
     */
    fun loadMoreProjects() {
        if (_uiState.value.isLoadingMoreProjects || !_uiState.value.hasMoreProjects) return
        viewModelScope.launch {
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
            }
        }
    }

    /**
     * 冷启动前用缓存立即填充工程历史列表
     *
     * 优先级：进程内存缓存 > 磁盘 DataStore 缓存
     * 目的：让工程历史区域在首屏渲染时就有数据可显示，避免白屏等待网络。
     * 已有内存缓存时跳过磁盘读，减少 IO 开销。
     */
    private suspend fun primeProjectsFromCache() {
        val yearMonth = _uiState.value.selectedYearMonth
        // 1. 内存缓存命中
        cachedProjectsByMonth[yearMonth]?.let { mem ->
            if (mem.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    projects = mem,
                    // 缓存只保存第一页，假设可能还有更多
                    hasMoreProjects = mem.size >= projectsPageSize
                )
                return
            }
        }
        // 2. 磁盘缓存回填
        try {
            val jsonStr = dashboardCache.loadProjectsJson(yearMonth)
            if (jsonStr.isNotBlank()) {
                val list = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    PROJECTS_JSON.decodeFromString(
                        ListSerializer(ProjectHistoryUiModel.serializer()),
                        jsonStr
                    )
                }
                if (list.isNotEmpty()) {
                    cachedProjectsByMonth[yearMonth] = list
                    _uiState.value = _uiState.value.copy(
                        projects = list,
                        hasMoreProjects = list.size >= projectsPageSize
                    )
                }
            }
        } catch (_: Exception) {
            // 缓存损坏时静默忽略，等 loadProjectsSuspend 从网络补齐
        }
    }

    /**
     * 将工程列表写入内存缓存和磁盘缓存
     */
    private suspend fun saveProjectsToCache(projects: List<ProjectHistoryUiModel>, yearMonth: String) {
        // 1. 更新内存缓存
        cachedProjectsByMonth[yearMonth] = projects
        // 2. 异步写入磁盘缓存（IO 线程，避免序列化阻塞主线程）
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val jsonStr = PROJECTS_JSON.encodeToString(
                    ListSerializer(ProjectHistoryUiModel.serializer()),
                    projects
                )
                dashboardCache.saveProjectsJson(yearMonth, jsonStr)
            } catch (_: Exception) {
                // 静默：写盘失败不影响业务
            }
        }
    }

    /**
     * 加载工程历史列表的suspend实现（供并行调用，加载第一页）
     *
     * 采用 cache-first 策略：
     * 1. primeProjectsFromCache 已在冷启动时用缓存渲染，这里直接走网络拉取最新数据
     * 2. 网络成功 → 覆盖 UI 列表并回写缓存
     * 3. 网络失败 → 若已有缓存数据则保留，仅提示"网络异常"；无缓存则提示错误
     */
    private suspend fun loadProjectsSuspend() {
        _uiState.value = _uiState.value.copy(isLoadingProjects = true)
        projectsCurrentPage = 1
        val yearMonth = _uiState.value.selectedYearMonth

        try {
            val response = projectApi.getProjects(
                page = 1,
                size = projectsPageSize,
                yearMonth = yearMonth
            )

            if (response.code == 200) {
                val pageData = response.data
                if (pageData == null) {
                    _uiState.value = _uiState.value.copy(
                        projects = emptyList(),
                        isLoadingProjects = false,
                        hasMoreProjects = false
                    )
                    // 清空对应月份缓存
                    cachedProjectsByMonth.remove(yearMonth)
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        dashboardCache.clearProjectsCache(yearMonth)
                    }
                    return
                }
                // 直接使用列表接口返回的数据（含subprojects），无需再发起N+1详情请求
                val projects = pageData.list.map { mapProjectDtoToUiModel(it) }
                _uiState.value = _uiState.value.copy(
                    projects = projects,
                    isLoadingProjects = false,
                    hasMoreProjects = pageData.list.size >= projectsPageSize
                )
                // 网络成功 → 回写内存缓存和磁盘缓存
                saveProjectsToCache(projects, yearMonth)
            } else {
                // 服务器返回错误：若已有缓存数据则保留，仅提示
                val hasCache = _uiState.value.projects.isNotEmpty()
                _uiState.value = _uiState.value.copy(
                    isLoadingProjects = false,
                    errorMessage = if (hasCache) {
                        "网络异常，显示缓存数据"
                    } else {
                        NetworkErrorHandler.translateServerError(response.msg, "加载工程历史失败")
                    }
                )
            }
        } catch (e: Exception) {
            // 网络异常：若已有缓存数据则保留，仅提示
            val hasCache = _uiState.value.projects.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                isLoadingProjects = false,
                errorMessage = if (hasCache) {
                    "网络异常，显示缓存数据"
                } else {
                    NetworkErrorHandler.translate(e, "加载工程历史失败")
                }
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
            unitDisplayName = getUnitDisplayName(unit),
            remark = dto.remark,
            measuredQuantity = dto.measuredQuantity,
            measuredNote = dto.measuredNote
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
     *
     * 形状切换时清理与新形状不相关的字段，避免残留输入误导用户：
     * - 切到 trapezoid：保留 length/width，清空 height 由用户重新输入
     * - 切到非 trapezoid：清空 height（不再使用）
     * - 切到 circle：保留 length（直径），清空 width（圆形不使用宽度）
     * - 切到 right_triangle：保留 length（底）和 width（高），清空 height
     */
    fun selectSpaceType(spaceType: String) {
        val newShape = getShapeForSpaceType(spaceType)
        val current = _uiState.value
        // 计算切换后的字段清理策略
        val clearedWidth = when (newShape) {
            // 圆形仅使用 length（直径），width 不再相关
            "circle" -> ""
            else -> current.widthCm
        }
        val clearedHeight = if (newShape == "trapezoid") {
            // 切到梯形：height 由用户重新输入，清空避免沿用旧值
            ""
        } else {
            // 切到非梯形：height 不再使用，清空
            ""
        }
        _uiState.value = current.copy(
            selectedSpaceType = spaceType,
            widthCm = clearedWidth,
            heightCm = clearedHeight
        )
        recalculate()
        saveFormDebounced()
    }

    /**
     * 根据空间类型名称返回对应的 shape（rectangle/right_triangle/trapezoid/circle）
     * 未匹配时默认 rectangle，保证向后兼容
     */
    private fun getShapeForSpaceType(spaceTypeName: String): String {
        val st = _uiState.value.spaceTypes.find { it.name == spaceTypeName }
        return st?.shape?.takeIf { it.isNotBlank() } ?: "rectangle"
    }

    /**
     * 获取当前选中空间类型的 shape
     * UI 层根据返回值动态渲染参数输入框（矩形/直角三角形/梯形/圆形）
     */
    fun currentSpaceShape(): String {
        val selected = _uiState.value.selectedSpaceType
        if (selected.isBlank()) return "rectangle"
        return getShapeForSpaceType(selected)
    }

    /**
     * 更新高度（厘米，仅梯形形状使用）
     * 触发防抖保存
     */
    fun updateHeight(value: String) {
        // 仅允许数字与小数点
        if (value.isNotEmpty() && !value.matches(Regex("^\\d*(\\.\\d*)?$"))) return
        _uiState.value = _uiState.value.copy(heightCm = value)
        recalculate()
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
     * 更新实测数量（异形空间现场实测值）
     * 非空且为正数时覆盖按长宽计算的 quantity；清空后回退到按长宽计算
     * 触发防抖保存
     */
    fun updateMeasuredQuantity(value: String) {
        // 只允许数字与小数点
        if (value.isNotEmpty() && !value.matches(Regex("^\\d*(\\.\\d*)?$"))) return
        _uiState.value = _uiState.value.copy(measuredQuantity = value)
        recalculate()
        saveFormDebounced()
    }

    /**
     * 更新实测备注
     * 触发防抖保存
     */
    fun updateMeasuredNote(note: String) {
        _uiState.value = _uiState.value.copy(measuredNote = note)
        saveFormDebounced()
    }

    /**
     * 切换实测信息区展开/折叠状态
     * 实测字段平时少用，默认折叠以简化表单；用户点击标题可展开
     */
    fun toggleMeasuredSection() {
        _uiState.value = _uiState.value.copy(
            isMeasuredSectionExpanded = !_uiState.value.isMeasuredSectionExpanded
        )
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
     *
     * 同时更新 [DashboardUiState.isWorkdaysConsistent] 供 UI 层判断样式，
     * 避免在 Composable 中通过字符串 contains 判断（防止文案调整导致样式失效）。
     */
    private fun validateWorkdays() {
        val state = _uiState.value
        val hint = WorkdaysValidator.validate(
            salaryDistribution = state.salaryDistribution,
            totalWorkdaysInput = state.totalWorkdaysInput,
            selectedConstructorIds = state.selectedConstructorIds,
            workerWorkdays = state.workerWorkdays
        )
        // hint 为空表示无需提示（非工日分配模式或总工日为空），此时 isWorkdaysConsistent 重置为 false
        // hint 包含"不一致"为不一致；其余非空 hint 视为一致
        val isConsistent = hint.isNotEmpty() && !hint.contains("不一致")
        _uiState.value = state.copy(
            workdaysValidationHint = hint,
            isWorkdaysConsistent = isConsistent
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
        // 切换月份时先用缓存立即渲染，再后台拉取最新数据
        viewModelScope.launch {
            primeProjectsFromCache()
            loadProjectsSuspend()
        }
    }

    /**
     * 重新计算预览公式
     *
     * 计算规则与后端 services/calculation.js 保持一致：
     * - unit=length: 数量 = lengthCm / 100 (米)，形状不参与
     * - unit=perimeter: 数量 = (lengthCm + widthCm) * 2 / 100 (米)，形状不参与
     * - unit=area: 按 shape 选择公式（rectangle/right_triangle/trapezoid/circle）
     *
     * 有实测数量时优先使用实测值覆盖上述计算（异形空间场景）。
     */
    private fun recalculate() {
        val state = _uiState.value
        val scheme = state.constructionPlans.find { it.name == state.selectedScheme }
        val lengthCm = state.lengthCm.toDoubleOrNull() ?: 0.0
        val widthCm = state.widthCm.toDoubleOrNull() ?: 0.0
        val heightCm = state.heightCm.toDoubleOrNull() ?: 0.0
        val unitPrice = state.unitPrice
        val shape = getShapeForSpaceType(state.selectedSpaceType)

        // 实测数量：非空且为正数时覆盖按长宽计算的 quantity（异形空间场景）
        val measured = state.measuredQuantity.toDoubleOrNull()
        val hasMeasured = measured != null && measured > 0

        // 根据计量单位计算数量（有实测值时优先使用实测值）
        val quantity = if (hasMeasured) {
            measured!!
        } else {
            calculateQuantityLocally(scheme?.unit, shape, lengthCm, widthCm, heightCm)
        }

        val totalAmount = quantity * unitPrice

        // 生成计算公式文本（含按工日分配的每人分摊明细）
        val formula = buildCalculationFormula(
            scheme, quantity, unitPrice, totalAmount, lengthCm, widthCm, heightCm, shape,
            salaryDistribution = state.salaryDistribution,
            workerWorkdays = state.workerWorkdays,
            selectedConstructorIds = state.selectedConstructorIds,
            constructors = state.constructors,
            hasMeasured = hasMeasured
        )

        _uiState.value = state.copy(
            quantity = quantity,
            totalAmount = totalAmount,
            calculationFormula = formula
        )
    }

    /**
     * 本地预览数量计算（与后端 calculateQuantity 等价）
     * 后端 services/calculation.js 的 normalizeUnit/calculateAreaByShape 已在此复刻
     */
    private fun calculateQuantityLocally(
        unit: String?,
        shape: String,
        lengthCm: Double,
        widthCm: Double,
        heightCm: Double
    ): Double {
        val lengthM = lengthCm / 100
        val widthM = widthCm / 100
        val heightM = heightCm / 100
        return when (unit) {
            "length" -> lengthM
            "perimeter" -> (lengthM + widthM) * 2
            "area", null -> calculateAreaByShape(shape, lengthM, widthM, heightM)
            // 兼容历史别名：m²/平方米 走面积；m/米 走长度
            "m²", "㎡", "平方米" -> calculateAreaByShape(shape, lengthM, widthM, heightM)
            "m", "米" -> lengthM
            else -> calculateAreaByShape(shape, lengthM, widthM, heightM)
        }
    }

    /**
     * 根据空间形状计算面积（平方米），与后端 calculateAreaByShape 等价
     */
    private fun calculateAreaByShape(
        shape: String,
        lengthM: Double,
        widthM: Double,
        heightM: Double
    ): Double {
        return when (shape) {
            "right_triangle" -> 0.5 * lengthM * widthM
            "trapezoid" -> 0.5 * (lengthM + widthM) * heightM
            "circle" -> Math.PI * (lengthM / 2) * (lengthM / 2)
            "rectangle" -> lengthM * widthM
            else -> lengthM * widthM
        }
    }

    /**
     * 构建计算公式文本
     *
     * 公式展示规则：
     * - 有实测值时直接展示"实测 数量 × 单价 = 金额"
     * - 按形状推导：
     *   - unit=length：长度(m) × ¥单价/m = ¥金额
     *   - unit=perimeter：周长(m) = (长+宽)×2 × ¥单价/m = ¥金额
     *   - unit=area：
     *     - rectangle：长×宽 = 面积(m²) × ¥单价/m² = ¥金额
     *     - right_triangle：底×高/2 = 面积(m²) × ¥单价/m² = ¥金额
     *     - trapezoid：(上底+下底)×高/2 = 面积(m²) × ¥单价/m² = ¥金额
     *     - circle：π×(直径/2)² = 面积(m²) × ¥单价/m² = ¥金额
     *
     * 按工日分配模式下追加显示每人分摊金额
     */
    private fun buildCalculationFormula(
        scheme: SchemeInfo?,
        quantity: Double,
        unitPrice: Double,
        totalAmount: Double,
        lengthCm: Double,
        widthCm: Double,
        heightCm: Double,
        shape: String,
        salaryDistribution: String = "average",
        workerWorkdays: Map<Int, String> = emptyMap(),
        selectedConstructorIds: Set<Int> = emptySet(),
        constructors: List<UserDto> = emptyList(),
        // 是否使用实测数量（异形空间场景，为true时公式标注"实测"）
        hasMeasured: Boolean = false
    ): String {
        val q = numberFormat.format(quantity)
        val p = numberFormat.format(unitPrice)
        val t = numberFormat.format(totalAmount)

        val lengthM = lengthCm / 100
        val widthM = widthCm / 100
        val heightM = heightCm / 100

        // 基础公式：有实测值时标注"实测"，否则按形状与单位展示推导过程
        val baseFormula = if (hasMeasured) {
            // 实测数量直接展示，不显示长宽推导（异形空间无法用长宽准确表达）
            val unitLabel = when (scheme?.unit) {
                "area" -> "m²"
                "perimeter", "length" -> "m"
                else -> "m²"
            }
            "实测 $q $unitLabel × ¥$p/$unitLabel = ¥$t"
        } else when (scheme?.unit) {
            "area" -> {
                // 按形状生成面积推导公式
                val areaFormula = when (shape) {
                    "right_triangle" ->
                        "${numberFormat.format(lengthM)}×${numberFormat.format(widthM)}÷2"
                    "trapezoid" ->
                        "(${numberFormat.format(lengthM)}+${numberFormat.format(widthM)})×${numberFormat.format(heightM)}÷2"
                    "circle" ->
                        "π×(${numberFormat.format(lengthM)}÷2)²"
                    "rectangle" ->
                        "${numberFormat.format(lengthM)}×${numberFormat.format(widthM)}"
                    else ->
                        "${numberFormat.format(lengthM)}×${numberFormat.format(widthM)}"
                }
                "$areaFormula = $q m² × ¥$p/m² = ¥$t"
            }
            "perimeter" -> {
                // 周长不参与 shape 计算，统一按矩形 (长+宽)×2
                val perimeter = (lengthCm + widthCm) * 2 / 100
                "${numberFormat.format(perimeter)} m × ¥$p/m = ¥$t"
            }
            "length" -> {
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

        // 表单校验（抽取为独立函数，便于阅读与单测）
        val formError = validateProjectForm(state)
        if (formError != null) {
            _uiState.value = state.copy(errorMessage = formError)
            return
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

                // 校验已通过，这里重新解析数值参数（校验函数保证非空且>0）
                val length = state.lengthCm.toDoubleOrNull()!!
                val width = state.widthCm.toDoubleOrNull()
                val height = state.heightCm.toDoubleOrNull()
                val shape = getShapeForSpaceType(state.selectedSpaceType)

                val request = CreateProjectRequest(
                    name = state.customerAddress,
                    spaceType = state.selectedSpaceType,
                    constructionScheme = state.selectedScheme,
                    length = length,
                    width = width ?: 0.0,
                    salaryDistribution = state.salaryDistribution,
                    constructors = state.selectedConstructorIds.map { ConstructorItem(it) },
                    remark = state.remark.ifBlank { null },
                    workerWorkDays = workerWorkDays,
                    // 实测数量：非空且为正数时传给后端覆盖按长宽计算的quantity（异形空间场景）
                    measuredQuantity = state.measuredQuantity.toDoubleOrNull()?.takeIf { it > 0 },
                    measuredNote = state.measuredNote.ifBlank { null },
                    // 高度：仅梯形等需要三维参数的形状传值，其他形状传 null
                    height = if (shape == "trapezoid") height else null
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
                        heightCm = "",
                        measuredQuantity = "",
                        measuredNote = "",
                        // 重置后实测区折叠（新建工程默认无实测数据）
                        isMeasuredSectionExpanded = false,
                        remark = "",
                        unitPrice = 0.0,
                        quantity = 0.0,
                        totalAmount = 0.0,
                        calculationFormula = "",
                        workerWorkdays = emptyMap()
                    )

                    // 清除表单缓存（保留地址映射）
                    viewModelScope.launch { dashboardCache.clearFormCache() }
                    // 保存成功后表单已重置并清除了缓存，无未落盘修改；
                    // 否则onCleared兜底保存会用重置后的状态覆盖clearFormCache的结果
                    formDirty = false

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
     * 工程表单校验
     *
     * 抽取自 [saveProject]，便于阅读和单测。校验规则：
     * - 客户地址、空间类型、施工方案 非空
     * - 长度 必须为正数
     * - 宽度：非 length 单位且非 circle 形状时必填
     * - 高度：仅 trapezoid 形状必填
     * - 施工人员 非空
     * - 单价 必须>0
     * - 按工日分配时：每位施工人员的工日数（非空时）必须>0
     *
     * @return 错误提示文案，null 表示校验通过
     */
    private fun validateProjectForm(state: DashboardUiState): String? {
        // 表单验证
        if (state.customerAddress.isBlank()) return "请输入客户地址"
        if (state.selectedSpaceType.isBlank()) return "请选择空间类型"
        if (state.selectedScheme.isBlank()) return "请选择施工方案"

        // 参数校验：根据空间形状决定哪些字段必填
        // - rectangle：长+宽
        // - right_triangle：底(length)+高(width)
        // - trapezoid：上底(length)+下底(width)+高(height)
        // - circle：直径(length)
        // 同时考虑施工方案 unit=length 时不强制 width（与历史逻辑保持一致）
        val length = state.lengthCm.toDoubleOrNull()
        if (length == null || length <= 0) return "请输入有效的长度"

        val schemeUnit = currentSchemeUnit()
        val shape = getShapeForSpaceType(state.selectedSpaceType)
        val needsWidth = schemeUnit != "length" && shape != "circle"
        val width = state.widthCm.toDoubleOrNull()
        if (needsWidth && (width == null || width <= 0)) return "请输入有效的宽度"

        // 梯形必须提供 height
        val height = state.heightCm.toDoubleOrNull()
        if (shape == "trapezoid" && (height == null || height <= 0)) return "请输入梯形的高"

        if (state.selectedConstructorIds.isEmpty()) return "请选择施工人员"
        if (state.unitPrice <= 0) return "单价无效，请重新选择施工方案"

        // 按工日分配模式校验：每人工日数必须>0（空值按默认1处理，无需用户必须输入）
        if (state.salaryDistribution == "work_days") {
            val invalidWorkdays = state.selectedConstructorIds.any { id ->
                val raw = state.workerWorkdays[id]?.trim() ?: ""
                // 空值视为1.0（有效）；非空时解析必须>0
                if (raw.isEmpty()) false
                else (raw.toDoubleOrNull() ?: 0.0) <= 0
            }
            if (invalidWorkdays) return "按工日分配模式下，每位施工人员的工日数必须大于0"
        }

        return null
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
                // 如果附件弹窗正在显示且正是当前上传的工程，立即刷新附件列表
                // 修复：原实现只刷新工程列表，附件弹窗内列表不更新，需关闭重开才能看到新附件
                val viewingProjectId = _uiState.value.viewingFilesProjectId
                if (viewingProjectId == projectId) {
                    loadProjectFiles(projectId)
                }
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
                AppLog.e(TAG, "加载附件列表失败: projectId=$projectId, ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    viewingFiles = emptyList(),
                    isLoadingFiles = false,
                    errorMessage = NetworkErrorHandler.translate(e, "加载附件列表失败")
                )
            }
        }
    }

    /**
     * 拼接附件完整访问URL（代理 ServerConfig.buildFileUrl，供 UI 层调用）
     */
    suspend fun buildFileUrl(relativePath: String): String =
        serverConfig.buildFileUrl(relativePath)

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
        // 用户有新修改，标记为未落盘状态（onCleared 据此决定是否兜底保存）
        formDirty = true
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
                        remark = state.remark,
                        measuredQuantity = state.measuredQuantity,
                        measuredNote = state.measuredNote,
                        heightCm = state.heightCm
                    )
                )
            }
            // 落盘完成，清除未保存标记。
            // 仅当自己仍是最新调度的防抖Job时才清除：保存期间用户若又输入，
            // saveFormJob已被替换为新Job，此时不清除（否则新输入的未落盘标记被误删）
            if (saveFormJob === coroutineContext[kotlinx.coroutines.Job]) {
                formDirty = false
            }
        }
    }

    /**
     * ViewModel销毁时兜底保存
     *
     * 场景：用户修改表单后800ms内退出App，防抖Job未执行就被取消，导致最后一次修改丢失。
     *
     * 覆盖保护：仅当 formDirty=true（存在未落盘的用户修改）时才兜底保存表单。
     * 若启动后表单尚未从缓存恢复（或恢复后用户未做任何修改）就退出，
     * 保存空表单/旧快照会覆盖磁盘上的有效缓存，导致"输入内容下次启动丢失"。
     *
     * 实现说明：
     * - 使用 viewModelScope + NonCancellable 异步保存，避免在主线程执行IO操作
     * - NonCancellable 确保即使 viewModelScope 已开始取消流程，保存操作仍能完成
     *   （viewModelScope 在 onCleared 调用后才正式取消，此时 launch + NonCancellable 仍可执行）
     * - 相比 GlobalScope：协程受 ViewModel 生命周期约束，避免进程级协程累积导致内存泄漏
     * - 相比 runBlocking：不阻塞主线程，避免ANR风险
     */
    override fun onCleared() {
        super.onCleared()
        // 捕获快照（非volatile读取在主线程，onCleared也在主线程调用，无竞态）
        val hasUnsavedChanges = formDirty
        // 取消未执行的防抖Job（避免重复保存）
        saveFormJob?.cancel()
        // 异步兜底保存表单快照（仅在有未落盘修改时，防抖未完成时强制落盘）
        if (hasUnsavedChanges) {
            // 使用 NonCancellable 确保保存操作不被取消
            viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
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
                            remark = state.remark,
                            measuredQuantity = state.measuredQuantity,
                            measuredNote = state.measuredNote,
                            heightCm = state.heightCm
                        )
                    )
                } catch (_: Exception) {
                    // 静默处理，销毁阶段无法向用户报错
                }
            }
        }
        // 地址映射兜底落盘（无覆盖风险：内存Map是最新状态，始终落盘）
        viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
            try {
                dashboardCache.saveAddressMap(addressConstructorMap.toMap())
            } catch (_: Exception) {
                // 静默处理，销毁阶段无法向用户报错
            }
        }
    }
}
