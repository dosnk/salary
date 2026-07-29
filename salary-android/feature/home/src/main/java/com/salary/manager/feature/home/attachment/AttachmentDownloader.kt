package com.salary.manager.feature.home.attachment

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 附件下载/分享/保存工具
 *
 * 三大能力：
 * 1) [downloadToCache]：把远端附件下载到 App 私有缓存目录 cacheDir/attachments/
 *    - 复用 core:network 的全局 [OkHttpClient]（自动带 Authorization 头）
 *    - 命中缓存直接返回（按 fileName+size 判定），避免重复下载
 * 2) [share]：把附件缓存文件通过 [FileProvider] 转 content:// Uri，
 *    再以 [Intent.ACTION_SEND] 触发系统分享面板
 * 3) [saveToDownloads]：把附件写入系统 Downloads 公共目录
 *    - Android 10+ (API 29+)：使用 [MediaStore.Downloads]，无需存储权限
 *    - Android <=9 (API <=28)：直接写入 [Environment.DIRECTORY_DOWNLOADS]
 *
 * 设计说明：
 * - 使用 Hilt EntryPoint 从 [SingletonComponent] 提取 [OkHttpClient]，
 *   避免让 UI 层 Composable 显式持有依赖，也复用了 App 现有的鉴权拦截器
 * - 所有 IO 操作强制在 [Dispatchers.IO] 执行
 * - 网络/文件异常向调用方以 [Result.failure] 抛出（内部不吞异常）
 *
 * FileProvider 依赖 AndroidManifest 声明的 authorities=${applicationId}.fileprovider
 * 与 res/xml/file_paths.xml 中的 cache-path "attachments/" 映射。
 */
object AttachmentDownloader {

    /**
     * Hilt EntryPoint：从 SingletonComponent 提取已配置好的 OkHttpClient
     * （包含 AuthInterceptor / LatencyInterceptor / LoggingInterceptor）
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface OkHttpClientEntryPoint {
        fun okHttpClient(): OkHttpClient
    }

    /**
     * 下载附件到缓存目录
     *
     * @param context Android 上下文
     * @param url 完整访问 URL（本方法内部会调用 encodeAttachmentUrl 做兜底编码）
     * @param fileName 期望保存的文件名（用于本地缓存文件与对外分享名称）
     * @param expectedSize 附件预期大小（字节）；用于命中缓存判定，<=0 表示不校验
     * @return 成功返回本地缓存 [File]；失败返回 [Result.failure]
     */
    suspend fun downloadToCache(
        context: Context,
        url: String,
        fileName: String,
        expectedSize: Long = -1L
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.isNotBlank()) { "附件 URL 为空" }
            val safeName = sanitizeFileName(fileName)
            val cacheDir = File(context.cacheDir, "attachments").apply { mkdirs() }
            val target = File(cacheDir, safeName)

            // 命中缓存：文件已存在且大小匹配（或未提供 expectedSize 时按存在即命中）
            if (target.exists() && (expectedSize <= 0 || target.length() == expectedSize)) {
                return@runCatching target
            }

            // 惰性获取 OkHttpClient（复用 App 已配置的鉴权拦截器）
            val client = EntryPoints
                .get(context.applicationContext, OkHttpClientEntryPoint::class.java)
                .okHttpClient()

