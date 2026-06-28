package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
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

    /** 获取对话历史 */
    @GET("v1/ai/history")
    suspend fun getChatHistory(@Query("sessionId") sessionId: String): ApiResponse<List<ChatHistoryItem>>

    /** 获取AI配置（仅admin） */
    @GET("v1/ai/config")
    suspend fun getAiConfig(): ApiResponse<AiConfigResponse>

    /** 更新AI配置（仅admin） */
    @PUT("v1/ai/config")
    suspend fun updateAiConfig(@Body body: AiConfigUpdateRequest): ApiResponse<AiConfigUpdateResponse>
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
    val categoryId: Int,
    val name: String,
    val brand: String? = null,
    val specification: String? = null,
    val unit: String = "张",
    val unitPrice: Double = 0.0,
    val widthCm: Double? = null,
    val lengthCm: Double? = null,
    val thicknessCm: Double? = null,
    val coverageArea: Double? = null,
    val keelSpacingCm: Double? = null,
    val isActive: Boolean = true,
    val categoryName: String? = null
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
