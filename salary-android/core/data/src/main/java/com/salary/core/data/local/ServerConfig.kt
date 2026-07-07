package com.salary.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 服务器地址配置存储
 *
 * 首次启动时用户填写服务器地址，保存后不再弹出。
 * 除非重新安装程序或清除程序数据。
 *
 * 默认值: http://192.168.1.68:9393
 *
 * 性能优化：提供同步内存缓存，避免OkHttp拦截器/Retrofit构建时使用runBlocking
 */
@Singleton
class ServerConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore by preferencesDataStore(name = "server_config")

    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        const val DEFAULT_URL = "http://192.168.1.68:9393"
    }

    /** 内存缓存：供OkHttp拦截器/Retrofit同步读取 */
    @Volatile
    private var cachedServerUrl: String = ""

    @Volatile
    private var cacheInitialized = false

    /** 获取服务器地址（Flow形式） */
    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        val url = prefs[SERVER_URL_KEY] ?: ""
        cachedServerUrl = url
        cacheInitialized = true
        url
    }

    /** 获取服务器地址（挂起函数，一次性读取） */
    suspend fun getServerUrl(): String {
        if (cacheInitialized) return cachedServerUrl
        return context.dataStore.data.map { prefs ->
            prefs[SERVER_URL_KEY] ?: ""
        }.first()
    }

    /**
     * 同步获取服务器地址（供Retrofit构建使用，零阻塞）
     * 注意：必须在[initConfig]后调用，否则返回空字符串
     */
    fun getServerUrlSync(): String = cachedServerUrl

    /** 初始化配置缓存（App启动时调用一次） */
    suspend fun initConfig() {
        if (!cacheInitialized) {
            cachedServerUrl = context.dataStore.data.map { prefs ->
                prefs[SERVER_URL_KEY] ?: ""
            }.first()
            cacheInitialized = true
        }
    }

    /** 服务器地址是否已配置 */
    suspend fun isConfigured(): Boolean {
        return getServerUrl().isNotEmpty()
    }

    /** 保存服务器地址 */
    suspend fun saveServerUrl(url: String) {
        // 确保URL以/结尾
        val normalizedUrl = if (url.isNotEmpty() && !url.endsWith("/")) "$url/" else url
        context.dataStore.edit { prefs ->
            prefs[SERVER_URL_KEY] = normalizedUrl
        }
        // 同步更新内存缓存
        cachedServerUrl = normalizedUrl
        cacheInitialized = true
    }

    /** 清除配置（用于调试） */
    suspend fun clearConfig() {
        context.dataStore.edit { prefs ->
            prefs.remove(SERVER_URL_KEY)
        }
        cachedServerUrl = ""
    }
}
