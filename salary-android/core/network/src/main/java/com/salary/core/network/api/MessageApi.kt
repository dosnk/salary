package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 消息通知API接口
 */
interface MessageApi {

    /** 获取消息列表 */
    @GET("v1/messages")
    suspend fun getMessages(
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 20,
        @Query("type") type: String? = null
    ): ApiResponse<MessageListResponse>

    /** 获取未读消息数量（后端路径：/v1/messages/unread/count） */
    @GET("v1/messages/unread/count")
    suspend fun getUnreadCount(): ApiResponse<UnreadCountResponse>

    /** 标记消息已读 */
    @PUT("v1/messages/{id}/read")
    suspend fun markAsRead(@Path("id") id: Int): ApiResponse<Unit>

    /** 全部标记已读 */
    @PUT("v1/messages/read-all")
    suspend fun markAllAsRead(): ApiResponse<Unit>

    /** 删除消息 */
    @DELETE("v1/messages/{id}")
    suspend fun deleteMessage(@Path("id") id: Int): ApiResponse<Unit>
}

@Serializable
data class MessageListResponse(
    val list: List<MessageDto>,
    val total: Int,
    val page: Int,
    val pageSize: Int
)

/**
 * 消息DTO
 * 后端返回snake_case字段名，使用@SerialName映射
 */
@Serializable
data class MessageDto(
    val id: Int,
    val title: String,
    val content: String,
    val type: String,
    @SerialName("is_read")
    val isRead: Boolean = false,
    @SerialName("related_id")
    val relatedId: Int? = null,
    @SerialName("related_type")
    val relatedType: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class UnreadCountResponse(
    val count: Int
)
