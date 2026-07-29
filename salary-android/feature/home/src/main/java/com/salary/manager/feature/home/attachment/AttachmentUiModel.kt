package com.salary.manager.feature.home.attachment

/**
 * 附件统一 UI 模型
 *
 * 承担主页附件弹窗与工程详情附件弹窗的展示需求。
 * 调用方负责把后端 DTO（FileDto）或页面模型（FileUiModel）映射为本模型：
 *   - fileUrl 必须是"完整 URL"或空字符串；本组件内部会做 URL 编码兜底
 *   - 空字符串表示 URL 未加载完成，缩略图会显示占位
 *
 * @property id 文件唯一标识（用作 LazyColumn key 的一部分）
 * @property fileName 用于展示的文件名（优先原始名，退化为存储名）
 * @property fileUrl 完整访问 URL；空字符串表示未加载
 * @property fileSize 文件字节数
 * @property uploadedAt 上传时间字符串（后端返回原样，DateFormatter 负责本地化）
 * @property type 文件 MIME 类型，如 image/jpeg、video/mp4、application/pdf
 */
data class AttachmentUiModel(
    val id: Int,
    val fileName: String,
    val fileUrl: String,
    val fileSize: Long,
    val uploadedAt: String,
    val type: String? = null
)
