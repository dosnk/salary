package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 结算API接口
 */
interface SettlementApi {

    /** 结算预览计算 */
    @POST("v1/settlements/calculate")
    suspend fun calculateSettlement(@Body body: CalculateSettlementRequest): ApiResponse<SettlementPreviewDto>

    /** 创建结算 */
    @POST("v1/settlements")
    suspend fun createSettlement(@Body body: CreateSettlementRequest): ApiResponse<SettlementDto>

    /** 获取结算列表 */
    @GET("v1/settlements")
    suspend fun getSettlements(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<SettlementListResponse>

    /** 获取结算详情 */
    @GET("v1/settlements/{id}")
    suspend fun getSettlementDetail(@Path("id") id: Int): ApiResponse<SettlementDto>

    /** 确认结算 */
    @POST("v1/settlements/{id}/confirm")
    suspend fun confirmSettlement(@Path("id") id: Int): ApiResponse<Unit>

    /** 获取结算历史 */
    @GET("v1/settlements/history")
    suspend fun getSettlementHistory(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<SettlementListResponse>
}

@Serializable
data class CalculateSettlementRequest(
    val projectIds: List<Int>
)

@Serializable
data class CreateSettlementRequest(
    val startMonth: String,
    val endMonth: String,
    val projectIds: List<Int>? = null
)

@Serializable
data class SettlementPreviewDto(
    val projects: List<SettlementProjectItem>,
    val totalAmount: Double,
    val totalAdvance: Double,
    val netAmount: Double
)

@Serializable
data class SettlementProjectItem(
    val projectId: Int,
    val projectName: String,
    val amount: Double,
    val advance: Double,
    val netAmount: Double
)

@Serializable
data class SettlementDto(
    val id: Int,
    val startMonth: String,
    val endMonth: String,
    val totalAmount: Double,
    val status: String,
    val createdAt: String? = null,
    val projects: List<SettlementProjectItem> = emptyList()
)

@Serializable
data class SettlementListResponse(
    val list: List<SettlementDto>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)
