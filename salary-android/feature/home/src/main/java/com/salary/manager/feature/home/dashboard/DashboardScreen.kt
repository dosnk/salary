package com.salary.manager.feature.home.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Upload
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.salary.core.network.dto.FileDto
import com.salary.core.network.interceptor.LatencyTracker
import kotlinx.coroutines.launch

/**
 * 工作台页面 - 复刻Vue前端Dashboard设计
 * 包含：顶部导航栏、工程创建表单、工程历史列表、底部版权
 *
 * @param onNavigateToProject 点击工程卡片时导航到工程详情
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onNavigateToProject: (Int) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    latencyTracker: LatencyTracker? = null,
    userNickname: String = ""
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 服务器在线状态和时延
    val isOnline by latencyTracker?.isOnline?.collectAsState() ?: remember { mutableStateOf(true) }
    val latencyMs by latencyTracker?.latencyMs?.collectAsState() ?: remember { mutableStateOf(0L) }
    val lastError by latencyTracker?.lastError?.collectAsState() ?: remember { mutableStateOf(null) }

    // 弹窗状态
    var showSpaceTypeDialog by remember { mutableStateOf(false) }
    var showSchemeDialog by remember { mutableStateOf(false) }
    var showMonthDialog by remember { mutableStateOf(false) }

    // 文件选择器：监听pendingUploadProjectId变化触发，支持多选
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadAttachments(uris)
        } else {
            viewModel.cancelUpload()
        }
    }
    LaunchedEffect(uiState.pendingUploadProjectId) {
        uiState.pendingUploadProjectId?.let {
            filePickerLauncher.launch("*/*")
        }
    }

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

    // 用于启动 Intent 打开附件 URL
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ===== 媒体查看器状态（图片/视频内置预览） =====
    var viewingMediaUrl by remember { mutableStateOf<String?>(null) }
    var viewingMediaName by remember { mutableStateOf("") }
    var viewingMediaType by remember { mutableStateOf<String?>(null) }

    // ===== 附件完整URL预计算（网格缩略图加载用，避免每个缩略图都异步请求） =====
    var fileUrls by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(uiState.viewingFiles) {
        if (uiState.viewingFiles.isEmpty()) {
            fileUrls = emptyMap()
        } else {
            val urls = mutableMapOf<Int, String>()
            uiState.viewingFiles.forEach { file ->
                urls[file.id] = viewModel.buildFileUrl(file.fileUrl)
            }
            fileUrls = urls
        }
    }

    // ===== 附件网格浏览弹窗（媒体文件直接展示缩略图，参考微信样式） =====
    if (uiState.viewingFilesProjectId != null) {
        AttachmentGridDialog(
            projectName = uiState.viewingFilesProjectName,
            files = uiState.viewingFiles,
            fileUrls = fileUrls,
            isLoading = uiState.isLoadingFiles,
            onDismiss = { viewModel.closeAttachmentList() },
            onMediaClick = { fullUrl, fileName, fileType ->
                // 媒体文件：用内置 MediaViewerDialog 预览
                viewingMediaUrl = fullUrl
                viewingMediaName = fileName
                viewingMediaType = fileType
            },
            onFileClick = { file ->
                // 非媒体文件：用系统应用打开
                scope.launch {
                    val fullUrl = fileUrls[file.id] ?: viewModel.buildFileUrl(file.fileUrl)
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(android.net.Uri.parse(fullUrl), file.type ?: "*/*")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // 没有可用应用打开该类型文件时，回退为用浏览器打开
                        val browserIntent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(fullUrl)
                        ).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(browserIntent)
                        } catch (_: Exception) {
                            // 静默处理
                        }
                    }
                }
            }
        )
    }

    // ===== 媒体查看器弹窗（图片缩放/视频播放） =====
    viewingMediaUrl?.let { url ->
        MediaViewerDialog(
            fileUrl = url,
            fileName = viewingMediaName,
            fileType = viewingMediaType,
            onDismiss = {
                viewingMediaUrl = null
                viewingMediaType = null
            }
        )
    }

    // ===== 上传进度弹窗（多文件上传时实时显示） =====
    uiState.uploadProgress?.let { progress ->
        UploadProgressDialog(progress = progress)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ===== 顶部导航栏：绿色渐变背景，高度自适应内容 =====
            GreenTopNavBar(
                title = "三人行装修管理系统",
                userNickname = userNickname.ifBlank { uiState.userNickname }.ifBlank { "未登录" },
                unreadCount = uiState.unreadCount
            )

            // ===== 可滚动内容区域 =====
            // 外层水平padding设为4dp（原8dp），使工程历史卡片宽度约占屏幕98%
            // 表单Card单独补偿4dp水平padding，保持原有视觉边距
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .background(AppColors.Background)
                    .padding(horizontal = 4.dp)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // ===== 工程创建表单 Card =====
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        }

                        // 施工人员选择（方形Checkbox标签，FlowRow自动换行，一行约7个）
                        if (uiState.constructors.isNotEmpty()) {
                            Column {
                                Text(
                                    text = "施工人员",
                                    fontSize = 14.sp,
                                    color = AppColors.TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                // 使用FlowRow实现自动换行，一行约7个
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    uiState.constructors.forEach { worker ->
                                        val isSelected = uiState.selectedConstructorIds.contains(worker.id)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    if (isSelected) AppColors.Green50
                                                    else AppColors.NeutralSurface
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) AppColors.Green400
                                                    else AppColors.Outline,
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .clickable { viewModel.toggleConstructor(worker.id) }
                                                .padding(horizontal = 6.dp, vertical = 3.dp),
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            // 缩小方形复选框图标
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(
                                                        color = if (isSelected) AppColors.Green400
                                                        else Color.Transparent,
                                                        shape = RoundedCornerShape(2.dp)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) AppColors.Green400
                                                        else AppColors.NeutralBorder,
                                                        shape = RoundedCornerShape(2.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSelected) {
                                                    // 使用矢量图标替代文字"✓"，在小尺寸下依然清晰可见
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(10.dp),
                                                        tint = Color.White
                                                    )
                                                }
                                            }
                                            Text(
                                                text = worker.nickname,
                                                fontSize = 12.sp,
                                                color = if (isSelected) AppColors.Green400
                                                else AppColors.TextPrimary
                                            )
                                        }
                                    }
                                }

                                // 按工日分配模式下显示工日输入区（仅已选施工人员）
                                if (uiState.salaryDistribution == "work_days" &&
                                    uiState.selectedConstructorIds.isNotEmpty()
                                ) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "工日设置",
                                        fontSize = 13.sp,
                                        color = AppColors.TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    // 已选施工人员的工日输入列表（每行一个，姓名+工日输入框）
                                    uiState.constructors.filter {
                                        uiState.selectedConstructorIds.contains(it.id)
                                    }.forEach { worker ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = worker.nickname,
                                                fontSize = 13.sp,
                                                color = AppColors.TextPrimary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            // 工日输入框：小宽度，仅允许数字和小数点
                                            // 默认值为空，点击直接输入；空值在计算和保存时按1工日处理
                                            // 压缩高度：移除label改用placeholder，限制固定高度
                                            val workdayValue = uiState.workerWorkdays[worker.id] ?: ""
                                            OutlinedTextField(
                                                value = workdayValue,
                                                onValueChange = { newValue: String ->
                                                    viewModel.updateWorkerWorkdays(worker.id, newValue)
                                                },
                                                placeholder = {
                                                    Text("工日(1)", fontSize = 12.sp)
                                                },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Decimal
                                                ),
                                                modifier = Modifier
                                                    .width(110.dp)
                                                    .height(40.dp),
                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                    fontSize = 13.sp,
                                                    textAlign = TextAlign.End
                                                ),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = AppColors.Green400
                                                ),
                                                shape = RoundedCornerShape(6.dp)
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

                        // 工程备注（压缩高度：限制最多2行，减少垂直占用）
                        OutlinedTextField(
                            value = uiState.remark,
                            onValueChange = { viewModel.updateRemark(it) },
                            label = { Text("工程备注") },
                            placeholder = { Text("请输入工程备注") },
                            maxLines = 2,
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

                // ===== 工程历史区域（合并外层双重Column，减少嵌套层级） =====
                // 水平padding设为0，直接填满外层Column可用宽度，使工程历史卡片宽度约占屏幕98%
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    // 标题行：工程历史（深色大字+下方绿色短线，建立区域权威感）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "工程历史",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.TextPrimary
                        )
                        // 年月选择器：胶囊按钮（浅绿底+绿描边+日历图标+绿色文字+下拉箭头，三重视觉提示可点击）
                        Surface(
                            onClick = { showMonthDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            color = AppColors.Green50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Green200)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = AppColors.Green400
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatYearMonth(uiState.selectedYearMonth),
                                    color = AppColors.Green400,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = AppColors.Green400
                                )
                            }
                        }
                    }

                    // 区域标题下方绿色短线，强化区域标识
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 8.dp)
                            .width(32.dp)
                            .height(2.dp)
                            .background(AppColors.Green400, RoundedCornerShape(1.dp))
                    )

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
                        // 工程卡片列表（卡片不跳转详情页，仅展示信息）
                        uiState.projects.forEach { project ->
                            ProjectHistoryCard(
                                project = project,
                                viewModel = viewModel
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ===== 底部版权 + 服务器状态（版权左，状态右对齐）=====
                // 补偿4dp水平padding，保持与表单Card一致的视觉边距
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：版权信息
                    Text(
                        text = "©微信群：三人行必有我师",
                        fontSize = 12.sp,
                        color = AppColors.TextSecondary
                    )
                    // 右侧：服务器在线状态和时延
                    // 三种状态：检测中（灰）/在线（绿/橙/红按延迟）/离线（红）
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 检测中状态：isOnline=true && latencyMs=0 && lastError=null
                        // HealthMonitor刚启动尚未收到响应时的初始状态
                        val isChecking = isOnline && latencyMs == 0L && lastError == null
                        Icon(
                            imageVector = if (isChecking) Icons.Default.Info else if (isOnline) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = if (isChecking) "检测中" else if (isOnline) "在线" else "离线",
                            modifier = Modifier.size(14.dp),
                            tint = when {
                                isChecking -> AppColors.TextTertiary
                                isOnline -> AppColors.Success
                                else -> AppColors.Error
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                isChecking -> "正在检测..."
                                isOnline -> if (latencyMs > 0) "服务器在线：${latencyMs}ms" else "服务器在线"
                                else -> "服务器离线：${lastError ?: "连接失败"}"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                isChecking -> AppColors.TextTertiary
                                isOnline -> when {
                                    latencyMs <= 0 -> AppColors.Success
                                    latencyMs < 200 -> AppColors.Success
                                    latencyMs < 500 -> AppColors.Warning
                                    else -> AppColors.Error
                                }
                                else -> AppColors.Error
                            },
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
            onConfirm = {
                viewModel.selectYearMonth(it)
                // 确认后关闭弹窗
                showMonthDialog = false
            },
            onDismiss = { showMonthDialog = false }
        )
    }
}

