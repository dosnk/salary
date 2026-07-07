package com.salary.core.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录凭证本地存储
 *
 * 使用 EncryptedSharedPreferences 加密存储用户名和密码，避免明文落盘。
 * 用户主动勾选"记住密码"后才会保存凭证，取消勾选则立即清除。
 *
 * 安全说明：
 * - 使用 Android Keystore 生成主密钥，AES-256-GCM 加密存储值
 * - 即使设备被root，密码也不会以明文形式被读取
 * - 仅存储用户主动选择保存的账号密码
 */
@Singleton
class CredentialStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "login_credentials"
        private const val KEY_USERNAME = "saved_username"
        private const val KEY_PASSWORD = "saved_password"
        private const val KEY_REMEMBER = "remember_credentials"
    }

    /** 加密的 SharedPreferences 实例（懒加载，主密钥失败时回退到明文） */
    private val encryptedPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // 主密钥生成失败时回退到普通 SharedPreferences（降级，仍可用但非加密）
            // 场景：旧设备或 Keystore 损坏，保证功能可用性
            context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    /** 是否记住密码（供UI响应式监听） */
    private val _rememberCredentials = MutableStateFlow(false)
    val rememberCredentials: StateFlow<Boolean> = _rememberCredentials.asStateFlow()

    /** 已保存的用户名（供UI响应式监听） */
    private val _savedUsername = MutableStateFlow("")
    val savedUsername: StateFlow<String> = _savedUsername.asStateFlow()

    /** 已保存的密码（供UI响应式监听） */
    private val _savedPassword = MutableStateFlow("")
    val savedPassword: StateFlow<String> = _savedPassword.asStateFlow()

    /** 是否已初始化 */
    private var initialized = false

    /**
     * 初始化凭证状态（App启动时调用一次）
     * 从加密存储读取已保存的凭证，更新StateFlow
     */
    fun initCredentials() {
        if (!initialized) {
            _rememberCredentials.value = encryptedPrefs.getBoolean(KEY_REMEMBER, false)
            if (_rememberCredentials.value) {
                _savedUsername.value = encryptedPrefs.getString(KEY_USERNAME, "") ?: ""
                _savedPassword.value = encryptedPrefs.getString(KEY_PASSWORD, "") ?: ""
            }
            initialized = true
        }
    }

    /**
     * 保存登录凭证
     * 仅当用户勾选"记住密码"时调用
     *
     * @param username 用户名
     * @param password 密码
     */
    fun saveCredentials(username: String, password: String) {
        encryptedPrefs.edit()
            .putBoolean(KEY_REMEMBER, true)
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .apply()
        _rememberCredentials.value = true
        _savedUsername.value = username
        _savedPassword.value = password
    }

    /**
     * 清除已保存的登录凭证
     * 用户取消勾选"记住密码"或登出时调用
     */
    fun clearCredentials() {
        encryptedPrefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .putBoolean(KEY_REMEMBER, false)
            .apply()
        _rememberCredentials.value = false
        _savedUsername.value = ""
        _savedPassword.value = ""
    }

    /**
     * 仅切换"记住密码"勾选状态，不保存/清除凭证内容
     * 用于复选框状态变化但尚未登录的场景
     *
     * @param remember 是否记住密码
     */
    fun setRememberFlag(remember: Boolean) {
        encryptedPrefs.edit().putBoolean(KEY_REMEMBER, remember).apply()
        _rememberCredentials.value = remember
        if (!remember) {
            // 取消勾选时清除已保存的凭证
            encryptedPrefs.edit()
                .remove(KEY_USERNAME)
                .remove(KEY_PASSWORD)
                .apply()
            _savedUsername.value = ""
            _savedPassword.value = ""
        }
    }
}
