package com.salary.manager.feature.statistics.monthly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.ui.state.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class MonthlyStatsViewModel @Inject constructor(
    // 后续注入 StatisticsApi
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<MonthlyStatsData>>(UiState.Loading)
    val state: StateFlow<UiState<MonthlyStatsData>> = _state.asStateFlow()

    private val calendar = Calendar.getInstance()
    private val monthFormat = SimpleDateFormat("yyyy年M月", Locale.CHINA)

    private val _currentMonth = MutableStateFlow(monthFormat.format(calendar.time))
    val currentMonth: StateFlow<String> = _currentMonth.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                // TODO: 调用 StatisticsApi.getMonthlyStats
                _state.value = UiState.Success(
                    MonthlyStatsData(
                        projectCount = 12,
                        completedCount = 8,
                        totalIncome = "28500.00",
                        advanceTotal = "5000.00",
                        incomeChart = listOf(
                            com.salary.manager.feature.statistics.charts.BarChartData("1月", 15000f),
                            com.salary.manager.feature.statistics.charts.BarChartData("2月", 22000f),
                            com.salary.manager.feature.statistics.charts.BarChartData("3月", 18000f),
                            com.salary.manager.feature.statistics.charts.BarChartData("4月", 25000f),
                            com.salary.manager.feature.statistics.charts.BarChartData("5月", 30000f),
                            com.salary.manager.feature.statistics.charts.BarChartData("6月", 28500f),
                        )
                    )
                )
            } catch (e: Exception) {
                _state.value = UiState.Error(NetworkErrorHandler.translate(e, "加载月度统计失败"))
            }
        }
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
