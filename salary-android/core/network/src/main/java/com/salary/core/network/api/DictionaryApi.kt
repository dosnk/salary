package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * 字典管理API接口
 */
interface DictionaryApi {

    // ========== 空间类型 ==========

    /** 获取空间类型列表 */
    @GET("v1/dictionary/space-types")
    suspend fun getSpaceTypes(): ApiResponse<List<DictionaryItemDto>>

    /** 创建空间类型（admin） */
    @POST("v1/dictionary/space-types")
    suspend fun createSpaceType(@Body body: CreateDictionaryRequest): ApiResponse<DictionaryItemDto>

    /** 更新空间类型（admin） */
    @PUT("v1/dictionary/space-types/{id}")
    suspend fun updateSpaceType(@Path("id") id: Int, @Body body: UpdateDictionaryRequest): ApiResponse<Unit>

    /** 删除空间类型（admin） */
    @DELETE("v1/dictionary/space-types/{id}")
    suspend fun deleteSpaceType(@Path("id") id: Int): ApiResponse<Unit>

    // ========== 施工方案 ==========

    /** 获取施工方案列表 */
    @GET("v1/dictionary/construction-plans")
    suspend fun getConstructionPlans(): ApiResponse<List<DictionaryItemDto>>

    /** 创建施工方案（admin） */
    @POST("v1/dictionary/construction-plans")
    suspend fun createConstructionPlan(@Body body: CreateDictionaryRequest): ApiResponse<DictionaryItemDto>

    /** 更新施工方案（admin） */
    @PUT("v1/dictionary/construction-plans/{id}")
    suspend fun updateConstructionPlan(@Path("id") id: Int, @Body body: UpdateDictionaryRequest): ApiResponse<Unit>

    /** 删除施工方案（admin） */
    @DELETE("v1/dictionary/construction-plans/{id}")
    suspend fun deleteConstructionPlan(@Path("id") id: Int): ApiResponse<Unit>

    // ========== 其他字典 ==========

    /** 获取工资分配类型 */
    @GET("v1/dictionary/wage-distribution-types")
    suspend fun getWageDistributionTypes(): ApiResponse<List<DictionaryItemDto>>

    /** 获取工程状态列表 */
    @GET("v1/dictionary/project-statuses")
    suspend fun getProjectStatuses(): ApiResponse<List<DictionaryItemDto>>

    /** 获取结算状态列表 */
    @GET("v1/dictionary/settlement-statuses")
    suspend fun getSettlementStatuses(): ApiResponse<List<DictionaryItemDto>>

    /** 获取施工单位列表 */
    @GET("v1/dictionary/construction-units")
    suspend fun getConstructionUnits(): ApiResponse<List<DictionaryItemDto>>
}

@Serializable
data class DictionaryItemDto(
    val id: Int,
    val name: String,
    val description: String? = null,
    val sortOrder: Int = 0,
    /** 计量单位：area=面积, perimeter=周长, length=长度 */
    val unit: String? = null,
    /** 单价 */
    val price: Double? = null
)

@Serializable
data class CreateDictionaryRequest(
    val name: String,
    val description: String? = null
)

@Serializable
data class UpdateDictionaryRequest(
    val name: String? = null,
    val description: String? = null
)
