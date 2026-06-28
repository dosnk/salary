package com.salary.manager.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.data.local.TokenStorage
import com.salary.core.data.local.UserStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 个人中心ViewModel
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userStorage: UserStorage,
    private val tokenStorage: TokenStorage
) : ViewModel() {

    private val _nickname = MutableStateFlow("")
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    private val _role = MutableStateFlow("")
    val role: StateFlow<String> = _role.asStateFlow()

    private val _roleDisplay = MutableStateFlow("")
    val roleDisplay: StateFlow<String> = _roleDisplay.asStateFlow()

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone.asStateFlow()

    init {
        loadUserInfo()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            _nickname.value = userStorage.getNickname() ?: ""
            val roleValue = userStorage.getRole() ?: ""
            _role.value = roleValue
            _roleDisplay.value = when (roleValue) {
                "admin" -> "管理员"
                "constructor" -> "施工员"
                "documenter" -> "资料员"
                else -> "未知"
            }
        }
    }

    /** 退出登录 */
    fun logout() {
        viewModelScope.launch {
            tokenStorage.clearTokens()
            userStorage.clearUserInfo()
        }
    }
}
