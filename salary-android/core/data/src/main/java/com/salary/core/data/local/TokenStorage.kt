package com.salary.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
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
 * Token存储管理 - 使用DataStore持久化
 * 同时提供认证状态StateFlow，支持401响应自动退出登录
 *
 * 性能优化：为OkHttp拦截器提供同步内存缓存，避免在拦截器中使用runBlocking
 * 缓存策略：内存缓存作为热数据，DataStore作为持久化备份
 * - App启动时同步加载缓存（runBlocking仅执行一次，在App初始化阶段）
 * - saveTokens/clearTokens时同步更新缓存
 * - 拦截器直接读缓存，零阻塞
 */
@Singleton
class TokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore by preferencesDataStore(name = "auth_tokens")

    /** 认证状态（供UI层监听401自动退出） */
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    /** 内存缓存：供OkHttp拦截器同步读取，避免runBlocking阻塞线程池 */
    @Volatile
    private var cachedAccessToken: String? = null

    @Volatile
    private var cachedRefreshToken: String? = null

    /** 缓存是否已初始化 */
    @Volatile
    private var cacheInitialized = false

    /** 初始化认证状态（App启动时调用一次） */
    suspend fun initAuthState() {
        if (!cacheInitialized) {
            cachedAccessToken = context.dataStore.data.map { it[ACCESS_TOKEN_KEY] }.first()
            cachedRefreshToken = context.dataStore.data.map { it[REFRESH_TOKEN_KEY] }.first()
            _isAuthenticated.value = cachedAccessToken != null
            cacheInitialized = true
        }
    }

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    }

    /** 获取访问令牌（挂起函数，用于需要持久化的场景） */
    suspend fun getAccessToken(): String? {
        if (cacheInitialized) return cachedAccessToken
        return context.dataStore.data.map { it[ACCESS_TOKEN_KEY] }.first()
    }

    /**
     * 同步获取访问令牌（供OkHttp拦截器使用，零阻塞）
     * 注意：必须在[initAuthState]后调用，否则返回null
     */
    fun getAccessTokenSync(): String? = cachedAccessToken

    /** 获取刷新令牌（挂起函数） */
    suspend fun getRefreshToken(): String? {
        if (cacheInitialized) return cachedRefreshToken
        return context.dataStore.data.map { it[REFRESH_TOKEN_KEY] }.first()
    }

    /**
     * 同步获取刷新令牌（供OkHttp拦截器使用，零阻塞）
     */
    fun getRefreshTokenSync(): String? = cachedRefreshToken

    /** 保存令牌对 */
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
        }
        // 同步更新内存缓存
        cachedAccessToken = accessToken
        cachedRefreshToken = refreshToken
        cacheInitialized = true
        _isAuthenticated.value = true
    }

    /** 清除所有令牌（登出） */
    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
        }
        // 同步清空内存缓存
        cachedAccessToken = null
        cachedRefreshToken = null
        _isAuthenticated.value = false
    }

    /** 是否已登录 */
    suspend fun isLoggedIn(): Boolean = getAccessToken() != null
}
