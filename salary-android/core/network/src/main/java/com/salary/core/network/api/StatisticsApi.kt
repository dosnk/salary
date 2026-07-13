package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 统计API接口
 */
interface StatisticsApi {

    /** 月度统计 */
    @GET("v1/statistics/monthly")
    suspend fun getMonthlyStatistics(
        @Query("month") month: String? = null
    ): ApiResponse<MonthlyStatsDto>

    /** 工程统计 */
    @GET("v1/statistics/projects")
    suspend fun getProjectStatistics(): ApiResponse<ProjectStatsDto>

    /** 收入统计 */
    @GET("v1/statistics/income")
    suspend fun getIncomeStatistics(
        @Query("startMonth") startMonth: String? = null,
        @Query("endMonth") endMonth: String? = null
    ): ApiResponse<IncomeStatsDto>

    /** 人员统计 */
    @GET("v1/statistics/workers")
    suspend fun getWorkerStatistics(): ApiResponse<WorkerStatsDto>

    /**
     * 仪表盘卡片统计（统计页顶部4个卡片）
     * 所有计算由后端完成，前端直接展示。
     */
    @GET("v1/statistics/dashboard")
    suspend fun getDashboard(): ApiResponse<DashboardStatsDto>
}

/**
 * 仪表盘卡片统计数据DTO（后端返回snake_case，前端映射为camelCase）
 *
 * 对应4个卡片：
 * - 待结算工程：unsettledProjectCount + unsettledAmount
 * - 预支金额：advanceCount + advanceTotal
 * - 今年工程量：yearProjectCount + yearProjectAmount
 * - 月均收入：monthlyAvgCount + monthlyAvgAmount
 */
@Serializable
data class DashboardStatsDto(
    /** 卡片1：待结算工程份数（工程级，settling状态） */
    @SerialName("unsettled_project_count")
    val unsettledProjectCount: Int = 0,
    /** 卡片1：个人应收总额（个人级，wage_distributions合计） */
    @SerialName("unsettled_amount")
    val unsettledAmount: Double = 0.0,
    /** 卡片2：未结算预支条数 */
    @SerialName("advance_count")
    val advanceCount: Int = 0,
    /** 卡片2：未结算预支总金额 */
    @SerialName("advance_total")
    val advanceTotal: Double = 0.0,
    /** 卡片3：今年创建的所有工程份数（所有状态，工程级） */
    @SerialName("year_project_count")
    val yearProjectCount: Int = 0,
    /** 卡片3：今年创建的所有工程总额（工程级 total_amount 合计） */
    @SerialName("year_project_amount")
    val yearProjectAmount: Double = 0.0,
    /** 卡片4：月均份数（今年已结算工程数 / 当前月份，工程级） */
    @SerialName("monthly_avg_count")
    val monthlyAvgCount: Double = 0.0,
    /** 卡片4：月均金额（今年个人已结算工资 / 当前月份，个人级） */
    @SerialName("monthly_avg_amount")
    val monthlyAvgAmount: Double = 0.0
)

@Serializable
data class MonthlyStatsDto(
    val totalIncome: String,
    val totalProjects: Int,
    val totalSettled: String,
    val totalUnsettled: String,
    val monthlyData: List<MonthlyDataItem> = emptyList()
)

@Serializable
data class MonthlyDataItem(
    val month: String,
    val income: String,
    val projects: Int
)

@Serializable
data class ProjectStatsDto(
    val total: Int,
    val preparing: Int,
    val constructing: Int,
    val completed: Int,
    val cancelled: Int
)

@Serializable
data class IncomeStatsDto(
    val totalIncome: String,
    val settledIncome: String,
    val unsettledIncome: String,
    val monthlyBreakdown: List<IncomeBreakdownItem> = emptyList()
)

@Serializable
data class IncomeBreakdownItem(
    val month: String,
    val amount: String
)

@Serializable
data class WorkerStatsDto(
    val totalWorkers: Int,
    val activeWorkers: Int,
    val topEarners: List<WorkerEarningItem> = emptyList()
)

@Serializable
data class WorkerEarningItem(
    val userId: Int,
    val nickname: String,
    val totalEarnings: String
)
