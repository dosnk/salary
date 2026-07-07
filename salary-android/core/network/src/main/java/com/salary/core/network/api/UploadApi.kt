package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * 文件上传API接口
 * 对应后端 POST /v1/upload，使用multipart/form-data
 */
interface UploadApi {

    /**
     * 上传文件
     * @param file 文件part，字段名必须为"file"（后端要求）
     * @param projectName 工程名称（表单字段，后端用于生成存储路径 upload/YYYYMM/工程名/）
     */
    @Multipart
    @POST("v1/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part,
        @Part("projectName") projectName: RequestBody
    ): ApiResponse<UploadResultDto>
}

/**
 * 上传成功响应DTO
 * 后端单文件上传返回字段：url, fileName, fileSize, fileType, fileHash, uploadedAt
 * 注意：后端返回camelCase字段名，需用@SerialName显式映射
 */
@Serializable
data class UploadResultDto(
    /** 文件访问URL（相对路径，如 /upload/202512/工程名/xxx.jpg） */
    val url: String? = null,
    /** 文件名（后端字段 fileName） */
    @SerialName("fileName")
    val fileName: String? = null,
    /** 文件大小（字节，后端字段 fileSize） */
    @SerialName("fileSize")
    val fileSize: Long = 0,
    /** 文件MIME类型（后端字段 fileType） */
    @SerialName("fileType")
    val fileType: String? = null,
    /** 文件哈希（后端字段 fileHash） */
    @SerialName("fileHash")
    val fileHash: String? = null,
    /** 上传时间（后端字段 uploadedAt） */
    @SerialName("uploadedAt")
    val uploadedAt: String? = null
)
