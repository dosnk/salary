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
    val createdAt: String
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

    /** 用户昵称 */
    private val _userNickname = MutableStateFlow("")
    val userNickname: StateFlow<String> = _userNickname.asStateFlow()

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
        loadUserNickname()
    }

    /**
     * 加载用户昵称
     */
    private fun loadUserNickname() {
        viewModelScope.launch {
            _userNickname.value = userStorage.getNickname() ?: ""
        }
    }

    /**
     * 加载工程列表
     */
    private fun loadProjects(isLoadMore: Boolean = false) {
        if (isLoadingMore) return
        // 重试限制：如果超过最大重试次数且不是加载更多，拒绝重试
        if (!isLoadMore && retryCount >= maxRetryCount) {
            _state.value = com.salary.core.ui.state.ListUiState.Error("加载失败次数过多，请稍后重试")
            return
        }
        viewModelScope.launch {
            if (!isLoadMore) {
                _state.value = com.salary.core.ui.state.ListUiState.Loading
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
                        ProjectUiModel(
                            id = dto.id,
                            name = dto.name,
                            status = dto.status,
                            totalAmount = String.format("%.2f", dto.totalAmount),
                            settlementStatus = dto.settlementStatus,
                            salaryDistribution = dto.salaryDistribution,
                            workerNames = dto.workers.map { it.nickname },
                            workerCount = dto.workers.size,
                            createdAt = dto.createdAt
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
                    retryCount++
                    _state.value = com.salary.core.ui.state.ListUiState.Error(
                        NetworkErrorHandler.translateServerError(response.msg, "加载工程列表失败")
                    )
                }
            } catch (e: Exception) {
                retryCount++
                _state.value = com.salary.core.ui.state.ListUiState.Error(
                    NetworkErrorHandler.translate(e, "加载工程列表失败")
                )
            } finally {
                isLoadingMore = false
            }
        }
    }

    /** 刷新列表 */
    fun refresh() = loadProjects(isLoadMore = false)

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
