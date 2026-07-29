package com.salary.manager.feature.home.attachment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.salary.core.design.theme.AppColors

/**
 * 附件管理弹窗（统一入口）
 *
 * 合并原来的两个组件：
 * - AttachmentGridDialog（主页附件浏览）
 * - AttachmentViewDialog（工程详情附件管理）
 *
 * 差异化能力通过参数控制：
 * - showTitleProjectName：是否在标题栏显示工程名（主页附件浏览为 true，工程详情为 false）
 * - canDelete：是否允许删除（工程详情且施工员可编辑时为 true，其他为 false）
 * - onDelete：删除回调（null 表示不显示删除按钮；当 canDelete=false 时也应传 null）
 *
 * 关键健壮性设计（对齐前几轮修复经验）：
 * - 外层 DisableSelection：Dialog 独立 Window，禁用文本选择避免与 MainActivity
 *   全局 SelectionContainer 手势竞争引发的边缘点击闪退
 * - Dialog 属性 dismissOnClickOutside=false：防止边缘误触自动关闭
 * - 每个 AttachmentItem 内部使用 combinedClickable(onLongClick={}) 屏蔽长按
 * - items key 加分类前缀避免跨列表冲突
 * - 所有点击回调均 try-catch 兜底，防止调用方抛异常杀进程
 *
 * @param title 标题（如"附件浏览"或"附件管理"）
 * @param projectName 工程名；空则不显示
 * @param files 附件列表（fileUrl 应为已拼接的完整 URL；本组件内部会调用 encodeAttachmentUrl 兜底）
 * @param isLoading 是否加载中
 * @param canDelete 是否允许删除附件
 * @param onDismiss 关闭弹窗回调
 * @param onMediaClick 媒体文件点击回调；参数为已 URL 编码的安全 URL、文件名、MIME 类型
 * @param onFileClick 非媒体文件点击回调；参数为附件模型（调用方自行处理 URL 编码或 Intent 打开）
 * @param onDelete 删除回调（可选）
 */
@Composable
fun AttachmentDialog(
    title: String = "附件管理",
    projectName: String = "",
    files: List<AttachmentUiModel>,
    isLoading: Boolean = false,
    canDelete: Boolean = false,
    onDismiss: () -> Unit,
    onMediaClick: (fullUrl: String, fileName: String, fileType: String?) -> Unit,
    onFileClick: (AttachmentUiModel) -> Unit,
    onDelete: ((AttachmentUiModel) -> Unit)? = null
) {
    // 删除二次确认
    var deletingFile by remember { mutableStateOf<AttachmentUiModel?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.98f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            // 关键修复：禁用本弹窗内的文本选择，避免与全局 SelectionContainer 手势竞争
            DisableSelection {
                Column(modifier = Modifier.padding(16.dp)) {
                    // ===== 标题栏 =====
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (projectName.isBlank()) title else "$title - $projectName",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        if (files.isNotEmpty()) {
                            Text(
                                text = "共 ${files.size} 个",
                                fontSize = 12.sp,
                                color = AppColors.TextTertiary
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        thickness = 1.dp,
                        color = Color(0xFFE6F4D0)
                    )

                    // ===== 内容区 =====
                    when {
                        isLoading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AppColors.Green400)
                            }
                        }
                        files.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无附件", color = AppColors.TextTertiary, fontSize = 14.sp)
                            }
                        }
                        else -> {
                            val mediaFiles = files.filter { isMediaFile(it.type) }
                            val nonMediaFiles = files.filter { !isMediaFile(it.type) }

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 480.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 媒体项
                                items(mediaFiles, key = { "media_${it.id}" }) { file ->
                                    MediaAttachmentItem(
                                        file = file,
                                        onClick = {
                                            try {
                                                val safeUrl = encodeAttachmentUrl(file.fileUrl)
                                                    .takeIf { it.isNotBlank() }
                                                    ?: return@MediaAttachmentItem
                                                onMediaClick(safeUrl, file.fileName, file.type)
                                            } catch (_: Throwable) {
                                                // 静默处理，防止未捕获异常导致进程崩溃
                                            }
                                        },
                                        onDelete = if (canDelete && onDelete != null) {
                                            { deletingFile = file }
                                        } else null
                                    )
                                }

                                // 非媒体分组标题
                                if (nonMediaFiles.isNotEmpty() && mediaFiles.isNotEmpty()) {
                                    item(key = "non_media_header") {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "其他文件",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = AppColors.TextSecondary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }

                                // 非媒体项
                                items(nonMediaFiles, key = { "file_${it.id}" }) { file ->
                                    NonMediaAttachmentItem(
                                        file = file,
                                        onClick = {
                                            try {
                                                onFileClick(file)
                                            } catch (_: Throwable) {
                                                // 静默处理
                                            }
                                        },
                                        onDelete = if (canDelete && onDelete != null) {
                                            { deletingFile = file }
                                        } else null
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // ===== 关闭按钮 =====
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("关闭", color = AppColors.Green400)
                    }
                }
            }
        }
    }

    // ===== 删除二次确认弹窗 =====
    deletingFile?.let { file ->
        AlertDialog(
            onDismissRequest = { deletingFile = null },
            title = {
                Text("删除附件", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text(
                    "确认删除附件「${file.fileName}」吗？\n删除后不可恢复。",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        onDelete?.invoke(file)
                    } catch (_: Throwable) {
                        // 静默处理
                    }
                    deletingFile = null
                }) {
                    Text("确认删除", color = Color(0xFFE53935), fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFile = null }) {
                    Text("取消", color = AppColors.TextSecondary)
                }
            }
        )
    }
}
