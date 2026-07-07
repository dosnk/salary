package com.salary.core.network.interceptor

import com.salary.core.data.local.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
 * 后端健康监控器
 *
 * 主动定时调用 /v1/health 接口探测后端在线状态，不依赖业务API请求。
 *
 * 使用场景：
 * - 登录页面无业务请求时，仍能实时显示后端在线状态
 * - App空闲时持续监控后端可达性
 *
 * 工作机制：
 * - 启动后每 [intervalOnlineMs]（在线）或 [intervalOfflineMs]（离线）毫秒发起一次健康检查
 * - 检查结果同步到 LatencyTracker，复用现有UI展示
 * - 检测到离线时降低检查频率避免耗电，检测到在线时恢复正常频率
 *
 * 重要设计：不依赖注入的Retrofit单例，而是使用独立的OkHttpClient + 动态读取ServerConfig地址。
 * 原因：Retrofit是@Singleton，App启动时用ServerConfig缓存构建baseUrl后就锁定。
 * 如果用户首次启动时配置了新地址，Retrofit单例不会重建，仍用旧地址（或默认地址）。
 * 因此HealthMonitor必须自己动态读取最新地址，才能正确检测用户配置的服务器。
 */
@Singleton
class HealthMonitor @Inject constructor(
    private val serverConfig: ServerConfig,
    private val latencyTracker: LatencyTracker
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    /** 健康检查间隔（在线时15秒一次） */
    private val intervalOnlineMs = 15_000L

    /** 健康检查间隔（离线时5秒一次，更快感知恢复） */
    private val intervalOfflineMs = 5_000L

    /** 健康检查超时阈值（毫秒） */
    private val timeoutMs = 5_000L

    /** 当前是否正在监控 */
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    /**
     * 独立的OkHttpClient（短超时，不共享拦截器链）
     * 懒加载，仅在首次使用时创建
     *
     * 不使用注入的OkHttpClient，因为那个配置了30秒超时和多个拦截器，
     * 对健康检查来说太重且太慢。
     */
    private val healthClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 启动健康监控
     * 重复调用安全，已运行则忽略
     */
    fun start() {
        if (monitorJob?.isActive == true) return
        _isMonitoring.value = true
        monitorJob = scope.launch {
            while (true) {
                checkOnce()
                // 根据当前在线状态动态调整检查频率
                val interval = if (latencyTracker.isOnline.value) intervalOnlineMs else intervalOfflineMs
                delay(interval)
            }
        }
    }

    /**
     * 停止健康监控
     */
    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        _isMonitoring.value = false
    }

    /**
     * 立即执行一次健康检查（不等待下一定时周期）
     *
     * 动态从ServerConfig读取最新服务器地址，构建健康检查请求。
     * 这样即使用户刚配置了新地址，HealthMonitor也能立即检测新地址。
     */
    suspend fun checkOnce() {
        // 从ServerConfig读取最新地址（同步读取内存缓存，零阻塞）
        val baseUrl = serverConfig.getServerUrlSync().ifEmpty { ServerConfig.DEFAULT_URL }
        val healthUrl = "${baseUrl.trimEnd('/')}/v1/health"

        val startTime = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(healthUrl)
                    .get()
                    .build()

                // 使用协程超时控制，避免OkHttp超时设置不生效的边缘情况
                val response = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                    healthClient.newCall(request).execute()
                } ?: run {
                    latencyTracker.onFailure("健康检查超时(${timeoutMs}ms)")
                    return@withContext
                }

                val latency = System.currentTimeMillis() - startTime
                response.use {
                    if (it.code == 200) {
                        // 健康检查成功：更新延迟值并标记在线
                        latencyTracker.onSuccess(latency)
                    } else {
                        // 服务器返回非200，标记离线
                        latencyTracker.onFailure("服务器错误(${it.code})")
                    }
                }
            } catch (e: UnknownHostException) {
                latencyTracker.onFailure("服务器地址无法解析")
            } catch (e: ConnectException) {
                latencyTracker.onFailure("无法连接到服务器")
            } catch (e: SocketTimeoutException) {
                val latency = System.currentTimeMillis() - startTime
                latencyTracker.onFailure("连接超时(${latency}ms)")
            } catch (e: Exception) {
                latencyTracker.onFailure("连接异常: ${e.message}")
            }
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        stop()
        scope.cancel()
    }
}
