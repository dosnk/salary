package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 用户管理API接口
 */
interface UserApi {

    /** 获取用户列表（admin） */
    @GET("v1/users")
    suspend fun getUsers(@Query("page") page: Int = 1, @Query("size") size: Int = 50): ApiResponse<UserListResponse>

    /** 获取施工员列表 */
    @GET("v1/users/constructors")
    suspend fun getConstructors(): ApiResponse<List<UserDto>>

    /** 获取当前用户信息 */
    @GET("v1/users/profile")
    suspend fun getProfile(): ApiResponse<UserDto>

    /** 创建用户（admin） */
    @POST("v1/users")
    suspend fun createUser(@Body body: CreateUserRequest): ApiResponse<UserDto>

    /** 修改密码 */
    @POST("v1/users/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): ApiResponse<Unit>

    /** 更新用户信息（admin） */
    @PUT("v1/users/{id}")
    suspend fun updateUser(@Path("id") id: Int, @Body body: UpdateUserRequest): ApiResponse<Unit>

    /** 删除用户（admin） */
    @DELETE("v1/users/{id}")
    suspend fun deleteUser(@Path("id") id: Int): ApiResponse<Unit>

    /** 重置用户密码（admin） */
    @POST("v1/users/{id}/reset-password")
    suspend fun resetPassword(@Path("id") id: Int, @Body body: ResetPasswordRequest): ApiResponse<Unit>
}

@Serializable
data class UserListResponse(
    val list: List<UserDto>,
    val total: Int,
    val page: Int,
    val size: Int
)

@Serializable
data class UserDto(
    val id: Int,
    val username: String,
    val nickname: String,
    val phone: String? = null,
    /** 角色：admin/constructor/documenter，施工人员列表API可能不返回此字段 */
    val role: String = "constructor",
    @SerialName("created_at")
    val createdAt: String? = null
)

@Serializable
data class CreateUserRequest(
    val username: String,
    val password: String,
    val nickname: String,
    val phone: String? = null,
    val role: String = "constructor"
)

@Serializable
data class ChangePasswordRequest(
    val old_password: String,
    val new_password: String
)

@Serializable
data class UpdateUserRequest(
    val nickname: String? = null,
    val phone: String? = null
)

@Serializable
data class ResetPasswordRequest(
    val new_password: String
)
