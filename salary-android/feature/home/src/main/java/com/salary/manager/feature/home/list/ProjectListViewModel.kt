package com.salary.manager.feature.home.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.data.local.UserStorage
import com.salary.core.network.api.ProjectApi
import com.salary.core.network.dto.UpdateProjectRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 工程列表UI模型
 * totalWorkdays: 按工日分配模式下的总工日（用于计算工日工费）
 * workerWageDetails: 按工日分配模式下的各施工人员工日及工费明细
 */
data class ProjectUiModel(
    val id: Int,
    val name: String,
    val status: String,
    val totalAmount: String,
    val settlementStatus: String?,
    val salaryDistribution: String?,
    val workerNames: List<String>,
    val workerCount: Int,
    val createdAt: String,
    /** 总工日（按工日分配模式下使用） */
    val totalWorkdays: Double = 0.0,
    /** 各施工人员的工日及工费明细（仅按工日分配模式下使用） */
    val workerWageDetails: List<WorkerWageDetail> = emptyList()
)

/**
 * 施工人员工日工费明细（工程列表卡片按工日分配模式下显示）
 */
data class WorkerWageDetail(
    val nickname: String,
    /** 工日数 */
    val workdays: Double,
    /** 按工日比例分摊的工费 */
    val wage: Double
)

/**
 * 高级筛选条件
 */
data class AdvancedFilterState(
    val year: Int? = null,
    val month: Int? = null,
    val status: String? = null,
    val settlementStatus: String? = null,
    val startDate: String? = null,
    val endDate: String? = null
) {
    /** 是否有激活的筛选条件 */
    val hasActiveFilters: Boolean
        get() = month != null || status != null || settlementStatus != null || startDate != null || endDate != null
}

/**
 * 工程列表ViewModel
 */
