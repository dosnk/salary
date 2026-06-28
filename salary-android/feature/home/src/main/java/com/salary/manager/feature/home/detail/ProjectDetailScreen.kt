package com.salary.manager.feature.home.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.common.util.AmountFormatter
import com.salary.core.common.util.DateFormatter
import com.salary.core.design.component.ProjectStatusTag
import com.salary.core.design.component.SalaryTag
import com.salary.core.design.theme.AppColors
import com.salary.core.ui.state.UiState

/**
 * 工程详情页面 - 对齐Vue前端ProjectDetail.vue设计
 * 包含：绿色渐变TopAppBar、工程信息Cell行、施工人员Tag列表、
 *       子项目表格、子项目编辑弹窗、附件管理、修改历史
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Int,
    onBack: () -> Unit = {},
    onEdit: (Int) -> Unit = {},
    viewModel: ProjectDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    Scaffold(
        topBar = {
            // 绿色渐变TopAppBar，对齐Vue前端van-nav-bar设计
            TopAppBar(
                title = {
                    Text(
                        "工程详情",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        // 绿色渐变背景延伸到TopAppBar下方
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
        ) {
            // 顶部绿色渐变区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AppColors.Green400, AppColors.Green600)
                        )
                    )
            )

            when (state) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AppColors.Green400)
                    }
                }
                is UiState.Success -> {
                    val detail = (state as UiState.Success<ProjectDetailUiModel>).data
                    ProjectDetailContent(
                        detail = detail,
                        projectId = projectId,
                        onEdit = onEdit,
                        modifier = Modifier.padding(padding)
                    )
                }
                is UiState.Error -> {
                    val errorMsg = (state as UiState.Error).message
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMsg, color = AppColors.TextSecondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("请检查网络连接后返回重试", color = AppColors.TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 工程详情内容区域
 */
@Composable
fun ProjectDetailContent(
    detail: ProjectDetailUiModel,
    projectId: Int,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 子项目编辑弹窗状态
    var editingSubproject by remember { mutableStateOf<SubprojectUiModel?>(null) }
    // 修改历史展开/收起状态
    var historyExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ===== 工程信息区 =====
        item {
            SectionTitle("工程信息")
        }
        item {
            ProjectInfoSection(detail)
        }

        // ===== 施工人员区 =====
        item {
            SectionTitle("施工人员")
        }
        item {
            WorkerTagSection(detail)
        }

        // ===== 操作区 =====
        item {
            SectionTitle("操作")
        }
        item {
            ActionSection(projectId = projectId, onEdit = onEdit)
        }

        // ===== 子项目列表区 =====
        item {
            SectionTitle("子项目列表")
        }
        if (detail.subprojects.isEmpty()) {
            item {
                CellRow(label = "暂无子项目", value = "")
            }
        } else {
            item {
                SubprojectTableHeader()
            }
            itemsIndexed(detail.subprojects) { index, sub ->
                SubprojectTableRow(
                    index = index,
                    subproject = sub,
                    onEdit = { editingSubproject = sub }
                )
            }
        }

        // ===== 附件管理区 =====
        item {
            SectionTitle("附件管理")
        }
        item {
            AttachmentSection(fileCount = detail.files.size)
        }

        // ===== 修改历史区 =====
        item {
            SectionTitle("修改历史")
        }
        if (detail.history.isEmpty()) {
            item {
                CellRow(label = "暂无修改历史", value = "")
            }
        } else {
            // 根据展开状态决定显示条目数
            val displayHistory = if (historyExpanded) {
                detail.history
            } else {
                detail.history.take(3)
            }
            items(displayHistory) { item ->
                HistoryItem(item)
            }
            if (detail.history.size > 3) {
                item {
                    // 展开/收起按钮
                    TextButton(
                        onClick = { historyExpanded = !historyExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (historyExpanded) "收起" else "展开全部 (${detail.history.size}条)",
                            color = AppColors.Green400,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 底部留白
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // 子项目编辑弹窗
    editingSubproject?.let { sub ->
        SubprojectEditDialog(
            subproject = sub,
            onDismiss = { editingSubproject = null },
            onConfirm = {
                // TODO: 调用API保存子项目修改
                editingSubproject = null
            }
        )
    }
}

/**
 * 分组标题 - 对齐Vue前端van-cell-group title
 */
@Composable
fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp)
    )
}

/**
 * 工程信息区 - 对齐Vue前端van-cell-group "工程信息"
 * 使用Cell行展示（左标签右值）
 */
@Composable
fun ProjectInfoSection(detail: ProjectDetailUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            // 工程名称
            CellRow(label = "工程名称", value = detail.name)
            HorizontalDivider(color = AppColors.Green100, thickness = 0.5.dp)
            // 总金额
            CellRow(
                label = "总金额",
                value = AmountFormatter.format(detail.totalAmount),
                valueColor = AppColors.Green400
            )
            HorizontalDivider(color = AppColors.Green100, thickness = 0.5.dp)
            // 工资分配方式 - Tag标签
            DistributionCellRow(detail.salaryDistribution)
            HorizontalDivider(color = AppColors.Green100, thickness = 0.5.dp)
            // 工程备注
            if (!detail.remark.isNullOrBlank()) {
                CellRow(label = "工程备注", value = detail.remark)
                HorizontalDivider(color = AppColors.Green100, thickness = 0.5.dp)
            }
            // 状态 - Tag标签
            StatusCellRow(detail.status)
            HorizontalDivider(color = AppColors.Green100, thickness = 0.5.dp)
            // 创建/更新时间（浅绿背景）
            TimeCellRow(
                createdAt = detail.createdAt,
                updatedAt = detail.updatedAt
            )
        }
    }
}

