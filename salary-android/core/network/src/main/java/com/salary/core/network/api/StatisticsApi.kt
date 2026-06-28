package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
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
}

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