@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val projectApi: ProjectApi,
    private val userStorage: UserStorage
) : ViewModel() {

    private val _state = MutableStateFlow<com.salary.core.ui.state.ListUiState<ProjectUiModel>>(
        com.salary.core.ui.state.ListUiState.Loading
    )
    val state: StateFlow<com.salary.core.ui.state.ListUiState<ProjectUiModel>> = _state.asStateFlow()

    private val _selectedStatus = MutableStateFlow<String?>(null)
    val selectedStatus: StateFlow<String?> = _selectedStatus.asStateFlow()

    /** 高级筛选条件 */
    private val _advancedFilter = MutableStateFlow(AdvancedFilterState())
    val advancedFilter: StateFlow<AdvancedFilterState> = _advancedFilter.asStateFlow()

    /** 用户昵称（从UserStorage响应式获取） */
    val userNickname: StateFlow<String> = userStorage.nicknameFlow

    /** 成功消息 */
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    /** 错误消息 */
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var currentKeyword: String? = null
    private var currentPage = 1
    private var isLoadingMore = false
    /** 错误重试次数，最多3次 */
    private var retryCount = 0
    private val maxRetryCount = 3

    init {
        loadProjects()
    }

    /**
     * 加载工程列表
     * @param isLoadMore 是否为加载更多
     * @param silent 静默模式：不显示Loading状态、不覆盖错误状态，用于Tab切换时的后台刷新
     */
    private fun loadProjects(isLoadMore: Boolean = false, silent: Boolean = false) {
        if (isLoadingMore) return
        // 重试限制：如果超过最大重试次数且不是加载更多，拒绝重试（静默模式跳过此限制）
        if (!isLoadMore && !silent && retryCount >= maxRetryCount) {
            _state.value = com.salary.core.ui.state.ListUiState.Error("加载失败次数过多，请稍后重试")
            return
        }
        viewModelScope.launch {
            if (!isLoadMore) {
                // 静默模式下不显示Loading，避免页面闪烁
                if (!silent) {
                    _state.value = com.salary.core.ui.state.ListUiState.Loading
                }
                currentPage = 1
            }
            isLoadingMore = true

            try {
                val filter = _advancedFilter.value
                val response = projectApi.getProjects(
                    page = currentPage,
                    size = 20,
                    keyword = currentKeyword,
                    status = filter.status ?: _selectedStatus.value,
                    settlementStatus = filter.settlementStatus,
                    yearMonth = filter.month?.let { "${filter.year ?: java.time.LocalDate.now().year}-${String.format("%02d", it)}" }
                )

                if (response.code == 200) {
                    // 成功时重置重试计数
                    retryCount = 0
                    val pageData = response.data ?: run {
                        _state.value = com.salary.core.ui.state.ListUiState.Error("暂无工程数据")
                        return@launch
                    }
                    val newItems = pageData.list.map { dto ->
                        // 计算总工日（按工日分配模式下用于计算工日工费）
                        val totalWorkdays = dto.workers.sumOf { it.workdays ?: 0.0 }
                        // 按工日分配模式下计算每个施工人员的工日工费明细
                        val totalAmount = dto.totalAmount
                        val workerWageDetails = if (dto.salaryDistribution == "work_days" && totalWorkdays > 0) {
                            dto.workers.map { worker ->
                                val days = worker.workdays ?: 0.0
                                // 按工日比例分摊：总额 × (个人工日 / 总工日)
                                val wage = if (days > 0) totalAmount * (days / totalWorkdays) else 0.0
                                WorkerWageDetail(
                                    nickname = worker.nickname,
                                    workdays = days,
                                    wage = wage
                                )
                            }
                        } else emptyList()
                        ProjectUiModel(
                            id = dto.id,
                            name = dto.name,
                            status = dto.status,
                            totalAmount = String.format("%.2f", dto.totalAmount),
                            settlementStatus = dto.settlementStatus,
                            salaryDistribution = dto.salaryDistribution,
                            workerNames = dto.workers.map { it.nickname },
                            workerCount = dto.workers.size,
                            createdAt = dto.createdAt,
                            totalWorkdays = totalWorkdays,
                            workerWageDetails = workerWageDetails
                        )
                    }

                    val currentItems = if (isLoadMore) {
                        (_state.value as? com.salary.core.ui.state.ListUiState.Success)?.items.orEmpty()
                    } else emptyList()

                    _state.value = com.salary.core.ui.state.ListUiState.Success(
                        items = currentItems + newItems,
                        hasMore = pageData.hasNext,
                        page = currentPage
                    )
                } else {
                    // 加载更多失败时回退页码，避免跳页
                    if (isLoadMore) currentPage--
                    if (!silent) {
                        retryCount++
                        _state.value = com.salary.core.ui.state.ListUiState.Error(
                            NetworkErrorHandler.translateServerError(response.msg, "加载工程列表失败")
                        )
                    }
                }
            } catch (e: Exception) {
                // 加载更多失败时回退页码，避免跳页
                if (isLoadMore) currentPage--
                if (!silent) {
                    retryCount++
                    _state.value = com.salary.core.ui.state.ListUiState.Error(
                        NetworkErrorHandler.translate(e, "加载工程列表失败")
                    )
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    /** 刷新列表 - 重置重试计数，允许用户从错误状态恢复 */
    fun refresh() {
        retryCount = 0
        loadProjects(isLoadMore = false)
    }

    /** 静默刷新（不显示Loading、不覆盖已有状态），用于Tab切换时后台刷新数据 */
    fun silentRefresh() = loadProjects(isLoadMore = false, silent = true)

    /** 加载更多 */
    fun loadMore() {
        val currentState = _state.value
        if (currentState is com.salary.core.ui.state.ListUiState.Success && currentState.hasMore) {
            currentPage++
            loadProjects(isLoadMore = true)
        }
    }

    /** 更新搜索关键词 */
    fun updateKeyword(keyword: String) {
        currentKeyword = keyword.ifBlank { null }
        loadProjects()
    }

    /** 更新状态筛选 */
    fun updateStatus(status: String?) {
        _selectedStatus.value = status
        loadProjects()
    }

    /** 更新高级筛选条件 */
    fun updateAdvancedFilter(filter: AdvancedFilterState) {
        _advancedFilter.value = filter
        loadProjects()
    }

    /** 清除单个筛选条件 */
    fun clearFilter(type: String) {
        val current = _advancedFilter.value
        _advancedFilter.value = when (type) {
            "month" -> current.copy(year = null, month = null)
            "status" -> {
                _selectedStatus.value = null
                current.copy(status = null)
            }
            "settlementStatus" -> current.copy(settlementStatus = null)
            "date" -> current.copy(startDate = null, endDate = null)
            else -> current
        }
        loadProjects()
    }

    /** 清除所有筛选条件 */
    fun clearAllFilters() {
        _selectedStatus.value = null
        _advancedFilter.value = AdvancedFilterState()
        currentKeyword = null
        loadProjects()
    }

    /**
     * 确认工程完工 - 对齐Vue前端handleStatusChange逻辑
     * 调用updateProject更新状态为completed，本地同步更新结算状态为settling
     */
    fun confirmProjectComplete(projectId: Int) {
        viewModelScope.launch {
            try {
                val response = projectApi.updateProject(projectId, UpdateProjectRequest(status = "completed"))
                if (response.code == 200) {
                    // 本地更新状态，不重新加载列表（保持滚动位置）
                    val currentState = _state.value
                    if (currentState is com.salary.core.ui.state.ListUiState.Success) {
                        val updatedItems = currentState.items.map {
                            if (it.id == projectId) {
                                it.copy(status = "completed", settlementStatus = "settling")
                            } else it
                        }
                        _state.value = currentState.copy(items = updatedItems)
                    }
                    _successMessage.value = "工程状态已更新"
                } else {
                    _errorMessage.value = NetworkErrorHandler.translateServerError(response.msg, "修改工程状态失败")
                }
            } catch (e: Exception) {
                _errorMessage.value = NetworkErrorHandler.translate(e, "修改工程状态失败")
            }
        }
    }

    /** 清除成功消息 */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    /** 清除错误消息 */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
