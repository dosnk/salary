package com.salary.core.network.api

import com.salary.core.network.dto.ApiResponse
import kotlinx.serialization.Serializable
import retrofit2.http.POST

/**
 * 数据库备份结果
 *
 * 对应后端 POST /v1/backup/database 返回值（camelCase 字段名）
 */
@Serializable
data class BackupResultDto(
    /** 备份结果提示信息 */
    val message: String = "",
    /** 生成的备份文件名（backup-full-YYYYMMDD-HHmmss.json） */
    val fileName: String? = null,
    /** 备份的表数量 */
    val tableCount: Int = 0,
    /** 备份的总记录数 */
    val totalRows: Int = 0,
    /** 当前保留的备份份数 */
    val keptBackups: Int = 0
)

/**
 * 备份管理API接口
 *
 * 用于前端启动时自动触发后端数据库全量备份
 * 走统一 OkHttpClient（自动携带 Authorization，401 自动刷新）
 */
interface BackupApi {

    /** 触发数据库全量备份（需登录，任意角色） */
    @POST("v1/backup/database")
    suspend fun backupDatabase(): ApiResponse<BackupResultDto>
}