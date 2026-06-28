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

    /** 获取服务器地址（Flow形式） */
    val serverUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_URL_KEY] ?: ""
    }

    /** 获取服务器地址（挂起函数，一次性读取） */
    suspend fun getServerUrl(): String {
        return context.dataStore.data.map { prefs ->
            prefs[SERVER_URL_KEY] ?: ""
        }.first()
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
    }

    /** 清除配置（用于调试） */
    suspend fun clearConfig() {
        context.dataStore.edit { prefs ->
            prefs.remove(SERVER_URL_KEY)
        }
    }
}
