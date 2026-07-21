package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
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

    /** 获取已结算工程列表（扁平结构：每行一个project+plan组合） */
    @GET("v1/salary-sheet/settled-projects")
    suspend fun getSettledProjects(): ApiResponse<List<SettledProjectRowDto>>

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
    @SerialName("project_name")
    val projectName: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("salary_distribution")
    val salaryDistribution: String = "",
    val subprojects: List<SubprojectDto> = emptyList(),
    // 后端返回字段名为驼峰 planQuantities（非 snake_case），需与后端对齐
    @SerialName("planQuantities")
    val planQuantities: Map<String, PlanQuantityDto> = emptyMap()
)

/** 子项目明细 */
@Serializable
data class SubprojectDto(
    @SerialName("subproject_id")
    val subprojectId: Int,
    @SerialName("space_type_name")
    val spaceTypeName: String = "",
    @SerialName("plan_id")
    val planId: Int = 0,
    @SerialName("plan_name")
    val planName: String = "",
    val unit: String = "",
    /** 单价（后端NUMERIC类型，P11后返回数字） */
    val price: Double = 0.0,
    val quantity: Double = 0.0,
    @SerialName("user_quantity")
    val userQuantity: Double = 0.0,
    @SerialName("user_amount")
    val userAmount: Double = 0.0
)

/** 方案数量汇总 */
@Serializable
data class PlanQuantityDto(
    @SerialName("total_quantity")
    val totalQuantity: Double = 0.0,
    @SerialName("total_amount")
    val totalAmount: Double = 0.0,
    val unit: String? = null,
    /** 单价（后端NUMERIC类型，P11后返回数字） */
    val price: Double? = null,
    @SerialName("plan_name")
    val planName: String? = null
)

/** 方案总计 */
@Serializable
data class PlanTotalDto(
    // 后端存在两种格式：结算单预览接口返回 snake_case（total_quantity/total_amount），
    // 结算历史接口从快照反序列化返回驼峰（totalQuantity/totalAmount）。
    // 使用 @JsonNames 同时兼容两种格式，主名 @SerialName 用 snake_case 对齐预览接口。
    @SerialName("total_quantity")
    @JsonNames("totalQuantity")
    val totalQuantity: Double = 0.0,
    @SerialName("total_amount")
    @JsonNames("totalAmount")
    val totalAmount: Double = 0.0
)

// ========== 预支数据 ==========

/** 预支数据 */
@Serializable
data class AdvanceDataDto(
    val id: Int,
    @SerialName("user_id")
    val userId: Int = 0,
    val username: String = "",
    val nickname: String = "",
    @SerialName("advance_amount")
    val advanceAmount: Double = 0.0,
    @SerialName("advance_date")
    val advanceDate: String = "",
    val remark: String = ""
)

// ========== 结算操作 ==========

/** 结算请求 */
@Serializable
data class SettleRequest(
    val projectIds: List<Int>,
    /** 结算备注（可选，空字符串表示无备注） */
    val remark: String = ""
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
    @SerialName("settlement_id")
    val settlementId: Int,
    @SerialName("settlement_no")
    val settlementNo: String = "",
    @SerialName("start_month")
    val startMonth: String = "",
    @SerialName("end_month")
    val endMonth: String = "",
    @SerialName("total_amount")
    val totalAmount: Double = 0.0,
    @SerialName("advance_amount")
    val advanceAmount: Double = 0.0,
    @SerialName("actual_amount")
    val actualAmount: Double = 0.0,
    val confirmed: Boolean = false,
    @SerialName("confirmed_at")
    val confirmedAt: String? = null,
    @SerialName("settled_by")
    val settledBy: Int = 0,
    @SerialName("settled_by_username")
    val settledByUsername: String = "",
    @SerialName("settled_by_nickname")
    val settledByNickname: String = "",
    @SerialName("settled_at")
    val settledAt: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    val projects: List<SalaryProjectDto> = emptyList(),
    val advances: List<AdvanceDataDto> = emptyList(),
    @SerialName("plan_totals")
    val planTotals: Map<String, PlanTotalDto> = emptyMap(),
    @SerialName("grand_total")
    val grandTotal: Double = 0.0,
    @SerialName("total_advance")
    val totalAdvance: Double = 0.0,
    @SerialName("final_total")
    val finalTotal: Double = 0.0,
    /** 结算备注（null或空字符串表示无备注） */
    val remark: String? = null
)

// ========== 已结算工程（扁平行结构） ==========

/**
 * 已结算工程扁平行DTO
 * 后端 /v1/salary-sheet/settled-projects 返回的是扁平行结构（每行一个project+plan组合），
 * 不是嵌套结构，因此需要单独的DTO接收。
 * 字段对齐后端SQL返回：p.id, p.name as project_name, p.created_at, cp.id as plan_id,
 * cp.name as plan_name, cp.unit, user_amount, user_quantity
 */
@Serializable
data class SettledProjectRowDto(
    val id: Int = 0,
    @SerialName("project_name")
    val projectName: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("plan_id")
    val planId: Int = 0,
    @SerialName("plan_name")
    val planName: String = "",
    val unit: String? = null,
    /** 用户分摊金额（元） */
    @SerialName("user_amount")
    val userAmount: Double = 0.0,
    /** 用户分摊数量 */
    @SerialName("user_quantity")
    val userQuantity: Double = 0.0
)
