package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
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

    /** 获取知识库文档列表（仅admin） */
    @GET("v1/ai/knowledge")
    suspend fun listKnowledge(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20
    ): ApiResponse<KnowledgeListResponse>

    /** 添加知识文档（仅admin） */
    @POST("v1/ai/knowledge")
    suspend fun createKnowledge(@Body body: CreateKnowledgeRequest): ApiResponse<CreateKnowledgeResponse>

    /** 获取知识文档详情（仅admin） */
    @GET("v1/ai/knowledge/{title}")
    suspend fun getKnowledgeDetail(@Path("title") title: String): ApiResponse<KnowledgeDetailResponse>

    /** 删除知识文档（仅admin） */
    @DELETE("v1/ai/knowledge/{title}")
    suspend fun deleteKnowledge(@Path("title") title: String): ApiResponse<DeleteKnowledgeResponse>
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
    val intent: String
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
    @SerialName("providers") val providers: Map<String, AiProviderConfigDto> = emptyMap()
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
    @SerialName("hasApiKey") val hasApiKey: Boolean = false,
    @SerialName("hasSecretKey") val hasSecretKey: Boolean = false
)

/** AI配置更新请求 */
@Serializable
data class AiConfigUpdateRequest(
    @SerialName("defaultProvider") val defaultProvider: String? = null,
    @SerialName("providerConfigs") val providerConfigs: Map<String, AiProviderConfigUpdate>? = null
)

/** 单个提供商配置更新 */
@Serializable
data class AiProviderConfigUpdate(
    @SerialName("apiKey") val apiKey: String? = null,
    @SerialName("secretKey") val secretKey: String? = null,
    @SerialName("model") val model: String? = null
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

/** 知识库文档列表项 */
@Serializable
data class KnowledgeItemDto(
    /** 文档标题（作为唯一标识） */
    @SerialName("title") val title: String = "",
    /** 来源类型: manual/upload/api */
    @SerialName("source_type") val sourceType: String = "manual",
    /** 来源ID */
    @SerialName("source_id") val sourceId: Int? = null,
    /** 分块数量 */
    @SerialName("chunk_count") val chunkCount: String = "0",
    /** 总字符数 */
    @SerialName("total_chars") val totalChars: String = "0",
    /** 创建时间 */
    @SerialName("created_at") val createdAt: String? = null,
    /** 更新时间 */
    @SerialName("updated_at") val updatedAt: String? = null
)

/** 知识库列表响应 */
@Serializable
data class KnowledgeListResponse(
    @SerialName("total") val total: Int = 0,
    @SerialName("page") val page: Int = 1,
    @SerialName("pageSize") val pageSize: Int = 20,
    @SerialName("items") val items: List<KnowledgeItemDto> = emptyList()
)

/** 创建知识文档请求 */
@Serializable
data class CreateKnowledgeRequest(
    @SerialName("title") val title: String,
    @SerialName("content") val content: String,
    @SerialName("sourceType") val sourceType: String = "manual"
)

/** 创建知识文档响应 */
@Serializable
data class CreateKnowledgeResponse(
    @SerialName("message") val message: String = "",
    @SerialName("chunks") val chunks: Int = 0,
    @SerialName("items") val items: List<KnowledgeChunkResultDto> = emptyList()
)

/** 知识分块结果 */
@Serializable
data class KnowledgeChunkResultDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("title") val title: String = "",
    @SerialName("chunkIndex") val chunkIndex: Int = 0,
    @SerialName("charCount") val charCount: Int = 0
)

/** 知识文档详情响应 */
@Serializable
data class KnowledgeDetailResponse(
    @SerialName("title") val title: String = "",
    @SerialName("sourceType") val sourceType: String = "manual",
    @SerialName("sourceId") val sourceId: Int? = null,
    @SerialName("chunkCount") val chunkCount: Int = 0,
    @SerialName("chunks") val chunks: List<KnowledgeChunkDto> = emptyList()
)

/** 知识分块详情 */
@Serializable
data class KnowledgeChunkDto(
    @SerialName("id") val id: Int = 0,
    @SerialName("chunkIndex") val chunkIndex: Int = 0,
    @SerialName("content") val content: String = "",
    @SerialName("charCount") val charCount: Int = 0
)

/** 删除知识文档响应 */
@Serializable
data class DeleteKnowledgeResponse(
    @SerialName("message") val message: String = "",
    @SerialName("deletedChunks") val deletedChunks: Int = 0
)
