package com.salary.manager.feature.home.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.common.util.DateFormatter
import com.salary.core.design.component.GreenTopNavBar
import com.salary.core.design.theme.AppColors

/**
 * 工作台页面 - 复刻Vue前端Dashboard设计
 * 包含：顶部导航栏、工程创建表单、工程历史列表、底部版权
 *
 * @param onNavigateToProject 点击工程卡片时导航到工程详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToProject: (Int) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 弹窗状态
    var showSpaceTypeDialog by remember { mutableStateOf(false) }
    var showSchemeDialog by remember { mutableStateOf(false) }
    var showMonthDialog by remember { mutableStateOf(false) }

    // 监听消息提示
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // 绿色渐变画刷
    val greenGradientBrush = Brush.horizontalGradient(
        colors = listOf(AppColors.Green400, AppColors.Green500)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ===== 顶部导航栏：绿色渐变背景，高度自适应内容 =====
            GreenTopNavBar(
                title = "三人行装修管理系统",
                userNickname = uiState.userNickname.ifBlank { "未登录" },
                unreadCount = uiState.unreadCount
            )

            // ===== 可滚动内容区域 =====
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .background(AppColors.Background)
                    .padding(horizontal = 12.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ===== 工程创建表单 Card =====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 客户地址
                        OutlinedTextField(
                            value = uiState.customerAddress,
                            onValueChange = { viewModel.updateCustomerAddress(it) },
                            label = { Text("客户地址") },
                            placeholder = { Text("请输入客户地址") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Green400,
                                focusedLabelColor = AppColors.Green400
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // 空间类型（点击弹出选择器，用Box包裹实现点击）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSpaceTypeDialog = true }
                        ) {
                            OutlinedTextField(
                                value = uiState.selectedSpaceType,
                                onValueChange = {},
                                label = { Text("空间类型") },
                                placeholder = { Text("请选择空间类型") },
                                readOnly = true,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppColors.Green400,
                                    focusedLabelColor = AppColors.Green400,
                                    disabledBorderColor = AppColors.Green400,
                                    disabledTextColor = AppColors.TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                enabled = false,
                                trailingIcon = {
                                    Text("▼", fontSize = 12.sp, color = AppColors.TextTertiary)
                                }
                            )
                        }

                        // 施工方案（点击弹出选择器，用Box包裹实现点击）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSchemeDialog = true }
                        ) {
                            OutlinedTextField(
                                value = uiState.selectedScheme,
                                onValueChange = {},
                                label = { Text("施工方案") },
                                placeholder = { Text("请选择施工方案") },
                                readOnly = true,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppColors.Green400,
                                    focusedLabelColor = AppColors.Green400,
                                    disabledBorderColor = AppColors.Green400,
                                    disabledTextColor = AppColors.TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                enabled = false,
                                trailingIcon = {
                                    Text("▼", fontSize = 12.sp, color = AppColors.TextTertiary)
                                }
                            )
                        }

                        // 长度cm（独立一行）
                        OutlinedTextField(
                            value = uiState.lengthCm,
                            onValueChange = { viewModel.updateLength(it) },
                            label = { Text("长度(cm)") },
                            placeholder = { Text("请输入长度") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Green400,
                                focusedLabelColor = AppColors.Green400
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // 宽度cm（独立一行）
                        val isLengthOnly = viewModel.currentSchemeUnit() == "length"
                        OutlinedTextField(
                            value = uiState.widthCm,
                            onValueChange = { viewModel.updateWidth(it) },
                            label = { Text("宽度(cm)") },
                            placeholder = { Text("请输入宽度") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLengthOnly,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Green400,
                                focusedLabelColor = AppColors.Green400,
                                disabledBorderColor = AppColors.TextPlaceholder,
                                disabledTextColor = AppColors.TextTertiary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // 分配方式（单选：平均/按工日）
                        Column {
                            Text(
                                text = "分配方式",
                                fontSize = 14.sp,
                                color = AppColors.TextSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.salaryDistribution == "average",
                                    onClick = { viewModel.updateSalaryDistribution("average") },
                                    colors = RadioButtonDefaults.colors(selectedColor = AppColors.Green400)
                                )
                                Text("平均", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(16.dp))
                                RadioButton(
                                    selected = uiState.salaryDistribution == "work_days",
                                    onClick = { viewModel.updateSalaryDistribution("work_days") },
                                    colors = RadioButtonDefaults.colors(selectedColor = AppColors.Green400)
                                )
                                Text("按工日", fontSize = 14.sp)
                            }
                            Text(
                                text = "如需按工日分配，在工程管理里更改",
                                fontSize = 12.sp,
                                color = AppColors.TextTertiary
                            )
                        }

                        // 施工人员选择（横向滚动的方形Checkbox列表，参考Vue van-checkbox shape=square）
                        if (uiState.constructors.isNotEmpty()) {
                            Column {
                                Text(
                                    text = "施工人员",
                                    fontSize = 14.sp,
                                    color = AppColors.TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uiState.constructors.forEach { worker ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (uiState.selectedConstructorIds.contains(worker.id))
                                                        Color(0xFFECFCCB)
                                                    else Color(0xFFF5F5F5)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (uiState.selectedConstructorIds.contains(worker.id))
                                                        AppColors.Green400
                                                    else Color(0xFFE0E0E0),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable { viewModel.toggleConstructor(worker.id) }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            // 方形复选框图标（参考Vue van-checkbox shape=square）
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .background(
                                                        color = if (uiState.selectedConstructorIds.contains(worker.id))
                                                            AppColors.Green400 else Color.Transparent,
                                                        shape = RoundedCornerShape(3.dp)
                                                    )
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = if (uiState.selectedConstructorIds.contains(worker.id))
                                                            AppColors.Green400 else Color(0xFFBDBDBD),
                                                        shape = RoundedCornerShape(3.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (uiState.selectedConstructorIds.contains(worker.id)) {
                                                    Text(
                                                        text = "✓",
                                                        fontSize = 12.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                            Text(
                                                text = worker.nickname,
                                                fontSize = 13.sp,
                                                color = if (uiState.selectedConstructorIds.contains(worker.id))
                                                    AppColors.Green400 else AppColors.TextPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 计算预览公式（浅绿背景+绿色边框）
                        if (uiState.calculationFormula.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = Color(0xFFF9FEF5),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFE6F4D0),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "计算预览：${uiState.calculationFormula}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AppColors.TextPrimary
                                )
                            }
                        }

                        // 工程备注（多行输入）
                        OutlinedTextField(
                            value = uiState.remark,
                            onValueChange = { viewModel.updateRemark(it) },
                            label = { Text("工程备注") },
                            placeholder = { Text("请输入工程备注") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Green400,
                                focusedLabelColor = AppColors.Green400
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // 保存按钮（绿色渐变）
                        Button(
                            onClick = { viewModel.saveProject() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            enabled = !uiState.isSaving,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.Green400,
                                disabledContainerColor = AppColors.Green300
                            )
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("保存中...", color = Color.White, fontSize = 16.sp)
                            } else {
                                Text(
                                    text = "保存",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ===== 工程历史 Card =====
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // 标题行：工程历史 + 年月选择器
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "工程历史",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppColors.Green400
                            )
                            // 年月选择器
                            TextButton(onClick = { showMonthDialog = true }) {
                                Text(
                                    text = formatYearMonth(uiState.selectedYearMonth),
                                    color = AppColors.Green400,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 加载中状态
                        if (uiState.isLoadingProjects) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AppColors.Green400)
                            }
                        } else if (uiState.projects.isEmpty()) {
                            // 空状态
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("暂无数据", color = AppColors.TextTertiary, fontSize = 14.sp)
                            }
                        } else {
                            // 工程卡片列表
                            uiState.projects.forEach { project ->
                                ProjectHistoryCard(
                                    project = project,
                                    viewModel = viewModel,
                                    onClick = { onNavigateToProject(project.id) }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // ===== 弹窗：空间类型选择器 =====
    if (showSpaceTypeDialog) {
        SimplePickerDialog(
            title = "选择空间类型",
            items = uiState.spaceTypes.map { it.name },
            selectedItem = uiState.selectedSpaceType,
            onConfirm = { viewModel.selectSpaceType(it) },
            onDismiss = { showSpaceTypeDialog = false }
        )
    }

    // ===== 弹窗：施工方案选择器 =====
    if (showSchemeDialog) {
        SimplePickerDialog(
            title = "选择施工方案",
            items = uiState.constructionPlans.map { it.name },
            selectedItem = uiState.selectedScheme,
            onConfirm = { viewModel.selectScheme(it) },
            onDismiss = { showSchemeDialog = false }
        )
    }

    // ===== 弹窗：年月选择器 =====
    if (showMonthDialog) {
        MonthPickerDialog(
            currentYearMonth = uiState.selectedYearMonth,
            onConfirm = { viewModel.selectYearMonth(it) },
            onDismiss = { showMonthDialog = false }
        )
    }
}

/**
 * 工程历史卡片组件
 * 显示工程名称、金额、施工人员、附件、时间、子项目表格
 */
