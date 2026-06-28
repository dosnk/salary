package com.salary.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工作台页面缓存存储
 *
 * 用于持久化以下数据：
 * - 客户地址与施工人员的映射缓存（addressConstructorMap）
 * - 表单数据（formData）
 *
 * 参考Vue前端的localStorage缓存机制
 */
@Singleton
class DashboardCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore by preferencesDataStore(name = "dashboard_cache")

    companion object {
        /** 地址→施工人员ID列表映射（JSON格式：[{"address":"xxx","ids":[1,2]}]） */
        private val ADDRESS_MAP_KEY = stringPreferencesKey("address_constructor_map")
        /** 表单数据（JSON格式） */
        private val FORM_DATA_KEY = stringPreferencesKey("form_data")
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** 地址→施工人员映射数据类 */
    @kotlinx.serialization.Serializable
    data class AddressMapping(
        val address: String,
        val ids: List<Int>
    )

    /** 表单缓存数据类 */
    @kotlinx.serialization.Serializable
    data class FormCache(
        val customerAddress: String = "",
        val selectedSpaceType: String = "",
        val selectedScheme: String = "",
        val lengthCm: String = "",
        val widthCm: String = "",
        val salaryDistribution: String = "average",
        val selectedConstructorIds: List<Int> = emptyList(),
        val remark: String = ""
    )

    /**
     * 加载地址→施工人员映射
     * @return Map<客户地址, 施工人员ID列表>
     */
    suspend fun loadAddressMap(): Map<String, List<Int>> {
        return try {
            val jsonStr = context.dataStore.data.map { it[ADDRESS_MAP_KEY] ?: "" }.first()
            if (jsonStr.isBlank()) return emptyMap()
            val mappings = json.decodeFromString<List<AddressMapping>>(jsonStr)
            mappings.associate { it.address to it.ids }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * 保存地址→施工人员映射
     */
    suspend fun saveAddressMap(map: Map<String, List<Int>>) {
        try {
            val mappings = map.map { (address, ids) -> AddressMapping(address, ids) }
            val jsonStr = json.encodeToString(mappings)
            context.dataStore.edit { it[ADDRESS_MAP_KEY] = jsonStr }
        } catch (_: Exception) {
            // 静默处理
        }
    }

    /**
     * 加载表单缓存数据
     */
    suspend fun loadFormCache(): FormCache? {
        return try {
            val jsonStr = context.dataStore.data.map { it[FORM_DATA_KEY] ?: "" }.first()
            if (jsonStr.isBlank()) return null
            json.decodeFromString<FormCache>(jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 保存表单缓存数据
     */
    suspend fun saveFormCache(cache: FormCache) {
        try {
            val jsonStr = json.encodeToString(cache)
            context.dataStore.edit { it[FORM_DATA_KEY] = jsonStr }
        } catch (_: Exception) {
            // 静默处理
        }
    }

    /**
     * 清除表单缓存（保存工程成功后调用）
     */
    suspend fun clearFormCache() {
        try {
            context.dataStore.edit { it.remove(FORM_DATA_KEY) }
        } catch (_: Exception) {
            // 静默处理
        }
    }
}
