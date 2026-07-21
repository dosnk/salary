package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import com.salary.core.network.dto.FileRecordDto
import com.salary.core.network.dto.PageResponse
import com.salary.core.network.dto.ProjectDto
import com.salary.core.network.dto.ProjectDetailDto
import com.salary.core.network.dto.ProjectFileRequest
import com.salary.core.network.dto.ProjectHistoryDto
import com.salary.core.network.dto.CreateProjectRequest
import com.salary.core.network.dto.CreateProjectResponse
import com.salary.core.network.dto.UpdateProjectRequest
import kotlinx.serialization.Serializable
import retrofit2.http.*

/**
 * 工程管理API接口
 */
interface ProjectApi {
    @GET("v1/projects")
    suspend fun getProjects(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("keyword") keyword: String? = null,
        @Query("status") status: String? = null,
        @Query("settlementStatus") settlementStatus: String? = null,
        /** 年月筛选，格式：2026-06，对应后端yearMonth参数 */
        @Query("yearMonth") yearMonth: String? = null,
        /** 开始日期筛选，格式：yyyy-MM-dd，对应后端startDate参数 */
        @Query("startDate") startDate: String? = null,
        /** 结束日期筛选，格式：yyyy-MM-dd，对应后端endDate参数 */
        @Query("endDate") endDate: String? = null
    ): ApiResponse<PageResponse<ProjectDto>>

    @GET("v1/projects/{id}")
    suspend fun getProjectDetail(@Path("id") id: Int): ApiResponse<ProjectDetailDto>

    @POST("v1/projects")
    suspend fun createProject(@Body body: CreateProjectRequest): ApiResponse<CreateProjectResponse>

    @PUT("v1/projects/{id}")
    suspend fun updateProject(@Path("id") id: Int, @Body body: UpdateProjectRequest): ApiResponse<Unit>

    @DELETE("v1/projects/{id}")
    suspend fun deleteProject(@Path("id") id: Int): ApiResponse<Unit>

    /** 获取工程修改历史 */
    @GET("v1/projects/{id}/history")
    suspend fun getProjectHistory(@Path("id") id: Int): ApiResponse<List<ProjectHistoryDto>>

    /**
     * 上传文件记录到工程（第二步：把已上传的文件信息写入数据库，关联projectId）
     * 对应后端 POST /v1/projects/:id/files，使用JSON请求体
     * @param id 工程ID
     * @param body 文件信息（filename, originalName, path, size, type）
     */
    @POST("v1/projects/{id}/files")
    suspend fun uploadFile(@Path("id") id: Int, @Body body: ProjectFileRequest): ApiResponse<List<FileRecordDto>>

    /**
     * 删除工程附件
     * 对应后端 DELETE /v1/projects/:id/files/:fileId
     * 后端会同时删除数据库记录和物理文件（物理文件删除失败不影响接口结果）
     * @param id 工程ID
     * @param fileId 文件ID
     */
    @DELETE("v1/projects/{id}/files/{fileId}")
    suspend fun deleteFile(@Path("id") id: Int, @Path("fileId") fileId: Int): ApiResponse<Unit>

    /**
     * 更新子项目
     * 对应后端 PUT /v1/projects/:id/subprojects/:subprojectId
     * 后端会根据 length/width 重新计算金额
     * @param id 工程ID
     * @param subprojectId 子项目ID
     * @param body 更新内容（length/width 单位为厘米，后端存储厘米）
     */
    @PUT("v1/projects/{id}/subprojects/{subprojectId}")
    suspend fun updateSubproject(
        @Path("id") id: Int,
        @Path("subprojectId") subprojectId: Int,
        @Body body: UpdateSubprojectRequest
    ): ApiResponse<Unit>
}

/**
 * 更新子项目请求
 * 后端期望字段：spaceType, constructionScheme, length, width, remark
 * 注意：length/width 单位为厘米（后端统一存储厘米）
 */
@Serializable
data class UpdateSubprojectRequest(
    val spaceType: String? = null,
    val constructionScheme: String? = null,
    val length: Double? = null,
    val width: Double? = null,
    val remark: String? = null
)
