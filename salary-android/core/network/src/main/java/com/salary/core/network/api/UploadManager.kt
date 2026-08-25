package com.salary.core.network.api

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.salary.core.common.util.AppLog
import com.salary.core.network.dto.ApiResponse
import com.salary.core.network.dto.FileRecordDto
import com.salary.core.network.dto.ProjectFileRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.source
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上传附件结果
 * - [UploadOutcome.Success] 上传成功，携带服务端返回的上传信息
 * - [UploadOutcome.Error] 上传失败，携带用户友好的错误消息
 */
sealed interface UploadOutcome {
    data class Success(val data: UploadResultDto) : UploadOutcome
    data class Error(val message: String) : UploadOutcome
}

/**
 * 批量上传进度信息（用于UI实时展示）
 *
 * @param totalCount 总文件数
 * @param currentIndex 当前正在上传的文件索引（从0开始）
 * @param currentFileName 当前文件名
 * @param currentFilePercent 当前文件上传百分比（0-100）
 * @param successCount 已成功上传的文件数
 * @param failedCount 已失败的文件数
 */
data class UploadProgress(
    val totalCount: Int,
    val currentIndex: Int,
    val currentFileName: String,
    val currentFilePercent: Int,
    val successCount: Int,
    val failedCount: Int
) {
    /** 整体进度百分比（0-100） */
    val overallPercent: Int
        get() {
            if (totalCount == 0) return 100
            // 已完成文件数 * 100 + 当前文件进度，再除以总文件数
            val completed = successCount + failedCount
            val totalUnits = totalCount * 100
            val doneUnits = completed * 100 + currentFilePercent
            return (doneUnits * 100 / totalUnits).coerceIn(0, 100)
        }
}

/**
 * 附件上传管理器
 * 封装 Uri → MultipartBody.Part 的转换和 UploadApi 调用，
 * 避免上层模块（feature）直接依赖 okhttp3 类型，符合分层架构。
 */
