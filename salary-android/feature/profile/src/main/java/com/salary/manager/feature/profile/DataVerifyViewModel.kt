package com.salary.manager.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.network.api.DataVerifyResultDto
import com.salary.core.network.api.SystemApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 数据一致性校验ViewModel
 *
 * 调用后端 /v1/system/data-consistency/verify 接口，
 * 对金额、统计、结算三个口径进行一致性校验并展示结果。
 *
 * 仅 admin 角色可访问（后端已通过 requireAdmin() 限制）。
 */
@HiltViewModel
class DataVerifyViewModel @Inject constructor(
    private val systemApi: SystemApi
) : ViewModel() {

    /** 校验结果（null 表示尚未执行校验） */
    private val _result = MutableStateFlow<DataVerifyResultDto?>(null)
    val result: StateFlow<DataVerifyResultDto?> = _result.asStateFlow()

    /** 是否正在执行校验 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 错误信息（null 表示无错误） */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * 执行数据一致性校验
     * @param userId 可选，指定用户ID进行校验（null=全部用户）
     */
    fun verify(userId: Int? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = systemApi.verifyDataConsistency(userId = userId, tolerance = 0.01)
                if (response.code == 200 && response.data != null) {
                    _result.value = response.data
                } else {
                    _error.value = NetworkErrorHandler.translateServerError(
                        response.msg,
                        "数据一致性校验失败"
                    )
                }
            } catch (e: Exception) {
                _error.value = NetworkErrorHandler.translate(e, "数据一致性校验失败")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** 清除错误状态 */
    fun clearError() {
        _error.value = null
    }
}