/**
 * Cell行组件 - 左标签右值，对齐Vue前端van-cell
 */
@Composable
fun CellRow(
    label: String,
    value: String,
    valueColor: Color = AppColors.TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = AppColors.TextSecondary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor,
            fontWeight = if (valueColor != AppColors.TextPrimary) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/**
 * 工资分配方式Cell行 - 带Tag标签
 */
@Composable
fun DistributionCellRow(salaryDistribution: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "工资分配方式",
            fontSize = 14.sp,
            color = AppColors.TextSecondary
        )
        if (salaryDistribution == "average") {
            SalaryTag(
                text = "平均分配",
                backgroundColor = AppColors.Green400.copy(alpha = 0.12f),
                textColor = AppColors.Green400
            )
        } else {
            SalaryTag(
                text = "按工时分配",
                backgroundColor = AppColors.Success.copy(alpha = 0.12f),
                textColor = AppColors.Success
            )
        }
    }
}

/**
 * 状态Cell行 - 带Tag标签
 */
@Composable
fun StatusCellRow(status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "状态",
            fontSize = 14.sp,
            color = AppColors.TextSecondary
        )
        ProjectStatusTag(status)
    }
}

/**
 * 时间Cell行 - 浅绿背景，对齐Vue前端time-cell样式
 */
@Composable
fun TimeCellRow(createdAt: String, updatedAt: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(AppColors.Green50, AppColors.Green100.copy(alpha = 0.3f))
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 创建时间
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "创建：",
                fontSize = 12.sp,
                color = AppColors.TextTertiary
            )
            Text(
                text = DateFormatter.formatDate(createdAt),
                fontSize = 12.sp,
                color = AppColors.TextPrimary
            )
        }
        // 更新时间
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "更新：",
                fontSize = 12.sp,
                color = AppColors.TextTertiary
            )
            Text(
                text = DateFormatter.formatDate(updatedAt),
                fontSize = 12.sp,
                color = AppColors.TextPrimary
            )
        }
    }
}

/**
 * 施工人员Tag列表 - 对齐Vue前端constructors-list
 * 按工日分配时显示工日数
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkerTagSection(detail: ProjectDetailUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        if (detail.workers.isEmpty()) {
            CellRow(label = "暂无施工人员", value = "")
        } else {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detail.workers.forEach { worker ->
                    // 人员Tag，按工日分配时显示工日数
                    val tagText = if (detail.salaryDistribution == "work_days") {
                        "${worker.nickname} ${worker.workdays ?: 1}工日"
                    } else {
                        worker.nickname
                    }
                    SalaryTag(
                        text = tagText,
                        backgroundColor = AppColors.Green400.copy(alpha = 0.12f),
                        textColor = AppColors.Green400
                    )
                }
            }
        }
    }
}

/**
 * 操作区 - 编辑工程按钮
 */
@Composable
fun ActionSection(projectId: Int, onEdit: (Int) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { onEdit(projectId) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Green400,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("编辑工程", fontSize = 14.sp)
            }
        }
    }
}

/**
 * 子项目表格表头 - 对齐Vue前端subproject-table thead
 */
@Composable
fun SubprojectTableHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Green50)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TableHeaderCell("序号", weight = 0.5f)
            TableHeaderCell("空间", weight = 1f)
            TableHeaderCell("方案", weight = 1f)
            TableHeaderCell("尺寸(米)", weight = 1f)
            TableHeaderCell("数量", weight = 0.6f)
            TableHeaderCell("金额", weight = 1f)
            TableHeaderCell("操作", weight = 1f)
        }
    }
}

/**
 * 子项目表格行 - 对齐Vue前端subproject-table tbody
 */
