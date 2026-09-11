package com.salary.manager

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.salary.core.network.interceptor.AuthInterceptor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 应用入口
 *
 * 职责：
 *   1. Hilt 依赖注入初始化（@HiltAndroidApp）
 *   2. 在 onCreate 里注册自定义 Coil ImageLoader，
 *      让所有 AsyncImage 图片请求都携带 Authorization: Bearer <token> 头，
 *      解决 /upload 附件被后端 JWT 中间件拒绝返回 401 的问题
 *
 * 关键实现说明：
 *   - 使用 EntryPointAccessors.fromApplication 手动获取 AuthInterceptor，
 *     而不是通过 @Inject lateinit var。原因：Hilt 编译器扫描 @Inject 字段所在类
 *     引用的所有类型时，会读取 Coil3 类的 Kotlin metadata，而 Hilt 当前版本
 *     不支持 Coil3 的新 metadata 版本，导致编译失败。
 *   - 使用 SingletonImageLoader.setSafe 全局注册单例 ImageLoader，
 *     所有 AsyncImage 调用会自动使用带 Authorization 头的 OkHttpClient
 */
@HiltAndroidApp
class SalaryApp : Application() {

    /**
     * Hilt EntryPoint：用于在非 Hilt 管理的场景（如手动初始化 Coil）中
     * 获取依赖。这里手动读取 AuthInterceptor 而不用 @Inject 字段，
     * 避免 Hilt 编译期扫描 Coil3 类导致的 metadata 错误。
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CoilEntryPoint {
        fun authInterceptor(): AuthInterceptor
    }

    override fun onCreate() {
        super.onCreate()
        // 注册全局 ImageLoader，让 Coil 加载图片时自动带 Authorization 头
        SingletonImageLoader.setSafe { context ->
            // 从 Hilt 手动获取 AuthInterceptor，避免 @Inject 字段引入 Coil 类到扫描表
            val entryPoint = EntryPointAccessors.fromApplication(
                this,
                CoilEntryPoint::class.java
            )
            val authInterceptor = entryPoint.authInterceptor()

            val imageOkHttpClient = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            ImageLoader.Builder(context)
                .components {
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = { imageOkHttpClient }
                        )
                    )
                }
                .crossfade(true)
                .build()
        }
    }
}
