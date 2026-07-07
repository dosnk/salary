package com.salary.manager.feature.statistics.monthly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.network.api.MonthlyDataItem
import com.salary.core.network.api.MonthlyStatsDto
import com.salary.core.network.api.StatisticsApi
import com.salary.core.ui.state.UiState
import com.salary.manager.feature.statistics.charts.BarChartData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * 月度统计ViewModel
 * 对接后端 GET /v1/statistics/monthly 接口
 *
 * 注意：后端 MonthlyStatsDto 缺少 completedCount 和 advanceTotal 字段，
 * 这两项暂显示为0，待后端补充字段后自动生效。
 */
@HiltViewModel
class MonthlyStatsViewModel @Inject constructor(
    private val statisticsApi: StatisticsApi
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<MonthlyStatsData>>(UiState.Loading)
    val state: StateFlow<UiState<MonthlyStatsData>> = _state.asStateFlow()

    private val calendar = Calendar.getInstance()
    private val monthFormat = SimpleDateFormat("yyyy年M月", Locale.CHINA)
    // 后端期望的月份格式为 "yyyy-MM"
    private val apiMonthFormat = SimpleDateFormat("yyyy-MM", Locale.CHINA)

    private val _currentMonth = MutableStateFlow(monthFormat.format(calendar.time))
    val currentMonth: StateFlow<String> = _currentMonth.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                // 将 "yyyy年M月" 格式转为后端期望的 "yyyy-MM" 格式
                val apiMonth = apiMonthFormat.format(calendar.time)
                val response = statisticsApi.getMonthlyStatistics(apiMonth)
                val data = response.data
                if (response.code == 200 && data != null) {
                    _state.value = UiState.Success(data.toUiModel())
                } else {
                    _state.value = UiState.Error(
                        NetworkErrorHandler.translateServerError(response.msg, "加载月度统计失败")
                    )
                }
            } catch (e: Exception) {
                _state.value = UiState.Error(NetworkErrorHandler.translate(e, "加载月度统计失败"))
            }
        }
    }

    /** 将后端 MonthlyStatsDto 映射为 UI 模型 MonthlyStatsData */
    private fun MonthlyStatsDto.toUiModel(): MonthlyStatsData {
        // 后端缺少 completedCount 和 advanceTotal 字段，暂用0填充
        return MonthlyStatsData(
            projectCount = totalProjects,
            completedCount = 0,
            totalIncome = totalIncome,
            advanceTotal = "0.00",
            incomeChart = monthlyData.map { it.toBarChartData() }
        )
    }

    /** 将 MonthlyDataItem 映射为 BarChartData */
    private fun MonthlyDataItem.toBarChartData(): BarChartData {
        // income 为 String 类型，需转为 Float
        val incomeValue = income.toFloatOrNull() ?: 0f
        return BarChartData(label = month, value = incomeValue)
    }

    /** 上一个月 */
    fun previousMonth() {
        calendar.add(Calendar.MONTH, -1)
        _currentMonth.value = monthFormat.format(calendar.time)
        loadStats()
    }

    /** 下一个月 */
    fun nextMonth() {
        calendar.add(Calendar.MONTH, 1)
        _currentMonth.value = monthFormat.format(calendar.time)
        loadStats()
    }
}
