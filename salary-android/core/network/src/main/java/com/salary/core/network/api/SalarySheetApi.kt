package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming

/**
 * 工资单API接口 - 对应前端 salarySheet.ts
 *
 * 提供结算单数据加载、计算、结算操作等功能
 */
interface SalarySheetApi {

    /** 获取施工方案列表 */
    @GET("v1/salary-sheet/construction-plans")
    suspend fun getConstructionPlans(): ApiResponse<List<ConstructionPlanDto>>

    /** 获取结算单工程数据（含子项目明细、方案汇总、预支等） */
    @GET("v1/salary-sheet/projects")
    suspend fun getProjects(): ApiResponse<SalarySheetProjectsDto>

    /** 获取预支列表 */
    @GET("v1/salary-sheet/advances")
    suspend fun getAdvances(): ApiResponse<List<AdvanceDataDto>>

    /** 执行结算操作 */
    @POST("v1/salary-sheet/settle")
    suspend fun settle(@Body body: SettleRequest): ApiResponse<SettleResponseDto>

    /** 获取已结算工程列表 */
    @GET("v1/salary-sheet/settled-projects")
    suspend fun getSettledProjects(): ApiResponse<List<SalaryProjectDto>>

    /** 获取已结算预支列表 */
    @GET("v1/salary-sheet/settled-advances")
    suspend fun getSettledAdvances(): ApiResponse<List<AdvanceDataDto>>

    /** 获取结算历史列表 */
    @GET("v1/salary-sheet/settlement-history")
    suspend fun getSettlementHistory(): ApiResponse<List<SettlementHistoryDto>>

    /** 计算结算金额 */
    @POST("v1/settlements/calculate")
    suspend fun calculate(@Body body: CalculateRequest): ApiResponse<CalculateResultDto>

    /** 导出结算历史Excel */
    @Streaming
    @GET("v1/settlements/history/export/{settlementId}")
    suspend fun exportSettlementExcel(@Path("settlementId") settlementId: Int): ResponseBody
}

// ========== 施工方案 ==========

/** 施工方案数据 */
@Serializable
data class ConstructionPlanDto(
    val id: Int,
    val name: String,
    val unit: String? = null,
    val price: Double? = null
)

// ========== 结算单工程数据 ==========

/** 结算单工程数据响应 */
@Serializable
data class SalarySheetProjectsDto(
    val projects: List<SalaryProjectDto> = emptyList(),
    @SerialName("plan_totals")
    val planTotals: Map<String, PlanTotalDto> = emptyMap(),
    @SerialName("grand_total")
    val grandTotal: Double = 0.0,
    @SerialName("total_advance")
    val totalAdvance: Double = 0.0,
    @SerialName("final_total")
    val finalTotal: Double = 0.0,
    val advances: List<AdvanceDataDto> = emptyList()
)

/** 工程数据（含子项目明细） */
@Serializable
data class SalaryProjectDto(
    val id: Int,
    val project_name: String = "",
    val created_at: String = "",
    val salary_distribution: String = "",
    val subprojects: List<SubprojectDto> = emptyList(),
    @SerialName("plan_quantities")
    val planQuantities: Map<String, PlanQuantityDto> = emptyMap()
)

/** 子项目明细 */
@Serializable
data class SubprojectDto(
    val subproject_id: Int,
    val space_type_name: String = "",
    val plan_id: Int = 0,
    val plan_name: String = "",
    val unit: String = "",
    val price: String = "0",
    val quantity: Double = 0.0,
    val user_quantity: Double = 0.0,
    val user_amount: Double = 0.0
)

/** 方案数量汇总 */
@Serializable
data class PlanQuantityDto(
    @SerialName("total_quantity")
    val totalQuantity: Double = 0.0,
    @SerialName("total_amount")
    val totalAmount: Double = 0.0,
    val unit: String? = null,
    val price: String? = null,
    val plan_name: String? = null
)

/** 方案总计 */
@Serializable
data class PlanTotalDto(
    @SerialName("total_quantity")
    val totalQuantity: Double = 0.0,
    @SerialName("total_amount")
    val totalAmount: Double = 0.0
)

// ========== 预支数据 ==========

/** 预支数据 */
@Serializable
data class AdvanceDataDto(
    val id: Int,
    val user_id: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val advance_amount: Double = 0.0,
    val advance_date: String = "",
    val remark: String = ""
)

// ========== 结算操作 ==========

/** 结算请求 */
@Serializable
data class SettleRequest(
    val projectIds: List<Int>
)

/** 结算响应 */
@Serializable
data class SettleResponseDto(
    @SerialName("settlement_id")
    val settlementId: Int = 0,
    @SerialName("settlement_no")
    val settlementNo: String = "",
    @SerialName("total_amount")
    val totalAmount: Double = 0.0,
    @SerialName("advance_amount")
    val advanceAmount: Double = 0.0,
    @SerialName("actual_amount")
    val actualAmount: Double = 0.0
)

/** 计算请求 */
@Serializable
data class CalculateRequest(
    val projectIds: List<Int>
)

/** 计算结果 */
@Serializable
data class CalculateResultDto(
    @SerialName("plan_totals")
    val planTotals: Map<String, PlanTotalDto> = emptyMap(),
    @SerialName("grand_total")
    val grandTotal: Double = 0.0,
    @SerialName("total_advance")
    val totalAdvance: Double = 0.0,
    @SerialName("final_total")
    val finalTotal: Double = 0.0,
    val advances: List<AdvanceDataDto> = emptyList()
)

// ========== 结算历史 ==========

/** 结算历史数据 */
@Serializable
data class SettlementHistoryDto(
    val settlement_id: Int,
    val settlement_no: String = "",
    val start_month: String = "",
    val end_month: String = "",
    val total_amount: Double = 0.0,
    val advance_amount: Double = 0.0,
    val actual_amount: Double = 0.0,
    val confirmed: Boolean = false,
    val confirmed_at: String? = null,
    val settled_by: Int = 0,
    val settled_by_username: String = "",
    val settled_by_nickname: String = "",
    val created_at: String = "",
    val projects: List<SalaryProjectDto> = emptyList(),
    val advances: List<AdvanceDataDto> = emptyList(),
    @SerialName("plan_totals")
    val planTotals: Map<String, PlanTotalDto> = emptyMap(),
    @SerialName("grand_total")
    val grandTotal: Double = 0.0,
    @SerialName("total_advance")
    val totalAdvance: Double = 0.0,
    @SerialName("final_total")
    val finalTotal: Double = 0.0
)
