package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 预支API接口
 */
interface AdvanceApi {

    /** 获取预支列表 */
    @GET("v1/advances")
    suspend fun getAdvances(
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 20
    ): ApiResponse<AdvanceListResponse>

    /** 创建预支 */
    @POST("v1/advances")
    suspend fun createAdvance(@Body body: CreateAdvanceRequest): ApiResponse<AdvanceDto>

    /** 删除预支 */
    @DELETE("v1/advances/{id}")
    suspend fun deleteAdvance(@Path("id") id: Int): ApiResponse<Unit>
}

@Serializable
data class AdvanceListResponse(
    val list: List<AdvanceDto>,
    val total: Int,
    val page: Int,
    val size: Int
)

/**
 * 预支DTO
 * 后端返回snake_case字段名，使用@SerialName映射
 * 对齐后端wage_advances表实际字段：user_id, advance_amount, advance_date, settled, remark, created_by, created_at
 */
@Serializable
data class AdvanceDto(
    val id: Int,
    @SerialName("user_id")
    val userId: Int,
    @SerialName("advance_amount")
    val advanceAmount: Double = 0.0,
    @SerialName("advance_date")
    val advanceDate: String? = null,
    val settled: Boolean = false,
    @SerialName("settlement_id")
    val settlementId: Int? = null,
    @SerialName("created_by")
    val createdBy: Int? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val remark: String? = null,
    // 列表查询时JOIN返回的关联字段
    @SerialName("user_name")
    val userName: String? = null,
    @SerialName("creator_name")
    val creatorName: String? = null
)

/**
 * 创建预支请求
 * 对齐后端createAdvanceSchema的Joi校验规则：userId(必填)、advanceAmount(必填,>0,≤100000)、advanceDate(必填,ISO日期)、remark(可选,≤500字符)
 */
@Serializable
data class CreateAdvanceRequest(
    @SerialName("userId")
    val userId: Int,
    @SerialName("advanceAmount")
    val advanceAmount: Double,
    @SerialName("advanceDate")
    val advanceDate: String,
    val remark: String? = null
)
