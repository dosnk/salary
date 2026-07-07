package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * 健康检查响应数据
 */
@Serializable
data class HealthResponse(
    val status: String,
    val timestamp: Long,
    val uptime: Double
)

/**
 * 健康检查API接口
 *
 * 用于前端主动探测后端在线状态，无需鉴权
 */
interface HealthApi {
    @GET("v1/health")
    suspend fun checkHealth(): ApiResponse<HealthResponse>
}
