package com.salary.core.network.dto

import kotlinx.serialization.Serializable

/**
 * 分页响应格式
 */
@Serializable
data class PageResponse<T>(
    val list: List<T>,
    val total: Int,
    val page: Int,
    val size: Int,
    val hasNext: Boolean
)
