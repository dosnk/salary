package com.salary.manager.feature.home.dashboard

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.common.util.AmountFormatter
import com.salary.core.common.util.DateFormatter
import com.salary.core.design.component.GreenTopNavBar
import com.salary.core.design.theme.AppColors
import com.salary.core.network.dto.FileDto
import com.salary.core.network.interceptor.LatencyTracker
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * 工作台页面 - 复刻Vue前端Dashboard设计
 * 包含：顶部导航栏、工程创建表单、工程历史列表、底部版权
 *
 * @param onNavigateToProject 点击工程卡片时导航到工程详情
 * @param onMessageClick 顶部导航栏消息图标点击回调
 * @param unreadCount 未读消息数（由AppNavHost全局传入，确保与个人中心一致）
 * @param refreshTrigger 刷新触发器：工程详情页保存工程后递增此值，主页监听变化后自动刷新工程历史
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    onNavigateToProject: (Int) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    latencyTracker: LatencyTracker? = null,
    userNickname: String = "",
    onMessageClick: (() -> Unit)? = null,
    unreadCount: Int = 0,
    refreshTrigger: Int = 0
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 当前用户角色（用于按角色控制UI元素显示，如资料员隐藏上传附件按钮）
    val userRole by viewModel.userRole.collectAsStateWithLifecycle()

    // 服务器在线状态和时延已下沉到 ServerStatusText 组件内部订阅，避免整页重组

    // 弹窗状态
    var showSpaceTypeDialog by remember { mutableStateOf(false) }
    var showSchemeDialog by remember { mutableStateOf(false) }
    var showMonthDialog by remember { mutableStateOf(false) }

    // 年月选择器点击回调：用 remember 包装避免每次重组创建新 lambda 实例，
    // 让 HistoryHeader 子组件在参数未变时可被跳过重组
    val onMonthDialogClick = remember { { showMonthDialog = true } }

    // ===== 下拉刷新状态 =====
    // 用户在主页顶部下拉时触发工程历史刷新（从网络拉取最新数据并更新缓存）
    val refreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    // 监听工程详情页保存工程后的刷新触发器
    // refreshTrigger 由 AppNavHost 在 onDataChanged 回调中递增
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            viewModel.loadProjects()
        }
    }

    // 下拉刷新指示器关闭逻辑：
    // isLoadingProjects 变化时检查：若正在刷新且加载已结束，则关闭指示器
    // 时序保证：forceRefreshProjects 先将 isLoadingProjects 置 true，网络完成后置 false，
    // 此处仅在 false 且 isRefreshing=true 时关闭，避免提前关闭
    LaunchedEffect(uiState.isLoadingProjects) {
        if (isRefreshing && !uiState.isLoadingProjects) {
            isRefreshing = false
        }
    }

    // 图库选择器：调用 Android 官方 Photo Picker，直接进入手机图库
    // 特点：
    //   1. 支持图片和视频（ImageAndVideo）
    //   2. 支持多选（PickMultipleVisualMedia，最多 20 项，可按需调整）
    //   3. 无需运行时权限（系统托管 UI，返回临时可读 URI）
    //   4. Android 11 及以下会自动降级为兼容的图库选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.uploadAttachments(uris)
        } else {
            viewModel.cancelUpload()
        }
    }
    LaunchedEffect(uiState.pendingUploadProjectId) {
        uiState.pendingUploadProjectId?.let {
            filePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
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
        // 把 FileDto 映射为统一的 AttachmentUiModel，fileUrl 使用预计算的完整 URL
        val attachments = remember(uiState.viewingFiles, fileUrls) {
            uiState.viewingFiles.map { file ->
                com.salary.manager.feature.home.attachment.AttachmentUiModel(
                    id = file.id,
                    fileName = file.originalName?.takeIf { it.isNotBlank() } ?: file.fileName,
                    fileUrl = fileUrls[file.id].orEmpty(),
                    fileSize = file.fileSize,
                    uploadedAt = file.uploadedAt,
                    type = file.type
                )
            }
        }
        com.salary.manager.feature.home.attachment.AttachmentDialog(
            title = "附件浏览",
            projectName = uiState.viewingFilesProjectName,
            files = attachments,
            isLoading = uiState.isLoadingFiles,
            canDelete = false,
            onDismiss = { viewModel.closeAttachmentList() },
            onMediaClick = { safeUrl, fileName, fileType ->
                // 媒体文件：用内置 MediaViewerDialog 预览（safeUrl 已由组件内部编码）
                try {
                    viewingMediaUrl = safeUrl
                    viewingMediaName = fileName
                    viewingMediaType = fileType
                } catch (_: Throwable) {
                    // 静默处理
                }
            },
            onFileClick = { file ->
                // 非媒体文件：用系统应用打开
                scope.launch {
                    try {
                        val fullUrl = file.fileUrl.takeIf { it.isNotBlank() } ?: return@launch
                        val safeUrl = com.salary.manager.feature.home.attachment
                            .encodeAttachmentUrl(fullUrl)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(android.net.Uri.parse(safeUrl), file.type ?: "*/*")
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // 没有可用应用打开该类型文件时，回退为用浏览器打开
                            val browserIntent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(safeUrl)
                            ).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(browserIntent)
                            } catch (_: Exception) {
                                // 静默处理，防止未捕获异常导致进程崩溃
                            }
                        }
                    } catch (_: Throwable) {
                        // 兜底：任何异常均静默处理
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
            // 优先使用AppNavHost传入的全局未读数，确保各页面未读数一致
            GreenTopNavBar(
                title = "三人行装修管理系统",
                userNickname = userNickname.ifBlank { uiState.userNickname }.ifBlank { "未登录" },
                unreadCount = if (unreadCount > 0) unreadCount else uiState.unreadCount,
                onMessageClick = onMessageClick
            )

            // ===== 可滚动内容区域（LazyColumn懒加载，仅组合可见项） =====
            // 外层水平padding设为4dp（原8dp），使工程历史卡片宽度约占屏幕98%
            // 表单Card单独补偿4dp水平padding，保持原有视觉边距
            val listState = rememberLazyListState()

            // 滚动到底部自动加载更多工程
            // 防误触发：totalItemsCount > 0 且 lastVisible > 0，避免列表项 ≤3 时
            // lastVisible >= totalItemsCount - 3 永远为真反复触发分页请求
            LaunchedEffect(listState, uiState.hasMoreProjects, uiState.isLoadingMoreProjects) {
                snapshotFlow {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val total = listState.layoutInfo.totalItemsCount
                    total > 0 && lastVisible > 0 && lastVisible >= total - 3
                }.collect { isAtEnd ->
                    if (isAtEnd && uiState.hasMoreProjects && !uiState.isLoadingMoreProjects) {
                        viewModel.loadMoreProjects()
                    }
                }
            }

            // 用 PullToRefreshBox 包裹 LazyColumn，实现"滑到顶后再下拉触发刷新"
            // onRefresh 回调：设置刷新指示器可见并触发网络拉取，加载完成后由上方
            // LaunchedEffect(uiState.isLoadingProjects) 自动关闭指示器
            PullToRefreshBox(
                state = refreshState,
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    viewModel.forceRefreshProjects()
                },
                modifier = Modifier
                    .weight(1f)
                    .background(AppColors.Background)
            ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                item(key = "top_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ===== 工程创建表单（拆分为3个item，减少单个item体积，避免进出视口时卡顿）=====
                // 3个Surface视觉上连续：顶部圆角 + 无圆角中间 + 底部圆角，看起来像一个Card
                // form_card_basic：客户地址、空间类型、施工方案、长度、宽度
                item(key = "form_card_basic") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        color = Color.White,
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
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

                        // ===== 参数输入区：按空间形状动态渲染 =====
                        // 形状与参数语义对照：
                        // - rectangle：长(length) + 宽(width)
                        // - right_triangle：底(length) + 高(width)
                        // - trapezoid：上底(length) + 下底(width) + 高(height)
                        // - circle：直径(length)
                        // 同时考虑施工方案 unit=length 时禁用宽度输入（与历史逻辑保持一致）
                        val currentShape = viewModel.currentSpaceShape()
                        val isLengthOnlyUnit = viewModel.currentSchemeUnit() == "length"

                        // 主参数标签（length）随形状变化，避免用户误填
                        val primaryLabel = when (currentShape) {
                            "right_triangle" -> "底(cm)"
                            "trapezoid" -> "上底(cm)"
                            "circle" -> "直径(cm)"
                            "rectangle" -> "长度(cm)"
                            else -> "长度(cm)"
                        }
                        OutlinedTextField(
                            value = uiState.lengthCm,
                            onValueChange = { viewModel.updateLength(it) },
                            label = { Text(primaryLabel) },
                            placeholder = { Text("请输入$primaryLabel") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Green400,
                                focusedLabelColor = AppColors.Green400
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        // 次参数（width）：圆形不显示（仅用直径）；其他形状显示
                        // unit=length 时禁用（保留历史逻辑：长度计价不使用宽度）
                        val showWidthField = currentShape != "circle"
                        if (showWidthField) {
                            val secondaryLabel = when (currentShape) {
                                "right_triangle" -> "高(cm)"
                                "trapezoid" -> "下底(cm)"
                                "rectangle" -> "宽度(cm)"
                                else -> "宽度(cm)"
                            }
                            OutlinedTextField(
                                value = uiState.widthCm,
                                onValueChange = { viewModel.updateWidth(it) },
                                label = { Text(secondaryLabel) },
                                placeholder = { Text("请输入$secondaryLabel") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLengthOnlyUnit,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppColors.Green400,
                                    focusedLabelColor = AppColors.Green400,
                                    disabledBorderColor = AppColors.TextPlaceholder,
                                    disabledTextColor = AppColors.TextTertiary
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        // 高度（height）：仅梯形显示，其他形状不渲染
                        if (currentShape == "trapezoid") {
                            OutlinedTextField(
                                value = uiState.heightCm,
                                onValueChange = { viewModel.updateHeight(it) },
                                label = { Text("高(cm)") },
                                placeholder = { Text("请输入梯形的高") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppColors.Green400,
                                    focusedLabelColor = AppColors.Green400
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        // ===== 实测信息（可折叠，平时少用默认折叠；已填数据时显示标记） =====
                        // 用 Surface 模拟可点击的折叠标题行，点击切换展开/折叠
                        val hasMeasuredData = uiState.measuredQuantity.isNotBlank() || uiState.measuredNote.isNotBlank()
                        Surface(
                            onClick = { viewModel.toggleMeasuredSection() },
                            shape = RoundedCornerShape(8.dp),
                            color = if (hasMeasuredData) AppColors.Green50 else Color(0xFFF9FAFB),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("📏", fontSize = 15.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "实测信息（选填）",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AppColors.TextPrimary
                                    )
                                    // 已填写实测数据时显示橙色"已填"标记
                                    if (hasMeasuredData) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "已填",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFFE67E22),
                                            modifier = Modifier
                                                .background(
                                                    color = Color(0xFFFFE8CC),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                // 展开/收起箭头
                                Icon(
                                    imageVector = if (uiState.isMeasuredSectionExpanded)
                                        Icons.Default.KeyboardArrowUp
                                    else
                                        Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (uiState.isMeasuredSectionExpanded) "收起" else "展开",
                                    tint = AppColors.TextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 展开时显示实测数量和实测备注两个输入框
                        if (uiState.isMeasuredSectionExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            // 实测数量（异形空间现场实测值，可选）
                            // 填入后覆盖按长宽计算的数量，适用于L形/多边形/圆形等非矩形空间
                            OutlinedTextField(
                                value = uiState.measuredQuantity,
                                onValueChange = { viewModel.updateMeasuredQuantity(it) },
                                label = { Text("实测数量（可选，覆盖计算值）") },
                                placeholder = { Text("异形空间填实测值") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppColors.Green400,
                                    focusedLabelColor = AppColors.Green400
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )

                            // 实测备注（记录实测方式或现场说明，可选）
                            OutlinedTextField(
                                value = uiState.measuredNote,
                                onValueChange = { viewModel.updateMeasuredNote(it) },
                                label = { Text("实测备注（可选）") },
                                placeholder = { Text("如：L形客厅周长实测") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AppColors.Green400,
                                    focusedLabelColor = AppColors.Green400
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        } // end of form_card_basic Column
                    } // end of form_card_basic Surface
                } // end of form_card_basic item

                // form_card_distribution：分配方式、工日设置、施工人员
                item(key = "form_card_distribution") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        shape = RectangleShape,
                        color = Color.White
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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

                        // 按工日分配模式下，在分配方式与施工人员选择之间显示工日输入区
                        // 每行固定3个施工人员，超过3个自动换行，每行宽度自动占满容器
                        if (uiState.salaryDistribution == "work_days" &&
                            uiState.selectedConstructorIds.isNotEmpty()
                        ) {
                            val selectedWorkers = uiState.constructors.filter {
                                uiState.selectedConstructorIds.contains(it.id)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "工日设置（每人默认1工日）",
                                fontSize = 13.sp,
                                color = AppColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
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
                                    value = uiState.totalWorkdaysInput,
                                    onValueChange = { newValue: String ->
                                        viewModel.updateTotalWorkdaysInput(newValue)
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
                                    // 移除固定height，使用默认高度避免Material3内部padding裁切文字
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
                            // 校验结果提示（提取为独立 Composable，隔离无限循环动画作用域）
                            if (uiState.workdaysValidationHint.isNotEmpty()) {
                                WorkdaysValidationHint(hint = uiState.workdaysValidationHint)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
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
                                                // 默认值为空，placeholder提示"1"；空值在计算和保存时按1工日处理
                                                // 修复：移除固定height，使用默认高度+heightIn下限，避免Material3内部padding裁切文字
                                                val workdayValue = uiState.workerWorkdays[worker.id] ?: ""
                                                OutlinedTextField(
                                                    value = workdayValue,
                                                    onValueChange = { newValue: String ->
                                                        viewModel.updateWorkerWorkdays(worker.id, newValue)
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
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // 施工人员选择（方形Checkbox标签，FlowRow自动换行，一行约7个）
                        if (uiState.constructors.isNotEmpty()) {
                            Column {
                                Text(
                                    text = "施工人员",
                                    fontSize = 14.sp,
                                    color = AppColors.TextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
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
                            }
                        }

                        } // end of form_card_distribution Column
                    } // end of form_card_distribution Surface
                } // end of form_card_distribution item

                // form_card_action：计算预览、备注、保存按钮
                item(key = "form_card_action") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                        color = Color.White,
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                        // 计算预览公式（浅绿背景+绿色边框）
                        // 优化：直接用 Text + Modifier 修饰，去掉外层 Box 容器
                        if (uiState.calculationFormula.isNotBlank()) {
                            Text(
                                text = "计算预览：${uiState.calculationFormula}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                                color = AppColors.TextPrimary,
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
                                    .padding(8.dp)
                            )
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

                        // 保存按钮（绿色渐变，压缩高度）
                        Button(
                            onClick = { viewModel.saveProject() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            enabled = !uiState.isSaving,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.Green400,
                                disabledContainerColor = AppColors.Green300
                            ),
                            contentPadding = ButtonDefaults.ContentPadding
                        ) {
                            if (uiState.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("保存中...", color = Color.White, fontSize = 15.sp)
                            } else {
                                Text(
                                    text = "保存",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                } // end of form_card_action item

                item(key = "form_bottom_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ===== 工程历史区域标题 =====
                // 抽成独立子组件：隔离 selectedYearMonth 状态读取，
                // 避免年月变化或整页 uiState 重组时波及其他 item
                item(key = "history_header") {
                    HistoryHeader(
                        selectedYearMonth = uiState.selectedYearMonth,
                        onMonthClick = onMonthDialogClick
                    )
                }

                // 工程列表：加载中/空状态/懒加载列表
                if (uiState.isLoadingProjects) {
                    item(key = "loading_projects") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AppColors.Green400)
                        }
                    }
                } else if (uiState.projects.isEmpty()) {
                    // 空状态
                    item(key = "empty_projects") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无数据", color = AppColors.TextTertiary, fontSize = 14.sp)
                        }
                    }
                } else {
                    // 工程卡片列表（LazyColumn懒加载，仅组合可见项）
                    // contentType="project_card" 让所有工程卡片走同一组合池，
                    // 滚动新卡片进入视口时可复用已回收卡片的组合槽，避免每张新卡都从零构建
                    // 布局，是消除滚动到新标题时"轻微卡顿一下"的关键
                    items(
                        uiState.projects,
                        key = { it.id },
                        contentType = { "project_card" }
                    ) { project ->
                        // 用 remember 包装 lambda，避免每次重组创建新实例，
                        // 让 ProjectHistoryCard 在参数未变时可被跳过重组
                        val openAttachmentList = remember(project.id) {
                            { viewModel.openAttachmentList(project.id) }
                        }
                        val openFilePicker = remember(project.id) {
                            { viewModel.openFilePickerForProject(project.id) }
                        }
                        ProjectHistoryCard(
                            project = project,
                            // 仅施工员可上传附件（admin/documenter 只读）
                            canUploadFile = userRole == "constructor",
                            onOpenAttachmentList = openAttachmentList,
                            onOpenFilePicker = openFilePicker
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // 加载更多指示器（滚动到底部时自动触发分页加载）
                    if (uiState.isLoadingMoreProjects) {
                        item(key = "loading_more") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = AppColors.Green400,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else if (uiState.hasMoreProjects) {
                        item(key = "load_more_hint") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                    contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "上滑加载更多",
                                    color = AppColors.TextTertiary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                item(key = "footer_top_spacer") {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // ===== 底部版权 + 服务器状态（版权左，状态右对齐）=====
                // 补偿4dp水平padding，保持与表单Card一致的视觉边距
                item(key = "footer") {
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
                    // 右侧：服务器在线状态和时延（独立 Composable，状态订阅仅在此重组）
                    ServerStatusText(latencyTracker = latencyTracker)
                }
                } // end of footer item

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            } // end of PullToRefreshBox
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
 * 工程历史区域标题
 *
 * 独立子组件目的：
 * - 隔离 selectedYearMonth 状态读取，年月变化只触发本组件重组，不波及其他 item
 * - onMonthClick 由父级 remember 包装，lambda 实例稳定，Surface 内部子组件可被跳过重组
 */
@Composable
private fun HistoryHeader(
    selectedYearMonth: String,
    onMonthClick: () -> Unit
) {
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
                onClick = onMonthClick,
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
                        text = formatYearMonth(selectedYearMonth),
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
    }
}

/**
 * 工程历史卡片扩展图标缓存
 *
 * androidx.compose.material.icons.filled 命名空间下的图标属于 material-icons-extended 库，
 * 每个图标都是懒加载的 ImageVector（首次访问会触发反射 + 路径数据解析，主线程耗时 5~30ms）。
 *
 * 工程历史列表滚动时，每张新卡片进入可视区都会引用 AttachFile / Upload 两个图标；
 * 早期虽然属于同一 JVM 静态属性，但 delegate 的首次评估路径在部分设备上仍存在明显开销。
 * 用文件级 by lazy 缓存 ImageVector 引用，保证只在整个进程首次访问时解析一次，
 * 之后所有卡片复用同一 ImageVector 实例，避免滚动到新工程标题时的"轻微卡顿一下"。
 */
private val ATTACH_FILE_ICON: androidx.compose.ui.graphics.vector.ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    Icons.Default.AttachFile
}
private val UPLOAD_ICON: androidx.compose.ui.graphics.vector.ImageVector by lazy(LazyThreadSafetyMode.NONE) {
    Icons.Default.Upload
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
    canUploadFile: Boolean,
    onOpenAttachmentList: () -> Unit,
    onOpenFilePicker: () -> Unit
) {
    // 子项目表格展开/折叠状态
    // 子项目表格已通过移除 horizontalScroll + 自适应列宽优化，可完整展开所有子项目
    // 注意：使用 remember 而非 rememberSaveable（后者需要 runtime-saveable 额外依赖）
    // 配置更改（屏幕旋转）时会重置为默认展开/折叠状态，可接受
    var isSubprojectExpanded by remember(project.id) {
        mutableStateOf(project.subprojects.size <= 30)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 工程标题行：绿色背景 + 白色文字（名称+金额），建立标题栏权威感
        // 点击标题栏切换子项目表格展开/折叠
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Green400, RoundedCornerShape(8.dp))
                .clickable {
                    if (project.subprojects.isNotEmpty()) {
                        isSubprojectExpanded = !isSubprojectExpanded
                    }
                }
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
                // 加 maxLines=1 + Ellipsis 防止极端长金额换行破坏标题栏
                text = project.totalAmount,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 展开/折叠指示箭头（仅有子项目时显示）
            if (project.subprojects.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isSubprojectExpanded) "▼" else "▶",
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 附件按钮行：紧贴标题栏下方，查看附件左对齐，上传附件右对齐
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：查看附件（中性浅灰背景）
            // 触摸目标 ≥48dp 满足 Material 无障碍建议，视觉样式保持紧凑
            Surface(
                onClick = onOpenAttachmentList,
                shape = RoundedCornerShape(8.dp),
                color = AppColors.NeutralSurface,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = ATTACH_FILE_ICON,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = AppColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "查看附件 (${project.fileCount})",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                }
            }
            // 右：上传附件（浅绿背景）—— 资料员不可上传，隐藏按钮
            // 触摸目标 ≥48dp 满足 Material 无障碍建议，视觉样式保持紧凑
            if (canUploadFile) {
                Surface(
                    onClick = onOpenFilePicker,
                    shape = RoundedCornerShape(8.dp),
                    color = AppColors.Green50,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = UPLOAD_ICON,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = AppColors.Green400
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "上传附件",
                            fontSize = 13.sp,
                            color = AppColors.Green400
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 子项目表格（默认折叠，点击标题栏展开后才渲染，减少初始渲染量）
        if (project.subprojects.isNotEmpty() && isSubprojectExpanded) {
            SubprojectTable(
                subprojects = project.subprojects
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        // 工程备注预览（单行省略，空备注不显示，保持卡片紧凑）
        if (!project.remark.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(text = "📝", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = project.remark,
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
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
 * 布局：施工人员(左) + 创建时间(中) + 更新时间(右)
 * 移除 horizontalScroll 以消除每个卡片的 rememberScrollState() 开销，
 * 超长内容用省略号显示。
 */
@Composable
private fun ProjectInfoScrollRow(
    workerNames: List<String>,
    createdAt: String,
    updatedAt: String
) {
    // 性能优化：日期格式化和字符串拼接结果用 remember 缓存，
    // 避免 ProjectHistoryCard 因外层 uiState 变化被重组时重复执行 SimpleDateFormat 解析
    val workerNamesText = remember(workerNames) {
        if (workerNames.isEmpty()) "" else "施工人员：${workerNames.joinToString("、")}"
    }
    val createdText = remember(createdAt) { "创建 ${DateFormatter.formatDate(createdAt)}" }
    val updatedText = remember(updatedAt) { "更新 ${DateFormatter.formatDate(updatedAt)}" }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 施工人员（左侧，有施工人员时显示，占剩余空间）
        if (workerNamesText.isNotEmpty()) {
            // 浅绿背景胶囊，标识施工人员
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .background(AppColors.Green50, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = workerNamesText,
                    fontSize = 12.sp,
                    color = AppColors.Green400,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // 创建时间（中间，纯文字灰色弱化）
        Text(
            text = createdText,
            fontSize = 12.sp,
            color = AppColors.TextTertiary,
            maxLines = 1
        )
        // 更新时间（右侧，纯文字灰色弱化）
        Text(
            text = updatedText,
            fontSize = 12.sp,
            color = AppColors.TextTertiary,
            maxLines = 1
        )
    }
}

/**
 * 子项目表格组件
 *
 * 性能优化方案（解决滑动卡顿）：
 * 1. 移除 horizontalScroll：消除"父纵向滚动+子横向滚动"的嵌套滚动冲突，
 *    这是滑动卡顿的主要原因。改用 weight 自适应列宽，所有列填满容器宽度。
 * 2. 内联 TableCell/TableHeaderCell：消除每行 6 个 Composable 函数调用层，
 *    直接在 Row 内写 Text + Modifier.weight()。
 * 3. 保留 drawBehind 绘制行底线：单个 drawLine 开销极低，无需额外优化。
 *
 * 列宽分配（weight）：序号0.5 + 空间1 + 方案1.3 + 尺寸1.5 + 数量1 + 金额1.2
 */
@Composable
private fun SubprojectTable(
    subprojects: List<SubprojectUiModel>
) {
    // 性能优化：一次性预计算所有行的显示文本和备注列表，避免每次重组都重复执行
    // String.format 和字符串拼接（30个子项目 × 3次格式化 = 90次 String.format/卡片/重组）
    data class NoteEntry(val index: Int, val sub: SubprojectUiModel, val label: String, val content: String)
    data class RowText(
        val index: Int,
        val sub: SubprojectUiModel,
        val indexText: String,
        val sizeText: String,
        val quantityText: String
    )
    val rows = remember(subprojects) {
        subprojects.mapIndexed { index, sub ->
            RowText(
                index = index,
                sub = sub,
                indexText = "${index + 1}",
                sizeText = "${AmountFormatter.format2f(sub.length / 100.0)} × ${AmountFormatter.format2f(sub.width / 100.0)}",
                quantityText = "${AmountFormatter.format2f(sub.quantity)} ${sub.unitDisplayName}"
            )
        }
    }
    val noteList = remember(subprojects) {
        buildList {
            subprojects.forEachIndexed { index, sub ->
                if (!sub.remark.isNullOrBlank()) {
                    add(NoteEntry(index, sub, "备注", sub.remark))
                }
                if (!sub.measuredNote.isNullOrBlank()) {
                    add(NoteEntry(index, sub, "实测", sub.measuredNote))
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 表头（自适应列宽，浅灰底+主色底线，区分表头与表体）
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
            Text("序号", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary,
                modifier = Modifier.weight(0.5f))
            Text("空间", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f))
            Text("方案", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary,
                modifier = Modifier.weight(1.3f))
            Text("尺寸(米)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary,
                modifier = Modifier.weight(1.5f))
            Text("数量", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f))
            Text("金额", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary,
                modifier = Modifier.weight(1.2f))
        }

        // 表体 - 每行底部加浅灰水平线
        // 用 key(sub.id) 包裹每行，让 Compose 在重组时能复用已组合的行，避免重复组合
        rows.forEach { row ->
            key(row.sub.id) {
                val isLastRow = row.index == rows.lastIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                    Text(row.indexText, fontSize = 13.sp, color = AppColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(0.5f))
                    Text(row.sub.spaceTypeName, fontSize = 13.sp, color = AppColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    Text(row.sub.constructionPlanName, fontSize = 13.sp, color = AppColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.3f))
                    // 数据库存储厘米，UI显示时除以100转为米（与表头"尺寸(米)"单位一致）
                    Text(row.sizeText, fontSize = 13.sp, color = AppColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.5f))
                    Text(row.quantityText, fontSize = 13.sp, color = AppColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    // sub.amount 已由 AmountFormatter.format 格式化为 "¥12,345.00" 格式，直接显示即可
                    Text(row.sub.amount, fontSize = 13.sp, color = AppColors.Green400,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1.2f))
                }
            }
        }

        // ===== 子项目备注汇总区：所有有备注的子项目集中显示在表格最下方 =====
        // 不再分散在子项目行之间，避免打断表格视觉连续性
        // 每条备注以"序号. 空间-方案"作为前缀标识所属子项目
        // 同时收集"普通备注"和"实测备注"，统一渲染，实测备注用橙色标签区分
        if (noteList.isNotEmpty()) {
            // 备注区与末行表格保持间距，避免视觉粘连
            Spacer(modifier = Modifier.height(8.dp))
            noteList.forEach { entry ->
                // 性能优化：每条备注的完整文本预计算并缓存
                val entryText = remember(entry) {
                    "${entry.index + 1}. ${entry.sub.spaceTypeName}-${entry.sub.constructionPlanName}：${entry.content}"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // 标签：浅绿底+绿字小标签（"备注"）；实测标签用浅橙底+橙字区分
                    val isMeasured = entry.label == "实测"
                    val labelBg = if (isMeasured) Color(0xFFFFE8CC) else AppColors.Green50
                    val labelColor = if (isMeasured) Color(0xFFE67E22) else AppColors.Green400
                    Text(
                        text = entry.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = labelColor,
                        maxLines = 1,
                        modifier = Modifier
                            .background(color = labelBg, shape = RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // 备注内容：灰色小字，带序号标识，最多2行省略
                    // 格式："序号. 空间-方案：备注内容"，让用户能定位到具体子项目
                    Text(
                        text = entryText,
                        fontSize = 11.sp,
                        color = AppColors.TextTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = bgColor,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
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
    // 解析当前年月失败时，回退到系统当前年月，避免硬编码 2026/6 导致用户初次打开时月份不准
    val today = java.time.LocalDate.now()
    val parts = currentYearMonth.split("-")
    val initYear = parts.getOrNull(0)?.toIntOrNull() ?: today.year
    val initMonth = parts.getOrNull(1)?.toIntOrNull() ?: today.monthValue

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

// 附件相关：URL 编码、判断媒体类型、文件大小格式化、附件弹窗、列表项组件
// 已全部迁移到 com.salary.manager.feature.home.attachment 包下，由主页与工程详情页共用

/**
 * 服务器在线状态显示组件
 *
 * 将 LatencyTracker 的三个 Flow 订阅隔离在此组件内部作用域，
 * 避免每次心跳触发的 latencyMs/isOnline 更新引发整页重组（LazyColumn 及卡片）。
 * 状态变化仅触发本 Composable 自身重组。
 *
 * 三种状态：检测中（灰）/在线（绿/橙/红按延迟）/离线（红）
 */
@Composable
private fun ServerStatusText(latencyTracker: LatencyTracker?) {
    // 独立订阅，状态变化只触发本组件重组，不影响外层 LazyColumn
    val isOnline by latencyTracker?.isOnline?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(true) }
    val latencyMs by latencyTracker?.latencyMs?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(0L) }
    val lastError by latencyTracker?.lastError?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // 检测中状态：isOnline=true && latencyMs=0 && lastError=null
        // HealthMonitor 刚启动尚未收到响应时的初始状态
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

/**
 * 工日校验提示组件（独立隔离动画作用域）
 *
 * 将无限循环动画限制在此独立 Composable 内部，避免动画驱动的重组波及外层
 * DashboardScreen 和 LazyColumn 的其他 item。
 *
 * - 一致：绿色、11sp、Normal、无动画
 * - 不一致：橙色、14sp、Bold、alpha 0.4↔1.0 闪烁（700ms 循环）
 *
 * @param hint 校验提示文本
 */
@Composable
private fun WorkdaysValidationHint(hint: String) {
    // 注意：不能使用contains("一致")，因为"不一致"也包含"一致"二字
    val isConsistent = !hint.contains("不一致")
    if (!isConsistent) {
        // 不一致时才创建无限循环动画，避免一致时无谓的动画驱动重组
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
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE6A23C),
            modifier = Modifier
                .padding(start = 4.dp, top = 2.dp)
                .graphicsLayer { this.alpha = alpha }
        )
    } else {
        Text(
            text = hint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = AppColors.Green400,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}
