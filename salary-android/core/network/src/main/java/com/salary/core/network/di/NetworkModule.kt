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
import com.salary.core.network.api.UploadApi
import com.salary.core.network.api.UserApi
import com.salary.core.network.interceptor.AuthInterceptor
import com.salary.core.network.interceptor.LatencyInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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

    /**
     * 日志拦截器
     * - DEBUG构建：HEADERS级别，记录请求/响应头用于调试，但不记录请求/响应体（避免泄露Token和敏感数据）
     * - RELEASE构建：NONE级别，完全禁用日志
     *
     * 安全说明：
     * - 禁止使用BASIC或BODY级别，BASIC会暴露URL参数，BODY会完整打印Token和用户数据
     * - HEADERS级别仍会记录Authorization头，通过logger自定义过滤掉敏感头
     */
    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        val logger = HttpLoggingInterceptor.Logger { message ->
            // 过滤掉Authorization头，避免Token泄露到logcat
            if (message.startsWith("Authorization:", ignoreCase = true)) {
                return@Logger
            }
            // 过滤Cookie等敏感头
            if (message.startsWith("Cookie:", ignoreCase = true) ||
                message.startsWith("Set-Cookie:", ignoreCase = true)) {
                return@Logger
            }
            // 使用系统println输出到logcat，便于调试时查看
            println("[Network] $message")
        }
        return HttpLoggingInterceptor(logger).apply {
            level = if (com.salary.core.network.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
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
        // 同步读取服务器地址缓存，避免runBlocking阻塞线程池
        // 注意：App启动时需先调用serverConfig.initConfig()初始化缓存
        val baseUrl = serverConfig.getServerUrlSync().ifEmpty { ServerConfig.DEFAULT_URL + "/" }
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

    @Provides
    @Singleton
    fun provideUploadApi(retrofit: Retrofit): UploadApi = retrofit.create(UploadApi::class.java)
}
