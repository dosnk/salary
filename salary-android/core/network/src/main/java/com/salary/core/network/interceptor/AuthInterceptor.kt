package com.salary.core.network.interceptor

import com.salary.core.data.local.ServerConfig
import com.salary.core.data.local.TokenStorage
import com.salary.core.data.local.UserStorage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Token认证拦截器
 * 1. 请求时自动添加Authorization请求头
 * 2. 收到401响应时自动使用refreshToken刷新accessToken
 * 3. 刷新成功后重试原请求，失败则清除token并通知UI退出登录
 * 4. 支持并发安全：多个请求同时401时只刷新一次
 *
 * 性能优化：
 * - 通过TokenStorage的同步内存缓存读取token，避免runBlocking阻塞OkHttp线程池
 * - 仅在token刷新保存（写操作）和最终清除token时使用runBlocking
 * - 写操作频率极低（仅401时触发），不影响正常请求性能
 */
class AuthInterceptor @Inject constructor(
    private val tokenStorage: TokenStorage,
    private val serverConfig: ServerConfig,
    private val userStorage: UserStorage
) : Interceptor {

    /** 用于刷新token的独立OkHttpClient（不含AuthInterceptor，避免循环依赖） */
    private val refreshClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 刷新锁，防止并发多个401请求同时刷新token */
    private val refreshLock = Any()

    /** 是否正在刷新token */
    @Volatile
    private var isRefreshing = false

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 跳过登录/注册/刷新接口
        val path = originalRequest.url.encodedPath
        if (path.contains("/auth/login") || path.contains("/auth/register") || path.contains("/auth/refresh")) {
            return chain.proceed(originalRequest)
        }

        // 同步读取token缓存，避免runBlocking阻塞OkHttp线程池
        val token = tokenStorage.getAccessTokenSync()

        val request = if (token != null) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            originalRequest
        }

        val response = chain.proceed(request)

        // 收到401，尝试自动刷新token
        if (response.code == 401 && token != null) {
            return handleUnauthorized(chain, originalRequest, response, token)
        }

        return response
    }

    /**
     * 处理401未授权响应
     * 1. 先检查token是否已被其他请求刷新过，如已刷新则直接重试
     * 2. 否则尝试刷新token，成功后重试原请求
     * 3. 刷新失败则清除token
     */
    private fun handleUnauthorized(
        chain: Interceptor.Chain,
        originalRequest: Request,
        response: Response,
        usedToken: String
    ): Response {
        // 同步读取最新token，避免runBlocking
        val latestToken = tokenStorage.getAccessTokenSync()
        if (latestToken != null && latestToken != usedToken) {
            // token已被刷新，直接用新token重试
            response.close()
            val newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $latestToken")
                .build()
            return chain.proceed(newRequest)
        }

        synchronized(refreshLock) {
            // 再次检查（双重检查锁），同步读取避免runBlocking
            val currentToken = tokenStorage.getAccessTokenSync()
            if (currentToken != null && currentToken != usedToken) {
                response.close()
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
                return chain.proceed(newRequest)
            }

            if (!isRefreshing) {
                isRefreshing = true
                try {
                    val newTokens = performTokenRefresh()
                    if (newTokens != null) {
                        // 刷新成功，保存新token
                        // 写操作必须用runBlocking，因为OkHttp拦截器不支持挂起函数
                        // 此处频率极低（仅401时触发），不影响正常请求性能
                        runBlocking {
                            tokenStorage.saveTokens(newTokens.first, newTokens.second)
                        }
                        response.close()
                        val newRequest = originalRequest.newBuilder()
                            .header("Authorization", "Bearer ${newTokens.first}")
                            .build()
                        return chain.proceed(newRequest)
                    }
                } finally {
                    isRefreshing = false
                }
            }
        }

        // 如果正在刷新中（其他请求触发的），等待刷新完成后用新token重试
        if (isRefreshing) {
            val startTime = System.currentTimeMillis()
            while (isRefreshing && System.currentTimeMillis() - startTime < 5000) {
                Thread.sleep(100)
            }
            // 同步读取最新token
            val newToken = tokenStorage.getAccessTokenSync()
            if (newToken != null && newToken != usedToken) {
                response.close()
                val newRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return chain.proceed(newRequest)
            }
        }

        // 刷新失败，清除token和用户信息并通知退出登录
        // 写操作必须用runBlocking
        runBlocking {
            if (tokenStorage.isLoggedIn()) {
                tokenStorage.clearTokens()
                userStorage.clearUserInfo()
            }
        }
        return response
    }

    /**
     * 执行token刷新请求
     * 使用独立的OkHttpClient直接调用刷新接口，避免循环依赖
     * @return 新的(accessToken, refreshToken)对，失败返回null
     */
    private fun performTokenRefresh(): Pair<String, String>? {
        return try {
            // 同步读取refreshToken和baseUrl，避免runBlocking
            val refreshToken = tokenStorage.getRefreshTokenSync() ?: return null
            val baseUrl = serverConfig.getServerUrlSync().ifEmpty { return null }

            // 构建请求体JSON
            val requestBody = """{"refreshToken":"$refreshToken"}"""
                .toRequestBody("application/json".toMediaType())

            val refreshRequest = Request.Builder()
                .url("${baseUrl}v1/auth/refresh")
                .post(requestBody)
                .build()

            val refreshResponse = refreshClient.newCall(refreshRequest).execute()
            val bodyString = refreshResponse.body?.string() ?: return null
            refreshResponse.close()

            // 解析响应JSON
            val jsonElement = Json.parseToJsonElement(bodyString)
            val jsonObj = jsonElement.jsonObject
            val code = jsonObj["code"]?.jsonPrimitive?.int ?: return null

            if (code != 200) return null

            val data = jsonObj["data"]?.jsonObject ?: return null
            val newAccessToken = data["accessToken"]?.jsonPrimitive?.content ?: return null
            val newRefreshToken = data["refreshToken"]?.jsonPrimitive?.content ?: return null

            Pair(newAccessToken, newRefreshToken)
        } catch (_: Exception) {
            null
        }
    }
}
