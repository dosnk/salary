package com.salary.manager.feature.auth.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.data.local.ServerConfig
import com.salary.core.design.theme.AppColors
import kotlinx.coroutines.launch

/**
 * 服务器地址配置页面
 *
 * 首次启动时显示，要求用户填写服务器地址。
 * 保存后不再弹出，除非重装或清除数据。
 */
@Composable
fun ServerSetupScreen(
    serverConfig: ServerConfig,
    onConfigured: () -> Unit
) {
    var url by remember { mutableStateOf(ServerConfig.DEFAULT_URL) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 图标
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = AppColors.Green400,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "三人行吊顶管理系统",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "请配置服务器地址",
                fontSize = 16.sp,
                color = AppColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 服务器地址输入框
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; errorMessage = null },
                label = { Text("服务器地址", fontSize = 14.sp) },
                placeholder = { Text(ServerConfig.DEFAULT_URL, fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Green400,
                    unfocusedBorderColor = AppColors.Green100,
                    cursorColor = AppColors.Green400,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                ),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Dns, null, tint = AppColors.TextTertiary)
                }
            )

            // 提示文字
            Text(
                "格式: http://IP地址:端口号",
                fontSize = 12.sp,
                color = AppColors.TextTertiary,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp)
            )

            // 错误提示
            if (errorMessage != null) {
                Text(
                    errorMessage!!,
                    fontSize = 13.sp,
                    color = AppColors.Error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 确认按钮
            Button(
                onClick = {
                    val trimmed = url.trim()
                    when {
                        trimmed.isEmpty() -> errorMessage = "请输入服务器地址"
                        !trimmed.startsWith("http://") && !trimmed.startsWith("https://") ->
                            errorMessage = "地址必须以 http:// 或 https:// 开头"
                        else -> {
                            isSaving = true
                            scope.launch {
                                serverConfig.saveServerUrl(trimmed)
                                isSaving = false
                                onConfigured()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("确认连接", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
