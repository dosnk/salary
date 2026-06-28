package com.salary.manager.feature.auth.login

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.design.theme.AppColors
import com.salary.core.network.interceptor.LatencyTracker

/**
 * 登录页面
 *
 * @param onLoginSuccess 登录成功回调
 * @param onNavigateToRegister 跳转注册页回调
 * @param latencyTracker 全局延迟追踪器（用于显示API状态栏）
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit = {},
    onNavigateToRegister: () -> Unit = {},
    latencyTracker: LatencyTracker? = null,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    // 监听后端在线状态
    val isOnline by latencyTracker?.isOnline?.collectAsState() ?: remember { mutableStateOf(true) }
    val lastError by latencyTracker?.lastError?.collectAsState() ?: remember { mutableStateOf(null) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is LoginState.Success) {
            onLoginSuccess()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // 页面内容区域（居中显示登录表单）
        Box(modifier = Modifier.weight(1f)) {
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

            Spacer(modifier = Modifier.height(32.dp))

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
        }

        // ===== 底部版权信息 + API时延信息（同一行，左边版权，右边时延）
        val online by latencyTracker?.isOnline?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(false)
        val latency by latencyTracker?.latencyMs?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(0L)
        val error by latencyTracker?.lastError?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(null)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "©微信群：三人行必有我师",
                fontSize = 12.sp,
                color = AppColors.TextSecondary
            )
            // API时延信息显示在右边
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 状态指示点
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            color = if (online) Color(0xFF4CAF50) else AppColors.Error,
                            shape = RoundedCornerShape(3.dp)
                        )
                )
                // 状态文字
                if (online) {
                    Text(
                        text = if (latency > 0) "服务器在线：${latency}ms" else "服务器在线",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            latency <= 0 -> Color(0xFF4CAF50)
                            latency < 200 -> Color(0xFF4CAF50)
                            latency < 500 -> Color(0xFFFF9800)
                            else -> Color(0xFFFF5722)
                        }
                    )
                } else {
                    val errorText = when {
                        error != null -> error
                        else -> "连接失败"
                    }
                    Text(
                        text = "服务器离线：$errorText",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = AppColors.Error,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
