package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import com.salary.core.network.dto.PageResponse
import com.salary.core.network.dto.ProjectDto
import com.salary.core.network.dto.ProjectDetailDto
import com.salary.core.network.dto.ProjectHistoryDto
import com.salary.core.network.dto.CreateProjectRequest
import com.salary.core.network.dto.CreateProjectResponse
import com.salary.core.network.dto.UpdateProjectRequest
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
        @Query("yearMonth") yearMonth: String? = null
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
}
