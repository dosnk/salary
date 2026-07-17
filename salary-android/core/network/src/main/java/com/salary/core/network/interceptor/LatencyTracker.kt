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
 * - 请求成功：标记在线 + 更新延迟值 + 重置失败计数
 * - 请求失败：累计失败计数，达到阈值才标记离线（避免单次网络抖动误判）
 *
 * 设计说明（2026-07-17 修复）：
 * 原实现在任意一次请求失败时立即标记离线，会因 WiFi 信号波动、DNS 短暂解析失败、
 * 5xx 服务器异常等瞬态问题导致 isOnline=false，进而被上层误判为会话失效。
 * 现改为连续 [FAILURE_THRESHOLD] 次失败才标记离线，单次失败保持在线，
 * 配合 onSuccess 的失败计数重置，可有效过滤瞬态抖动。
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

    // 连续失败计数（任意一次成功会重置为0）
    // 阈值：连续 3 次失败才判定离线，过滤单次网络抖动
    private var consecutiveFailures = 0
    private val failureThreshold = 3

    /**
     * 请求成功时更新延迟值并标记在线，重置失败计数
     * @param latencyMs 网络请求耗时（毫秒）
     */
    fun onSuccess(latencyMs: Long) {
        _latencyMs.value = latencyMs
        consecutiveFailures = 0
        _isOnline.value = true
        _lastError.value = null
    }

    /**
     * 请求失败时累计失败计数，达到阈值才标记离线
     * @param error 错误描述信息
     */
    fun onFailure(error: String) {
        consecutiveFailures++
        _lastError.value = error
        // 连续失败达到阈值才判定离线，单次失败不切换状态
        if (consecutiveFailures >= failureThreshold) {
            _isOnline.value = false
        }
    }

    /**
     * 恢复在线状态（如服务器重新连接成功时）
     */
    fun reset() {
        consecutiveFailures = 0
        _isOnline.value = true
        _lastError.value = null
    }
}
