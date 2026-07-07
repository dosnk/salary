package com.salary.manager.feature.home.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.salary.core.common.util.DateFormatter
import com.salary.core.design.theme.AppColors
import com.salary.core.network.dto.FileDto

/**
 * 判断是否为图片类型
 */
private fun isImageType(type: String?): Boolean =
    type?.startsWith("image/") == true

/**
 * 判断是否为视频类型
 */
private fun isVideoType(type: String?): Boolean =
    type?.startsWith("video/") == true

/**
 * 判断是否为媒体文件（图片或视频）
 */
private fun isMediaFile(type: String?): Boolean =
    isImageType(type) || isVideoType(type)

/**
 * 附件网格浏览弹窗（参考微信聊天媒体浏览样式）
 *
 * 改造点：点击"查看附件"按钮后直接展示媒体文件网格，无需再经过文件名列表。
 *
 * 布局：
 * - 媒体文件（图片/视频）：3列网格，图片显示缩略图，视频显示缩略图+播放图标
 * - 非媒体文件：底部列表区域，显示文件名+大小+日期
 *
 * @param projectName 工程名称（弹窗标题展示用）
 * @param files 附件列表
 * @param fileUrls 文件ID→完整URL的映射（由外层预计算，避免每个缩略图都异步请求）
 * @param isLoading 是否正在加载
 * @param onDismiss 关闭弹窗回调
 * @param onMediaClick 媒体文件点击回调（参数：完整URL、文件名、MIME类型）
 * @param onFileClick 非媒体文件点击回调（参数：文件DTO，由外层用Intent打开）
 */
@Composable
fun AttachmentGridDialog(
    projectName: String,
    files: List<FileDto>,
    fileUrls: Map<Int, String>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onMediaClick: (fullUrl: String, fileName: String, fileType: String?) -> Unit,
    onFileClick: (FileDto) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.98f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // ===== 标题栏 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (projectName.isEmpty()) "附件浏览" else "附件浏览 - $projectName",
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
                if (isLoading) {
                    // 加载中
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppColors.Green400)
                    }
                } else if (files.isEmpty()) {
                    // 空列表
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无附件", color = AppColors.TextTertiary, fontSize = 14.sp)
                    }
                } else {
                    // 分离媒体文件和非媒体文件
                    val mediaFiles = files.filter { isMediaFile(it.type) }
                    val nonMediaFiles = files.filter { !isMediaFile(it.type) }

                    // 单列布局，限制最大高度可滚动
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ===== 媒体文件单列列表 =====
                        items(mediaFiles, key = { it.id }) { file ->
                            MediaGridItem(
                                file = file,
                                fullUrl = fileUrls[file.id] ?: "",
                                onClick = {
                                    val url = fileUrls[file.id] ?: return@MediaGridItem
                                    val name = file.originalName?.takeIf { it.isNotBlank() }
                                        ?: file.fileName
                                    onMediaClick(url, name, file.type)
                                }
                            )
                        }

                        // ===== 非媒体文件列表 =====
                        if (nonMediaFiles.isNotEmpty()) {
                            if (mediaFiles.isNotEmpty()) {
                                item {
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
                            items(nonMediaFiles, key = { it.id }) { file ->
                                NonMediaFileItem(
                                    file = file,
                                    onClick = { onFileClick(file) }
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

/**
 * 媒体列表项（纵向布局：缩略图占满弹窗宽度，高度按原始长宽比自适应）
 *
 * 缩略图使用 ContentScale.Fit：
 * - 宽度铺满弹窗可用宽度
 * - 高度按原始长宽比自适应（不裁剪、不拉伸变形）
 * - 图片小于弹窗宽度时拉伸至弹窗宽度（保持长宽比）
 *
 * @param file 文件DTO
 * @param fullUrl 完整URL（由外层预计算）
 * @param onClick 点击回调
 */
@Composable
private fun MediaGridItem(
    file: FileDto,
    fullUrl: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isVideo = isVideoType(file.type)
    val displayName = file.originalName?.takeIf { it.isNotBlank() } ?: file.fileName

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        // ===== 缩略图区域：宽度占满弹窗，高度按长宽比自适应 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5))
        ) {
            if (fullUrl.isBlank()) {
                // URL未加载时显示占位（固定高度避免无内容塌陷）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.Green400
                    )
                }
            } else {
                // 图片/视频缩略图
                // fillMaxWidth 拉伸宽度至弹窗宽度
                // ContentScale.Fit 保持原始长宽比，高度自适应
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(fullUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }

            // 视频文件叠加播放图标（居中）
            if (isVideo && fullUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),  // 与占位高度一致，确保播放图标居中显示
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "视频",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                // 右下角"视频"标签
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    color = Color(0x88000000),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "视频",
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // ===== 底部：文件信息（类型 + 大小 + 日期） =====
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件类型标签
            val typeLabel = when {
                isVideo -> "视频"
                isImageType(file.type) -> "图片"
                else -> "媒体"
            }
            Text(
                text = typeLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = AppColors.TextPrimary
            )
            Text(
                text = "${formatFileSize(file.fileSize)} · ${DateFormatter.formatDate(file.uploadedAt)}",
                fontSize = 12.sp,
                color = AppColors.TextTertiary
            )
        }
    }
}

/**
 * 非媒体文件列表项
 * 显示文件图标、文件名、大小和上传日期
 */
@Composable
private fun NonMediaFileItem(file: FileDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = null,
            tint = AppColors.Green400,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.originalName?.takeIf { it.isNotBlank() } ?: file.fileName,
                fontSize = 14.sp,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatFileSize(file.fileSize)} · ${DateFormatter.formatDate(file.uploadedAt)}",
                fontSize = 12.sp,
                color = AppColors.TextTertiary
            )
        }
    }
}

/**
 * 格式化文件大小为人类可读字符串
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
        else -> String.format("%.1fMB", bytes / (1024.0 * 1024))
    }
}
