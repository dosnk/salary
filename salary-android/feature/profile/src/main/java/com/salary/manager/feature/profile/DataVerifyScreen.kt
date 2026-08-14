package com.salary.manager.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
 * 仅 admin 角色可访问，展示后端13项数据一致性校验结果。
 * 按业务类别分组展示，低风险错误自动折叠，用户可手动展开查看。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataVerifyScreen(
    onBack: () -> Unit = {},
    viewModel: DataVerifyViewModel = hiltViewModel()
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.error.collect { msg -> error = msg }
    }

    LaunchedEffect(Unit) {
        viewModel.verify()
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
                IconButton(onClick = { error = null; viewModel.verify() }, enabled = !isLoading) {
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
                            onClick = { error = null; viewModel.verify() },
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400)
                        ) {
                            Text("重新校验")
                        }
                    }
                }
            }

            result != null -> {
                VerifyResultContent(
                    result = result!!,
                    isLoading = isLoading,
                    error = error,
                    onRetry = { error = null; viewModel.verify() }
                )
            }
        }
    }
}

// ===================== 业务类别分组定义 =====================

/**
 * 校验项业务类别
 */
enum class VerifyCategory(val title: String) {
    AMOUNT("金额一致性"),
    SETTLEMENT_STATUS("结算状态一致性"),
    STATISTICS("统计展示一致性"),
    DATA_INTEGRITY("数据完整性"),
    SYSTEM("系统状态")
}

/**
 * 判断校验项所属的业务类别（通过 name 关键字匹配）
 */
private fun categorize(detailName: String): VerifyCategory {
    return when {
        detailName.contains("工程总额") ||
        detailName.contains("结算单总额") ||
        detailName.contains("结算快照总额") ||
        detailName.contains("预支扣款") ||
        detailName.contains("结算实付") -> VerifyCategory.AMOUNT

        detailName.contains("待结算") ||
        detailName.contains("视图结算状态") ||
        detailName.contains("孤儿") -> VerifyCategory.SETTLEMENT_STATUS

        detailName.contains("月均收入") ||
        detailName.contains("卡片2/3") ||
        detailName.contains("Dashboard") -> VerifyCategory.STATISTICS

        detailName.contains("工日") ||
        detailName.contains("权限") -> VerifyCategory.DATA_INTEGRITY

        detailName.contains("物化视图") -> VerifyCategory.SYSTEM

        else -> VerifyCategory.AMOUNT
    }
}

/**
 * 判断校验项是否为低风险（失败时也自动折叠）
 *
 * 低风险项：冗余字段误差、历史快照误差、统计展示误差、刷新延迟等
 * 这些问题不影响核心财务数据和当前业务计算
 */
private fun isLowRisk(detailName: String): Boolean {
    return detailName.contains("工程总额") ||       // 冗余字段误差，统计不依赖此字段
           detailName.contains("结算快照总额") ||    // 历史快照精度误差，不影响当前业务
           detailName.contains("月均收入") ||        // 统计展示计算误差
           detailName.contains("卡片2/3") ||         // Dashboard 展示误差
           detailName.contains("Dashboard") ||
           detailName.contains("物化视图")           // 刷新延迟，非数据错误
}

// ===================== 结果展示 =====================

/**
 * 校验结果内容区（按业务类别分组 + 折叠）
 */
@Composable
private fun VerifyResultContent(
    result: DataVerifyResultDto,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    // 将校验项按业务类别分组
    val groupedDetails = remember(result) {
        result.details.groupBy { categorize(it.name) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部错误提示
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

        // 按业务类别分组展示
        VerifyCategory.values().forEach { category ->
            val items = groupedDetails[category]
            if (!items.isNullOrEmpty()) {
                CategorySection(
                    category = category,
                    details = items
                )
            }
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

            Text(
                text = "校验耗时: ${result.elapsed}s",
                color = AppColors.TextSecondary,
                fontSize = 12.sp
            )

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
 * 业务类别分组区块（可折叠）
 *
 * 折叠规则：
 * - 分组内全部通过 → 默认折叠，标题显示绿色通过数
 * - 分组内有失败项 → 默认展开，标题显示红色失败数
 * - 用户可手动点击标题行切换展开/折叠状态
 */
@Composable
private fun CategorySection(
    category: VerifyCategory,
    details: List<DataVerifyDetailDto>
) {
    val passedCount = details.count { it.passed }
    val failedCount = details.count { !it.passed }
    val allPassedInGroup = failedCount == 0

    // 分组默认展开状态：有失败项时展开，全通过时折叠
    var expanded by remember(category, allPassedInGroup) {
        mutableStateOf(!allPassedInGroup)
    }

    val headerColor = if (allPassedInGroup) Color(0xFFF1F8E9) else Color(0xFFFFF3E0)
    val titleColor = if (allPassedInGroup) Color(0xFF2E7D32) else Color(0xFFE65100)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // 分组标题行（可点击切换展开/折叠）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 展开/折叠箭头
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowDown
                              else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "折叠" else "展开",
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // 分组标题
            Text(
                text = category.title,
                color = AppColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )

            // 通过/失败数标签
            Surface(
                color = headerColor,
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (allPassedInGroup) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = titleColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${passedCount}项通过",
                            color = titleColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = titleColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${failedCount}项失败/${details.size}项",
                            color = titleColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 分组内容（展开时显示）
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                details.forEach { detail ->
                    VerifyDetailItem(detail = detail)
                }
            }
        }
    }
}

/**
 * 单项校验结果（可折叠）
 *
 * 折叠规则：
 * - 通过的项 → 折叠，只显示标题行（图标+名称）
 * - 低风险失败的项 → 折叠，显示标题行+警告标记，用户可展开查看详情
 * - 高风险失败的项 → 默认展开，直接显示详情
 */
@Composable
private fun VerifyDetailItem(detail: DataVerifyDetailDto) {
    val lowRisk = isLowRisk(detail.name)

    // 单项默认展开状态：
    // - 通过 → 折叠（无详情可看）
    // - 失败+低风险 → 折叠（问题不大，用户可手动展开）
    // - 失败+高风险 → 展开（需要用户关注）
    var expanded by remember(detail.name, detail.passed, lowRisk) {
        mutableStateOf(!detail.passed && !lowRisk)
    }

    val backgroundColor = when {
        detail.passed -> Color(0xFFFAFDF6)       // 浅绿
        lowRisk -> Color(0xFFFFFBF0)              // 浅黄（低风险）
        else -> Color(0xFFFFF5F5)                 // 浅红（高风险）
    }
    val iconTint = if (detail.passed) Color(0xFF4CAF50) else if (lowRisk) Color(0xFFFF9800) else Color(0xFFE57373)
    val titleColor = if (detail.passed) Color(0xFF2E7D32) else if (lowRisk) Color(0xFFE65100) else Color(0xFFC62828)

    // 是否可折叠（有详情且未通过时允许展开/折叠）
    val canExpand = detail.detail.isNotEmpty() && !detail.passed

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (canExpand) Modifier.clickable { expanded = !expanded } else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (detail.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = detail.name,
                    color = titleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                // 低风险失败标记
                if (!detail.passed && lowRisk) {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "低风险",
                            color = Color(0xFFE65100),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                // 展开/折叠箭头（仅有详情的失败项显示）
                if (canExpand) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowDown
                                      else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = if (expanded) "折叠" else "展开",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 详情内容（展开时显示）
            AnimatedVisibility(
                visible = expanded && detail.detail.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = Color(0xFFE0E0E0), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(6.dp))
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
}
