package com.salary.core.data.repository

import com.salary.core.data.local.dao.DictionaryDao
import com.salary.core.data.local.dao.ProjectDao
import com.salary.core.data.local.entity.DictionaryEntity
import com.salary.core.data.local.entity.ProjectEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 离线数据仓库
 *
 * 实现三级缓存策略: Memory → Room → Remote
 *
 * 读取流程:
 * 1. 先查内存缓存（Map）
 * 2. 内存未命中 → 查Room
 * 3. Room未命中 → 请求远程API
 * 4. 远程数据写入Room和内存
 *
 * 写入流程:
 * 1. 写远程API
 * 2. 成功后更新Room和内存
 * 3. 失败则标记为待同步
 */
@Singleton
class OfflineRepository @Inject constructor(
    private val projectDao: ProjectDao,
    private val dictionaryDao: DictionaryDao
) {
    // 内存缓存
    private val projectCache = mutableMapOf<Int, ProjectEntity>()
    private val dictionaryCache = mutableMapOf<String, List<DictionaryEntity>>()
    private var projectCacheTime = 0L
    private var dictionaryCacheTime = 0L

    companion object {
        private const val CACHE_TTL = 10 * 60 * 1000L // 10分钟
    }

    // ========== 工程缓存 ==========

    /** 获取缓存的工程列表 */
    suspend fun getCachedProjects(): List<ProjectEntity> {
        // 1. 检查内存缓存
        if (projectCache.isNotEmpty() && System.currentTimeMillis() - projectCacheTime < CACHE_TTL) {
            return projectCache.values.toList()
        }

        // 2. 检查Room
        val roomData = projectDao.getAll()
        if (roomData.isNotEmpty()) {
            projectCache.clear()
            roomData.forEach { projectCache[it.id] = it }
            projectCacheTime = System.currentTimeMillis()
            return roomData
        }

        return emptyList() // 需要远程获取
    }

    /** 更新工程缓存（远程获取后调用） */
    suspend fun updateProjectCache(projects: List<ProjectEntity>) {
        projectDao.insertAll(projects)
        projectCache.clear()
        projects.forEach { projectCache[it.id] = it }
        projectCacheTime = System.currentTimeMillis()
    }

    /** 清除工程缓存 */
    suspend fun clearProjectCache() {
        projectDao.deleteAll()
        projectCache.clear()
        projectCacheTime = 0L
    }

    // ========== 字典缓存 ==========

    /** 获取缓存的字典数据 */
    suspend fun getCachedDictionaries(type: String): List<DictionaryEntity> {
        // 1. 检查内存
        val cached = dictionaryCache[type]
        if (cached != null && System.currentTimeMillis() - dictionaryCacheTime < CACHE_TTL) {
            return cached
        }

        // 2. 检查Room
        val roomData = dictionaryDao.getByType(type)
        if (roomData.isNotEmpty()) {
            dictionaryCache[type] = roomData
            dictionaryCacheTime = System.currentTimeMillis()
            return roomData
        }

        return emptyList()
    }

    /** 更新字典缓存 */
    suspend fun updateDictionaryCache(type: String, items: List<DictionaryEntity>) {
        dictionaryDao.deleteByType(type)
        dictionaryDao.insertAll(items)
        dictionaryCache[type] = items
        dictionaryCacheTime = System.currentTimeMillis()
    }

    /** 清除所有缓存 */
    suspend fun clearAllCache() {
        clearProjectCache()
        dictionaryDao.deleteAll()
        dictionaryCache.clear()
        dictionaryCacheTime = 0L
    }
}
