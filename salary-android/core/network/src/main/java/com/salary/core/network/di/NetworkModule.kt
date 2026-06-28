package com.salary.core.network.di

import com.salary.core.data.local.ServerConfig
import com.salary.core.data.local.TokenStorage
import com.salary.core.network.api.AiApi
import com.salary.core.network.api.AuthApi
import com.salary.core.network.api.DictionaryApi
import com.salary.core.network.api.MessageApi
import com.salary.core.network.api.ProjectApi
import com.salary.core.network.api.SettlementApi
import com.salary.core.network.api.AdvanceApi
import com.salary.core.network.api.SalarySheetApi
import com.salary.core.network.api.StatisticsApi
import com.salary.core.network.api.UserApi
import com.salary.core.network.interceptor.AuthInterceptor
import com.salary.core.network.interceptor.LatencyInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 网络层依赖注入模块
 *
 * 服务器地址从ServerConfig读取，首次启动时用户配置
 * 默认值: http://192.168.1.68:9393/
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        latencyInterceptor: LatencyInterceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(latencyInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json, serverConfig: ServerConfig): Retrofit {
        // 从ServerConfig读取用户配置的服务器地址
        val baseUrl = runBlocking {
            serverConfig.getServerUrl().ifEmpty { ServerConfig.DEFAULT_URL + "/" }
        }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideProjectApi(retrofit: Retrofit): ProjectApi = retrofit.create(ProjectApi::class.java)

    @Provides
    @Singleton
    fun provideAiApi(retrofit: Retrofit): AiApi = retrofit.create(AiApi::class.java)

    @Provides
    @Singleton
    fun provideUserApi(retrofit: Retrofit): UserApi = retrofit.create(UserApi::class.java)

    @Provides
    @Singleton
    fun provideDictionaryApi(retrofit: Retrofit): DictionaryApi = retrofit.create(DictionaryApi::class.java)

    @Provides
    @Singleton
    fun provideMessageApi(retrofit: Retrofit): MessageApi = retrofit.create(MessageApi::class.java)

    @Provides
    @Singleton
    fun provideSettlementApi(retrofit: Retrofit): SettlementApi = retrofit.create(SettlementApi::class.java)

    @Provides
    @Singleton
    fun provideAdvanceApi(retrofit: Retrofit): AdvanceApi = retrofit.create(AdvanceApi::class.java)

    @Provides
    @Singleton
    fun provideStatisticsApi(retrofit: Retrofit): StatisticsApi = retrofit.create(StatisticsApi::class.java)

    @Provides
    @Singleton
    fun provideSalarySheetApi(retrofit: Retrofit): SalarySheetApi = retrofit.create(SalarySheetApi::class.java)
}
