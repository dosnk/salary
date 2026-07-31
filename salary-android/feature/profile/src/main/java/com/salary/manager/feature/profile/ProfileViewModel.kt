package com.salary.manager.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salary.core.common.constants.AppConstants
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
 *
 * 昵称和角色通过UserStorage的StateFlow响应式获取，确保登录后立即更新
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userStorage: UserStorage,
    private val tokenStorage: TokenStorage
) : ViewModel() {

    /** 用户昵称（从UserStorage响应式获取） */
    val nickname: StateFlow<String> = userStorage.nicknameFlow

    /** 用户角色（从UserStorage响应式获取） */
    val role: StateFlow<String> = userStorage.roleFlow

    /** 角色显示名称 */
    private val _roleDisplay = MutableStateFlow("")
    val roleDisplay: StateFlow<String> = _roleDisplay.asStateFlow()

    init {
        // 监听角色变化，更新显示名称
        viewModelScope.launch {
            userStorage.roleFlow.collect { roleValue ->
                _roleDisplay.value = when (roleValue) {
                    AppConstants.ROLE_ADMIN -> "管理员"
                    AppConstants.ROLE_CONSTRUCTOR -> "施工员"
                    AppConstants.ROLE_DOCUMENTER -> "资料员"
                    else -> "未知"
                }
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
