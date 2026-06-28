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
 * 结算历史ViewModel - 管理已结算记录的状态
 */
@HiltViewModel
class SettlementHistoryViewModel @Inject constructor(
    // 后续注入 SettlementApi
) : ViewModel() {

    private val _state = MutableStateFlow<ListUiState<SettlementHistoryItem>>(ListUiState.Loading)
    val state: StateFlow<ListUiState<SettlementHistoryItem>> = _state.asStateFlow()

    init {
        loadHistory()
    }

    /** 加载结算历史记录 */
    fun loadHistory() {
        viewModelScope.launch {
            _state.value = ListUiState.Loading
            try {
                // TODO: 调用 SettlementApi.getHistory
                _state.value = ListUiState.Success(
                    items = emptyList(),
                    hasMore = false,
                    page = 1
                )
            } catch (e: Exception) {
                _state.value = ListUiState.Error(NetworkErrorHandler.translate(e, "加载结算历史失败"))
            }
        }
    }
}
