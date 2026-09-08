package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * AI模块API接口
 *
 * SSE流式对话通过AiRepository直接使用OkHttp实现，
 * 此接口仅定义普通(非流式)请求
 */
interface AiApi {

    /** 发送消息（普通响应） */
    @POST("v1/ai/chat")
    suspend fun sendMessage(@Body body: AiChatRequest): ApiResponse<AiChatResponse>

    /** 排料计算 */
    @POST("v1/ai/layout")
    suspend fun calculateLayout(@Body body: LayoutRequest): ApiResponse<LayoutResponse>

    /** 获取材料分类 */
    @GET("v1/ai/materials/categories")
    suspend fun getMaterialCategories(): ApiResponse<List<MaterialCategoryDto>>

    /** 获取所有材料 */
    @GET("v1/ai/materials")
    suspend fun getAllMaterials(): ApiResponse<List<MaterialDto>>

    /** 创建材料参数（仅admin） */
    @POST("v1/ai/materials")
    suspend fun createMaterial(@Body body: CreateMaterialRequest): ApiResponse<MaterialDto>

    /** 更新材料参数（仅admin） */
    @PUT("v1/ai/materials/{id}")
    suspend fun updateMaterial(
        @Path("id") id: Int,
        @Body body: UpdateMaterialRequest
    ): ApiResponse<MaterialDto>

    /** 删除材料参数（仅admin，软删除） */
    @DELETE("v1/ai/materials/{id}")
    suspend fun deleteMaterial(@Path("id") id: Int): ApiResponse<Unit>

    /** 获取对话历史 */
    @GET("v1/ai/history")
    suspend fun getChatHistory(@Query("sessionId") sessionId: String): ApiResponse<List<ChatHistoryItem>>

    /** 获取AI配置（仅admin） */
    @GET("v1/ai/config")
    suspend fun getAiConfig(): ApiResponse<AiConfigResponse>

    /** 更新AI配置（仅admin） */
    @PUT("v1/ai/config")
    suspend fun updateAiConfig(@Body body: AiConfigUpdateRequest): ApiResponse<AiConfigUpdateResponse>

    /** API连接测试（仅admin） */
    @POST("v1/ai/test")
    suspend fun testConnection(@Body body: AiTestRequest): ApiResponse<AiTestResponse>

    /** 获取知识分类列表（仅admin，注意在 /knowledge/{id} 之前注册） */
    @GET("v1/ai/knowledge/categories")
    suspend fun getKnowledgeCategories(): ApiResponse<KnowledgeCategoriesResponse>

    /** 获取知识库文档列表（仅admin，分页+分类筛选+关键词搜索） */
    @GET("v1/ai/knowledge")
    suspend fun listKnowledge(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("category") category: String? = null,
        @Query("keyword") keyword: String? = null
    ): ApiResponse<KnowledgeListResponse>

    /** 手动录入知识文档（仅admin） */
    @POST("v1/ai/knowledge")
    suspend fun createKnowledge(@Body body: CreateKnowledgeRequest): ApiResponse<CreateKnowledgeResponse>

    /** 文件导入知识文档（仅admin，txt/md/pdf/docx） */
    @Multipart
    @POST("v1/ai/knowledge/import-file")
    suspend fun importKnowledgeFile(
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody?,
        @Part("category") category: RequestBody?
    ): ApiResponse<CreateKnowledgeResponse>

    /** 媒体导入知识文档（仅admin，图片/视频/音频） */
    @Multipart
    @POST("v1/ai/knowledge/import-media")
    suspend fun importKnowledgeMedia(
        @Part file: MultipartBody.Part,
        @Part("title") title: RequestBody,
        @Part("category") category: RequestBody?,
        @Part("description") description: RequestBody?
    ): ApiResponse<ImportMediaResponse>

    /** 获取知识文档详情（仅admin） */
    @GET("v1/ai/knowledge/{id}")
    suspend fun getKnowledgeDetail(@Path("id") id: Int): ApiResponse<KnowledgeDetailResponse>

    /** 编辑知识文档（仅admin，内容变更自动重新分块） */
    @PUT("v1/ai/knowledge/{id}")
    suspend fun updateKnowledge(
        @Path("id") id: Int,
        @Body body: UpdateKnowledgeRequest
    ): ApiResponse<UpdateKnowledgeResponse>

