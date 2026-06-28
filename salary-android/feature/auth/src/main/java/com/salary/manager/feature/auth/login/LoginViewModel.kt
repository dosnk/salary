package com.salary.manager.feature.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStorage: TokenStorage,
    private val userStorage: UserStorage
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    /**
     * 执行登录
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
