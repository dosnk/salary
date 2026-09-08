package com.salary.manager.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.util.AppLog
import com.salary.core.data.local.ServerConfig
import com.salary.core.data.local.TokenStorage
import com.salary.core.data.local.UserStorage
import com.salary.core.network.api.BackupApi
import com.salary.core.network.interceptor.HealthMonitor
import com.salary.core.network.interceptor.LatencyTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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
    val healthMonitor: HealthMonitor,
    private val backupApi: BackupApi
) : ViewModel() {

    companion object {
        private const val TAG = "AppViewModel"
    }

    /**
     * App启动且已登录时静默触发一次数据库全量备份
     *
     * 设计说明：
     * - 静默执行：网络不通/后端旧版不支持/接口失败均不影响启动流程，仅记日志
     * - 由AppNavHost通过 isAuthenticated 状态控制触发时机与去重
     */
    fun launchAutoBackup() {
        viewModelScope.launch {
            runCatching { backupApi.backupDatabase() }
                .onSuccess { response ->
                    if (response.code == 200) {
                        AppLog.i(TAG, "启动自动备份完成: ${response.data?.fileName ?: ""}")
                    } else {
                        AppLog.w(TAG, "启动自动备份被拒绝: ${response.code} ${response.msg}")
                    }
                }
                .onFailure { e ->
                    AppLog.w(TAG, "启动自动备份失败(静默忽略): ${e.message}")
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 释放健康监控资源
        healthMonitor.release()
    }
}
