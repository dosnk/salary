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
        @Query("pageSize") pageSize: Int = 20
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
    val pageSize: Int
)

/**
 * 预支DTO
 * 后端返回snake_case字段名，使用@SerialName映射
 */
@Serializable
data class AdvanceDto(
    val id: Int,
    @SerialName("user_id")
    val userId: Int,
    @SerialName("project_id")
    val projectId: Int? = null,
    val amount: Double,
    val reason: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("user_name")
    val userName: String? = null,
    @SerialName("project_name")
    val projectName: String? = null
)

@Serializable
data class CreateAdvanceRequest(
    val amount: Double,
    @SerialName("projectId")
    val projectId: Int? = null,
    val reason: String? = null
)
