package com.salary.manager.feature.ai.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.salary.core.common.util.AppLog
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.data.local.ServerConfig
import com.salary.core.data.local.TokenStorage
import com.salary.core.network.api.AiApi
import com.salary.core.network.api.AiChatRequest
import com.salary.core.network.api.CreateKnowledgeRequest
import com.salary.core.network.api.CreateMaterialRequest
import com.salary.core.network.api.DeleteKnowledgeResponse
import com.salary.core.network.api.ImportMediaResponse
import com.salary.core.network.api.KnowledgeCategoriesResponse
import com.salary.core.network.api.KnowledgeCitationDto
import com.salary.core.network.api.KnowledgeDetailResponse
import com.salary.core.network.api.KnowledgeListResponse
import com.salary.core.network.api.LayoutRequest
import com.salary.core.network.api.LayoutResponse
import com.salary.core.network.api.MaterialCategoryDto
import com.salary.core.network.api.MaterialDto
import com.salary.core.network.api.MaterialOptionsDto
import com.salary.core.network.api.StreamProgressRequestBody
import com.salary.core.network.api.UpdateKnowledgeRequest
import com.salary.core.network.api.UpdateMaterialRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI数据仓库
 *
 * 核心职责:
 * 1. SSE流式对话 - 使用OkHttp直接处理SSE事件流
 * 2. 普通对话 - 通过Retrofit
 * 3. 排料计算 - 通过Retrofit
 * 4. 材料查询 - 通过Retrofit
 * 5. 知识库管理 - 通过Retrofit（含文件/媒体multipart导入）
 */