@Composable
private fun ProjectHistoryCard(
    project: ProjectHistoryUiModel,
    viewModel: DashboardViewModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFAFFFA)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6F4D0))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 工程标题行：名称 + 金额
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFFECFCCB), Color(0xFFBBF7D0))
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, AppColors.Success, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = project.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "¥${formatNumber(project.totalAmount)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Green400
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 施工人员列表
            if (project.workerNames.isNotEmpty()) {
                Text(
                    text = project.workerNames.joinToString("、"),
                    fontSize = 14.sp,
                    color = AppColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 附件按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onClick,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp, vertical = 2.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = AppColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "查看附件 (${project.fileCount})",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 时间信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "创建时间：${DateFormatter.formatDateTime(project.createdAt)}",
                    fontSize = 12.sp,
                    color = AppColors.Green400,
                    modifier = Modifier
                        .background(Color(0xFFDCFCE7), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Text(
                    text = "更新时间：${DateFormatter.formatDateTime(project.updatedAt)}",
                    fontSize = 12.sp,
                    color = AppColors.Green400,
                    modifier = Modifier
                        .background(Color(0xFFDCFCE7), RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            // 子项目表格
            if (project.subprojects.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                SubprojectTable(
                    subprojects = project.subprojects,
                    viewModel = viewModel
                )
            }
        }
    }
}

/**
 * 子项目表格组件
 * 使用LazyColumn实现（Android不适合HTML table）
 */
@Composable
private fun SubprojectTable(
    subprojects: List<SubprojectUiModel>,
    viewModel: DashboardViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF0FDF4), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF86EFAC), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF9FAFB), RoundedCornerShape(4.dp))
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TableHeaderCell("序号", 0.1f)
            TableHeaderCell("空间", 0.18f)
            TableHeaderCell("方案", 0.18f)
            TableHeaderCell("尺寸(米)", 0.2f)
            TableHeaderCell("数量", 0.16f)
            TableHeaderCell("金额", 0.18f)
        }

        // 表体
        subprojects.forEachIndexed { index, sub ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TableCell("${index + 1}", 0.1f)
                TableCell(sub.spaceTypeName, 0.18f)
                TableCell(sub.constructionPlanName, 0.18f)
                TableCell("${formatNumber(sub.length)} × ${formatNumber(sub.width)}", 0.2f)
                TableCell(
                    "${formatNumber(sub.quantity)} ${viewModel.getUnitDisplayName(sub.unit)}",
                    0.16f
                )
                TableCell("¥${formatNumber(sub.amount)}", 0.18f, color = AppColors.Green400)
            }
        }
    }
}

