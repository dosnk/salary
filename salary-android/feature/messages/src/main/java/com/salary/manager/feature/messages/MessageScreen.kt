package com.salary.manager.feature.messages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.MessageDto

/**
 * 消息通知页面
 *
 * 功能:
 * - 消息列表（分页加载）
 * - 未读/已读状态
 * - 标记已读
 * - 全部已读
 * - 删除消息
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    onBack: () -> Unit = {},
    messages: List<MessageDto> = emptyList(),
    unreadCount: Int = 0,
    onMarkRead: (Int) -> Unit = {},
    onMarkAllRead: () -> Unit = {},
    onDeleteMessage: (Int) -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (unreadCount > 0) "消息通知($unreadCount)" else "消息通知",
                        fontSize = 20.sp,
                        color = AppColors.TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = AppColors.TextPrimary)
                    }
                },
                actions = {
                    if (messages.any { !it.isRead }) {
                        TextButton(onClick = onMarkAllRead) {
                            Text("全部已读", fontSize = 14.sp, color = AppColors.Green400)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无消息", fontSize = 16.sp, color = AppColors.TextTertiary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageCard(
                        message = message,
                        onRead = { onMarkRead(message.id) },
                        onDelete = { onDeleteMessage(message.id) }
                    )
                }
            }
        }
    }
}

/**
 * 消息卡片
 */
@Composable
private fun MessageCard(
    message: MessageDto,
    onRead: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isRead) Color.White else AppColors.Green50
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (message.isRead) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 未读圆点
            if (!message.isRead) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AppColors.Green400,
                    modifier = Modifier.size(8.dp)
                ) {}
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        message.title,
                        fontSize = 15.sp,
                        fontWeight = if (message.isRead) FontWeight.Normal else FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 类型标签
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (message.type) {
                            "settlement" -> AppColors.Green50
                            "project" -> AppColors.SurfaceVariant
                            else -> AppColors.SurfaceVariant
                        }
                    ) {
                        Text(
                            when (message.type) {
                                "settlement" -> "结算"
                                "project" -> "工程"
                                "advance" -> "预支"
                                else -> "通知"
                            },
                            fontSize = 11.sp,
                            color = when (message.type) {
                                "settlement" -> AppColors.Green600
                                else -> AppColors.TextSecondary
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    message.content,
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    message.createdAt ?: "",
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary
                )
            }

            // 操作
            if (!message.isRead) {
                TextButton(onClick = onRead) {
                    Text("已读", fontSize = 12.sp, color = AppColors.Green400)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Text("×", fontSize = 16.sp, color = AppColors.TextTertiary)
            }
        }
    }
}
