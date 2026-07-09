package com.salary.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.design.theme.AppColors

/**
 * 通用绿色渐变顶部导航栏
 *
 * 高度由内容自适应（标题文字 + 右侧操作区），不使用固定高度的TopAppBar
 *
 * @param title 左侧标题文字
 * @param userNickname 右侧用户昵称，为空则不显示
 * @param unreadCount 未读消息数，0则不显示Badge；传负数则完全不显示消息图标
 * @param onMessageClick 消息图标点击回调（传入null时图标不可点击，仅展示）
 * @param actions 右侧额外操作图标（如排料、知识库等）
 */
@Composable
fun GreenTopNavBar(
    title: String = "三人行装修管理系统",
    userNickname: String = "",
    unreadCount: Int = 0,
    onMessageClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    // 绿色渐变画刷
    val greenGradientBrush = Brush.horizontalGradient(
        colors = listOf(AppColors.Green400, AppColors.Green500)
    )

    Surface(
        color = Color.Transparent,
        modifier = Modifier.background(greenGradientBrush)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧标题
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            // 右侧用户昵称
            if (userNickname.isNotEmpty()) {
                Text(
                    text = userNickname,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            // 右侧额外操作区
            actions()
            // 消息图标（带未读数Badge，可点击）
            // unreadCount >= 0 时显示图标；onMessageClick 非空时用 IconButton 包裹使其可点击
            if (unreadCount >= 0) {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge {
                                Text(
                                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                ) {
                    if (onMessageClick != null) {
                        IconButton(onClick = onMessageClick) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "消息",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // 无点击回调时仅展示图标（保持向后兼容）
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "消息",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 带图标的操作按钮（用于顶部导航栏右侧）
 */
@Composable
fun TopBarActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit = {}
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White
        )
    }
}
