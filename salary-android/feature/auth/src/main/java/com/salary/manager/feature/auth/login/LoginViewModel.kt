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
     * 前端预校验：在发起网络请求前先做基础格式校验，避免用户看到
     * 后端返回的"参数错误: xxx"这种不够友好的技术性提示；
     * 若校验通过再由后端做完整业务校验。
     *
     * @param username 用户名
     * @param password 密码
     */
    fun login(username: String, password: String) {
        // ========== 前端预校验（提供更即时且友好的提示） ==========
        val trimmedUsername = username.trim()
        when {
            trimmedUsername.isEmpty() -> {
                _state.value = LoginState.Error("请输入用户名")
                return
            }
            password.isEmpty() -> {
                _state.value = LoginState.Error("请输入密码")
                return
            }
            // 与后端 Joi 规则保持一致：用户名 2-10 位中文
            !trimmedUsername.matches(Regex("^[\\u4e00-\\u9fa5]{2,10}$")) -> {
                _state.value = LoginState.Error("用户名格式不正确，请输入 2-10 位中文字符")
                return
            }
            // 与后端 Joi 规则保持一致：密码 6-20 位
            password.length < 6 || password.length > 20 -> {
                _state.value = LoginState.Error("密码格式不正确，密码应为 6-20 位字符")
                return
            }
        }

        viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                val response = authApi.login(LoginRequest(trimmedUsername, password))
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
                        credentialStorage.saveCredentials(trimmedUsername, password)
                    } else {
                        credentialStorage.clearCredentials()
                    }
                    _state.value = LoginState.Success
                } else {
                    // 走友好化翻译；translateServerError 已单独处理"用户名或密码错误"、
                    // "登录失败次数过多"、以及各种密码/用户名格式提示
                    _state.value = LoginState.Error(
                        NetworkErrorHandler.translateServerError(response.msg, "登录失败，请稍后重试")
                    )
                }
            } catch (e: Exception) {
                _state.value = LoginState.Error(NetworkErrorHandler.translate(e, "登录失败，请稍后重试"))
            }
        }
    }
}
