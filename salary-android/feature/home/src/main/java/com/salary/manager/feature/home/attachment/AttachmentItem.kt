package com.salary.manager.feature.home.attachment

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.salary.core.common.util.DateFormatter
import com.salary.core.design.theme.AppColors

/**
 * 媒体附件项（图片/视频缩略图）
 *
 * 视觉：宽度占满父容器，高度按图片长宽比自适应（最大 320.dp）；视频叠加播放图标。
 *
 * 手势：
 * - 用 combinedClickable 显式提供空 onLongClick，屏蔽长按默认行为
 * - onClick 内层由调用方 try-catch 兜底（本组件不吞异常，便于调用方按需处理）
 *
 * @param file 附件模型
 * @param onClick 点击回调（预览媒体）
 * @param onDelete 删除回调；null 表示不显示删除按钮
 * @param onShare 分享回调；null 表示不显示分享按钮
 * @param onSave 保存回调；null 表示不显示保存按钮
 * @param isBusy 是否正在执行分享/保存等耗时任务（用于禁用按钮与显示进度）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MediaAttachmentItem(
    file: AttachmentUiModel,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    isBusy: Boolean = false
) {
    val context = LocalContext.current
    val isVideo = isVideoType(file.type)
    val displayName = file.fileName

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // 先 padding 再 combinedClickable：手势热区只覆盖内容区，不响应边缘 padding
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {}
            )
    ) {
        // ===== 缩略图区域 =====
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5))
        ) {
            if (file.fileUrl.isBlank()) {
                // URL 未加载：显示占位（固定高度避免塌陷）
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
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file.fileUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = displayName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    contentScale = ContentScale.Fit
                )
            }

            // 视频叠加播放图标 + "视频"标签
            if (isVideo && file.fileUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = "视频",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
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

        // ===== 底部：类型标签 + 大小/日期 + 操作按钮 =====
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatFileSize(file.fileSize)} · ${DateFormatter.formatDate(file.uploadedAt)}",
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary
                )
                // 操作按钮组（分享/保存/删除）
                AttachmentActionButtons(
                    isBusy = isBusy,
                    onShare = onShare,
                    onSave = onSave,
                    onDelete = onDelete
                )
            }
        }
    }
}

/**
 * 非媒体附件项（文档/PDF/其他）
 *
 * 视觉：图标 + 文件名 + 大小·日期 + （可选）操作按钮
 *
 * @param file 附件模型
 * @param onClick 点击回调（通常拉起系统 Intent 用外部应用打开）
 * @param onDelete 删除回调；null 表示不显示删除按钮
 * @param onShare 分享回调；null 表示不显示分享按钮
 * @param onSave 保存回调；null 表示不显示保存按钮
 * @param isBusy 是否正在执行分享/保存等耗时任务
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NonMediaAttachmentItem(
    file: AttachmentUiModel,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    isBusy: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 先 padding 再 combinedClickable：手势热区只覆盖内容区
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {}
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                isImageType(file.type) -> Icons.Default.Image
                isVideoType(file.type) -> Icons.Default.PlayCircle
                file.type == null -> Icons.Default.AttachFile
                else -> Icons.Default.Description
            },
            contentDescription = null,
            tint = AppColors.Green400,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.fileName,
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
        // 操作按钮组（分享/保存/删除）
        AttachmentActionButtons(
            isBusy = isBusy,
            onShare = onShare,
            onSave = onSave,
            onDelete = onDelete
        )
    }
}

/**
 * 附件操作按钮组
 *
 * 统一收纳分享/保存/删除三种操作按钮，控制显示顺序与忙碌状态。
 * - isBusy=true 时按钮会被禁用，防止用户重复点击触发多次下载
 * - 参数为 null 的按钮不显示，实现按需展示（例如工程详情附件可删除、主页附件不可删除）
 */
@Composable
private fun AttachmentActionButtons(
    isBusy: Boolean,
    onShare: (() -> Unit)?,
    onSave: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    if (onShare == null && onSave == null && onDelete == null) return

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 忙碌指示器：优先展示，避免闪烁
        if (isBusy) {
            Spacer(modifier = Modifier.width(4.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = AppColors.Green400
            )
        }
        if (onShare != null) {
            Spacer(modifier = Modifier.width(2.dp))
            IconButton(
                onClick = onShare,
                enabled = !isBusy,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "分享附件",
                    tint = if (isBusy) AppColors.TextTertiary else AppColors.Green400,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (onSave != null) {
            IconButton(
                onClick = onSave,
                enabled = !isBusy,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "保存到本地",
                    tint = if (isBusy) AppColors.TextTertiary else AppColors.Green400,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (onDelete != null) {
            IconButton(
                onClick = onDelete,
                enabled = !isBusy,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除附件",
                    tint = if (isBusy) AppColors.TextTertiary else Color(0xFFE53935),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