    /** 删除知识文档（仅admin，级联删分块+删媒体文件） */
    @DELETE("v1/ai/knowledge/{id}")
    suspend fun deleteKnowledge(@Path("id") id: Int): ApiResponse<DeleteKnowledgeResponse>

    /** 图片重新识别（仅admin，视觉模型配置好后补识别） */
    @POST("v1/ai/knowledge/{id}/redescribe")
    suspend fun redescribeKnowledge(@Path("id") id: Int): ApiResponse<RedescribeResponse>
}

// ========== 请求体 ==========

@Serializable
data class AiChatRequest(
    val message: String,
    val sessionId: String
)

@Serializable
data class LayoutRequest(
    val roomLength: Double,
    val roomWidth: Double,
    val materialOptions: MaterialOptionsDto? = null
)

@Serializable
data class MaterialOptionsDto(
    val panelId: Int? = null,
    val mainKeelId: Int? = null,
    val subKeelId: Int? = null,
    val trimId: Int? = null
)

// ========== 响应体 ==========

@Serializable
data class AiChatResponse(
    val content: String,
    val intent: String,
    /** 引用溯源：本次回答引用的知识文档列表 */
    val citations: List<KnowledgeCitationDto> = emptyList()
)

/** 知识引用溯源项 */
@Serializable
data class KnowledgeCitationDto(
    val docId: Int = 0,
    val title: String = ""
)

@Serializable
data class LayoutResponse(
    val room: RoomInfoDto,
    val materials: LayoutMaterialsDto,
    val totalAmount: String,
    val layout: SvgLayoutDto,
    val calculationTime: String
)

@Serializable
data class RoomInfoDto(
    val length: Double,
    val width: Double,
    val area: String
)

@Serializable
data class LayoutMaterialsDto(
    val panel: PanelResultDto,
    val mainKeel: KeelResultDto,
    val subKeel: KeelResultDto,
    val trim: TrimResultDto,
    val accessories: List<AccessoryResultDto>
)

@Serializable
data class PanelResultDto(
    val name: String = "",
    val totalPanels: Int,
    val fullPanels: Int,
    val cutPanels: Int,
    val roomArea: String,
    val wasteRate: String,
    val amount: String
)

@Serializable
data class KeelResultDto(
    val name: String = "",
    val count: Int,
    val unitPrice: Double = 0.0,
    val amount: String,
    val spacing: String = ""
)

@Serializable
data class TrimResultDto(
    val name: String = "",
    val count: Int,
    val unitPrice: Double = 0.0,
    val amount: String,
    val perimeter: Double = 0.0,
    val perimeterDisplay: String = ""
)

@Serializable
data class AccessoryResultDto(
    val name: String = "",
    val count: Int,
    val unit: String = "",
    val unitPrice: Double = 0.0,
    val amount: String
)

@Serializable
data class SvgLayoutDto(
    val svgWidth: Int,
    val svgHeight: Int,
    val padding: Int,
    val roomRect: SvgRectDto,
    val panels: List<SvgPanelDto>,
    val dimensions: SvgDimensionsDto
)

@Serializable
data class SvgRectDto(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double
)

@Serializable
data class SvgPanelDto(
    val x: Double,
    val y: Double,
    val w: Double,
    val h: Double,
    val isFull: Boolean
)

@Serializable
data class SvgDimensionsDto(
    val roomLength: Double,
    val roomWidth: Double,
    val panelLength: Double,
    val panelWidth: Double
)

@Serializable
data class MaterialCategoryDto(
    val id: Int,
    val name: String,
    val description: String? = null,
    val sortOrder: Int = 0
)

@Serializable
data class MaterialDto(
    val id: Int,
    @SerialName("category_id") val categoryId: Int,
    val name: String,
    val brand: String? = null,
    val specification: String? = null,
    val unit: String = "张",
    @SerialName("unit_price") val unitPrice: Double = 0.0,
    @SerialName("width_cm") val widthCm: Double? = null,
    @SerialName("length_cm") val lengthCm: Double? = null,
    @SerialName("thickness_cm") val thicknessCm: Double? = null,
    @SerialName("coverage_area") val coverageArea: Double? = null,
    @SerialName("keel_spacing_cm") val keelSpacingCm: Double? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("category_name") val categoryName: String? = null
)

/**
 * 创建材料参数请求
 * 字段名使用 snake_case 与后端 Joi schema 一致
 */
