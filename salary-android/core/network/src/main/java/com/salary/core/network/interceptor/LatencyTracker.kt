package com.salary.core.network.interceptor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API延迟与连接状态追踪器
 *
 * 通过 StateFlow 实时推送每次API请求的网络耗时和连接状态，
 * UI层可直接收集 Flow 实现动态更新。
 *
 * - 请求成功：标记在线 + 更新延迟值
 * - 请求失败：标记离线 + 记录错误信息
 */
@Singleton
class LatencyTracker @Inject constructor() {

    private val _latencyMs = MutableStateFlow(0L)
    private val _isOnline = MutableStateFlow(true)
    private val _lastError = MutableStateFlow<String?>(null)

    /** 最近一次API请求的网络耗时（毫秒），通过Flow动态更新 */
    val latencyMs: StateFlow<Long> = _latencyMs.asStateFlow()

    /** 后端是否在线 */
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    /** 最近一次连接错误信息 */
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * 请求成功时更新延迟值并标记在线
     * @param latencyMs 网络请求耗时（毫秒）
     */
    fun onSuccess(latencyMs: Long) {
        _latencyMs.value = latencyMs
        _isOnline.value = true
        _lastError.value = null
    }

    /**
     * 请求失败时标记离线
     * @param error 错误描述信息
     */
    fun onFailure(error: String) {
        _isOnline.value = false
        _lastError.value = error
    }

    /**
     * 恢复在线状态（如服务器重新连接成功时）
     */
    fun reset() {
        _isOnline.value = true
        _lastError.value = null
    }
}
