package com.salary.manager.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.AiProviderConfigDto

/**
 * AI大模型配置页面
 *
 * 仅管理员可访问，配置AI提供商、API Key和模型
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiConfigScreen(
    onBack: () -> Unit = {},
    viewModel: AiConfigViewModel = hiltViewModel()
) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()
    val editedProviders by viewModel.editedProviders.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()

    // 加载配置
    LaunchedEffect(Unit) {
        viewModel.loadConfig()
    }

    // 保存成功提示
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveSuccess()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶部导航栏
        TopAppBar(
            title = { Text("AI大模型配置", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = AppColors.TextPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Green400)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 错误提示
                error?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Color(0xFFD32F2F),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // 保存成功提示
                if (saveSuccess) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AppColors.Green400)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("配置已保存", color = AppColors.Green400, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // 默认提供商选择
                Text(
                    text = "默认AI提供商",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )

                // 提供商选择列表
                val providerOrder = listOf("deepseek", "tongyi", "wenxin", "glm", "doubao")
                val providerNames = mapOf(
                    "deepseek" to "DeepSeek",
                    "tongyi" to "通义千问",
                    "wenxin" to "文心一言",
                    "glm" to "智谱ChatGLM",
                    "doubao" to "豆包"
                )

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    providerOrder.forEach { providerKey ->
                        val isSelected = selectedProvider == providerKey
                        val hasKey = config?.providers?.get(providerKey)?.hasApiKey == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectProvider(providerKey) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.Check else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) AppColors.Green400 else AppColors.TextTertiary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = providerNames[providerKey] ?: providerKey,
                                fontSize = 15.sp,
                                color = if (isSelected) AppColors.Green400 else AppColors.TextPrimary,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            // API Key状态指示
                            if (hasKey) {
                                Text("已配置", fontSize = 12.sp, color = AppColors.Green400)
                            } else {
                                Text("未配置", fontSize = 12.sp, color = AppColors.TextTertiary)
                            }
                        }
                        if (providerKey != providerOrder.last()) {
                            HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 各提供商详细配置
                Text(
                    text = "提供商配置",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )

                providerOrder.forEach { providerKey ->
                    val providerConfig = config?.providers?.get(providerKey)
                    val edited = editedProviders[providerKey]

                    if (providerConfig != null) {
                        ProviderConfigCard(
                            providerKey = providerKey,
                            providerName = providerNames[providerKey] ?: providerKey,
                            config = providerConfig,
                            editedApiKey = edited?.apiKey ?: "",
                            editedSecretKey = edited?.secretKey ?: "",
                            editedModel = edited?.model ?: "",
                            onApiKeyChange = { viewModel.updateApiKey(providerKey, it) },
                            onSecretKeyChange = { viewModel.updateSecretKey(providerKey, it) },
                            onModelChange = { viewModel.updateModel(providerKey, it) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 保存按钮
                Button(
                    onClick = { viewModel.saveConfig() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("保存配置", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

/**
 * 单个提供商配置卡片
 */
@Composable
private fun ProviderConfigCard(
    providerKey: String,
    providerName: String,
    config: AiProviderConfigDto,
    editedApiKey: String,
    editedSecretKey: String,
    editedModel: String,
    onApiKeyChange: (String) -> Unit,
    onSecretKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
) {
    var showApiKey by remember { mutableStateOf(false) }
    var showSecretKey by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 提供商标题
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = providerName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (config.hasApiKey) {
                    Text("已配置Key", fontSize = 12.sp, color = AppColors.Green400)
                }
            }

            // API Key输入
            OutlinedTextField(
                value = editedApiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Text(
                        text = if (showApiKey) "隐藏" else "显示",
                        fontSize = 12.sp,
                        color = AppColors.Green400,
                        modifier = Modifier.clickable { showApiKey = !showApiKey }.padding(8.dp)
                    )
                },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Green400,
                    unfocusedBorderColor = AppColors.SurfaceVariant
                )
            )

            // 文心一言额外需要Secret Key
            if (providerKey == "wenxin") {
                OutlinedTextField(
                    value = editedSecretKey,
                    onValueChange = onSecretKeyChange,
                    label = { Text("Secret Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showSecretKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            text = if (showSecretKey) "隐藏" else "显示",
                            fontSize = 12.sp,
                            color = AppColors.Green400,
                            modifier = Modifier.clickable { showSecretKey = !showSecretKey }.padding(8.dp)
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Green400,
                        unfocusedBorderColor = AppColors.SurfaceVariant
                    )
                )
            }

            // 模型名称输入
            OutlinedTextField(
                value = editedModel,
                onValueChange = onModelChange,
                label = { Text("模型名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Green400,
                    unfocusedBorderColor = AppColors.SurfaceVariant
                )
            )

            // 服务地址（只读）
            Text(
                text = "服务地址: ${config.baseUrl}",
                fontSize = 12.sp,
                color = AppColors.TextTertiary
            )
        }
    }
}
