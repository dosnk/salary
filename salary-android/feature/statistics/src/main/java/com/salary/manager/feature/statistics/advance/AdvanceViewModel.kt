package com.salary.manager.feature.statistics.advance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.ui.state.ListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 预支管理ViewModel - 管理预支记录列表的状态
 */
@HiltViewModel
class AdvanceViewModel @Inject constructor(
    // 后续注入 AdvanceApi
) : ViewModel() {

    private val _state = MutableStateFlow<ListUiState<AdvanceItem>>(ListUiState.Loading)
    val state: StateFlow<ListUiState<AdvanceItem>> = _state.asStateFlow()

    init {
        loadAdvances()
    }

    /** 加载预支记录列表 */
    fun loadAdvances() {
        viewModelScope.launch {
            _state.value = ListUiState.Loading
            try {
                // TODO: 调用 AdvanceApi 获取预支列表
                _state.value = ListUiState.Success(
                    items = emptyList(),
                    hasMore = false,
                    page = 1
                )
            } catch (e: Exception) {
                _state.value = ListUiState.Error(NetworkErrorHandler.translate(e, "加载预支记录失败"))
            }
        }
    }

    /** 创建预支记录 */
    fun createAdvance(amount: String, remark: String?) {
        viewModelScope.launch {
            try {
                // TODO: 调用 AdvanceApi.createAdvance
                loadAdvances() // 刷新列表
            } catch (e: Exception) {
                // 错误处理
            }
        }
    }
}