@Serializable
data class CreateMaterialRequest(
    @SerialName("category_id") val categoryId: Int,
    val name: String,
    val brand: String? = null,
    val specification: String? = null,
    val unit: String = "张",
    @SerialName("unit_price") val unitPrice: Double,
    @SerialName("width_cm") val widthCm: Double? = null,
    @SerialName("length_cm") val lengthCm: Double? = null,
    @SerialName("thickness_cm") val thicknessCm: Double? = null,
    @SerialName("coverage_area") val coverageArea: Double? = null,
    @SerialName("keel_spacing_cm") val keelSpacingCm: Double? = null,
    val remark: String? = null
)

/**
 * 更新材料参数请求
 * 所有字段可选，仅传需要更新的字段
 */
@Serializable
data class UpdateMaterialRequest(
    val name: String? = null,
    val brand: String? = null,
    val specification: String? = null,
    val unit: String? = null,
    @SerialName("unit_price") val unitPrice: Double? = null,
    @SerialName("width_cm") val widthCm: Double? = null,
    @SerialName("length_cm") val lengthCm: Double? = null,
    @SerialName("thickness_cm") val thicknessCm: Double? = null,
    @SerialName("coverage_area") val coverageArea: Double? = null,
    @SerialName("keel_spacing_cm") val keelSpacingCm: Double? = null,
    val remark: String? = null
)

@Serializable
data class ChatHistoryItem(
    val role: String,
    val content: String,
    val intent: String? = null,
    val createdAt: String? = null
)

// ========== AI配置相关 ==========

/** AI配置响应 */
@Serializable
data class AiConfigResponse(
    @SerialName("defaultProvider") val defaultProvider: String = "",
    @SerialName("providers") val providers: Map<String, AiProviderConfigDto> = emptyMap(),
    /** 向量模型（知识库embedding用，空表示按提供商默认映射） */
    @SerialName("embeddingModel") val embeddingModel: String = "",
    /** 视觉模型（知识库图片识别用，空表示按提供商默认映射） */
    @SerialName("visionModel") val visionModel: String = "",
    /** 各提供商默认向量/视觉模型（配置页展示占位提示用） */
    @SerialName("modelDefaults") val modelDefaults: Map<String, AiModelDefaultsDto> = emptyMap()
)

/** 提供商默认向量/视觉模型映射 */
@Serializable
data class AiModelDefaultsDto(
    @SerialName("embeddingModel") val embeddingModel: String? = null,
    @SerialName("visionModel") val visionModel: String? = null
)

/** 单个提供商配置 */
@Serializable
data class AiProviderConfigDto(
    @SerialName("name") val name: String = "",
    @SerialName("apiKey") val apiKey: String = "",
    @SerialName("secretKey") val secretKey: String = "",
    @SerialName("model") val model: String = "",
    @SerialName("maxTokens") val maxTokens: Int = 4096,
    @SerialName("temperature") val temperature: Double = 0.7,
    @SerialName("baseUrl") val baseUrl: String = "",
    @SerialName("defaultBaseUrl") val defaultBaseUrl: String = "",
    @SerialName("hasApiKey") val hasApiKey: Boolean = false,
    @SerialName("hasSecretKey") val hasSecretKey: Boolean = false
)

/** AI配置更新请求（embeddingModel/visionModel 仅在用户修改时传入，null不序列化） */
@Serializable
data class AiConfigUpdateRequest(
    @SerialName("defaultProvider") val defaultProvider: String? = null,
    @SerialName("providerConfigs") val providerConfigs: Map<String, AiProviderConfigUpdate>? = null,
    /** 向量模型（空字符串表示清除自定义、回退提供商默认映射） */
    @SerialName("embeddingModel") val embeddingModel: String? = null,
    /** 视觉模型（空字符串表示清除自定义、回退提供商默认映射） */
    @SerialName("visionModel") val visionModel: String? = null
)

/** 单个提供商配置更新 */
@Serializable
data class AiProviderConfigUpdate(
    @SerialName("apiKey") val apiKey: String? = null,
    @SerialName("secretKey") val secretKey: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("baseUrl") val baseUrl: String? = null
)

/** AI配置更新响应 */
@Serializable
data class AiConfigUpdateResponse(
    @SerialName("message") val message: String = ""
)

/** API连接测试请求 */
@Serializable
data class AiTestRequest(
    @SerialName("provider") val provider: String
)

