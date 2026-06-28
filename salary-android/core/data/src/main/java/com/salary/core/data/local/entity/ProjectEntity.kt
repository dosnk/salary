package com.salary.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 工程缓存实体
 * 离线时从Room读取，在线时从API刷新
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val status: String,
    val totalAmount: String,
    val spaceType: String? = null,
    val constructionPlan: String? = null,
    val address: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val cachedAt: Long = System.currentTimeMillis() // 缓存时间戳
)
