package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import com.salary.core.network.dto.LoginResponse
import com.salary.core.network.dto.LoginRequest
import com.salary.core.network.dto.RegisterRequest
import com.salary.core.network.dto.RefreshTokenRequest
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 认证API接口
 */
interface AuthApi {
    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<LoginResponse>

    @POST("v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<Unit>

    @POST("v1/auth/refresh")
    suspend fun refreshToken(@Body body: RefreshTokenRequest): ApiResponse<LoginResponse>
}
