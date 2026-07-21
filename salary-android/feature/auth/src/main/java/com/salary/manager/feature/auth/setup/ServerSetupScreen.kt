package com.salary.manager.feature.auth.setup

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.data.local.ServerConfig
import com.salary.core.design.theme.AppColors
import com.salary.core.network.interceptor.ConnectionTestResult
import kotlinx.coroutines.launch

/**
 * 服务器地址配置页面
 *
 * 首次启动时显示，要求用户填写服务器地址。
 * 保存后不再弹出，除非重装或清除数据。
 *
 * 支持：
 * - 测试连接：用独立OkHttpClient验证地址是否可达，显示延迟和具体错误
 * - 保存配置：保存后提示重启应用以应用新地址（Retrofit单例需要重建）
 */
@Composable
fun ServerSetupScreen(
    onConfigured: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val serverConfig = viewModel.serverConfig
    var url by remember { mutableStateOf(ServerConfig.DEFAULT_URL) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 监听测试结果和测试状态
    val testResult by viewModel.testResult.collectAsState()
    val isTesting by viewModel.isTesting.collectAsState()

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
                onValueChange = {
                    url = it
                    errorMessage = null
                    // 用户修改地址时清除上次的测试结果
                    viewModel.clearResult()
                },
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

            // 提示文字（两行显示）
            Column(modifier = Modifier.padding(top = 8.dp, start = 4.dp)) {
                Text(
                    "格式: http://域名或IP:端口号",
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary
                )
                Text(
                    "如: http://example.com:9393",
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary
                )
            }

            // 错误提示
            if (errorMessage != null) {
                Text(
                    errorMessage!!,
                    fontSize = 13.sp,
                    color = AppColors.Error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 测试结果显示
            testResult?.let { result ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (result) {
                            is ConnectionTestResult.Success -> AppColors.Green50
                            is ConnectionTestResult.Error -> Color(0xFFFFEBEE)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (result) {
                                is ConnectionTestResult.Success -> Icons.Default.CheckCircle
                                is ConnectionTestResult.Error -> Icons.Default.Error
                            },
                            contentDescription = null,
                            tint = when (result) {
                                is ConnectionTestResult.Success -> AppColors.Green400
                                is ConnectionTestResult.Error -> AppColors.Error
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (result) {
                                is ConnectionTestResult.Success ->
                                    "连接成功，延迟 ${result.latencyMs}ms（状态：${result.serverStatus}）"
                                is ConnectionTestResult.Error -> result.message
                            },
                            fontSize = 13.sp,
                            color = when (result) {
                                is ConnectionTestResult.Success -> AppColors.Green600
                                is ConnectionTestResult.Error -> AppColors.Error
                            },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 按钮行：测试连接（描边） + 保存配置（实心）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 测试连接按钮（描边样式）
                OutlinedButton(
                    onClick = {
                        val trimmed = url.trim()
                        when {
                            trimmed.isEmpty() -> errorMessage = "请输入服务器地址"
                            !trimmed.startsWith("http://") && !trimmed.startsWith("https://") ->
                                errorMessage = "地址必须以 http:// 或 https:// 开头"
                            else -> {
                                errorMessage = null
                                viewModel.testConnection(trimmed)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    enabled = !isTesting && !isSaving,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.Green400
                    )
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AppColors.Green400
                        )
                    } else {
                        Text("测试连接", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // 保存配置按钮（实心样式）
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
                                    // 显示重启提示对话框
                                    showRestartDialog = true
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400),
                    enabled = !isSaving && !isTesting
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("保存配置", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 重启提示对话框 - 使用 Dialog + usePlatformDefaultWidth=false 实现宽度自适应屏幕
    // 配置保存后，Retrofit单例需要重建才能使用新地址，因此必须重启应用
    // 不提供"稍后重启"选项，因为不重启会导致业务API用旧地址，登录等操作全部失败
    if (showRestartDialog) {
        Dialog(
            onDismissRequest = { /* 不可关闭，必须点击重启按钮 */ },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(0.92f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "配置已保存",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        "服务器地址已保存成功。应用需要重启以应用新的服务器地址，" +
                                "请点击下方按钮重启应用。",
                        fontSize = 14.sp,
                        color = AppColors.TextSecondary
                    )
                    // 重启按钮右对齐
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                // 通过重启Activity来重建Retrofit单例
                                val packageManager = context.packageManager
                                val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                                // 退出当前进程，让Retrofit等单例重建
                                android.os.Process.killProcess(android.os.Process.myPid())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400)
                        ) {
                            Text("重启应用", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
