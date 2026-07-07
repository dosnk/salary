package com.salary.manager.feature.statistics.settlement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.network.api.SettlementApi
import com.salary.core.network.api.SettlementDto
import com.salary.core.ui.state.ListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 结算历史ViewModel - 管理已结算记录的状态
 * 对接后端 GET /v1/settlements/history 接口
 */
@HiltViewModel
class SettlementHistoryViewModel @Inject constructor(
    private val settlementApi: SettlementApi
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
                val response = settlementApi.getSettlementHistory(page = 1, size = 20)
                if (response.code == 200) {
                    val data = response.data
                    val items = data?.list?.map { it.toUiModel() } ?: emptyList()
                    val hasMore = (data?.page ?: 1) * (data?.size ?: 20) < (data?.total ?: 0)
                    _state.value = ListUiState.Success(
                        items = items,
                        hasMore = hasMore,
                        page = data?.page ?: 1
                    )
                } else {
                    _state.value = ListUiState.Error(
                        NetworkErrorHandler.translateServerError(response.msg, "加载结算历史失败")
                    )
                }
            } catch (e: Exception) {
                _state.value = ListUiState.Error(NetworkErrorHandler.translate(e, "加载结算历史失败"))
            }
        }
    }

    /** 将后端 SettlementDto 映射为 UI 模型 SettlementHistoryItem */
    private fun SettlementDto.toUiModel(): SettlementHistoryItem {
        // SettlementDto 缺少 settlementNo/advanceAmount/actualAmount/confirmed 字段
        // 后端返回的 DTO 字段有限，缺失字段用默认值填充
        return SettlementHistoryItem(
            id = id,
            settlementNo = "STL-$id",
            totalAmount = totalAmount.toString(),
            advanceAmount = "0.00",
            actualAmount = totalAmount.toString(),
            settledAt = createdAt ?: "",
            confirmed = status == "confirmed"
        )
    }
}
