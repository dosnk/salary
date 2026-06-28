package com.salary.core.network.dto

import kotlinx.serialization.Serializable

/**
 * 认证相关数据传输对象
 * 后端登录接口返回camelCase字段名（accessToken/refreshToken），无需@SerialName映射
 */

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class LoginResponse(
    val user: UserInfo,
    val accessToken: String,
    val refreshToken: String
)

@Serializable
data class UserInfo(
    val id: Int,
    val username: String,
    val nickname: String,
    val role: String,
    val phone: String? = null
)
