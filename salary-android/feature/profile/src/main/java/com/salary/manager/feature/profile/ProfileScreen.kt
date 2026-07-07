package com.salary.manager.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salary.core.design.component.GreenTopNavBar
import com.salary.core.design.theme.AppColors

/**
 * 个人中心页面
 */
@Composable
fun ProfileScreen(
    onChangePassword: () -> Unit = {},
    onDictionaryManage: () -> Unit = {},
    onUserManage: () -> Unit = {},
    onAiConfig: () -> Unit = {},
    onAbout: () -> Unit = {},
    onMessages: () -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
    userNickname: String = "",
    unreadCount: Int = 0
) {
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val roleDisplay by viewModel.roleDisplay.collectAsStateWithLifecycle()
    val role by viewModel.role.collectAsStateWithLifecycle()

    // 退出登录确认弹窗状态
    var showLogoutDialog by remember { mutableStateOf(false) }

    // 退出登录确认弹窗
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "确认退出登录",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = "您确定要退出当前账号吗？退出后需要重新登录才能使用系统功能。",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                        onLogout()
                    }
                ) {
                    Text("确认退出", color = AppColors.Error, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("取消", color = AppColors.TextSecondary)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部导航栏（显示响应式用户昵称和未读消息数）
        GreenTopNavBar(
            title = "个人中心",
            userNickname = userNickname.ifBlank { nickname }.ifBlank { "未登录" },
            unreadCount = unreadCount
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 菜单列表
        val menuItems = buildList {
            // 个人信息（显示昵称和角色）
            add(MenuItemData(Icons.Default.Person, "个人信息 · $nickname · $roleDisplay", {}, showArrow = false))
            add(MenuItemData(Icons.Default.Notifications, "消息通知", onMessages))
            add(MenuItemData(Icons.Default.Lock, "修改密码", onChangePassword))
            if (role == "admin") {
                add(MenuItemData(Icons.Default.Book, "字典管理", onDictionaryManage))
                add(MenuItemData(Icons.Default.People, "用户管理", onUserManage))
                add(MenuItemData(Icons.Default.SmartToy, "AI大模型配置", onAiConfig))
            }
            add(MenuItemData(Icons.Default.Info, "关于", onAbout))
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                menuItems.forEachIndexed { index, item ->
                    MenuItem(item = item, showDivider = index < menuItems.size - 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 退出登录按钮（点击弹出确认弹窗）
        OutlinedButton(
            onClick = { showLogoutDialog = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Error)
        ) {
            Text("退出登录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 菜单项
 */
@Composable
private fun MenuItem(item: MenuItemData, showDivider: Boolean) {
    TextButton(
        onClick = item.onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(item.icon, contentDescription = null, tint = AppColors.TextSecondary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(item.title, fontSize = 15.sp, color = AppColors.TextPrimary, modifier = Modifier.weight(1f))
            if (item.showArrow) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AppColors.TextTertiary)
            }
        }
    }
    if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(start = 48.dp), color = AppColors.SurfaceVariant)
    }
}

/**
 * 菜单项数据类
 */
data class MenuItemData(
    val icon: ImageVector,
    val title: String,
    val onClick: () -> Unit,
    val showArrow: Boolean = true
)
