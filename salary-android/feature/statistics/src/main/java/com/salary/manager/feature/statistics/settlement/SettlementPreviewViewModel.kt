package com.salary.manager.feature.statistics.settlement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.network.api.CalculateSettlementRequest
import com.salary.core.network.api.SettlementApi
import com.salary.core.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 结算预览ViewModel - 调用后端结算计算接口获取预览数据
 *
 * 对接接口：POST /v1/settlements/calculate（SettlementApi.calculateSettlement）
 * 数据映射：SettlementPreviewDto → SettlementPreviewData
 */
@HiltViewModel
class SettlementPreviewViewModel @Inject constructor(
    private val settlementApi: SettlementApi
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<SettlementPreviewData>>(UiState.Loading)
    val state: StateFlow<UiState<SettlementPreviewData>> = _state.asStateFlow()

    /**
     * 加载结算预览数据
     * 调用后端计算接口，将 SettlementPreviewDto 映射为 SettlementPreviewData
     * 字段映射：totalAmount←totalAmount、advanceAmount←totalAdvance、actualAmount←netAmount
     */
    fun loadPreview(projectIds: List<Int>) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val response = settlementApi.calculateSettlement(
                    CalculateSettlementRequest(projectIds)
                )
                if (response.code == 200 && response.data != null) {
                    val dto = response.data!!
                    _state.value = UiState.Success(
                        SettlementPreviewData(
                            totalAmount = dto.totalAmount.toString(),
                            advanceAmount = dto.totalAdvance.toString(),
                            actualAmount = dto.netAmount.toString(),
                            projects = dto.projects.map {
                                SettlementProjectItem(
                                    id = it.projectId,
                                    name = it.projectName,
                                    amount = it.amount.toString()
                                )
                            }
                        )
                    )
                } else {
                    _state.value = UiState.Error(
                        NetworkErrorHandler.translateServerError(response.msg, "加载结算预览失败")
                    )
                }
            } catch (e: Exception) {
                _state.value = UiState.Error(NetworkErrorHandler.translate(e, "加载结算预览失败"))
            }
        }
    }
}
