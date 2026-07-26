package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.http.GET

/**
 * 健康检查响应数据
 *
 * 安全加固：后端仅返回在线状态，不暴露 uptime/timestamp 等运行时信息
 * 前端通过HTTP状态码(200)判断在线，通过请求往返耗时计算延迟
 */
@Serializable
data class HealthResponse(
    val status: String
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
