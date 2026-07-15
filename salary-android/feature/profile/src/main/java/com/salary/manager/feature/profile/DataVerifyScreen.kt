package com.salary.manager.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.DataVerifyDetailDto
import com.salary.core.network.api.DataVerifyResultDto

/**
 * 数据一致性校验页面
 *
 * 仅 admin 角色可访问，展示后端8项数据一致性校验结果，
 * 包括金额、统计、结算三个口径的一致性检查。
 *
 * 进入页面自动触发校验，支持下拉重新校验。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataVerifyScreen(
    onBack: () -> Unit = {},
    viewModel: DataVerifyViewModel = hiltViewModel()
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // 进入页面自动触发校验
    LaunchedEffect(Unit) {
        viewModel.verify()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部导航栏（白色背景 + 返回按钮，与同模块 AiConfigScreen 风格一致）
        TopAppBar(
            title = { Text("数据一致性校验", fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "返回",
                        tint = AppColors.TextPrimary
                    )
                }
            },
            actions = {
                // 重新校验按钮
                IconButton(onClick = { viewModel.verify() }, enabled = !isLoading) {
                    Icon(
                        Icons.Default.Refresh,
                        "重新校验",
                        tint = if (isLoading) AppColors.TextSecondary else AppColors.Green400
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        when {
            // 加载中
            isLoading && result == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppColors.Green400)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "正在执行数据一致性校验...",
                            color = AppColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 错误状态
            error != null && result == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = error!!,
                            color = Color(0xFFD32F2F),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Button(
                            onClick = { viewModel.verify() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400)
                        ) {
                            Text("重新校验")
                        }
                    }
                }
            }

            // 校验结果展示
            result != null -> {
                VerifyResultContent(
                    result = result!!,
                    isLoading = isLoading,
                    error = error,
                    onRetry = { viewModel.verify() }
                )
            }
        }
    }
}

/**
 * 校验结果内容区
 */
@Composable
private fun VerifyResultContent(
    result: DataVerifyResultDto,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部错误提示（非首次加载的错误，如重试失败）
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

        // 汇总卡片
        SummaryCard(result = result, isLoading = isLoading, onRetry = onRetry)

        // 校验明细列表
        result.details.forEach { detail ->
            VerifyDetailItem(detail = detail)
        }
    }
}

/**
 * 汇总卡片：显示通过/失败/警告数量和耗时
 */
@Composable
private fun SummaryCard(
    result: DataVerifyResultDto,
    isLoading: Boolean,
    onRetry: () -> Unit
) {
    val allPassed = result.failed == 0
    val cardColor = if (allPassed) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    val titleColor = if (allPassed) Color(0xFF2E7D32) else Color(0xFFE65100)

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (allPassed) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = titleColor,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (allPassed) "全部校验通过" else "存在不一致项",
                    color = titleColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 统计数据三列
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    label = "通过",
                    value = result.passed,
                    color = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "失败",
                    value = result.failed,
                    color = if (result.failed > 0) Color(0xFFD32F2F) else AppColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    label = "警告",
                    value = result.warnings,
                    color = if (result.warnings > 0) Color(0xFFE65100) else AppColors.TextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 耗时信息
            Text(
                text = "校验耗时: ${result.elapsed}s",
                color = AppColors.TextSecondary,
                fontSize = 12.sp
            )

            // 重新校验按钮
            if (!isLoading) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Green400)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("重新校验")
                }
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    color = AppColors.Green400,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 单个统计项
 */
@Composable
private fun StatItem(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            color = color,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = AppColors.TextSecondary,
            fontSize = 12.sp
        )
    }
}

/**
 * 单项校验结果
 */
@Composable
private fun VerifyDetailItem(detail: DataVerifyDetailDto) {
    val backgroundColor = if (detail.passed) Color(0xFFF1F8E9) else Color(0xFFFFEBEE)
    val iconTint = if (detail.passed) Color(0xFF4CAF50) else Color(0xFFE57373)
    val titleColor = if (detail.passed) Color(0xFF2E7D32) else Color(0xFFC62828)

    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (detail.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = detail.name,
                    color = titleColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (detail.detail.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = detail.detail,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