/**
 * 工程历史卡片
 *
 * 优化说明：
 * - 移除外层 Card 容器（绿色描边+圆角+背景色），主体内容直接展示，宽度占满父容器
 * - 主体内容用表格展示工程信息
 */
@Composable
private fun ProjectHistoryCard(
    project: ProjectHistoryUiModel,
    viewModel: DashboardViewModel
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 工程标题行：绿色背景 + 白色文字（名称+金额），建立标题栏权威感
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Green400, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = project.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                // project.totalAmount 已由 AmountFormatter.format 格式化为 "¥12,345.00" 格式，直接显示即可
                text = project.totalAmount,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 附件按钮行：紧贴标题栏下方，查看附件左对齐，上传附件右对齐
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：查看附件（中性浅灰背景）
            Surface(
                onClick = { viewModel.openAttachmentList(project.id) },
                shape = RoundedCornerShape(8.dp),
                color = AppColors.NeutralSurface
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
            // 右：上传附件（浅绿背景）
            Surface(
                onClick = { viewModel.openFilePickerForProject(project.id) },
                shape = RoundedCornerShape(8.dp),
                color = AppColors.Green50
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Upload,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = AppColors.Green400
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "上传附件",
                        fontSize = 12.sp,
                        color = AppColors.Green400
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 子项目表格（无背景化，融入卡片）
        if (project.subprojects.isNotEmpty()) {
            SubprojectTable(
                subprojects = project.subprojects,
                viewModel = viewModel
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // 底部信息行：施工人员(左) + 时间(右)，包裹在水平滚动容器中，支持横向滑动查看
        ProjectInfoScrollRow(
            workerNames = project.workerNames,
            createdAt = project.createdAt,
            updatedAt = project.updatedAt
        )
    }
}

/**
 * 工程底部信息行组件
 *
 * 将施工人员和时间信息包裹在水平滚动容器中，支持横向滑动查看。
 * 布局：施工人员(左) + 创建时间(中) + 更新时间(右)
 * 当内容超过容器宽度时，可左右滑动查看完整信息。
 */
@Composable
private fun ProjectInfoScrollRow(
    workerNames: List<String>,
    createdAt: String,
    updatedAt: String
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 施工人员（左侧，有施工人员时显示）
        if (workerNames.isNotEmpty()) {
            // 浅绿背景胶囊，标识施工人员
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(AppColors.Green50, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "施工人员：${workerNames.joinToString("、")}",
                    fontSize = 11.sp,
                    color = AppColors.Green400,
                    maxLines = 1
                )
            }
        }
        // 创建时间（中间，纯文字灰色弱化）
        Text(
            text = "创建 ${DateFormatter.formatDate(createdAt)}",
            fontSize = 11.sp,
            color = AppColors.TextTertiary,
            maxLines = 1
        )
        // 更新时间（右侧，纯文字灰色弱化）
        Text(
            text = "更新 ${DateFormatter.formatDate(updatedAt)}",
            fontSize = 11.sp,
            color = AppColors.TextTertiary,
            maxLines = 1
        )
    }
}

/**
 * 子项目表格组件
 *
 * 支持水平滚动：表头与表体使用固定列宽，总宽度超过容器可用宽度时可左右滑动查看。
 * 列宽方案：序号40 + 空间80 + 方案100 + 尺寸110 + 数量90 + 金额90 = 510dp
 */
@Composable
private fun SubprojectTable(
    subprojects: List<SubprojectUiModel>,
    viewModel: DashboardViewModel
) {
    val scrollState = rememberScrollState()
    // 固定总宽度，超过容器宽度时启用水平滚动
    val tableWidth = 510.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)  // 启用水平滚动，无背景无描边融入卡片
    ) {
        // 表头（固定宽度，浅灰底+主色底线，区分表头与表体）
        Row(
            modifier = Modifier
                .width(tableWidth)
                .background(AppColors.NeutralSurface)
                .drawBehind {
                    // 表头底部水平边框线（主色，加粗，区分表头与表体）
                    drawLine(
                        color = AppColors.Green400,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
                .padding(vertical = 8.dp, horizontal = 2.dp)
        ) {
            TableHeaderCell("序号", 40.dp)
            TableHeaderCell("空间", 80.dp)
            TableHeaderCell("方案", 100.dp)
            TableHeaderCell("尺寸(米)", 110.dp)
            TableHeaderCell("数量", 90.dp)
            TableHeaderCell("金额", 90.dp)
        }

        // 表体（与表头对齐，相同列宽）- 每行底部加浅灰水平线
        subprojects.forEachIndexed { index, sub ->
            val isLastRow = index == subprojects.lastIndex
            Row(
                modifier = Modifier
                    .width(tableWidth)
                    .drawBehind {
                        // 数据行底部水平边框线：行间用浅灰，末行用中灰收尾
                        drawLine(
                            color = if (isLastRow) AppColors.Outline else AppColors.OutlineVariant,
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .padding(vertical = 6.dp, horizontal = 2.dp)
            ) {
                TableCell("${index + 1}", 40.dp)
                TableCell(sub.spaceTypeName, 80.dp)
                TableCell(sub.constructionPlanName, 100.dp)
                // 数据库存储厘米，UI显示时除以100转为米（与表头"尺寸(米)"单位一致）
                TableCell("${formatNumber(sub.length / 100.0)} × ${formatNumber(sub.width / 100.0)}", 110.dp)
                TableCell(
                    "${formatNumber(sub.quantity)} ${viewModel.getUnitDisplayName(sub.unit)}",
                    90.dp
                )
                // sub.amount 已由 AmountFormatter.format 格式化为 "¥12,345.00" 格式，直接显示即可
                TableCell(sub.amount, 90.dp, color = AppColors.Green400)
            }
        }
    }
}

/**
 * 表头单元格（固定宽度）
 */
@Composable
private fun RowScope.TableHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
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
private fun RowScope.TableCell(text: String, width: androidx.compose.ui.unit.Dp, color: Color = AppColors.TextPrimary) {
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
    // 工装风格配色（主色保留设计规范的草木绿，通用色使用AppColors体系）
    val greenPrimary = Color(0xFF74B85C)        // 设计规范指定的柔和草木绿
    val bgColor = AppColors.Background
    val titleColor = AppColors.TextPrimary
    val dividerColor = AppColors.Divider
    val unselectedBorderColor = AppColors.DisabledBorder

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
                        // 使用选项文本作为稳定唯一key，提升列表复用性能
                        itemsIndexed(items, key = { _, item -> item }) { index, item ->
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
                    // 使用年份值作为稳定唯一key
                    items(years, key = { it }) { year ->
                        Box(
                            modifier = Modifier
                                // 宽度贴合文字，避免选中色块过长影响视觉
                                .widthIn(min = 64.dp)
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
                    // 使用月份值作为稳定唯一key
                    items(months, key = { it }) { month ->
                        Box(
                            modifier = Modifier
                                // 宽度贴合文字，避免选中色块过长影响视觉
                                .widthIn(min = 64.dp)
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

// 附件列表弹窗、附件列表项、文件大小格式化已迁移到 AttachmentGridDialog.kt
