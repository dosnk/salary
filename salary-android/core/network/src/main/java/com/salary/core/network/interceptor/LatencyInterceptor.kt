package com.salary.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * API延迟与连接状态拦截器
 *
 * 测量每次API请求的实际网络耗时，并追踪后端连接状态：
 * - 请求成功：更新延迟值 + 标记在线
 * - 连接失败：标记离线 + 记录错误信息
 */
class LatencyInterceptor @Inject constructor(
    private val latencyTracker: LatencyTracker
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startTime = System.currentTimeMillis()

        return try {
            val response = chain.proceed(request)
            val latency = System.currentTimeMillis() - startTime

            // 只记录API请求（排除静态资源等）
            val path = response.request.url.encodedPath
            if (path.startsWith("/v1/")) {
                if (response.isSuccessful) {
                    // 请求成功：更新延迟 + 标记在线
                    latencyTracker.onSuccess(latency)
                } else {
                    // 服务器返回错误状态码（如500、502、503）
                    // 4xx通常是业务错误，服务器仍然在线
                    if (response.code >= 500) {
                        latencyTracker.onFailure("服务器错误(${response.code})")
                    } else {
                        // 4xx表示服务器在线，只是请求有问题
                        latencyTracker.onSuccess(latency)
                    }
                }
            }

            response
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            val path = request.url.encodedPath

            if (path.startsWith("/v1/")) {
                val errorMessage = when (e) {
                    is ConnectException -> "无法连接到服务器"
                    is SocketTimeoutException -> "连接超时(${latency}ms)"
                    is UnknownHostException -> "服务器地址无法解析"
                    is IOException -> "网络连接失败"
                    else -> "连接异常: ${e.message}"
                }
                latencyTracker.onFailure(errorMessage)
            }

            throw e
        }
    }
}