/**
 * 表头单元格
 */
@Composable
private fun RowScope.TableHeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.TextPrimary,
        modifier = Modifier.weight(weight)
    )
}

/**
 * 表体单元格
 */
@Composable
private fun RowScope.TableCell(text: String, weight: Float, color: Color = AppColors.TextPrimary) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(weight)
    )
}

/**
 * 简约工装风格单选弹窗（用于空间类型和施工方案选择）
 * 设计规范：柔和草木绿#74b85c，背景浅灰白#f8f9f7，圆角22px，柔和阴影
 * 点击任意选项立即选中并自动关闭弹窗，无底部操作按钮
 */
@Composable
private fun SimplePickerDialog(
    title: String,
    items: List<String>,
    selectedItem: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 工装风格配色
    val greenPrimary = Color(0xFF74B85C)
    val bgColor = Color(0xFFF8F9F7)
    val titleColor = Color(0xFF2D302C)
    val dividerColor = Color(0xFFE8E9E6)
    val unselectedBorderColor = Color(0xFFC0C4BC)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = bgColor,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // 标题居中，深色，20sp，半粗
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 16.dp)
                        .padding(horizontal = 20.dp),
                    textAlign = TextAlign.Center
                )

                if (items.isEmpty()) {
                    // 空状态
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无选项", color = AppColors.TextTertiary, fontSize = 14.sp)
                    }
                } else {
                    // 选项列表，支持滚动
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp),
                        userScrollEnabled = true
                    ) {
                        itemsIndexed(items) { index, item ->
                            val isSelected = item == selectedItem

                            // 选项行：高64sp，左右内边距20sp，整行可点击
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clickable {
                                        onConfirm(item)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 圆形单选框：未选中灰色空心，选中绿色填充+白色对勾
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 2.dp,
                                            color = if (isSelected) greenPrimary else unselectedBorderColor,
                                            shape = CircleShape
                                        )
                                        .background(if (isSelected) greenPrimary else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        // 选中：白色对勾
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // 选项文字：16sp，选中变绿色，未选中深灰色
                                Text(
                                    text = item,
                                    fontSize = 16.sp,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isSelected) greenPrimary else titleColor
                                )
                            }

                            // 选项间浅灰分割线（最后一项不加）
                            if (index < items.size - 1) {
                                HorizontalDivider(
                                    color = dividerColor,
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 20.dp)
                                )
                            }
                        }
                    }
                }

                // 底部留白
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 年月选择器弹窗
 * 左侧年份列表 + 右侧月份列表
 */
