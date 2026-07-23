package com.salary.manager.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.CreateUserRequest
import com.salary.core.network.api.UserDto

/**
 * 用户管理页面（仅admin）
 *
 * 用户列表 + 创建/删除/重置密码
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    onBack: () -> Unit = {},
    users: List<UserDto> = emptyList(),
    onCreateUser: (CreateUserRequest, (String?) -> Unit) -> Unit = { _, callback -> callback(null) },
    onResetPassword: (userId: Int, newPassword: String, callback: (String?) -> Unit) -> Unit = { _, _, callback -> callback(null) },
    onDeleteUser: (userId: Int, callback: (String?) -> Unit) -> Unit = { _, callback -> callback(null) }
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // 待重置密码的目标用户（null 表示未打开弹窗）
    var resetTarget by remember { mutableStateOf<UserDto?>(null) }
    // 结果反馈用 Snackbar，重置成功后展示"已将 xx 的密码重置为默认密码"
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("用户管理", fontSize = 20.sp, color = AppColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = AppColors.TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "添加用户", tint = AppColors.Green400)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = AppColors.Background
    ) { padding ->
        // 错误提示
        if (errorMessage != null) {
            Text(
                errorMessage!!,
                fontSize = 13.sp,
                color = AppColors.Error,
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (users.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无用户数据", fontSize = 16.sp, color = AppColors.TextTertiary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    UserCard(
                        user = user,
                        onResetPassword = {
                            // 打开确认弹窗，由管理员显式确认并可自定义新密码
                            errorMessage = null
                            resetTarget = user
                        },
                        onDelete = {
                            onDeleteUser(user.id) { error ->
                                errorMessage = error
                            }
                        }
                    )
                }
            }
        }
    }

    // 添加用户弹窗
    if (showAddDialog) {
        AddUserDialog(
            onDismiss = { showAddDialog = false; errorMessage = null },
            onConfirm = { request ->
                onCreateUser(request) { error ->
                    if (error != null) {
                        errorMessage = error
                    } else {
                        showAddDialog = false
                        errorMessage = null
                    }
                }
            }
        )
    }

    // 重置密码弹窗（二次确认，统一重置为数据库初始化默认密码）
    val target = resetTarget
    if (target != null) {
        ResetPasswordDialog(
            user = target,
            onDismiss = { resetTarget = null; errorMessage = null },
            onConfirm = {
                // 与后端 init-db.js 中的 DEFAULT_PASSWORD 保持一致
                val defaultPassword = "990066"
                onResetPassword(target.id, defaultPassword) { error ->
                    if (error != null) {
                        errorMessage = error
                    } else {
                        resetTarget = null
                        errorMessage = null
                        // 成功后展示新密码，方便管理员告知用户
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "已将 ${target.nickname} 的密码重置为默认密码 $defaultPassword"
                            )
                        }
                    }
                }
            }
        )
    }
}

/**
 * 用户卡片
 */
@Composable
private fun UserCard(
    user: UserDto,
    onResetPassword: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    user.nickname,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (user.role) {
                            "admin" -> AppColors.Error.copy(alpha = 0.1f)
                            "constructor" -> AppColors.Green50
                            else -> AppColors.SurfaceVariant
                        }
                    ) {
                        Text(
                            when (user.role) {
                                "admin" -> "管理员"
                                "constructor" -> "施工员"
                                else -> "资料员"
                            },
                            fontSize = 12.sp,
                            color = when (user.role) {
                                "admin" -> AppColors.Error
                                "constructor" -> AppColors.Green600
                                else -> AppColors.TextSecondary
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    val phone = user.phone
                    if (!phone.isNullOrBlank()) {
                        Text(
                            phone,
                            fontSize = 13.sp,
                            color = AppColors.TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 操作按钮
            IconButton(onClick = onResetPassword) {
                Icon(Icons.Default.LockReset, "重置密码", tint = AppColors.TextTertiary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "删除", tint = AppColors.Error.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

/**
 * 添加用户弹窗
 */
@Composable
private fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (CreateUserRequest) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("constructor") }
    // 本地校验错误提示
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = { Text("添加用户", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = username, onValueChange = { username = it; localError = null },
                    label = { Text("用户名", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it; localError = null },
                    label = { Text("密码(6-20位)", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = nickname, onValueChange = { nickname = it; localError = null },
                    label = { Text("昵称", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it; localError = null },
                    label = { Text("手机号（可选）", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                // 角色选择
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("施工员" to "constructor", "资料员" to "documenter").forEach { (label, value) ->
                        FilterChip(
                            selected = selectedRole == value,
                            onClick = { selectedRole = value },
                            label = { Text(label, fontSize = 13.sp) }
                        )
                    }
                }
                // 本地校验错误提示
                if (localError != null) {
                    Text(localError!!, fontSize = 12.sp, color = AppColors.Error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 客户端校验：与后端 createUserSchema 保持一致（仅长度校验，不强制字母+数字）
                when {
                    username.isBlank() -> localError = "用户名不能为空"
                    username.length < 2 -> localError = "用户名长度至少2位"
                    password.isBlank() -> localError = "密码不能为空"
                    password.length < 6 -> localError = "密码长度至少6位"
                    password.length > 20 -> localError = "密码长度最多20位"
                    nickname.isBlank() -> localError = "昵称不能为空"
                    else -> {
                        onConfirm(
                            CreateUserRequest(
                                username = username.trim(),
                                password = password,
                                nickname = nickname.trim(),
                                phone = phone.ifBlank { null },
                                role = selectedRole
                            )
                        )
                    }
                }
            }) {
                Text("确定", color = AppColors.Green400, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 重置密码弹窗
 *
 * 需要管理员二次确认，统一将目标用户密码重置为数据库初始化默认密码 990066，
 * 与后端 init-db.js 中的 DEFAULT_PASSWORD 保持一致，避免管理员随意设置。
 */
@Composable
private fun ResetPasswordDialog(
    user: UserDto,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = { Text("重置密码", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "确认将 ${user.nickname} 的密码重置为初始默认密码 990066？",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
                Text(
                    "提示：重置后请提醒该用户尽快自行修改密码。",
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认重置", color = AppColors.Error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
