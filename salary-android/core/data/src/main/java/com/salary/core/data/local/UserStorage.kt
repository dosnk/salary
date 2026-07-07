package com.salary.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户信息本地存储
 *
 * 提供响应式的昵称和角色StateFlow，确保登录/登出后所有页面能实时获取最新状态
 * 解决问题：ViewModel在init中一次性读取昵称，登录时序竞争导致显示"未登录"
 */
@Singleton
class UserStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore by preferencesDataStore(name = "user_info")

    companion object {
        private val USER_ID_KEY = intPreferencesKey("user_id")
        private val USERNAME_KEY = stringPreferencesKey("username")
        private val NICKNAME_KEY = stringPreferencesKey("nickname")
        private val ROLE_KEY = stringPreferencesKey("role")
    }

    /** 用户昵称状态（供UI层实时监听） */
    private val _nicknameFlow = MutableStateFlow("")
    val nicknameFlow: StateFlow<String> = _nicknameFlow.asStateFlow()

    /** 用户角色状态（供UI层实时监听） */
    private val _roleFlow = MutableStateFlow("")
    val roleFlow: StateFlow<String> = _roleFlow.asStateFlow()

    /** 是否已初始化（从DataStore恢复过一次状态） */
    private var initialized = false

    /**
     * 初始化用户状态（App启动时调用一次）
     * 从DataStore读取已保存的用户信息，更新StateFlow
     */
    suspend fun initUserState() {
        if (!initialized) {
            _nicknameFlow.value = getNickname() ?: ""
            _roleFlow.value = getRole() ?: ""
            initialized = true
        }
    }

    suspend fun getUserId(): Int? = context.dataStore.data.map { it[USER_ID_KEY] }.first()
    suspend fun getUsername(): String? = context.dataStore.data.map { it[USERNAME_KEY] }.first()
    suspend fun getNickname(): String? = context.dataStore.data.map { it[NICKNAME_KEY] }.first()
    suspend fun getRole(): String? = context.dataStore.data.map { it[ROLE_KEY] }.first()

    /**
     * 保存用户信息
     * 同时更新StateFlow，确保所有监听者立即收到最新昵称
     */
    suspend fun saveUserInfo(id: Int, username: String, nickname: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = id
            prefs[USERNAME_KEY] = username
            prefs[NICKNAME_KEY] = nickname
            prefs[ROLE_KEY] = role
        }
        // 立即更新StateFlow，触发UI层响应式更新
        _nicknameFlow.value = nickname
        _roleFlow.value = role
        initialized = true
    }

    /**
     * 清除所有用户信息（登出）
     * 同时清空StateFlow，确保所有页面立即显示未登录状态
     */
    suspend fun clearUserInfo() {
        context.dataStore.edit { prefs ->
            prefs.remove(USER_ID_KEY)
            prefs.remove(USERNAME_KEY)
            prefs.remove(NICKNAME_KEY)
            prefs.remove(ROLE_KEY)
        }
        // 立即清空StateFlow
        _nicknameFlow.value = ""
        _roleFlow.value = ""
    }
}
