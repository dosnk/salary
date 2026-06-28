package com.salary.manager.feature.statistics.settlement

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
 * 结算列表ViewModel - 管理待结算工程列表的状态
 */
@HiltViewModel
class SettlementListViewModel @Inject constructor(
    // 后续注入 SettlementApi
) : ViewModel() {

    private val _state = MutableStateFlow<ListUiState<SettlementProjectItem>>(ListUiState.Loading)
    val state: StateFlow<ListUiState<SettlementProjectItem>> = _state.asStateFlow()

    init {
        loadSettlements()
    }

    /** 加载待结算工程列表 */
    fun loadSettlements() {
        viewModelScope.launch {
            _state.value = ListUiState.Loading
            try {
                // TODO: 调用 SettlementApi 获取待结算工程
                _state.value = ListUiState.Success(
                    items = emptyList(),
                    hasMore = false,
                    page = 1
                )
            } catch (e: Exception) {
                _state.value = ListUiState.Error(e.message ?: "加载结算列表失败")
            }
        }
    }
}
