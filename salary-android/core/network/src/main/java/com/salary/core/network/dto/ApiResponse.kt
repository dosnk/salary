package com.salary.core.network.dto

import kotlinx.serialization.Serializable

/**
 * 后端统一响应格式
 */
@Serializable
data class ApiResponse<T>(
    val code: Int,
    val data: T? = null,
    val msg: String = ""
)
