package com.salary.manager.navigation

import androidx.lifecycle.ViewModel
import com.salary.core.data.local.ServerConfig
import com.salary.core.data.local.TokenStorage
import com.salary.core.data.local.UserStorage
import com.salary.core.network.interceptor.HealthMonitor
import com.salary.core.network.interceptor.LatencyTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * App级ViewModel
 *
 * 提供ServerConfig、TokenStorage、UserStorage、LatencyTracker和HealthMonitor给AppNavHost使用
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    val serverConfig: ServerConfig,
    val tokenStorage: TokenStorage,
    val userStorage: UserStorage,
    val latencyTracker: LatencyTracker,
    val healthMonitor: HealthMonitor
) : ViewModel() {

    override fun onCleared() {
        super.onCleared()
        // 释放健康监控资源
        healthMonitor.release()
    }
}
