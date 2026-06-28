package com.salary.manager.feature.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.network.api.AuthApi
import com.salary.core.network.dto.RegisterRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import com.salary.core.common.util.NetworkErrorHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 注册状态
 */
sealed class RegisterState {
    data object Idle : RegisterState()
    data object Loading : RegisterState()
    data object Success : RegisterState()
    data class Error(val message: String) : RegisterState()
}

/**
 * 注册ViewModel
 */
@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authApi: AuthApi
) : ViewModel() {

    private val _state = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val state: StateFlow<RegisterState> = _state.asStateFlow()

    /**
     * 执行注册
     */
    fun register(username: String, password: String, nickname: String) {
        viewModelScope.launch {
            _state.value = RegisterState.Loading
            try {
                val response = authApi.register(RegisterRequest(username, password, nickname))
                if (response.code == 200) {
                    _state.value = RegisterState.Success
                } else {
                    _state.value = RegisterState.Error(NetworkErrorHandler.translateServerError(response.msg, "注册失败"))
                }
            } catch (e: Exception) {
                _state.value = RegisterState.Error(NetworkErrorHandler.translate(e, "注册失败"))
            }
        }
    }
}
