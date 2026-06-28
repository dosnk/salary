package com.salary.manager.navigation

import androidx.lifecycle.ViewModel
import com.salary.core.data.local.ServerConfig
import com.salary.core.data.local.TokenStorage
import com.salary.core.network.interceptor.LatencyTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * App级ViewModel
 *
 * 提供ServerConfig、TokenStorage和LatencyTracker给AppNavHost使用
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    val serverConfig: ServerConfig,
    val tokenStorage: TokenStorage,
    val latencyTracker: LatencyTracker
) : ViewModel()
