package com.salary.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户信息本地存储
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

    suspend fun getUserId(): Int? = context.dataStore.data.map { it[USER_ID_KEY] }.first()
    suspend fun getUsername(): String? = context.dataStore.data.map { it[USERNAME_KEY] }.first()
    suspend fun getNickname(): String? = context.dataStore.data.map { it[NICKNAME_KEY] }.first()
    suspend fun getRole(): String? = context.dataStore.data.map { it[ROLE_KEY] }.first()

    suspend fun saveUserInfo(id: Int, username: String, nickname: String, role: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_ID_KEY] = id
            prefs[USERNAME_KEY] = username
            prefs[NICKNAME_KEY] = nickname
            prefs[ROLE_KEY] = role
        }
    }

    suspend fun clearUserInfo() {
        context.dataStore.edit { prefs ->
            prefs.remove(USER_ID_KEY)
            prefs.remove(USERNAME_KEY)
            prefs.remove(NICKNAME_KEY)
            prefs.remove(ROLE_KEY)
        }
    }
}
