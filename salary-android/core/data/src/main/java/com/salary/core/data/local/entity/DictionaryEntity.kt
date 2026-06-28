package com.salary.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 字典数据缓存实体
 */
@Entity(tableName = "dictionaries")
data class DictionaryEntity(
    @PrimaryKey val id: Int,
    val type: String,       // space_type / construction_plan / wage_distribution_type
    val name: String,
    val description: String? = null,
    val sortOrder: Int = 0,
    val cachedAt: Long = System.currentTimeMillis()
)
