package com.salary.core.network.interceptor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 连接测试错误类型
 */
enum class ConnectionErrorType {
    /** 域名解析失败 */
    DNS_RESOLUTION_FAILED,

    /** 连接被拒绝（端口未开放或防火墙拦截） */
    CONNECTION_REFUSED,

    /** 连接超时 */
    CONNECTION_TIMEOUT,

    /** SSL证书验证失败 */
    SSL_ERROR,

    /** 服务器返回HTTP错误状态码（404/502等） */
    HTTP_ERROR,

    /** URL格式错误 */
    INVALID_URL,

    /** 未知错误 */
    UNKNOWN
}

/**
 * 连接测试结果
 */
sealed interface ConnectionTestResult {
    /**
     * 连接成功
     * @param latencyMs 延迟毫秒数
     * @param serverStatus 服务器返回的状态字段（如"ok"）
     */
    data class Success(val latencyMs: Long, val serverStatus: String) : ConnectionTestResult

    /**
     * 连接失败
     * @param errorType 错误类型
     * @param message 用户友好的错误描述
     */
    data class Error(val errorType: ConnectionErrorType, val message: String) : ConnectionTestResult
}

/**
 * 服务器连接测试器
 *
 * 独立于Retrofit单例，使用独立的OkHttpClient（短超时）测试指定URL的连通性。
 * 用于ServerSetupScreen让用户在保存配置前验证地址是否可达。
 *
 * 设计原因：Retrofit是@Singleton，App启动时用ServerConfig缓存构建baseUrl后就锁定。
 * 用户在ServerSetupScreen输入新地址时，Retrofit还用着旧地址，无法通过Retrofit测试新地址。
 * 因此需要独立的OkHttpClient，不依赖注入的Retrofit。
 */
@Singleton
class ServerConnectionTester @Inject constructor() {

    /**
     * 测试用OkHttpClient（独立实例，5秒超时，不共享拦截器链）
     * 懒加载，仅在首次使用时创建
     */
    private val testClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 测试指定URL的后端连通性
     *
     * 调用 GET {url}/v1/health 接口，根据响应结果返回详细错误信息。
     *
     * @param url 服务器地址（如 http://example.com:9393 或 http://192.168.1.68:9393）
     * @return 连接测试结果
     */
    suspend fun testConnection(url: String): ConnectionTestResult {
        // 规范化URL：去除尾部斜杠
        val trimmedUrl = url.trim().trimEnd('/')

        // URL格式校验
        if (trimmedUrl.isEmpty()) {
            return ConnectionTestResult.Error(
                ConnectionErrorType.INVALID_URL,
                "请输入服务器地址"
            )
        }
        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
            return ConnectionTestResult.Error(
                ConnectionErrorType.INVALID_URL,
                "地址必须以 http:// 或 https:// 开头"
            )
        }

        val healthUrl = "$trimmedUrl/v1/health"
        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(healthUrl)
                    .get()
                    .build()

                val response = testClient.newCall(request).execute()
                val latency = System.currentTimeMillis() - startTime

                response.use {
                    when (it.code) {
                        200 -> {
                            val body = it.body?.string()
                            val status = extractStatus(body)
                            ConnectionTestResult.Success(latency, status)
                        }
                        404 -> ConnectionTestResult.Error(
                            ConnectionErrorType.HTTP_ERROR,
                            "服务器响应但未找到/v1/health接口（HTTP 404），" +
                                    "请检查Nginx反向代理是否正确转发到后端9393端口"
                        )
                        502 -> ConnectionTestResult.Error(
                            ConnectionErrorType.HTTP_ERROR,
                            "Nginx反向代理后端服务不可用（HTTP 502），" +
                                    "请检查后端服务是否已启动"
                        )
                        503 -> ConnectionTestResult.Error(
                            ConnectionErrorType.HTTP_ERROR,
                            "服务暂时不可用（HTTP 503），请稍后重试"
                        )
                        else -> ConnectionTestResult.Error(
                            ConnectionErrorType.HTTP_ERROR,
                            "服务器返回错误状态码：HTTP ${it.code}"
                        )
                    }
                }
            } catch (e: UnknownHostException) {
                ConnectionTestResult.Error(
                    ConnectionErrorType.DNS_RESOLUTION_FAILED,
                    "域名无法解析：${e.message}，请检查域名拼写或设备DNS设置"
                )
            } catch (e: ConnectException) {
                ConnectionTestResult.Error(
                    ConnectionErrorType.CONNECTION_REFUSED,
                    "无法连接到服务器：连接被拒绝，请检查地址和端口是否正确"
                )
            } catch (e: SocketTimeoutException) {
                ConnectionTestResult.Error(
                    ConnectionErrorType.CONNECTION_TIMEOUT,
                    "连接超时：服务器可能未启动、端口被防火墙拦截或网络不通"
                )
            } catch (e: javax.net.ssl.SSLHandshakeException) {
                ConnectionTestResult.Error(
                    ConnectionErrorType.SSL_ERROR,
                    "SSL证书验证失败：${e.message}，请检查证书是否有效"
                )
            } catch (e: Exception) {
                ConnectionTestResult.Error(
                    ConnectionErrorType.UNKNOWN,
                    "连接异常：${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    /**
     * 从响应体中提取status字段（简单正则解析，避免引入JSON库）
     */
    private fun extractStatus(body: String?): String {
        if (body.isNullOrBlank()) return "未知"
        return try {
            // 匹配 "status":"ok" 格式
            val regex = """"status"\s*:\s*"([^"]+)"""".toRegex()
            regex.find(body)?.groupValues?.getOrNull(1) ?: "未知"
        } catch (_: Exception) {
            "未知"
        }
    }
}
