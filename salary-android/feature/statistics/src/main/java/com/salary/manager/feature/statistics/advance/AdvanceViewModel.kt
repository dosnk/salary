package com.salary.manager.feature.statistics.advance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.data.local.UserStorage
import com.salary.core.network.api.AdvanceApi
import com.salary.core.network.api.CreateAdvanceRequest
import com.salary.core.network.api.UserApi
import com.salary.core.network.api.UserDto
import com.salary.core.ui.state.ListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 预支管理ViewModel - 调用后端预支接口管理预支记录列表
 *
 * 对接接口：
 * - GET /v1/advances（AdvanceApi.getAdvances）获取预支列表，支持按userId筛选
 * - POST /v1/advances（AdvanceApi.createAdvance）创建预支记录
 *
 * 权限规则（V2.0 重新界定）：
 * - admin：不能创建预支（可查看全部预支，可按人员筛选）
 * - constructor（施工员）：可以为自己创建预支（只能查看自己的预支）
 * - documenter（资料员）：不能创建预支（可查看全部预支，可按人员筛选）
 *
 * 创建预支必填字段（对齐后端createAdvanceSchema的Joi校验）：
 * - userId：用户ID（施工员为自己创建时取当前登录用户ID）
 * - advanceAmount：预支金额（>0且≤100000）
 * - advanceDate：预支日期（ISO格式 yyyy-MM-dd）
 * - remark：备注（可选，≤500字符）
 */
@HiltViewModel
class AdvanceViewModel @Inject constructor(
    private val advanceApi: AdvanceApi,
    private val userApi: UserApi,
    private val userStorage: UserStorage
) : ViewModel() {

    private val _state = MutableStateFlow<ListUiState<AdvanceItem>>(ListUiState.Loading)
    val state: StateFlow<ListUiState<AdvanceItem>> = _state.asStateFlow()

    /** 创建预支的错误消息（null表示无错误） */
    private val _createErrorMessage = MutableStateFlow<String?>(null)
    val createErrorMessage: StateFlow<String?> = _createErrorMessage.asStateFlow()

    /** 创建预支是否正在进行中 */
    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    /** 当前用户角色（用于UI层控制创建按钮显示） */
    private val _currentUserRole = MutableStateFlow("")
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    /** 施工人员列表（用于资料员/管理员按人员筛选预支记录） */
    private val _constructors = MutableStateFlow<List<UserDto>>(emptyList())
    val constructors: StateFlow<List<UserDto>> = _constructors.asStateFlow()

    /** 当前选中的筛选人员ID（null表示不筛选，显示全部） */
    private val _selectedUserId = MutableStateFlow<Int?>(null)
    val selectedUserId: StateFlow<Int?> = _selectedUserId.asStateFlow()

    init {
        loadUserRole()
        loadAdvances()
        // 资料员/管理员可按人员筛选，加载施工人员列表
        loadConstructorsIfNeeded()
    }

    /** 加载当前用户角色，用于UI层控制创建按钮显示 */
    private fun loadUserRole() {
        viewModelScope.launch {
            _currentUserRole.value = userStorage.getRole() ?: ""
        }
    }

    /** 当前用户是否可以创建预支（仅施工员constructor可以） */
    fun canCreateAdvance(): Boolean = _currentUserRole.value == "constructor"

    /** 当前用户是否可以按人员筛选预支（资料员和管理员可以） */
    fun canFilterByUser(): Boolean = _currentUserRole.value == "admin" || _currentUserRole.value == "documenter"

    /** 加载施工人员列表（仅资料员/管理员需要，用于按人员筛选） */
    private fun loadConstructorsIfNeeded() {
        viewModelScope.launch {
            if (!canFilterByUser()) return@launch
            try {
                val response = userApi.getConstructors()
                if (response.code == 200 && response.data != null) {
                    _constructors.value = response.data!!
                }
            } catch (_: Exception) {
                // 静默处理，不影响主流程
            }
        }
    }

    /** 设置筛选人员ID（null表示显示全部） */
    fun setSelectedUserId(userId: Int?) {
        _selectedUserId.value = userId
        loadAdvances()
    }

    /**
     * 加载预支记录列表
     * 调用后端预支列表接口，将 AdvanceDto 映射为 AdvanceItem
     * 字段映射：id←id、amount←advance_amount、remark←remark、date←advance_date、createdAt←created_at、settled←settled
     */
    fun loadAdvances() {
        viewModelScope.launch {
            _state.value = ListUiState.Loading
            try {
                val response = advanceApi.getAdvances(1, 20, _selectedUserId.value)
                if (response.code == 200 && response.data != null) {
                    val data = response.data!!
                    _state.value = ListUiState.Success(
                        items = data.list.map {
                            AdvanceItem(
                                id = it.id,
                                amount = it.advanceAmount.toString(),
                                remark = it.remark,
                                date = it.advanceDate ?: "",
                                createdAt = it.createdAt ?: "",
                                settled = it.settled,
                                userName = it.userName,
                                creatorName = it.creatorName
                            )
                        },
                        hasMore = data.page * data.size < data.total,
                        page = data.page
                    )
                } else {
                    _state.value = ListUiState.Error(
                        NetworkErrorHandler.translateServerError(response.msg, "加载预支记录失败")
                    )
                }
            } catch (e: Exception) {
                _state.value = ListUiState.Error(NetworkErrorHandler.translate(e, "加载预支记录失败"))
            }
        }
    }

    /**
     * 创建预支记录
     * 对齐后端createAdvanceSchema：userId必填、advanceAmount必填(>0,≤100000)、advanceDate必填(ISO日期)、remark可选(≤500字符)
     * 后端权限中间件requireAdvanceCreate：仅施工员可创建，且会自动用当前登录用户ID覆盖传入的userId（targetUserId = currentUserId）
     *
     * @param amount 预支金额字符串（需>0且≤100000）
     * @param advanceDate 预支日期（ISO格式 yyyy-MM-dd）
     * @param remark 备注（可选）
     */
    fun createAdvance(amount: String, advanceDate: String, remark: String?) {
        viewModelScope.launch {
            try {
                _createErrorMessage.value = null
                _isCreating.value = true

                // 客户端校验：金额必须>0
                val amountValue = amount.toDoubleOrNull()
                if (amountValue == null || amountValue <= 0) {
                    _createErrorMessage.value = "预支金额必须大于0"
                    return@launch
                }
                // 客户端校验：金额上限100000（对齐后端Joi规则）
                if (amountValue > 100000) {
                    _createErrorMessage.value = "预支金额不能超过100000元"
                    return@launch
                }
                // 客户端校验：日期必填
                if (advanceDate.isBlank()) {
                    _createErrorMessage.value = "请选择预支日期"
                    return@launch
                }

                // 获取当前登录用户ID（施工员为自己创建预支）
                val userId = userStorage.getUserId()
                if (userId == null) {
                    _createErrorMessage.value = "登录信息已过期，请重新登录"
                    return@launch
                }

                advanceApi.createAdvance(
                    CreateAdvanceRequest(
                        userId = userId,
                        advanceAmount = amountValue,
                        advanceDate = advanceDate,
                        remark = remark
                    )
                )
                loadAdvances() // 刷新列表
            } catch (e: Exception) {
                // 创建失败时反馈错误给UI，不刷新列表避免覆盖已有数据
                _createErrorMessage.value = NetworkErrorHandler.translate(e, "创建预支失败")
            } finally {
                _isCreating.value = false
            }
        }
    }

    /** 清除创建预支的错误消息 */
    fun clearCreateError() {
        _createErrorMessage.value = null
    }
}
