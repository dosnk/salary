package com.salary.manager.feature.ai.data

import android.util.Log
import com.salary.core.common.util.NetworkErrorHandler
import com.salary.core.data.local.ServerConfig
import com.salary.core.data.local.TokenStorage
import com.salary.core.network.api.AiApi
import com.salary.core.network.api.AiChatRequest
import com.salary.core.network.api.AiChatResponse
import com.salary.core.network.api.CreateKnowledgeRequest
import com.salary.core.network.api.CreateMaterialRequest
import com.salary.core.network.api.DeleteKnowledgeResponse
import com.salary.core.network.api.KnowledgeDetailResponse
import com.salary.core.network.api.KnowledgeItemDto
import com.salary.core.network.api.KnowledgeListResponse
import com.salary.core.network.api.LayoutRequest
import com.salary.core.network.api.LayoutResponse
import com.salary.core.network.api.MaterialCategoryDto
import com.salary.core.network.api.MaterialDto
import com.salary.core.network.api.MaterialOptionsDto
import com.salary.core.network.api.UpdateMaterialRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
 */
@Singleton
class AiRepository @Inject constructor(
    private val aiApi: AiApi,
    private val tokenStorage: TokenStorage,
    private val serverConfig: ServerConfig,
    private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    companion object {
        private const val TAG = "AiRepository"
    }

    /**
     * SSE流式发送消息
     *
     * 返回Flow<SseEvent>，调用方通过collect实时接收:
     * - SseEvent.Content(text) — 流式文本片段
     * - SseEvent.Done(intent) — 结束标记
     * - SseEvent.Error(message) — 错误
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

        try {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(SseEvent.Error("服务异常: ${response.code}"))
                return@flow
            }

            val reader = response.body?.byteStream()?.bufferedReader()
            if (reader == null) {
                emit(SseEvent.Error("响应体为空"))
                return@flow
            }

            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
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
                                emit(SseEvent.Done(intent))
                            }
                            "error" -> {
                                val errorMsg = element["message"]?.jsonPrimitive?.content ?: "未知错误"
                                emit(SseEvent.Error(errorMsg))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "解析SSE数据失败: $data", e)
                    }
                }
            } finally {
                reader.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "SSE连接失败", e)
            emit(SseEvent.Error(NetworkErrorHandler.translate(e, "连接失败")))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 发送消息（普通响应，非流式）
     */
    suspend fun sendMessage(message: String, sessionId: String): Result<AiChatResponse> {
        return try {
            val response = aiApi.sendMessage(AiChatRequest(message, sessionId))
            if (response.code == 200) {
                val data = response.data ?: return Result.failure(Exception("响应数据为空"))
                Result.success(data)
            } else {
                Result.failure(Exception(response.msg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(NetworkErrorHandler.translate(e, "请求失败")))
        }
    }

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
     */
    suspend fun listKnowledge(page: Int = 1, pageSize: Int = 20): Result<KnowledgeListResponse> {
        return try {
            val response = aiApi.listKnowledge(page, pageSize)
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
     * 添加知识文档
     * @param title 文档标题
     * @param content 文档内容（10-50000字符）
     */
    suspend fun createKnowledge(title: String, content: String): Result<Unit> {
        return try {
            val request = CreateKnowledgeRequest(title = title, content = content, sourceType = "manual")
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
     * 获取知识文档详情
     * @param title 文档标题
     */
    suspend fun getKnowledgeDetail(title: String): Result<KnowledgeDetailResponse> {
        return try {
            val response = aiApi.getKnowledgeDetail(java.net.URLEncoder.encode(title, "UTF-8"))
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
     * 删除知识文档
     * @param title 文档标题
     */
    suspend fun deleteKnowledge(title: String): Result<DeleteKnowledgeResponse> {
        return try {
            val response = aiApi.deleteKnowledge(java.net.URLEncoder.encode(title, "UTF-8"))
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
}

/**
 * SSE事件密封类
 */
sealed class SseEvent {
    /** 流式文本片段 */
    data class Content(val text: String) : SseEvent()

    /** 结束标记 */
    data class Done(val intent: String) : SseEvent()

    /** 错误 */
    data class Error(val message: String) : SseEvent()
}
