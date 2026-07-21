package com.salary.manager.feature.auth.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.design.theme.AppColors
import com.salary.core.network.interceptor.LatencyTracker

/**
 * 登录页面
 *
 * 功能：
 * - 用户名/密码登录
 * - 支持"记住密码"勾选，使用加密存储保存凭证
 * - 实时显示后端在线状态（通过 LatencyTracker）
 *
 * @param onLoginSuccess 登录成功回调
 * @param onNavigateToRegister 跳转注册页回调
 * @param latencyTracker 全局延迟追踪器（用于显示API状态栏）
 * @param viewModel 登录ViewModel
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
    latencyTracker: LatencyTracker? = null,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // 初始化凭证（从加密存储恢复已保存的用户名密码）
    LaunchedEffect(Unit) {
        viewModel.initCredentials()
    }

    // 监听已保存的凭证，自动填充用户名密码
    val savedUsername by viewModel.savedUsername.collectAsState()
    val savedPassword by viewModel.savedPassword.collectAsState()
    val rememberCredentials by viewModel.rememberCredentials.collectAsState()

    // 用户名密码输入框状态，初始值为已保存的凭证
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // 凭证加载后自动填充
    LaunchedEffect(savedUsername, savedPassword) {
        if (savedUsername.isNotEmpty() && username.isEmpty()) {
            username = savedUsername
        }
        if (savedPassword.isNotEmpty() && password.isEmpty()) {
            password = savedPassword
        }
    }

    // 监听后端在线状态
    val isOnline by latencyTracker?.isOnline?.collectAsState() ?: remember { mutableStateOf(true) }
    val lastError by latencyTracker?.lastError?.collectAsState() ?: remember { mutableStateOf(null) }

    LaunchedEffect(state) {
        if (state is LoginState.Success) {
            onLoginSuccess()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo/标题
            Text(
                text = "三人行吊顶",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Green400
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "管理系统",
                fontSize = 18.sp,
                color = AppColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 后端离线提示
            if (!isOnline) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "服务器离线",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Error
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastError ?: "无法连接到服务器",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "请检查服务器是否启动或网络是否正常",
                        fontSize = 12.sp,
                        color = AppColors.TextTertiary
                    )
                }
            }

            // 用户名输入框
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 密码输入框
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            )

            // 记住密码复选框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberCredentials,
                    onCheckedChange = { viewModel.setRememberCredentials(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AppColors.Green400,
                        uncheckedColor = AppColors.TextTertiary
                    )
                )
                Text(
                    text = "记住密码",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 登录按钮
            Button(
                onClick = { viewModel.login(username, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                enabled = username.isNotBlank() && password.isNotBlank() && state !is LoginState.Loading,
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Green400,
                    disabledContainerColor = AppColors.Green100
                )
            ) {
                if (state is LoginState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("登录", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }

            // 错误提示
            if (state is LoginState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (state as LoginState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 注册入口
            TextButton(onClick = onNavigateToRegister) {
                Text("没有账号？立即注册", color = AppColors.Green400)
            }
        }

        // 底部信息栏：左侧版权信息 + 右侧服务器在线状态
        // 两侧样式统一：10sp字号 + TextTertiary灰色
        // 使用 spacedBy 避免窄屏挤压，长文本省略号
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：版权信息（左对齐，占主权重）
            Text(
                text = "© 2026 三人行吊顶管理系统 保留所有权利",
                fontSize = 10.sp,
                color = AppColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // 右侧：服务器在线状态（右对齐，样式与左侧版权信息一致）
            // 原ApiLatencyChip为浮动阴影胶囊样式，与左侧版权信息不协调
            // 改为简洁文字样式：状态圆点 + 文字，10sp + TextTertiary
            if (latencyTracker != null) {
                val online by latencyTracker.isOnline.collectAsState()
                val latency by latencyTracker.latencyMs.collectAsState()
                val error by latencyTracker.lastError.collectAsState()
                ServerStatusText(
                    isOnline = online,
                    latencyMs = latency,
                    lastError = error
                )
            }
        }
    }
}

/**
 * 服务器状态文字组件
 *
 * 样式与版权信息一致：10sp字号 + TextTertiary灰色
 * - 检测中：灰色圆点 + "正在检测服务器状态..."（HealthMonitor刚启动，尚未收到响应）
 * - 在线：绿色圆点 + "服务器在线：100ms"
 * - 离线：红色圆点 + "服务器离线：错误信息"
 *
 * @param isOnline 后端是否在线
 * @param latencyMs 最近一次API请求的网络耗时（毫秒）
 * @param lastError 最近一次连接错误信息
 */
@Composable
private fun ServerStatusText(
    isOnline: Boolean,
    latencyMs: Long,
    lastError: String?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态判断：
        // - isOnline=false → 离线（红色圆点）
        // - isOnline=true && latencyMs>0 → 在线（绿色圆点）
        // - isOnline=true && latencyMs=0 && lastError=null → 检测中（灰色圆点）
        //   初始状态LatencyTracker默认isOnline=true/latencyMs=0，HealthMonitor首次检查完成前显示"检测中"
        val isChecking = isOnline && latencyMs == 0L && lastError == null

        // 状态指示圆点（8dp）
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(
                color = when {
                    isChecking -> AppColors.TextTertiary
                    isOnline -> AppColors.Green400
                    else -> AppColors.Error
                }
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        // 状态文字（10sp，与版权信息样式一致）
        val statusText = when {
            isChecking -> "正在检测服务器状态..."
            isOnline -> if (latencyMs > 0) "服务器在线：${latencyMs}ms" else "服务器在线"
            else -> "服务器离线：${lastError ?: "连接失败"}"
        }
        Text(
            text = statusText,
            fontSize = 10.sp,
            color = AppColors.TextTertiary,
            maxLines = 1
        )
    }
}
