package com.salary.manager.feature.statistics.settlement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettlementPreviewViewModel @Inject constructor(
    // 后续注入 SettlementApi
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SettlementPreviewData>>(UiState.Loading)
    val state: StateFlow<UiState<SettlementPreviewData>> = _state.asStateFlow()

    fun loadPreview(projectIds: List<Int>) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                // TODO: 调用 SettlementApi.preview
                _state.value = UiState.Success(
                    SettlementPreviewData(
                        totalAmount = "28500.00",
                        advanceAmount = "5000.00",
                        actualAmount = "23500.00",
                        projects = listOf(
                            SettlementProjectItem(1, "张宅吊顶工程", "12000.00"),
                            SettlementProjectItem(2, "万达办公楼", "16500.00")
                        )
                    )
                )
            } catch (e: Exception) {
                _state.value = UiState.Error(NetworkErrorHandler.translate(e, "加载结算预览失败"))
            }
        }
    }
}
