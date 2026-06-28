package com.salary.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.salary.core.data.local.entity.DictionaryEntity

/**
 * 字典缓存数据访问对象
 */
@Dao
interface DictionaryDao {

    @Query("SELECT * FROM dictionaries WHERE type = :type ORDER BY sortOrder")
    suspend fun getByType(type: String): List<DictionaryEntity>

    @Query("SELECT * FROM dictionaries ORDER BY type, sortOrder")
    suspend fun getAll(): List<DictionaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DictionaryEntity>)

    @Query("DELETE FROM dictionaries WHERE type = :type")
    suspend fun deleteByType(type: String)

    @Query("DELETE FROM dictionaries")
    suspend fun deleteAll()
}
