package com.salary.core.network.api

import android.content.ContentResolver
import android.net.Uri
import com.salary.core.common.util.AppLog
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.buffer
import okio.source

/**
 * 流式带进度回调的RequestBody
 *
 * 直接从 ContentResolver 打开 InputStream 流式读取文件内容，
 * 在 writeTo 时边读边写，内存占用恒定为单个 buffer 大小（8KB），
 * 不再将整个文件读入内存，避免上传大文件(如500MB视频)时 OOM 崩溃。
 *
 * 抽取为公开类供工程附件上传（UploadManager）与知识库导入（AiRepository）共用，
 * 避免重复实现。
 *
 * @param contentResolver ContentResolver，用于打开文件输入流
 * @param uri 文件 Uri（来自系统文件选择器）
 * @param mediaType 文件MIME类型
 * @param totalSize 文件总大小（字节），用于计算进度百分比
 * @param onProgress 进度回调，参数为百分比（0-100）
 */
class StreamProgressRequestBody(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
    private val mediaType: MediaType?,
    private val totalSize: Long,
    private val onProgress: (Int) -> Unit
) : RequestBody() {

    /** 上传进度回调最小间隔（字节），避免过度刷新UI */
    private val progressNotifyIntervalBytes = 4096L

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = totalSize

    override fun writeTo(sink: okio.BufferedSink) {
        val inputStream = try {
            contentResolver.openInputStream(uri) ?: return
        } catch (e: Exception) {
            AppLog.w("StreamProgressRequestBody", "打开文件输入流失败: ${e.message}")
            return
        }

        // 使用 okio 从 InputStream 流式读取，避免 readBytes() 将整个文件加载到内存
        val source = inputStream.source().buffer()
        val buffer = okio.Buffer()
        var written = 0L
        var lastNotifiedBytes = 0L

        try {
            var read: Long
            // 每次最多读取 8KB，边读边写
            while (source.read(buffer, 8192L).also { read = it } != -1L) {
                sink.write(buffer, read)
                written += read
                // 超过节流间隔才回调一次
                if (written - lastNotifiedBytes >= progressNotifyIntervalBytes || written == totalSize) {
                    val percent = if (totalSize > 0) {
                        (written * 100 / totalSize).toInt().coerceIn(0, 100)
                    } else 100
                    onProgress(percent)
                    lastNotifiedBytes = written
                }
            }
            // 确保最终回调100
            onProgress(100)
        } finally {
            source.close()
            inputStream.close()
        }
    }
}
