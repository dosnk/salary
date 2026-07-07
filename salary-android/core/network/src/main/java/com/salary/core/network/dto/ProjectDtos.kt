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
    /** 子项目列表（列表接口已聚合返回，避免N+1详情请求；字段名与详情接口一致） */
    @SerialName("sub_projects")
    val subprojects: List<SubprojectDto> = emptyList(),
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
 * 注意：workdays后端为NUMERIC(6,2)，pg默认按字符串返回(如"1.00")，用Double?接收
 */
@Serializable
data class WorkerDto(
    val id: Int,
    val username: String? = null,
    val nickname: String,
    val workdays: Double? = null
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
    /** 原始文件名（后端返回 camelCase 别名 originalName，无需 @SerialName） */
    val originalName: String? = null,
    @SerialName("path")
    val fileUrl: String,
    @SerialName("size")
    val fileSize: Long,
    /** 文件 MIME 类型，如 image/jpeg、application/pdf */
    val type: String? = null,
    @SerialName("created_at")
    val uploadedAt: String
)

/**
 * 上传文件记录请求（第二步：关联工程）
 * 后端 POST /v1/projects/:id/files 期望JSON请求体
 * 字段：filename, originalName, path, size, type
 */
@Serializable
data class ProjectFileRequest(
    /** 文件名（建议用原始名） */
    val filename: String,
    /** 原始文件名 */
    val originalName: String,
    /** 文件访问路径（第一步上传返回的 url） */
    val path: String,
    /** 文件大小（字节） */
    val size: Long,
    /** 文件MIME类型 */
    val type: String
)

/**
 * 上传文件记录响应项
 * 后端返回数组，每项含 filename 和 url
 */
@Serializable
data class FileRecordDto(
    val filename: String? = null,
    val url: String? = null,
    val size: Long = 0,
    val type: String? = null
)

/**
 * 创建工程请求
 * 后端constructors期望对象数组: [{userId: 1}, {userId: 2}]
 * 按工日分配模式下workerWorkDays传递每人工日数: [{userId: 1, workdays: 2.0}]
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
    val remark: String? = null,
    /** 按工日分配模式下的工日列表（可选） */
    @SerialName("workerWorkDays")
    val workerWorkDays: List<WorkerWorkdayItem>? = null
)

/**
 * 施工人员项（后端期望 {userId: number} 格式）
 */
@Serializable
data class ConstructorItem(
    val userId: Int
)

/**
 * 施工人员工日项（按工日分配模式使用）
 * 后端期望 {userId: number, workdays: number} 格式
 */
@Serializable
data class WorkerWorkdayItem(
    val userId: Int,
    val workdays: Double
)

/**
 * 更新工程请求
 * 后端constructors期望对象数组: [{userId: 1}, {userId: 2}]
 * 后端workerWorkDays期望对象数组: [{userId: 1, workdays: 2.0}]
 * 所有字段可选，按需更新（Joi校验至少1个字段）
 */
@Serializable
data class UpdateProjectRequest(
    val name: String? = null,
    val status: String? = null,
    val remark: String? = null,
    val description: String? = null,
    @SerialName("salaryDistribution")
    val salaryDistribution: String? = null,
    val constructors: List<ConstructorItem>? = null,
    /** 按工日分配模式下的工日列表（可选） */
    @SerialName("workerWorkDays")
    val workerWorkDays: List<WorkerWorkdayItem>? = null
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