@Singleton
class UploadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val uploadApi: UploadApi,
    private val projectApi: ProjectApi
) {

    private companion object {
        const val TAG = "UploadManager"
    }

    /**
     * 上传单个附件（两步式：先上传文件到磁盘，再写入数据库关联工程）
     *
     * 兼容保留单文件接口供旧调用方使用，无进度回调。
     *
     * @param uri 文件 Uri（来自系统文件选择器）
     * @param projectId 工程ID（用于第二步写入数据库关联）
     * @param projectName 工程名称，传给后端用于生成存储路径 upload/YYYYMM/工程名/
     * @return 上传结果（成功/失败）
     */
    suspend fun uploadAttachment(uri: Uri, projectId: Int, projectName: String): UploadOutcome {
        return uploadAttachmentWithProgress(uri, projectId, projectName, null)
    }

    /**
     * 上传单个附件（带进度回调）
     *
     * 流式上传：直接从 ContentResolver 打开 InputStream 边读边写，
     * 不再将整个文件读入内存，避免上传大视频(500MB)时 OOM 崩溃。
     *
     * @param onProgress 进度回调，参数为当前文件百分比（0-100）。在UploadManager内部线程调用，
     *                   调用方需自行切换到主线程更新UI。可为null。
     */
    suspend fun uploadAttachmentWithProgress(
        uri: Uri,
        projectId: Int,
        projectName: String,
        onProgress: ((Int) -> Unit)?
    ): UploadOutcome {
        // 1. 查询文件信息（不读入内存）
        val originalFileName = queryFileName(uri) ?: "attachment_${System.currentTimeMillis()}"
        val fileSize = queryFileSize(uri)
        val fileType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        return try {
            // 2. 构造流式可监听进度的 RequestBody（不在内存中持有整个文件）
            val mediaType = fileType.toMediaTypeOrNull()
            val progressBody = StreamProgressRequestBody(
                contentResolver = context.contentResolver,
                uri = uri,
                mediaType = mediaType,
                totalSize = fileSize,
                onProgress = { percent -> onProgress?.invoke(percent) }
            )
            val filePart = MultipartBody.Part.createFormData("file", originalFileName, progressBody)

            // 3. 构造工程名表单字段
            // 注意1: contentType 必须传 null，不能使用 "text/plain"
            // 原因: 后端 koa-body + formidable v2.x 通过 part 是否有 mimetype 判断字段类型
            //       - 无 mimetype → 作为 text field 解析到 ctx.request.body
            //       - 有 mimetype → 作为文件解析到 ctx.request.files
            // 若设置 text/plain，formidable 会把 projectName 当成文件处理，
            // 导致 ctx.request.body.projectName 为 undefined，最终回退为 "salary" 目录
            //
            // 注意2: 工程名必须 URL 编码后再发送
            // 原因: formidable 的 StringDecoder 在某些情况下会用错误的编码解码中文，
            // 导致文件夹名称乱码。URL 编码后只包含 ASCII 字符，避免编码问题。
            // 后端收到后用 decodeURIComponent 还原。
            val encodedProjectName = java.net.URLEncoder.encode(
                projectName.ifBlank { "salary" },
                "UTF-8"
            )
            val projectNameBody = encodedProjectName.toRequestBody(null)

            // 4. 第一步：调用后端上传接口，文件存到磁盘
            val response: ApiResponse<UploadResultDto> = uploadApi.uploadFile(filePart, projectNameBody)

            // 上传完成，确保进度到100
            onProgress?.invoke(100)

            if (response.code != 200 || response.data == null) {
                // 后端返回失败，直接使用后端的错误消息（已包含具体的中文提示）
                // 如"不支持的文件类型（.apk），仅支持图片、文档、视频、音频"
                val errorMsg = response.msg.ifBlank { "上传附件失败" }
                AppLog.w(TAG, "上传附件被后端拒绝: code=${response.code}, msg=${response.msg}")
                return UploadOutcome.Error(errorMsg)
            }

            val uploadResult = response.data
            val fileUrl = uploadResult.url
                ?: return UploadOutcome.Error("上传成功但未获取到文件URL")

            // 5. 第二步：把文件信息写入数据库，关联工程ID
            val fileRequest = ProjectFileRequest(
                filename = uploadResult.fileName ?: originalFileName,
                originalName = originalFileName,
                path = fileUrl,
                size = uploadResult.fileSize.takeIf { it > 0 } ?: fileSize,
                type = uploadResult.fileType ?: fileType
            )

            val recordResponse: ApiResponse<List<FileRecordDto>> =
                projectApi.uploadFile(projectId, fileRequest)

            if (recordResponse.code != 200) {
                AppLog.e(
                    TAG,
                    "文件已上传但写入数据库失败: projectId=$projectId, url=$fileUrl, ${recordResponse.msg}"
                )
                return UploadOutcome.Error("附件已上传但关联工程失败，请联系管理员")
            }

            UploadOutcome.Success(uploadResult)
        } catch (e: Exception) {
            AppLog.e(TAG, "上传附件异常: ${e.javaClass.simpleName}: ${e.message}", e)
            UploadOutcome.Error("上传附件失败，请检查网络后重试")
        }
    }

    /**
     * 批量上传附件（串行执行，带整体进度回调）
     *
     * @param uris 文件Uri列表
     * @param projectId 工程ID
     * @param projectName 工程名称
     * @param onProgress 进度回调（已在内部线程，调用方需自行切主线程）。每上传一个字节都可能触发。
     * @return 上传结果汇总（成功数、失败数、失败文件名列表）
     */
    suspend fun uploadAttachments(
        uris: List<Uri>,
        projectId: Int,
        projectName: String,
        onProgress: (UploadProgress) -> Unit
    ): BatchUploadResult {
        if (uris.isEmpty()) {
            return BatchUploadResult(0, 0, emptyList(), emptyList())
        }

        val total = uris.size
        var successCount = 0
        var failedCount = 0
        val failedFileNames = mutableListOf<String>()
        val failedDetails = mutableListOf<FailedFileDetail>()

        uris.forEachIndexed { index, uri ->
            val fileName = queryFileName(uri) ?: "文件${index + 1}"
            // 通知开始上传新文件（进度为0）
            onProgress(
                UploadProgress(
                    totalCount = total,
                    currentIndex = index,
                    currentFileName = fileName,
                    currentFilePercent = 0,
                    successCount = successCount,
                    failedCount = failedCount
                )
            )

            val outcome = uploadAttachmentWithProgress(uri, projectId, projectName) { percent ->
                onProgress(
                    UploadProgress(
                        totalCount = total,
                        currentIndex = index,
                        currentFileName = fileName,
                        currentFilePercent = percent,
                        successCount = successCount,
                        failedCount = failedCount
                    )
                )
            }

            when (outcome) {
                is UploadOutcome.Success -> successCount++
                is UploadOutcome.Error -> {
                    failedCount++
                    failedFileNames.add(fileName)
                    failedDetails.add(FailedFileDetail(fileName, outcome.message))
                    AppLog.w(TAG, "批量上传第${index + 1}个文件失败: ${outcome.message}")
                }
            }
        }

        // 上传全部完成，最终进度回调
        onProgress(
            UploadProgress(
                totalCount = total,
                currentIndex = total - 1,
                currentFileName = "",
                currentFilePercent = 100,
                successCount = successCount,
                failedCount = failedCount
            )
        )

        return BatchUploadResult(successCount, failedCount, failedFileNames, failedDetails)
    }

    /**
     * 批量上传结果汇总
     */
    data class BatchUploadResult(
        val successCount: Int,
        val failedCount: Int,
        val failedFileNames: List<String>,
        /** 失败文件详情（含错误原因），用于UI展示具体失败原因 */
        val failedDetails: List<FailedFileDetail>
    ) {
        /** 是否全部成功 */
        val isAllSuccess: Boolean get() = failedCount == 0
        /** 总数 */
        val totalCount: Int get() = successCount + failedCount
    }

    /**
     * 失败文件详情
     * @param fileName 文件名
     * @param error 失败原因（用户友好的中文提示）
     */
    data class FailedFileDetail(val fileName: String, val error: String)

    /**
     * 查询文件大小（字节）
     */
    private fun queryFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst()) cursor.getLong(sizeIndex) else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * 查询 Uri 对应的文件显示名
     */
    private fun queryFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * 流式带进度回调的RequestBody
 *
 * 直接从 ContentResolver 打开 InputStream 流式读取文件内容，
 * 在 writeTo 时边读边写，内存占用恒定为单个 buffer 大小（8KB），
 * 不再将整个文件读入内存，避免上传大视频(500MB)时 OOM 崩溃。
 *
 * @param contentResolver ContentResolver，用于打开文件输入流
 * @param uri 文件 Uri（来自系统文件选择器）
 * @param mediaType 文件MIME类型
 * @param totalSize 文件总大小（字节），用于计算进度百分比
 * @param onProgress 进度回调，参数为百分比（0-100）
 */
private class StreamProgressRequestBody(
    private val contentResolver: android.content.ContentResolver,
    private val uri: android.net.Uri,
    private val mediaType: okhttp3.MediaType?,
    private val totalSize: Long,
    private val onProgress: (Int) -> Unit
) : RequestBody() {

    /** 上传进度回调最小间隔（字节），避免过度刷新UI */
    private val progressNotifyIntervalBytes = 4096L

    override fun contentType(): okhttp3.MediaType? = mediaType

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
