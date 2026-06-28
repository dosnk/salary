package com.salary.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.salary.core.data.local.entity.ProjectEntity

/**
 * 工程缓存数据访问对象
 */
@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY createdAt DESC")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Int): ProjectEntity?

    @Query("SELECT * FROM projects WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getByStatus(status: String): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE name LIKE '%' || :keyword || '%' ORDER BY createdAt DESC")
    suspend fun search(keyword: String): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(projects: List<ProjectEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    @Query("DELETE FROM projects WHERE cachedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