@Composable
fun SubprojectTableRow(
    index: Int,
    subproject: SubprojectUiModel,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (index % 2 == 0) Color.White else Color(0xFFFAFAFA)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TableCell("${index + 1}", weight = 0.5f)
            TableCell(subproject.spaceTypeName, weight = 1f)
            TableCell(subproject.constructionPlanName, weight = 1f)
            TableCell(
                "${formatNumber(subproject.length)} × ${formatNumber(subproject.width)}",
                weight = 1f
            )
            TableCell(formatNumber(subproject.quantity), weight = 0.6f)
            TableCell(AmountFormatter.format(subproject.amount), weight = 1f)
            // 操作按钮
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 编辑按钮
                OutlinedButton(
                    onClick = onEdit,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.Green400
                    )
                ) {
                    Text("编辑", fontSize = 11.sp)
                }
                // 转交按钮（禁用）
                OutlinedButton(
                    onClick = { },
                    enabled = false,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("转交", fontSize = 11.sp)
                }
                // 历史按钮（禁用）
                OutlinedButton(
                    onClick = { },
                    enabled = false,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("历史", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * 表头单元格
 */
@Composable
fun RowScope.TableHeaderCell(text: String, weight: Float) {
    Box(
        modifier = Modifier.weight(weight),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.TextPrimary
        )
    }
}

/**
 * 表格数据单元格
 */
@Composable
fun RowScope.TableCell(text: String, weight: Float) {
    Box(
        modifier = Modifier.weight(weight),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 子项目编辑弹窗 - 对齐Vue前端编辑弹窗设计
 * 包含：空间类型、施工方案、长宽、单价(只读)、计算预览、备注
 */
@Composable
fun SubprojectEditDialog(
    subproject: SubprojectUiModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    // 表单状态
    var spaceType by remember { mutableStateOf(subproject.spaceTypeName) }
    var constructionScheme by remember { mutableStateOf(subproject.constructionPlanName) }
    var length by remember { mutableStateOf(subproject.length.toString()) }
    var width by remember { mutableStateOf(subproject.width.toString()) }
    var remark by remember { mutableStateOf("") }

    // 计算面积（单位：米）
    val lengthValue = length.toDoubleOrNull() ?: 0.0
    val widthValue = width.toDoubleOrNull() ?: 0.0
    val area = lengthValue * widthValue

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Green50,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "编辑子项目",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        },
        titleContentColor = Color.White,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 空间类型（只读展示）
                OutlinedTextField(
                    value = spaceType,
                    onValueChange = { },
                    label = { Text("空间类型") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // 施工方案（只读展示）
                OutlinedTextField(
                    value = constructionScheme,
                    onValueChange = { },
                    label = { Text("施工方案") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // 长度
                OutlinedTextField(
                    value = length,
                    onValueChange = { length = it },
                    label = { Text("长度(米)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // 宽度
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it },
                    label = { Text("宽度(米)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // 计算预览
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, AppColors.Green100, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "${formatNumber(area)} m²",
                        fontSize = 13.sp,
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
                // 备注
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Green400,
                        contentColor = Color.White
                    )
                ) {
                    Text("保存")
                }
            }
        }
    )
}

/**
 * 附件管理区 - 查看附件按钮
 */
@Composable
fun AttachmentSection(fileCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            OutlinedButton(
                onClick = { /* TODO: 打开附件查看 */ },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.Green400
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("查看附件 ($fileCount)")
            }
        }
    }
}

/**
 * 修改历史条目 - 对齐Vue前端history-item设计
 * 左侧绿色边框、操作类型+时间、操作人、描述
 */
@Composable
fun HistoryItem(item: HistoryUiModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(3.dp, AppColors.Green400, RoundedCornerShape(0.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // 头部：操作类型 + 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.actionName.ifBlank { item.action },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = DateFormatter.formatDateTime(item.createdAt),
                    fontSize = 11.sp,
                    color = AppColors.TextTertiary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            // 操作人
            Text(
                text = "操作人：${item.nickname.ifBlank { item.username }}",
                fontSize = 12.sp,
                color = AppColors.TextSecondary
            )
            // 描述
            if (item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.Green50, RoundedCornerShape(4.dp))
                        .padding(6.dp, 8.dp)
                ) {
                    Text(
                        text = item.description,
                        fontSize = 12.sp,
                        color = AppColors.Green400,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * 格式化数字为两位小数
 */
private fun formatNumber(value: Double): String {
    return String.format("%.2f", value)
}

/**
 * 格式化数字字符串为两位小数
 */
private fun formatNumber(value: String): String {
    return try {
        String.format("%.2f", value.toDouble())
    } catch (e: Exception) {
        "0.00"
    }
}