/** API连接测试响应 */
@Serializable
data class AiTestResponse(
    @SerialName("provider") val provider: String = "",
    @SerialName("providerName") val providerName: String = "",
    @SerialName("model") val model: String = "",
    @SerialName("response") val response: String = "",
    @SerialName("message") val message: String = ""
)

// ========== 知识库相关 ==========

/** 知识库文档列表项（后端返回camelCase） */
@Serializable
data class KnowledgeItemDto(
    /** 文档ID */
    val id: Int = 0,
    /** 标题 */
    val title: String = "",
    /** 知识分类 */
    val category: String = "未分类",
    /** 录入方式: manual/file/media */
    val sourceType: String = "manual",
    /** 文档类型: text/markdown/pdf/docx/image/video/audio */
    val docType: String = "text",
    /** 原始文件名 */
    val fileName: String? = null,
    /** 文件大小（字节） */
    val fileSize: Long = 0,
    /** 媒体文件URL（相对路径，文本类为null） */
    val mediaUrl: String? = null,
    /** 检索文本字符数 */
    val charCount: Int = 0,
    /** 分块数量 */
    val chunkCount: Int = 0,
    /** 向量化状态: none/partial/full */
    val embeddingStatus: String = "none",
    /** 图片识别状态: ok/failed/unsupported（仅图片类型） */
    val visionStatus: String? = null,
    /** 创建时间（yyyy-MM-dd HH:mm） */
    val createdAt: String? = null,
    /** 更新时间（yyyy-MM-dd HH:mm） */
    val updatedAt: String? = null
)

/** 知识库列表响应 */
@Serializable
data class KnowledgeListResponse(
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 20,
    val items: List<KnowledgeItemDto> = emptyList()
)

/** 知识分类列表响应 */
@Serializable
data class KnowledgeCategoriesResponse(
    val categories: List<KnowledgeCategoryDto> = emptyList()
)

/** 知识分类项（含文档数） */
@Serializable
data class KnowledgeCategoryDto(
    val name: String = "",
    val count: Int = 0
)

/** 创建/导入知识文档请求（手动录入） */
@Serializable
data class CreateKnowledgeRequest(
    val title: String,
    val content: String,
    val category: String? = null
)

/** 创建/导入知识文档响应 */
@Serializable
data class CreateKnowledgeResponse(
    val message: String = "",
    val id: Int = 0,
    val title: String? = null,
    val chunkCount: Int = 0,
    val charCount: Int = 0,
    val truncated: Boolean = false,
    val embeddingStatus: String = "none"
)

/** 媒体导入响应 */
@Serializable
data class ImportMediaResponse(
    val message: String = "",
    val id: Int = 0,
    /** 图片识别状态: ok/failed/unsupported */
    val visionStatus: String? = null,
    val chunkCount: Int = 0,
    val embeddingStatus: String = "none"
)

/** 知识文档详情响应 */
@Serializable
data class KnowledgeDetailResponse(
    val id: Int = 0,
    val title: String = "",
    val category: String = "未分类",
    val sourceType: String = "manual",
    val docType: String = "text",
    /** 媒体文件URL（相对路径） */
    val mediaUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val mimeType: String? = null,
    /** 检索文本全文 */
    val content: String = "",
    /** 媒体文件手动描述 */
    val description: String? = null,
    val charCount: Int = 0,
    val chunkCount: Int = 0,
    val embeddingStatus: String = "none",
    val visionStatus: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/** 编辑知识文档请求（所有字段可选，仅传需要更新的字段；null不序列化） */
@Serializable
data class UpdateKnowledgeRequest(
    val title: String? = null,
    val content: String? = null,
    val category: String? = null,
    val description: String? = null
)

/** 编辑知识文档响应 */
@Serializable
data class UpdateKnowledgeResponse(
    val message: String = "",
    val id: Int = 0,
    val chunkCount: Int = 0,
    val embeddingStatus: String = "none"
)

/** 删除知识文档响应 */
@Serializable
data class DeleteKnowledgeResponse(
    val message: String = "",
    val id: Int = 0
)

/** 图片重新识别响应 */
@Serializable
data class RedescribeResponse(
    val message: String = "",
    val id: Int = 0,
    /** 识别状态: ok/failed/unsupported */
    val visionStatus: String? = null,
    /** 识别成功时的图片描述 */
    val description: String? = null,
    val chunkCount: Int = 0
)
