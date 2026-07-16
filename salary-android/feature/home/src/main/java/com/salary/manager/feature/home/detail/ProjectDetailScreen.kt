package com.salary.manager.feature.home.detail

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayCircle
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.salary.core.common.util.AmountFormatter
import com.salary.core.common.util.DateFormatter
import com.salary.core.design.component.ProjectStatusTag
import com.salary.core.design.component.SalaryTag
import com.salary.core.design.theme.AppColors
import com.salary.core.ui.state.UiState
import com.salary.manager.feature.home.dashboard.MediaViewerDialog

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
    /** 工程数据变更回调（编辑工程/删除附件/编辑子项目后触发），用于通知上层列表刷新 */
    onDataChanged: () -> Unit = {},
    viewModel: ProjectDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    // 监听成功消息：保存成功时通知上层刷新列表
    LaunchedEffect(viewModel.successMessage) {
        viewModel.successMessage?.let {
            onDataChanged()
        }
    }

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            // 自定义绿色渐变顶部栏，与状态栏融合
            // 原TopAppBar设置containerColor=Transparent导致状态栏区域为白色，与内容区绿色渐变不融合
            // 改用Box+statusBarsPadding，绿色渐变背景覆盖状态栏+标题栏，与GreenTopNavBar风格一致
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(AppColors.Green400, AppColors.Green600)
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "工程详情",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { padding ->
        // 滑动返回手势由 AppNavHost 中的 SwipeBackLayout 统一处理
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Background)
        ) {
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
                        viewModel = viewModel,
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
    viewModel: ProjectDetailViewModel,
    modifier: Modifier = Modifier
) {
    // 子项目编辑弹窗状态
    var editingSubproject by remember { mutableStateOf<SubprojectUiModel?>(null) }
    // 附件查看弹窗状态
    var showAttachmentDialog by remember { mutableStateOf(false) }
    // 编辑工程弹窗状态
    var showEditProjectDialog by remember { mutableStateOf(false) }
    // 修改历史展开/收起状态
    var historyExpanded by remember { mutableStateOf(false) }

    // 当前用户角色（仅施工员可编辑工程/子项目/删除附件，admin/documenter 只读）
    val userRole by viewModel.userRole.collectAsState()
    val canEdit = userRole == "constructor"

    // 打开编辑工程弹窗时加载施工人员列表
    LaunchedEffect(showEditProjectDialog) {
        if (showEditProjectDialog) {
            viewModel.loadConstructors()
        }
    }

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

        // ===== 操作区（含编辑工程 + 查看附件，水平排列） =====
        item {
            SectionTitle("操作")
        }
        item {
            ActionSection(
                projectId = projectId,
                onEdit = { showEditProjectDialog = true },
                fileCount = detail.files.size,
                onViewAttachment = { showAttachmentDialog = true },
                canEdit = canEdit
            )
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
                SubprojectTable(
                    subprojects = detail.subprojects,
                    onEdit = { sub -> editingSubproject = sub },
                    canEdit = canEdit
                )
            }
        }

        // ===== 修改历史区（流式展示，无外层边框） =====
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
            // 使用历史记录id作为稳定唯一key，提升列表复用性能
            items(displayHistory, key = { it.id }) { item ->
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
            saving = viewModel.savingSubproject.collectAsState().value,
            onDismiss = { editingSubproject = null },
            onConfirm = { lengthMeter, widthMeter, remark ->
                viewModel.updateSubproject(
                    projectId = projectId,
                    subprojectId = sub.id,
                    lengthMeter = lengthMeter,
                    widthMeter = widthMeter,
                    remark = remark
                )
                editingSubproject = null
            }
        )
    }

    // 附件查看弹窗
    if (showAttachmentDialog) {
        AttachmentViewDialog(
            files = detail.files,
            onDelete = { fileId -> viewModel.deleteFile(projectId, fileId) },
            canDelete = canEdit,
            onDismiss = { showAttachmentDialog = false }
        )
    }

    // 编辑工程弹窗
    if (showEditProjectDialog) {
        EditProjectDialog(
            detail = detail,
            constructors = viewModel.constructors.collectAsState().value,
            saving = viewModel.savingProject.collectAsState().value,
            onDismiss = { showEditProjectDialog = false },
            onConfirm = { name, remark, status, salaryDistribution, constructorIds, workerWorkdays ->
                viewModel.updateProject(
                    projectId = projectId,
                    name = name,
                    remark = remark,
                    status = status,
                    salaryDistribution = salaryDistribution,
                    constructorIds = constructorIds,
                    workerWorkdays = workerWorkdays
                )
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 按工日分配时，在施工人员上方显示工日汇总信息
                if (detail.salaryDistribution == "work_days") {
                    WorkdaysSummaryRow(detail.workers)
                }

                // 施工人员Tag列表
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.workers.forEach { worker ->
                        // 人员Tag，按工日分配时显示工日数
                        val tagText = if (detail.salaryDistribution == "work_days") {
                            // workdays为Double，显示时取整
                            val days = worker.workdays?.toInt() ?: 1
                            "${worker.nickname} ${days}工日"
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
}

/**
 * 工日汇总信息行
 *
 * 按工日分配模式下，显示在施工人员Tag列表上方的摘要信息：
 * - 总工日
 * - 各施工人员的工日明细（昵称: 工日）
 *
 * @param workers 施工人员列表
 */
@Composable
private fun WorkdaysSummaryRow(workers: List<WorkerUiModel>) {
    // 计算总工日
    val totalWorkdays = workers.sumOf { it.workdays ?: 0.0 }
    // 工日明细
    val detailText = workers.joinToString("、") { worker ->
        val days = worker.workdays?.toInt() ?: 0
        "${worker.nickname}: ${days}工日"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = AppColors.Green50
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "按工日分配",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.Green600
                )
                Text(
                    text = "总工日: ${totalWorkdays.toInt()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Green400
                )
            }
            Text(
                text = detailText,
                fontSize = 11.sp,
                color = AppColors.TextTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 操作区 - 编辑工程按钮 + 查看附件按钮，水平排列（操作左，附件右）
 * @param canEdit 是否可编辑工程（仅施工员可编辑，admin/documenter 隐藏编辑按钮）
 */
@Composable
fun ActionSection(
    projectId: Int,
    onEdit: (Int) -> Unit,
    fileCount: Int,
    onViewAttachment: () -> Unit,
    canEdit: Boolean = true
) {
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：编辑工程按钮（仅施工员可见）
            if (canEdit) {
                Button(
                    onClick = { onEdit(projectId) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Green400,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("编辑工程", fontSize = 14.sp)
                }
            }
            // 右侧：查看附件按钮（所有角色可查看）
            OutlinedButton(
                onClick = onViewAttachment,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.Green400
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("附件管理 ($fileCount)", fontSize = 14.sp)
            }
        }
    }
}

/**
 * 子项目表格组件 - 对齐主页历史记录的表格样式
 *
 * 支持水平滚动：表头与表体使用固定列宽，总宽度超过容器可用宽度时可左右滑动查看。
 * 列宽方案：序号40 + 空间80 + 方案100 + 尺寸110 + 数量80 + 金额90 + 操作48 = 548dp
 * 边框线规范：表头顶部和底部为深绿#4ADE80+1.5dp，数据行底部为中绿#86EFAC+1dp，最后一行底部为深绿+1.5dp
 *
 * @param subprojects 子项目列表
 * @param onEdit 点击编辑按钮回调，参数为子项目UI模型
 * @param canEdit 是否可编辑子项目（仅施工员可编辑，admin/documenter 隐藏操作列）
 */
@Composable
fun SubprojectTable(
    subprojects: List<SubprojectUiModel>,
    onEdit: (SubprojectUiModel) -> Unit,
    canEdit: Boolean = true
) {
    val scrollState = rememberScrollState()
    // 固定总宽度，超过容器宽度时启用水平滚动（可编辑时含操作列48dp，否则不含）
    val tableWidth = if (canEdit) 548.dp else 500.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        // ===== 表头（固定宽度，浅灰底+主色顶/底线） =====
        Row(
            modifier = Modifier
                .width(tableWidth)
                .background(AppColors.NeutralSurface)
                .drawBehind {
                    // 表头顶部水平边框线（主色，加粗）
                    drawLine(
                        color = AppColors.Green400,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    // 表头底部水平边框线（主色，加粗，区分表头与表体）
                    drawLine(
                        color = AppColors.Green400,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
                .padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SubprojectHeaderCell("序号", 40.dp)
            SubprojectHeaderCell("空间", 80.dp)
            SubprojectHeaderCell("方案", 100.dp)
            SubprojectHeaderCell("尺寸(米)", 110.dp)
            SubprojectHeaderCell("数量", 80.dp)
            SubprojectHeaderCell("金额", 90.dp)
            // 操作列表头（仅施工员可见）
            if (canEdit) {
                SubprojectHeaderCell("操作", 48.dp)
            }
        }

        // ===== 表体（与表头对齐，相同列宽） =====
        subprojects.forEachIndexed { index, sub ->
            val isLastRow = index == subprojects.lastIndex
            Row(
                modifier = Modifier
                    .width(tableWidth)
                    .drawBehind {
                        // 数据行底部水平边框线：行间用中绿，末行用主色收尾
                        drawLine(
                            color = if (isLastRow) AppColors.Green400 else AppColors.OutlineVariant,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = if (isLastRow) 1.5.dp.toPx() else 1.dp.toPx()
                        )
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubprojectCell("${index + 1}", 40.dp)
                SubprojectCell(sub.spaceTypeName, 80.dp)
                SubprojectCell(sub.constructionPlanName, 100.dp)
                // 数据库存储厘米，UI显示时除以100转为米（与表头"尺寸(米)"单位一致）
                SubprojectCell(
                    "${formatNumber(sub.length / 100.0)} × ${formatNumber(sub.width / 100.0)}",
                    110.dp
                )
                SubprojectCell(formatNumber(sub.quantity), 80.dp)
                SubprojectCell(AmountFormatter.format(sub.amount), 90.dp, color = AppColors.Green400)
                // 操作列：编辑图标按钮（仅施工员可见）
                if (canEdit) {
                    Box(
                        modifier = Modifier.width(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { onEdit(sub) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑子项目",
                                tint = AppColors.Green400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 表头单元格（固定宽度）
 */
@Composable
private fun RowScope.SubprojectHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.TextPrimary,
        modifier = Modifier.width(width)
    )
}

/**
 * 表体单元格（固定宽度，超长省略）
 */
@Composable
private fun RowScope.SubprojectCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    color: Color = AppColors.TextPrimary
) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(width)
    )
}

/**
 * 子项目编辑弹窗 - 对齐Vue前端编辑弹窗设计
 * 包含：空间类型、施工方案、长宽、单价(只读)、计算预览、备注
 * 注意：弹窗内长度/宽度以米为单位展示和编辑，保存时由ViewModel转换为厘米
 */
@Composable
fun SubprojectEditDialog(
    subproject: SubprojectUiModel,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (lengthMeter: Double, widthMeter: Double, remark: String?) -> Unit
) {
    // 表单状态（数据库存厘米，弹窗显示米，需除以100）
    var spaceType by remember { mutableStateOf(subproject.spaceTypeName) }
    var constructionScheme by remember { mutableStateOf(subproject.constructionPlanName) }
    var length by remember { mutableStateOf(formatNumber(subproject.length / 100.0)) }
    var width by remember { mutableStateOf(formatNumber(subproject.width / 100.0)) }
    var remark by remember { mutableStateOf("") }

    // 计算面积（单位：米）
    val lengthValue = length.toDoubleOrNull() ?: 0.0
    val widthValue = width.toDoubleOrNull() ?: 0.0
    val area = lengthValue * widthValue

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(12.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "编辑子项目",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
            }
        },
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
                // 长度（米）
                OutlinedTextField(
                    value = length,
                    onValueChange = { length = it },
                    label = { Text("长度(米)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // 宽度（米）
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
                        .background(AppColors.Green50, RoundedCornerShape(8.dp))
                        .border(1.dp, AppColors.Green100, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "${formatNumber(area)} m²",
                        fontSize = 13.sp,
                        color = AppColors.Green400,
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
                OutlinedButton(onClick = onDismiss, enabled = !saving) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        onConfirm(lengthValue, widthValue, remark.ifBlank { null })
                    },
                    enabled = !saving && lengthValue > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Green400,
                        contentColor = Color.White
                    )
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("保存")
                    }
                }
            }
        }
    )
}

/**
 * 判断是否为图片类型
 */
private fun isImageType(type: String?): Boolean =
    type?.startsWith("image/") == true

/**
 * 判断是否为视频类型
 */
private fun isVideoType(type: String?): Boolean =
    type?.startsWith("video/") == true

/**
 * 判断是否为媒体文件（图片或视频）
 */
private fun isMediaFile(type: String?): Boolean =
    isImageType(type) || isVideoType(type)

/**
 * 附件管理弹窗 - 宽度占屏幕98%，支持媒体直接预览和删除
 *
 * 改造点：
 * 1. 使用Dialog + usePlatformDefaultWidth=false，宽度fillMaxWidth(0.98f)
 * 2. 媒体文件（图片/视频）直接在弹窗内预览，点击可全屏查看
 * 3. 每个附件项右上角增加删除按钮，二次确认后删除
 *
 * @param files 附件列表（fileUrl已为完整URL）
 * @param onDelete 删除附件回调，参数为文件ID（由调用方闭包捕获projectId）
 * @param canDelete 是否可删除附件（仅施工员可删除，admin/documenter 隐藏删除按钮）
 * @param onDismiss 关闭弹窗回调
 */
@Composable
fun AttachmentViewDialog(
    files: List<FileUiModel>,
    onDelete: (Int) -> Unit,
    canDelete: Boolean = true,
    onDismiss: () -> Unit
) {
    // 待全屏预览的媒体文件（null表示不预览）
    var previewFile by remember { mutableStateOf<FileUiModel?>(null) }
    // 待删除的文件（null表示不显示删除确认弹窗）
    var deletingFile by remember { mutableStateOf<FileUiModel?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.98f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                // ===== 标题栏（保留左右内边距） =====
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "附件管理 (${files.size})",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.TextPrimary
                    )
                    if (files.isNotEmpty()) {
                        Text(
                            "共 ${files.size} 个",
                            fontSize = 12.sp,
                            color = AppColors.TextTertiary
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    thickness = 1.dp,
                    color = AppColors.Green100
                )

                // ===== 内容区（媒体预览占满弹窗宽度） =====
                if (files.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无附件", color = AppColors.TextTertiary, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(files, key = { it.id }) { file ->
                            AttachmentItemRow(
                                file = file,
                                onClick = {
                                    // 媒体文件点击进入全屏预览
                                    if (isMediaFile(file.type)) {
                                        previewFile = file
                                    }
                                },
                                onDelete = { deletingFile = file },
                                canDelete = canDelete
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 关闭按钮（保留左右内边距） =====
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 16.dp)
                ) {
                    Text("关闭", color = AppColors.Green400, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // 全屏媒体预览弹窗
    previewFile?.let { file ->
        MediaViewerDialog(
            fileUrl = file.fileUrl,
            fileName = file.fileName,
            fileType = file.type,
            onDismiss = { previewFile = null }
        )
    }

    // 删除确认弹窗（二次确认避免误删）
    deletingFile?.let { file ->
        AlertDialog(
            onDismissRequest = { deletingFile = null },
            title = {
                Text(
                    "删除附件",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "确认删除附件「${file.fileName}」吗？\n删除后不可恢复。",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(file.id)
                        deletingFile = null
                    }
                ) {
                    Text("确认删除", color = Color(0xFFE53935), fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFile = null }) {
                    Text("取消", color = AppColors.TextSecondary)
                }
            }
        )
    }
}

/**
 * 附件列表项 - 纵向Column布局
 *
 * - 文件信息行（图标+文件名+删除按钮）：保留horizontal padding，与标题栏对齐
 * - 媒体文件预览：宽度占满弹窗（无horizontal padding），高度按原始长宽比自适应；点击进入全屏预览
 * - 非媒体文件：仅显示文件图标+文件名+大小+上传时间
 *
 * @param file 附件UI模型
 * @param onClick 点击附件内容区域回调（媒体文件用于打开全屏预览）
 * @param onDelete 点击删除按钮回调
 * @param canDelete 是否可删除附件（仅施工员可删除，admin/documenter 隐藏删除按钮）
 */
@Composable
fun AttachmentItemRow(
    file: FileUiModel,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    canDelete: Boolean = true
) {
    val context = LocalContext.current
    val isMedia = isMediaFile(file.type)
    val isVideo = isVideoType(file.type)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ===== 顶部行：文件信息（左）+ 删除按钮（右），保留horizontal padding =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 文件类型图标
            Icon(
                imageVector = if (isImageType(file.type)) {
                    Icons.Default.Image
                } else if (isVideo) {
                    Icons.Default.PlayCircle
                } else {
                    Icons.Default.Description
                },
                contentDescription = null,
                tint = AppColors.Green400,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 文件名 + 大小/时间
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.fileName,
                    fontSize = 13.sp,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "大小：${formatFileSize(file.fileSize)}  上传：${DateFormatter.formatDate(file.uploadedAt)}",
                    fontSize = 11.sp,
                    color = AppColors.TextTertiary
                )
            }
            // 删除按钮（红色垃圾桶图标，仅施工员可见）
            if (canDelete) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除附件",
                        tint = Color(0xFFE53935),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // ===== 媒体预览区（仅媒体文件显示，宽度占满弹窗） =====
        if (isMedia) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .clickable(enabled = true, onClick = onClick)
            ) {
                // 图片/视频缩略图：宽度占满弹窗（无horizontal padding），高度按原始长宽比自适应
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(file.fileUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = file.fileName,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )

                // 视频文件叠加播放图标（居中）
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "播放视频",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    // 右下角"视频"标签
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp),
                        color = Color(0x88000000),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "视频",
                            fontSize = 10.sp,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 编辑工程弹窗
 *
 * 支持编辑：
 * - 工程名称、工程备注、工程状态
 * - 工资分配方式（平均分配 / 按工日分配）
 * - 施工人员多选（按工日分配模式下显示工日输入框）
 *
 * 保存时调用 onConfirm 回调，由 ViewModel 调用 updateProject 接口
 *
 * @param detail 当前工程详情（提供默认值）
 * @param constructors 可选施工人员列表（由 ViewModel 异步加载）
 * @param saving 是否保存中
 * @param onDismiss 关闭弹窗回调
 * @param onConfirm 保存回调，参数：名称、备注、状态、分配方式、施工人员ID列表、工日映射
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProjectDialog(
    detail: ProjectDetailUiModel,
    constructors: List<com.salary.core.network.api.UserDto>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        remark: String?,
        status: String,
        salaryDistribution: String,
        constructorIds: List<Int>,
        workerWorkdays: Map<Int, String>
    ) -> Unit
) {
    // 表单状态（默认值取自当前工程详情）
    var name by remember { mutableStateOf(detail.name) }
    var remark by remember { mutableStateOf(detail.remark ?: "") }
    var status by remember { mutableStateOf(detail.status) }
    var salaryDistribution by remember { mutableStateOf(detail.salaryDistribution ?: "average") }
    // 选中的施工人员ID集合（默认勾选当前工程的施工人员）
    var selectedConstructorIds by remember {
        mutableStateOf(detail.workers.map { it.userId }.toSet())
    }
    // 工日输入值（userId → 工日字符串），默认值取自当前工程的 workdays
    var workerWorkdays by remember {
        mutableStateOf(
            detail.workers.associate { it.userId to (it.workdays?.toString() ?: "") }
        )
    }
    // 总工日校验输入值（为空时不校验；有值时校验各施工人员工日之和是否等于此值）
    var totalWorkdaysInput by remember { mutableStateOf("") }
    // 总工日校验结果提示（空字符串表示无提示）
    var workdaysValidationHint by remember { mutableStateOf("") }

    /**
     * 校验各施工人员工日之和是否等于总工日输入值
     * - 总工日为空时不校验
     * - 差值<=0.01视为一致
     */
    fun validateWorkdays() {
        if (salaryDistribution != "work_days") {
            workdaysValidationHint = ""
            return
        }
        val input = totalWorkdaysInput.trim()
        if (input.isEmpty()) {
            workdaysValidationHint = ""
            return
        }
        val targetTotal = input.toDoubleOrNull()
        if (targetTotal == null || targetTotal <= 0) {
            workdaysValidationHint = "总工日输入无效"
            return
        }
        if (selectedConstructorIds.isEmpty()) {
            workdaysValidationHint = ""
            return
        }
        val sum = selectedConstructorIds.sumOf { id ->
            val v = workerWorkdays[id]?.trim()
            val parsed = v?.toDoubleOrNull()
            if (parsed != null && parsed > 0) parsed else 1.0
        }
        val diff = kotlin.math.abs(sum - targetTotal)
        val sumStr = String.format("%.2f", sum)
        val targetStr = String.format("%.2f", targetTotal)
        workdaysValidationHint = if (diff > 0.01) {
            "工日合计 $sumStr 与总工日 $targetStr 不一致"
        } else {
            "工日合计 $sumStr 与总工日一致 ✓"
        }
    }

    // 工程状态选项
    val statusOptions = listOf(
        "preparing" to "备料中",
        "constructing" to "施工中",
        "completed" to "已完工",
        "canceled" to "已取消"
    )
    // 工资分配方式选项
    val distributionOptions = listOf(
        "average" to "平均分配",
        "work_days" to "按工日分配"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // ===== 标题 =====
                Text(
                    "编辑工程",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 滚动表单区
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ===== 工程名称 =====
                    Column {
                        Text("工程名称", fontSize = 13.sp, color = AppColors.TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            placeholder = { Text("请输入工程名称", fontSize = 14.sp) }
                        )
                    }

                    // ===== 工程状态 =====
                    Column {
                        Text("工程状态", fontSize = 13.sp, color = AppColors.TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            statusOptions.forEach { (value, label) ->
                                StatusChip(
                                    text = label,
                                    selected = status == value,
                                    onClick = { status = value }
                                )
                            }
                        }
                    }

                    // ===== 工程备注 =====
                    Column {
                        Text("工程备注", fontSize = 13.sp, color = AppColors.TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = remark,
                            onValueChange = { remark = it },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3,
                            shape = RoundedCornerShape(8.dp),
                            placeholder = { Text("请输入工程备注（可选）", fontSize = 14.sp) }
                        )
                    }

                    // ===== 工资分配方式 =====
                    Column {
                        Text("工资分配方式", fontSize = 13.sp, color = AppColors.TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            distributionOptions.forEach { (value, label) ->
                                StatusChip(
                                    text = label,
                                    selected = salaryDistribution == value,
                                    onClick = { salaryDistribution = value }
                                )
                            }
                        }
                    }

                    // ===== 施工人员多选 =====
                    Column {
                        Text("施工人员", fontSize = 13.sp, color = AppColors.TextSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (constructors.isEmpty()) {
                            Text(
                                "正在加载施工人员列表...",
                                fontSize = 12.sp,
                                color = AppColors.TextTertiary
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                constructors.forEach { worker ->
                                    val selected = worker.id in selectedConstructorIds
                                    // 方形标签式选择器：选中时绿色实底，未选中时浅绿描边
                                    Surface(
                                        onClick = {
                                            selectedConstructorIds = if (selected) {
                                                selectedConstructorIds - worker.id
                                            } else {
                                                selectedConstructorIds + worker.id
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (selected) AppColors.Green400 else Color.White,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (selected) AppColors.Green400 else AppColors.Green200
                                        )
                                    ) {
                                        Text(
                                            text = worker.nickname,
                                            fontSize = 13.sp,
                                            color = if (selected) Color.White else AppColors.Green400,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ===== 按工日分配模式：工日输入（与主页样式一致）=====
                    // 布局：工日设置标题 → 总工日输入框 → 校验提示 → 每行3个施工人员等宽
                    if (salaryDistribution == "work_days" && selectedConstructorIds.isNotEmpty()) {
                        val selectedWorkers = constructors.filter { it.id in selectedConstructorIds }
                        Column {
                            Text(
                                text = "工日设置（每人默认1工日）",
                                fontSize = 13.sp,
                                color = AppColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            // 总工日输入框：独立一行，占满容器宽度（位于工日设置上方）
                            // 为空时不校验；有值时校验各施工人员工日之和是否等于此值
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "总工日",
                                    fontSize = 13.sp,
                                    color = AppColors.TextSecondary
                                )
                                OutlinedTextField(
                                    value = totalWorkdaysInput,
                                    onValueChange = { value ->
                                        // 仅允许数字和小数点
                                        val filtered = value.filter { it.isDigit() || it == '.' }
                                        totalWorkdaysInput = filtered
                                        validateWorkdays()
                                    },
                                    placeholder = {
                                        Text(
                                            "输入总工数进行校验（可选）",
                                            fontSize = 12.sp,
                                            color = AppColors.TextTertiary
                                        )
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Decimal
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.End
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = AppColors.Green400,
                                        unfocusedBorderColor = AppColors.Outline
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                Text(
                                    text = "天",
                                    fontSize = 13.sp,
                                    color = AppColors.TextSecondary
                                )
                            }
                            // 校验结果提示
                            // 一致时：绿色常规字号
                            // 不一致时：橙色加大字号+加粗+闪烁动画（alpha 0.4↔1.0），提示用户注意
                            if (workdaysValidationHint.isNotEmpty()) {
                                val hint = workdaysValidationHint
                                // 注意：不能使用contains("一致")，因为"不一致"也包含"一致"二字
                                val isConsistent = !hint.contains("不一致")
                                // 不一致时使用无限循环透明度动画
                                val infiniteTransition = rememberInfiniteTransition(label = "workdaysHint")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(durationMillis = 700),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "hintAlpha"
                                )
                                Text(
                                    text = hint,
                                    fontSize = if (isConsistent) 11.sp else 14.sp,
                                    fontWeight = if (isConsistent) FontWeight.Normal else FontWeight.Bold,
                                    color = if (isConsistent) AppColors.Green400 else Color(0xFFE6A23C),
                                    modifier = Modifier
                                        .padding(start = 4.dp, top = 2.dp)
                                        .graphicsLayer {
                                            this.alpha = if (isConsistent) 1.0f else alpha
                                        }
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            // 按每行3个分组，使用Row+weight实现等宽占满容器
                            selectedWorkers.chunked(3).forEach { rowWorkers ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowWorkers.forEach { worker ->
                                        // 每个施工人员一个等宽标签：姓名 + 工日输入框
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = AppColors.Green50,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                AppColors.Green200
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(
                                                    horizontal = 6.dp,
                                                    vertical = 6.dp
                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = worker.nickname,
                                                    fontSize = 12.sp,
                                                    color = AppColors.Green700,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                // 工日输入框：仅允许数字和小数点
                                                val workdayValue = workerWorkdays[worker.id] ?: ""
                                                OutlinedTextField(
                                                    value = workdayValue,
                                                    onValueChange = { value ->
                                                        // 仅允许数字和小数点
                                                        val filtered = value.filter { it.isDigit() || it == '.' }
                                                        workerWorkdays = workerWorkdays + (worker.id to filtered)
                                                        validateWorkdays()
                                                    },
                                                    placeholder = {
                                                        Text(
                                                            "1",
                                                            fontSize = 12.sp,
                                                            color = AppColors.TextTertiary
                                                        )
                                                    },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Decimal
                                                    ),
                                                    modifier = Modifier
                                                        .width(56.dp)
                                                        .heightIn(min = 48.dp),
                                                    textStyle = androidx.compose.ui.text.TextStyle(
                                                        fontSize = 14.sp,
                                                        textAlign = TextAlign.Center
                                                    ),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor = Color.White,
                                                        unfocusedContainerColor = Color.White,
                                                        focusedBorderColor = AppColors.Green400,
                                                        unfocusedBorderColor = AppColors.Green200
                                                    ),
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = "天",
                                                    fontSize = 11.sp,
                                                    color = AppColors.TextTertiary
                                                )
                                            }
                                        }
                                    }
                                    // 不足3个时用空占位填充，保持每行等宽对齐
                                    repeat(3 - rowWorkers.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 按钮区：取消 + 保存 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = AppColors.TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onConfirm(
                                name.trim(),
                                remark.trim().ifBlank { null },
                                status,
                                salaryDistribution,
                                selectedConstructorIds.toList(),
                                workerWorkdays
                            )
                        },
                        enabled = !saving && name.isNotBlank() && selectedConstructorIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Green400,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (saving) "保存中..." else "保存", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * 状态选择标签（胶囊样式）
 * 选中：绿色实底+白字；未选中：白底+绿色描边+绿字
 */
@Composable
private fun StatusChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AppColors.Green400 else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) AppColors.Green400 else AppColors.Green200
        )
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (selected) Color.White else AppColors.Green400,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * 格式化文件大小
 */
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
        else -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
    }
}

/**
 * 修改历史条目 - 流式展示，无外层边框
 * 左侧绿色竖线 + 操作类型+时间、操作人、描述
 */
@Composable
fun HistoryItem(item: HistoryUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 左侧绿色竖线（时间轴样式）
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(48.dp)
                .background(AppColors.Green400, RoundedCornerShape(1.5.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        // 右侧内容
        Column(
            modifier = Modifier.weight(1f)
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
            Spacer(modifier = Modifier.height(2.dp))
            // 操作人
            Text(
                text = "操作人：${item.nickname.ifBlank { item.username }}",
                fontSize = 12.sp,
                color = AppColors.TextSecondary
            )
            // 描述
            if (item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
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
