package com.salary.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 工程列表项DTO
 * 后端返回snake_case字段名，使用@SerialName映射
 * 注意：total_amount后端返回数字类型，前端用Double接收再格式化
 */
@Serializable
data class ProjectDto(
    val id: Int,
    val name: String,
    val status: String,
    @SerialName("total_amount")
    val totalAmount: Double,
    @SerialName("settlement_status")
    val settlementStatus: String? = null,
    @SerialName("salary_distribution")
    val salaryDistribution: String? = null,
    val workers: List<WorkerDto> = emptyList(),
    val description: String? = null,
    @SerialName("created_by")
    val createdBy: Int? = null,
    @SerialName("files_count")
    val filesCount: Int = 0,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
)

/**
 * 工程详情DTO
 * 后端返回snake_case字段名，使用@SerialName映射
 * 注意：total_amount后端返回数字类型，前端用Double接收再格式化
 */
@Serializable
data class ProjectDetailDto(
    val id: Int,
    val name: String,
    val status: String,
    @SerialName("total_amount")
    val totalAmount: Double,
    val remark: String? = null,
    val description: String? = null,
    @SerialName("settlement_status")
    val settlementStatus: String? = null,
    @SerialName("salary_distribution")
    val salaryDistribution: String? = null,
    val workers: List<WorkerDto> = emptyList(),
    /** 后端返回 sub_projects，映射为 subprojects */
    @SerialName("sub_projects")
    val subprojects: List<SubprojectDto> = emptyList(),
    val files: List<FileDto> = emptyList(),
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String
)

/**
 * 子项目DTO
 * 后端返回snake_case字段名，使用@SerialName映射
 * 注意：amount后端返回数字类型，前端用Double接收再格式化
 */
@Serializable
data class SubprojectDto(
    val id: Int,
    @SerialName("space_type_name")
    val spaceTypeName: String,
    @SerialName("construction_plan_name")
    val constructionPlanName: String,
    val length: Double? = null,
    val width: Double? = null,
    val quantity: Double? = null,
    val amount: Double? = null,
    @SerialName("salary_distribution")
    val salaryDistribution: String? = null,
    val status: String? = null
)

/**
 * 施工人员DTO
 * 后端返回 id, username, nickname, workdays
 */
@Serializable
data class WorkerDto(
    val id: Int,
    val username: String? = null,
    val nickname: String,
    val workdays: Int? = null
)

/**
 * 附件DTO
 * 后端返回snake_case字段名，使用@SerialName映射
 */
@Serializable
data class FileDto(
    val id: Int,
    @SerialName("filename")
    val fileName: String,
    @SerialName("path")
    val fileUrl: String,
    @SerialName("size")
    val fileSize: Long,
    @SerialName("created_at")
    val uploadedAt: String
)

/**
 * 创建工程请求
 * 后端constructors期望对象数组: [{userId: 1}, {userId: 2}]
 */
@Serializable
data class CreateProjectRequest(
    val name: String,
    @SerialName("spaceType")
    val spaceType: String,
    @SerialName("constructionScheme")
    val constructionScheme: String,
    val length: Double,
    val width: Double,
    @SerialName("salaryDistribution")
    val salaryDistribution: String? = null,
    val constructors: List<ConstructorItem> = emptyList(),
    val remark: String? = null
)

/**
 * 施工人员项（后端期望 {userId: number} 格式）
 */
@Serializable
data class ConstructorItem(
    val userId: Int
)

/**
 * 更新工程请求
 * 后端constructors期望对象数组: [{userId: 1}, {userId: 2}]
 */
@Serializable
data class UpdateProjectRequest(
    val name: String? = null,
    val status: String? = null,
    val remark: String? = null,
    val constructors: List<ConstructorItem>? = null
)

/**
 * 创建工程响应
 * 后端返回 { projectId: number }，与ProjectDetailDto结构不同
 */
@Serializable
data class CreateProjectResponse(
    @SerialName("projectId")
    val projectId: Int
)

/** 工程修改历史DTO */
@Serializable
data class ProjectHistoryDto(
    val id: Int,
    @SerialName("project_id")
    val projectId: Int,
    val action: String,
    @SerialName("action_name")
    val actionName: String,
    val description: String,
    @SerialName("performed_by")
    val performedBy: Int,
    val username: String,
    val nickname: String,
    @SerialName("created_at")
    val createdAt: String
)
