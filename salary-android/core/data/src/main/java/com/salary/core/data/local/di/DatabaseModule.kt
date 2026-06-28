package com.salary.core.data.local.di

import android.content.Context
import androidx.room.Room
import com.salary.core.data.local.SalaryDatabase
import com.salary.core.data.local.dao.DictionaryDao
import com.salary.core.data.local.dao.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Room数据库依赖注入
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SalaryDatabase {
        return Room.databaseBuilder(
            context,
            SalaryDatabase::class.java,
            "salary_cache"
        ).build()
    }

    @Provides
    fun provideProjectDao(db: SalaryDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideDictionaryDao(db: SalaryDatabase): DictionaryDao = db.dictionaryDao()
}
