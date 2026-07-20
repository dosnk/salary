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

        // 刷新失败处理：区分"网络异常"和"刷新接口明确拒绝"两种场景
        // - 网络异常（SocketTimeoutException/ConnectException/UnknownHostException 等）：
        //   保留 token，让用户在网络恢复后可继续操作，避免弱网误退出登录
        // - 刷新接口明确返回业务失败（code != 200，如 refresh token 过期）：
        //   清除 token 和用户信息，让 UI 跳转登录页重新登录
        when (lastRefreshResult) {
            // SUCCESS 状态理论上不会走到这里（成功分支已提前 return），
            // 为满足 Kotlin when 穷尽性要求而补充，按保留 token 处理
            RefreshResult.SUCCESS, RefreshResult.NetworkError -> {
                // 网络异常：不清除 token，返回原 401 响应让上层显示"网络错误"提示
                // 用户网络恢复后可正常重试，token 仍有效（可能已过期但 refresh token 仍可用）
            }
            RefreshResult.InvalidGrant, RefreshResult.UnknownFailure -> {
                // 刷新接口明确拒绝或解析失败：清除 token 和用户信息并通知退出登录
                runBlocking {
                    if (tokenStorage.isLoggedIn()) {
                        tokenStorage.clearTokens()
                        userStorage.clearUserInfo()
                    }
                }
            }
        }
        return response
    }

    /**
     * token 刷新结果状态
     * 用于区分网络异常和业务失败，避免弱网场景下误清除 token 导致用户被强制退出登录
     */
    private enum class RefreshResult {
        /** 成功 */
        SUCCESS,
        /** 网络异常（超时、连接失败、DNS 解析失败等），token 可能仍有效 */
        NetworkError,
        /** 刷新接口明确拒绝（refresh token 过期/无效），必须重新登录 */
        InvalidGrant,
        /** 其他未知失败（响应解析失败等），保守按 InvalidGrant 处理 */
        UnknownFailure
    }

    /** 最近一次 token 刷新的结果，用于 401 处理时判断是否应清除 token */
    private var lastRefreshResult: RefreshResult = RefreshResult.UnknownFailure

    /**
     * 执行token刷新请求
     * 使用独立的OkHttpClient直接调用刷新接口，避免循环依赖
     * @return 新的(accessToken, refreshToken)对，失败返回null（失败原因记录在 lastRefreshResult）
     */
    private fun performTokenRefresh(): Pair<String, String>? {
        return try {
            // 同步读取refreshToken和baseUrl，避免runBlocking
            val refreshToken = tokenStorage.getRefreshTokenSync() ?: run {
                lastRefreshResult = RefreshResult.InvalidGrant
                return null
            }
            val baseUrl = serverConfig.getServerUrlSync().ifEmpty {
                lastRefreshResult = RefreshResult.UnknownFailure
                return null
            }

            // 构建请求体JSON
            val requestBody = """{"refreshToken":"$refreshToken"}"""
                .toRequestBody("application/json".toMediaType())

            val refreshRequest = Request.Builder()
                .url("${baseUrl}v1/auth/refresh")
                .post(requestBody)
                .build()

            val refreshResponse = refreshClient.newCall(refreshRequest).execute()
            val bodyString = refreshResponse.body?.string() ?: run {
                lastRefreshResult = RefreshResult.UnknownFailure
                return null
            }
            refreshResponse.close()

            // 解析响应JSON
            val jsonElement = Json.parseToJsonElement(bodyString)
            val jsonObj = jsonElement.jsonObject
            val code = jsonObj["code"]?.jsonPrimitive?.int ?: run {
                lastRefreshResult = RefreshResult.UnknownFailure
                return null
            }

            if (code != 200) {
                // 刷新接口明确返回业务失败（如 refresh token 过期/无效）
                lastRefreshResult = RefreshResult.InvalidGrant
                return null
            }

            val data = jsonObj["data"]?.jsonObject ?: run {
                lastRefreshResult = RefreshResult.UnknownFailure
                return null
            }
            val newAccessToken = data["accessToken"]?.jsonPrimitive?.content ?: run {
                lastRefreshResult = RefreshResult.UnknownFailure
                return null
            }
            val newRefreshToken = data["refreshToken"]?.jsonPrimitive?.content ?: run {
                lastRefreshResult = RefreshResult.UnknownFailure
                return null
            }

            lastRefreshResult = RefreshResult.SUCCESS
            Pair(newAccessToken, newRefreshToken)
        } catch (e: java.net.SocketTimeoutException) {
            // 网络超时：弱网场景，保留 token 避免误退出
            lastRefreshResult = RefreshResult.NetworkError
            null
        } catch (e: java.net.ConnectException) {
            // 连接被拒绝/无法连接：弱网或服务不可用，保留 token
            lastRefreshResult = RefreshResult.NetworkError
            null
        } catch (e: java.net.UnknownHostException) {
            // DNS 解析失败：网络不可用，保留 token
            lastRefreshResult = RefreshResult.NetworkError
            null
        } catch (e: javax.net.ssl.SSLException) {
            // SSL 握手失败：网络问题或证书问题，保留 token
            lastRefreshResult = RefreshResult.NetworkError
            null
        } catch (e: java.io.IOException) {
            // 其他 IO 异常：按网络异常处理，保留 token
            lastRefreshResult = RefreshResult.NetworkError
            null
        } catch (_: Exception) {
            // 其他非 IO 异常（如 JSON 解析）：按未知失败处理
            lastRefreshResult = RefreshResult.UnknownFailure
            null
        }
    }
}
