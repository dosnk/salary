package com.salary.manager.feature.home.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.UploadProgress

/**
 * 上传进度弹窗
 *
 * 多文件上传时实时展示进度，包含：
 * - 标题（正在上传 + x/总数）
 * - 当前文件名
 * - 当前文件进度条（百分比）
 * - 整体进度条（已成功/失败/总数）
 *
 * 上传过程中禁止点击外部关闭（避免误触中断），由 ViewModel 在上传完成后清除 uploadProgress 自动关闭。
 *
 * @param progress 上传进度信息
 */
@Composable
fun UploadProgressDialog(progress: UploadProgress) {
    Dialog(
        onDismissRequest = { /* 上传中禁止关闭，由ViewModel完成后自动清除 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ===== 标题行：正在上传 + 文件计数 + 转圈指示器 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = AppColors.Green400
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "正在上传 ${progress.currentIndex + 1}/${progress.totalCount}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                }

                // ===== 当前文件名 =====
                if (progress.currentFileName.isNotBlank()) {
                    Text(
                        text = progress.currentFileName,
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // ===== 当前文件进度条 =====
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "当前文件",
                            fontSize = 11.sp,
                            color = AppColors.TextTertiary
                        )
                        Text(
                            text = "${progress.currentFilePercent}%",
                            fontSize = 11.sp,
                            color = AppColors.Green400,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress.currentFilePercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = AppColors.Green400,
                        trackColor = AppColors.Green100,
                        strokeCap = StrokeCap.Round
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // ===== 整体进度条 =====
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "整体进度",
                            fontSize = 11.sp,
                            color = AppColors.TextTertiary
                        )
                        Text(
                            text = "${progress.overallPercent}%",
                            fontSize = 11.sp,
                            color = AppColors.Green400,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress.overallPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                        color = AppColors.Green400,
                        trackColor = AppColors.Green100,
                        strokeCap = StrokeCap.Round
                    )

                    // 成功/失败计数
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "成功 ${progress.successCount}",
                            fontSize = 11.sp,
                            color = AppColors.Green400
                        )
                        if (progress.failedCount > 0) {
                            Text(
                                text = "失败 ${progress.failedCount}",
                                fontSize = 11.sp,
                                color = AppColors.Error
                            )
                        } else {
                            Text(
                                text = "失败 0",
                                fontSize = 11.sp,
                                color = AppColors.TextTertiary
                            )
                        }
                    }
                }

                // ===== 底部提示 =====
                Text(
                    text = "请勿关闭应用，上传完成后自动关闭",
                    fontSize = 11.sp,
                    color = AppColors.TextTertiary
                )
            }
        }
    }
}
