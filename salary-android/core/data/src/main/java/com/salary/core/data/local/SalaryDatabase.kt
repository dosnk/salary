package com.salary.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.salary.core.data.local.dao.ProjectDao
import com.salary.core.data.local.dao.DictionaryDao
import com.salary.core.data.local.entity.ProjectEntity
import com.salary.core.data.local.entity.DictionaryEntity

/**
 * Room数据库定义
 *
 * 离线缓存表:
 * - projects: 工程列表缓存
 * - dictionaries: 字典数据缓存
 */
@Database(
    entities = [ProjectEntity::class, DictionaryEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SalaryDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun dictionaryDao(): DictionaryDao
}
