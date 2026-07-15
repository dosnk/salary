package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 系统管理API接口
 * 仅 admin 角色可访问（后端已通过 requireAdmin() 限制）
 */
interface SystemApi {

    /**
     * 数据一致性校验
     * 调用后端 verify-data-consistency 脚本的可复用函数，对金额、统计、结算三个口径进行一致性校验。
     *
     * @param userId    可选，指定用户ID进行校验（null=全部用户）
     * @param tolerance 可选，金额容差（元），默认 0.01
     */
    @GET("v1/system/data-consistency/verify")
    suspend fun verifyDataConsistency(
        @Query("userId") userId: Int? = null,
        @Query("tolerance") tolerance: Double? = null
    ): ApiResponse<DataVerifyResultDto>
}

/**
 * 数据一致性校验结果 DTO
 * 对应后端 verifyDataConsistency 函数的返回结构
 */
@Serializable
data class DataVerifyResultDto(
    /** 通过项数 */
    val passed: Int = 0,
    /** 失败项数 */
    val failed: Int = 0,
    /** 警告项数 */
    val warnings: Int = 0,
    /** 校验总耗时（秒） */
    val elapsed: Double = 0.0,
    /** 校验明细列表 */
    val details: List<DataVerifyDetailDto> = emptyList()
)

/**
 * 单项校验结果明细 DTO
 */
@Serializable
data class DataVerifyDetailDto(
    /** 校验项名称 */
    val name: String = "",
    /** 是否通过 */
    val passed: Boolean = false,
    /** 详细描述（失败时包含差异信息） */
    val detail: String = ""
)
