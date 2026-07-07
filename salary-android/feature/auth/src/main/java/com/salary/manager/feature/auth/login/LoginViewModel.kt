package com.salary.manager.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.data.local.CredentialStorage
import com.salary.core.data.local.TokenStorage
import com.salary.core.data.local.UserStorage
import com.salary.core.network.api.AuthApi
import com.salary.core.network.dto.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import com.salary.core.common.util.NetworkErrorHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 登录状态
 */
sealed class LoginState {
    data object Idle : LoginState()
    data object Loading : LoginState()
    data object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

/**
 * 登录ViewModel
 *
 * 支持"记住密码"功能：
 * - 启动时从 CredentialStorage 加载已保存的凭证，自动填充用户名密码
 * - 登录成功后根据用户勾选状态保存/清除凭证
 * - 用户可随时切换"记住密码"勾选状态
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
    private val userStorage: UserStorage,
    private val credentialStorage: CredentialStorage
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    /** 是否记住密码（响应式，供UI监听） */
    val rememberCredentials: StateFlow<Boolean> = credentialStorage.rememberCredentials

    /** 已保存的用户名（响应式，供UI监听） */
    val savedUsername: StateFlow<String> = credentialStorage.savedUsername

    /** 已保存的密码（响应式，供UI监听） */
    val savedPassword: StateFlow<String> = credentialStorage.savedPassword

    /**
     * 初始化凭证状态
     * 在LoginScreen创建时调用，从加密存储恢复已保存的用户名密码
     */
    fun initCredentials() {
        credentialStorage.initCredentials()
    }

    /**
     * 切换"记住密码"勾选状态
     * 仅更新标志位，不保存/清除凭证内容（登录成功时才保存）
     *
     * @param remember 是否记住密码
     */
    fun setRememberCredentials(remember: Boolean) {
        credentialStorage.setRememberFlag(remember)
    }

    /**
     * 执行登录
     *
     * 登录成功后：
     * - 如果用户勾选"记住密码"，保存用户名密码到加密存储
     * - 如果用户未勾选，清除已保存的凭证
     *
     * @param username 用户名
     * @param password 密码
     */
    fun login(username: String, password: String) {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                val response = authApi.login(LoginRequest(username, password))
                if (response.code == 200) {
                    val loginData = response.data ?: return@launch run {
                        _state.value = LoginState.Error("登录响应数据为空")
                    }
                    // 保存Token
                    tokenStorage.saveTokens(
                        loginData.accessToken,
                        loginData.refreshToken
                    )
                    // 保存用户信息
                    userStorage.saveUserInfo(
                        id = loginData.user.id,
                        username = loginData.user.username,
                        nickname = loginData.user.nickname,
                        role = loginData.user.role
                    )
                    // 根据用户勾选状态保存/清除凭证
                    if (credentialStorage.rememberCredentials.value) {
                        credentialStorage.saveCredentials(username, password)
                    } else {
                        credentialStorage.clearCredentials()
                    }
                    _state.value = LoginState.Success
                } else {
                    _state.value = LoginState.Error(NetworkErrorHandler.translateServerError(response.msg, "登录失败"))
                }
            } catch (e: Exception) {
                _state.value = LoginState.Error(NetworkErrorHandler.translate(e, "登录失败"))
            }
        }
    }
}
