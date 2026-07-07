package com.salary.manager.feature.auth.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.data.local.ServerConfig
import com.salary.core.network.interceptor.ConnectionTestResult
import com.salary.core.network.interceptor.ServerConnectionTester
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 服务器配置页ViewModel
 *
 * 持有ServerConfig和ServerConnectionTester，提供测试连接功能。
 * 用户在保存配置前可先测试地址是否可达，避免配置错误地址后无法连接。
 */
@HiltViewModel
class SetupViewModel @Inject constructor(
    val serverConfig: ServerConfig,
    private val connectionTester: ServerConnectionTester
) : ViewModel() {

    /** 测试结果（null表示未测试） */
    private val _testResult = MutableStateFlow<ConnectionTestResult?>(null)
    val testResult: StateFlow<ConnectionTestResult?> = _testResult.asStateFlow()

    /** 是否正在测试连接 */
    private val _isTesting = MutableStateFlow(false)
    val isTesting: StateFlow<Boolean> = _isTesting.asStateFlow()

    /**
     * 测试指定URL的后端连通性
     * @param url 待测试的服务器地址
     */
    fun testConnection(url: String) {
        viewModelScope.launch {
            _isTesting.value = true
            _testResult.value = null
            _testResult.value = connectionTester.testConnection(url)
            _isTesting.value = false
        }
    }

    /**
     * 清除测试结果（用户修改URL时调用）
     */
    fun clearResult() {
        _testResult.value = null
    }
}