            val safeUrl = encodeAttachmentUrl(url)
            val request = Request.Builder().url(safeUrl).build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IOException("下载失败：HTTP ${resp.code}")
                }
                val body = resp.body ?: throw IOException("下载失败：响应体为空")

                // 写入临时文件后再重命名，避免中途失败留下半成品
                val tmp = File(cacheDir, "$safeName.tmp")
                body.byteStream().use { input ->
                    FileOutputStream(tmp).use { output ->
                        input.copyTo(output)
                    }
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    // 极少数文件系统 rename 失败：兜底改为复制
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                target
            }
        }
    }

    /**
     * 分享附件
     *
     * 流程：下载到缓存 → FileProvider 生成 content:// Uri → 系统分享面板
     *
     * @param context Android 上下文
     * @param url 附件 URL
     * @param fileName 分享用文件名
     * @param mimeType 文件 MIME 类型；空时按扩展名推断，仍拿不到则用通配 MIME
     * @param expectedSize 附件字节数（用于缓存命中判定）
     * @return 成功返回 [Result.success]；失败返回 [Result.failure]，异常来自网络或文件层
     */
    suspend fun share(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?,
        expectedSize: Long = -1L
    ): Result<Unit> {
        val downloaded = downloadToCache(context, url, fileName, expectedSize)
            .getOrElse { return Result.failure(it) }

        return runCatching {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, downloaded)
            val resolvedType = mimeType?.takeIf { it.isNotBlank() }
                ?: guessMimeType(downloaded.name)
                ?: "*/*"

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = resolvedType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, downloaded.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "分享附件").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    /**
     * 保存附件到系统 Downloads 目录
     *
     * @param context Android 上下文
     * @param url 附件 URL
     * @param fileName 保存用文件名（重名会自动加"(1)、(2)…"后缀）
     * @param mimeType 文件 MIME 类型；空时按扩展名推断，最终兜底 "application/octet-stream"
     * @param expectedSize 附件字节数（用于缓存命中判定，可减少一次网络请求）
     * @return 成功返回目标文件的 URI 字符串或路径（用于 Toast 展示）；失败返回 [Result.failure]
     */
    suspend fun saveToDownloads(
        context: Context,
        url: String,
        fileName: String,
        mimeType: String?,
        expectedSize: Long = -1L
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cached = downloadToCache(context, url, fileName, expectedSize).getOrThrow()
            val resolvedMime = mimeType?.takeIf { it.isNotBlank() }
                ?: guessMimeType(cached.name)
                ?: "application/octet-stream"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+：使用 MediaStore.Downloads，App 卸载后文件仍保留
                saveViaMediaStore(context, cached, resolvedMime)
            } else {
                // Android 9-：直接写入公共 Downloads/ 目录（依赖 WRITE_EXTERNAL_STORAGE 权限）
                saveViaLegacyPath(cached)
            }
        }
    }

    /**
     * Android 10+：通过 MediaStore.Downloads 写入分区存储
     * 优点：无需运行时存储权限，卸载 App 不影响文件
     */
    private fun saveViaMediaStore(context: Context, source: File, mimeType: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uniqueName = ensureUniqueMediaStoreName(context, source.name)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, uniqueName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.SIZE, source.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(collection, values)
            ?: throw IOException("无法在 Downloads 目录中创建文件")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("无法打开输出流")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
        } catch (e: Throwable) {
            // 写入失败清理已插入的空记录
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return "Downloads/$uniqueName"
    }

    /**
     * Android 9-：直接写入 /sdcard/Download/ 目录
     */
    private fun saveViaLegacyPath(source: File): String {
        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS
        )
        if (!downloadsDir.exists()) downloadsDir.mkdirs()

        var target = File(downloadsDir, source.name)
        // 重名兜底
        if (target.exists()) {
            val base = source.name.substringBeforeLast('.', source.name)
            val ext = source.name.substringAfterLast('.', "")
            var idx = 1
            while (target.exists()) {
                val newName = if (ext.isEmpty()) "$base($idx)" else "$base($idx).$ext"
                target = File(downloadsDir, newName)
                idx++
            }
        }
        source.copyTo(target, overwrite = false)
        return target.absolutePath
    }

    /**
     * MediaStore 侧的重名兜底：查询 Downloads 中是否已存在同名文件，若存在则加序号
     */
    private fun ensureUniqueMediaStoreName(context: Context, name: String): String {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var candidate = name
        var idx = 1
        while (existsInMediaStore(resolver, collection, candidate)) {
            candidate = if (ext.isEmpty()) "$base($idx)" else "$base($idx).$ext"
            idx++
        }
        return candidate
    }

    private fun existsInMediaStore(
        resolver: android.content.ContentResolver,
        uri: Uri,
        displayName: String
    ): Boolean {
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val args = arrayOf(displayName)
        return resolver.query(uri, projection, selection, args, null)?.use { cursor ->
            cursor.count > 0
        } ?: false
    }

    /**
     * 清理文件名中的非法字符，避免 Windows/Linux/Android 文件系统冲突
     */
    private fun sanitizeFileName(name: String): String {
        if (name.isBlank()) return "attachment"
        val cleaned = name.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
        return cleaned.take(150).ifBlank { "attachment" }
    }

    /**
     * 按文件扩展名推断 MIME 类型，未识别时返回 null
     */
    private fun guessMimeType(fileName: String): String? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
}
