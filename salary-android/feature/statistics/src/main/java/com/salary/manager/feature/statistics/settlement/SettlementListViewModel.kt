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
 * 结算列表ViewModel - 管理待结算工程列表的状态
 * 对接后端 GET /v1/settlements 接口
 */
@HiltViewModel
class SettlementListViewModel @Inject constructor(
    private val settlementApi: SettlementApi
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
                val response = settlementApi.getSettlements(page = 1, size = 20)
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
                        NetworkErrorHandler.translateServerError(response.msg, "加载结算列表失败")
                    )
                }
            } catch (e: Exception) {
                _state.value = ListUiState.Error(NetworkErrorHandler.translate(e, "加载结算列表失败"))
            }
        }
    }

    /** 将后端 SettlementDto 映射为 UI 模型 SettlementProjectItem */
    private fun SettlementDto.toUiModel(): SettlementProjectItem {
        // 结算单名称：以月份区间作为显示名称
        val displayName = if (startMonth.isNotEmpty() && endMonth.isNotEmpty()) {
            "${startMonth} ~ ${endMonth}"
        } else {
            "结算单 #$id"
        }
        return SettlementProjectItem(
            id = id,
            name = displayName,
            amount = totalAmount.toString()
        )
    }
}