@Composable
private fun MonthPickerDialog(
    currentYearMonth: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parts = currentYearMonth.split("-")
    val initYear = parts.getOrNull(0)?.toIntOrNull() ?: 2026
    val initMonth = parts.getOrNull(1)?.toIntOrNull() ?: 6

    var selectedYear by remember { mutableIntStateOf(initYear) }
    var selectedMonth by remember { mutableIntStateOf(initMonth) }

    val years = remember { (2020..2030).toList() }
    val months = remember { (1..12).toList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "选择年月", fontWeight = FontWeight.SemiBold)
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // 年份列表
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    state = rememberLazyListState()
                ) {
                    items(years) { year ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (year == selectedYear) AppColors.Green50 else Color.Transparent
                                )
                                .clickable { selectedYear = year }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${year}年",
                                fontSize = 15.sp,
                                fontWeight = if (year == selectedYear) FontWeight.Bold else FontWeight.Normal,
                                color = if (year == selectedYear) AppColors.Green400 else AppColors.TextPrimary
                            )
                        }
                    }
                }

                // 月份列表
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    state = rememberLazyListState()
                ) {
                    items(months) { month ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (month == selectedMonth) AppColors.Green50 else Color.Transparent
                                )
                                .clickable { selectedMonth = month }
                                .padding(vertical = 10.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${month}月",
                                fontSize = 15.sp,
                                fontWeight = if (month == selectedMonth) FontWeight.Bold else FontWeight.Normal,
                                color = if (month == selectedMonth) AppColors.Green400 else AppColors.TextPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val monthStr = selectedMonth.toString().padStart(2, '0')
                onConfirm("$selectedYear-$monthStr")
            }) {
                Text("确定", color = AppColors.Green400)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 格式化数字为两位小数
 */
private fun formatNumber(value: Any?): String {
    if (value == null) return "0.00"
    return when (value) {
        is Double -> {
            if (value == 0.0) "0.00"
            else String.format("%.2f", value)
        }
        is String -> {
            if (value.isBlank()) "0.00"
            else try {
                String.format("%.2f", value.toDouble())
            } catch (_: NumberFormatException) {
                "0.00"
            }
        }
        is Int -> String.format("%.2f", value.toDouble())
        else -> "0.00"
    }
}

/**
 * 格式化年月字符串为中文显示
 * "2026-06" → "2026年6月"
 */
private fun formatYearMonth(yearMonth: String): String {
    return try {
        val parts = yearMonth.split("-")
        val year = parts[0]
        val month = parts[1].toIntOrNull()?.toString() ?: parts[1]
        "${year}年${month}月"
    } catch (_: Exception) {
        yearMonth
    }
}