@Singleton
class AiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiApi: AiApi,
    private val tokenStorage: TokenStorage,
    private val serverConfig: ServerConfig,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val TAG = "AiRepository"

        /**
         * SSE 流式读超时：两个数据块之间的最大间隔时间。
         *
         * 超过该时间未收到任何数据则判定为服务端假死/网络断开，主动断开连接。
         * AI 流式响应通常每 1-3 秒推送一次，120 秒留足余量。
         */
        private const val SSE_READ_TIMEOUT_SECONDS = 120L

        /**
         * SSE 调用总超时：整个流式请求的最长持续时间。
         *
         * 防止异常情况下流式连接无限挂起导致资源泄漏。
         * 10 分钟覆盖绝大多数长对话场景。
         */
        private const val SSE_CALL_TIMEOUT_MINUTES = 10L
    }

    /**
     * SSE流式发送消息
     *
     * 返回Flow<SseEvent>，调用方通过collect实时接收:
     * - SseEvent.Content(text) — 流式文本片段
     * - SseEvent.Done(intent) — 结束标记
     * - SseEvent.Error(message) — 错误
     *
     * 超时与泄漏防护：
     * - readTimeout=120s：防止服务端假死时连接无限挂起
     * - callTimeout=10min：防止异常流式连接长期占用资源
     * - Response 使用 use{} 确保连接释放
     * - 调用方取消 collect 会自动终止 flow（结构化并发），底层 readLine 会抛 IOException 退出循环
     *
     * @param message 用户消息
     * @param sessionId 会话ID
     */
    fun sendMessageStream(message: String, sessionId: String): Flow<SseEvent> = flow {
        // 从ServerConfig动态获取服务器地址
        val baseUrl = serverConfig.getServerUrl()
        if (baseUrl.isEmpty()) {
            emit(SseEvent.Error("服务器地址未配置"))
            return@flow
        }

        val token = tokenStorage.getAccessToken()
        val requestBody = json.encodeToString(
            kotlinx.serialization.serializer<AiChatRequest>(),
            AiChatRequest(message, sessionId)
        )

        val request = Request.Builder()
            .url("${baseUrl}v1/ai/chat/stream")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Accept", "text/event-stream")
            .addHeader("Cache-Control", "no-cache")
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()

        // 为SSE流式请求创建独立超时配置的Client（不影响全局OkHttpClient配置）
        // readTimeout 控制两个数据块之间的间隔超时；callTimeout 控制整个请求总时长
        val sseClient = okHttpClient.newBuilder()
            .readTimeout(SSE_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(SSE_CALL_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .build()

        try {
            // 使用 execute() 同步发起请求（flowOn(Dispatchers.IO) 保证在IO线程）
            // response.use{} 确保无论正常结束还是异常，Response 都被关闭，避免连接泄漏
            sseClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit(SseEvent.Error("服务异常: ${response.code}"))
                    return@use
                }

                val reader = response.body?.byteStream()?.bufferedReader()
                if (reader == null) {
                    emit(SseEvent.Error("响应体为空"))
                    return@use
                }

                reader.use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        val currentLine = line ?: continue
                        // SSE格式: data: {json}\n\n
                        if (!currentLine.startsWith("data: ")) continue

                        val data = currentLine.removePrefix("data: ").trim()
                        if (data.isEmpty()) continue

                        try {
                            val element = json.parseToJsonElement(data).jsonObject
                            val type = element["type"]?.jsonPrimitive?.content ?: continue

                            when (type) {
                                "content" -> {
                                    val text = element["text"]?.jsonPrimitive?.content ?: ""
                                    emit(SseEvent.Content(text))
                                }
                                "done" -> {
                                    val intent = element["intent"]?.jsonPrimitive?.content ?: ""
                                    // 解析引用溯源（后端携带citations数组）
                                    val citations = element["citations"]?.let { citElement ->
                                        runCatching {
                                            json.decodeFromJsonElement(
                                                kotlinx.serialization.serializer<List<KnowledgeCitationDto>>(),
                                                citElement
                                            )
                                        }.getOrDefault(emptyList())
                                    } ?: emptyList()
                                    emit(SseEvent.Done(intent, citations))
                                }
                                "error" -> {
                                    val errorMsg = element["message"]?.jsonPrimitive?.content ?: "未知错误"
                                    emit(SseEvent.Error(errorMsg))
                                }
                            }
                        } catch (e: Exception) {
                            AppLog.w(TAG, "解析SSE数据失败: $data", e)
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 调用方取消（如用户停止生成/切换页面），不视为错误，直接传播取消语义
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            AppLog.w(TAG, "SSE读超时（服务端长时间无数据推送）", e)
            emit(SseEvent.Error("响应超时，请检查网络后重试"))
        } catch (e: Exception) {
            AppLog.e(TAG, "SSE连接失败", e)
            emit(SseEvent.Error(NetworkErrorHandler.translate(e, "连接失败")))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 排料计算
     * @param roomLength 房间长度(cm)
     * @param roomWidth 房间宽度(cm)
     * @param materialOptions 材料选项（面材/主龙骨/副龙骨/收边条ID）
     */
    suspend fun calculateLayout(
        roomLength: Double,
        roomWidth: Double,
        materialOptions: MaterialOptionsDto? = null
    ): Result<LayoutResponse> {
        return try {
            val request = LayoutRequest(roomLength, roomWidth, materialOptions)
            val response = aiApi.calculateLayout(request)
            if (response.code == 200) {
                val data = response.data ?: return Result.failure(Exception("响应数据为空"))
                Result.success(data)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "排料计算失败")))
        }
    }

    /**
     * 获取材料分类
     */
    suspend fun getMaterialCategories(): Result<List<MaterialCategoryDto>> {
        return try {
            val response = aiApi.getMaterialCategories()
            if (response.code == 200) {
                val data = response.data ?: return Result.failure(Exception("响应数据为空"))
                Result.success(data)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "加载材料分类失败")))
        }
    }

    /**
     * 获取所有材料
     */
    suspend fun getAllMaterials(): Result<List<MaterialDto>> {
        return try {
            val response = aiApi.getAllMaterials()
            if (response.code == 200) {
                val data = response.data ?: return Result.failure(Exception("响应数据为空"))
                Result.success(data)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "加载材料数据失败")))
        }
    }

    /**
     * 创建材料参数（仅admin）
     * @param request 创建请求
     */
    suspend fun createMaterial(request: CreateMaterialRequest): Result<MaterialDto> {
        return try {
            val response = aiApi.createMaterial(request)
            if (response.code == 200) {
                val data = response.data ?: return Result.failure(Exception("响应数据为空"))
                Result.success(data)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "创建材料失败")))
        }
    }

    /**
     * 更新材料参数（仅admin）
     * @param id 材料ID
     * @param request 更新请求
     */
    suspend fun updateMaterial(id: Int, request: UpdateMaterialRequest): Result<MaterialDto> {
        return try {
            val response = aiApi.updateMaterial(id, request)
            if (response.code == 200) {
                val data = response.data ?: return Result.failure(Exception("响应数据为空"))
                Result.success(data)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "更新材料失败")))
        }
    }

    /**
     * 删除材料参数（仅admin，软删除）
     * @param id 材料ID
     */
    suspend fun deleteMaterial(id: Int): Result<Unit> {
        return try {
            val response = aiApi.deleteMaterial(id)
            if (response.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "删除材料失败")))
        }
    }

    /**
     * 获取知识库文档列表
     * @param page 页码（从1开始）
     * @param pageSize 每页数量
     * @param category 分类筛选（null=全部）
     * @param keyword 标题/内容关键词（null=不筛选）
     */
    suspend fun listKnowledge(
        page: Int = 1,
        pageSize: Int = 20,
        category: String? = null,
        keyword: String? = null
    ): Result<KnowledgeListResponse> {
        return try {
            val response = aiApi.listKnowledge(page, pageSize, category?.takeIf { it.isNotBlank() }, keyword?.takeIf { it.isNotBlank() })
            if (response.code == 200) {
                val data = response.data ?: return Result.failure(Exception("响应数据为空"))
                Result.success(data)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "加载知识库列表失败")))
        }
    }

    /**
     * 获取知识分类列表（含各分类文档数）
     */
    suspend fun getKnowledgeCategories(): Result<KnowledgeCategoriesResponse> {
        return try {
            val response = aiApi.getKnowledgeCategories()
            if (response.code == 200) {
                val data = response.data ?: return Result.failure(Exception("响应数据为空"))
                Result.success(data)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "加载知识分类失败")))
        }
    }

    /**
     * 手动录入知识文档（纯文本）
     * @param title 文档标题
     * @param content 文档内容（10-50000字符）
     * @param category 知识分类
     */
    suspend fun createKnowledge(title: String, content: String, category: String?): Result<Unit> {
        return try {
            val request = CreateKnowledgeRequest(title = title, content = content, category = category?.takeIf { it.isNotBlank() })
            val response = aiApi.createKnowledge(request)
            if (response.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "添加知识文档失败")))
        }
    }

    /**
     * 文件导入知识文档（txt/md/pdf/docx）
     * 流式上传（ContentResolver边读边写），避免大文件OOM
     *
     * @param uri 文件Uri（来自系统文件选择器）
     * @param title 标题（空则后端用文件名）
     * @param category 知识分类
     * @param onProgress 上传进度回调（0-100，IO线程）
     */
    suspend fun importKnowledgeFile(
        uri: Uri,
        title: String,
        category: String?,
        onProgress: ((Int) -> Unit)? = null
    ): Result<Unit> {
        return try {
            val parts = buildMultipartParts(uri, onProgress) ?: return Result.failure(Exception("无法读取所选文件"))
            val titleBody = title.takeIf { it.isNotBlank() }?.toFormBody()
            val categoryBody = category?.takeIf { it.isNotBlank() }?.toFormBody()

            val response = aiApi.importKnowledgeFile(parts.filePart, titleBody, categoryBody)
            if (response.code == 200) {
                onProgress?.invoke(100)
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "文件导入失败")))
        }
    }

    /**
     * 媒体导入知识文档（图片/视频/音频）
     * 图片由后端调用视觉模型自动识别；视频/音频靠标题+描述检索
     *
     * @param uri 文件Uri
     * @param title 标题（必填）
     * @param category 知识分类
     * @param description 手动描述（补充检索关键词）
     * @param onProgress 上传进度回调（0-100，IO线程）
     */
    suspend fun importKnowledgeMedia(
        uri: Uri,
        title: String,
        category: String?,
        description: String,
        onProgress: ((Int) -> Unit)? = null
    ): Result<ImportMediaResponse> {
        return try {
            val parts = buildMultipartParts(uri, onProgress) ?: return Result.failure(Exception("无法读取所选文件"))
            val titleBody = title.toFormBody()
            val categoryBody = category?.takeIf { it.isNotBlank() }?.toFormBody()
            val descBody = description.takeIf { it.isNotBlank() }?.toFormBody()

            val response = aiApi.importKnowledgeMedia(parts.filePart, titleBody, categoryBody, descBody)
            if (response.code == 200) {
                onProgress?.invoke(100)
                val data = response.data ?: return Result.failure(Exception("响应数据为空"))
                Result.success(data)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "媒体导入失败")))
        }
    }

    /**
     * 获取知识文档详情（元信息+全文+媒体URL）
     * @param id 文档ID
     */
    suspend fun getKnowledgeDetail(id: Int): Result<KnowledgeDetailResponse> {
        return try {
            val response = aiApi.getKnowledgeDetail(id)
            if (response.code == 200) {
                val data = response.data ?: return Result.failure(Exception("响应数据为空"))
                Result.success(data)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "加载知识文档详情失败")))
        }
    }

    /**
     * 编辑知识文档（内容变更时后端自动重新分块）
     * @param id 文档ID
     * @param title 新标题（null=不修改）
     * @param content 新内容（null=不修改）
     * @param category 新分类（null=不修改）
     * @param description 新描述（null=不修改，媒体类型用）
     */
    suspend fun updateKnowledge(
        id: Int,
        title: String? = null,
        content: String? = null,
        category: String? = null,
        description: String? = null
    ): Result<Unit> {
        return try {
            val request = UpdateKnowledgeRequest(
                title = title?.takeIf { it.isNotBlank() },
                content = content?.takeIf { it.isNotBlank() },
                category = category?.takeIf { it.isNotBlank() },
                description = description
            )
            val response = aiApi.updateKnowledge(id, request)
            if (response.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "更新知识文档失败")))
        }
    }

    /**
     * 删除知识文档（级联删分块+删媒体文件）
     * @param id 文档ID
     */
    suspend fun deleteKnowledge(id: Int): Result<DeleteKnowledgeResponse> {
        return try {
            val response = aiApi.deleteKnowledge(id)
            if (response.code == 200) {
                val data = response.data ?: return Result.failure(Exception("响应数据为空"))
                Result.success(data)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "删除知识文档失败")))
        }
    }

    /**
     * 图片重新识别（视觉模型配置好后补识别）
     * @param id 文档ID
     */
    suspend fun redescribeKnowledge(id: Int): Result<Unit> {
        return try {
            val response = aiApi.redescribeKnowledge(id)
            if (response.code == 200) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "图片重新识别失败")))
        }
    }

    /**
     * 构造multipart文件part（流式，带进度回调）
     * @return 文件part与原始文件名；无法读取时返回null
     */
    private fun buildMultipartParts(
        uri: Uri,
        onProgress: ((Int) -> Unit)?
    ): MultipartParts? {
        val fileName = queryFileName(uri) ?: return null
        val fileSize = queryFileSize(uri)
        val fileType = context.contentResolver.getType(uri) ?: "application/octet-stream"

        val progressBody = StreamProgressRequestBody(
            contentResolver = context.contentResolver,
            uri = uri,
            mediaType = fileType.toMediaTypeOrNull(),
            totalSize = fileSize,
            onProgress = { percent -> onProgress?.invoke(percent) }
        )
        val filePart = MultipartBody.Part.createFormData("file", fileName, progressBody)
        return MultipartParts(filePart, fileName)
    }

    /**
     * 构造multipart文本表单字段
     * 注意: contentType必须传null（不设置"text/plain"），否则formidable会将其识别为文件
     */
    private fun String.toFormBody(): okhttp3.RequestBody = toRequestBody(null)

    /** multipart构造结果 */
    private data class MultipartParts(val filePart: MultipartBody.Part, val fileName: String)

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
}

/**
 * SSE事件密封类
 */
sealed class SseEvent {
    /** 流式文本片段 */
    data class Content(val text: String) : SseEvent()

    /** 结束标记（含引用溯源：本次回答引用的知识文档列表） */
    data class Done(val intent: String, val citations: List<KnowledgeCitationDto> = emptyList()) : SseEvent()

    /** 错误 */
    data class Error(val message: String) : SseEvent()
}
