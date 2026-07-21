package com.salary.manager.feature.statistics.advance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.common.util.AmountFormatter
import com.salary.core.common.util.DateFormatter
import com.salary.core.design.theme.AppColors
import com.salary.core.ui.state.ListUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 预支管理页面 - 预支记录列表+创建预支
 * 独立页面入口（含Scaffold和FAB）
 *
 * 权限规则（对齐后端requireAdvanceCreate中间件）：
 * - 仅施工员（constructor）可创建预支，admin/documenter不显示FAB按钮
 */
@Composable
fun AdvanceScreen(
    viewModel: AdvanceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val createError by viewModel.createErrorMessage.collectAsState()
    val isCreating by viewModel.isCreating.collectAsState()
    val constructors by viewModel.constructors.collectAsState()
    val selectedUserId by viewModel.selectedUserId.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AdvanceContent(
            state = state,
            onRetry = { viewModel.loadAdvances() },
            showFilter = viewModel.canFilterByUser(),
            constructors = constructors,
            selectedUserId = selectedUserId,
            onSelectUser = { viewModel.setSelectedUserId(it) }
        )

        // 仅施工员可创建预支，其他角色不显示FAB按钮
        if (viewModel.canCreateAdvance()) {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = AppColors.Green400,
                contentColor = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "创建预支")
            }
        }
    }

    // 创建预支弹窗
    if (showCreateDialog) {
        CreateAdvanceDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { amount, advanceDate, remark ->
                viewModel.createAdvance(amount, advanceDate, remark)
                showCreateDialog = false
            },
            errorMessage = createError,
            isCreating = isCreating
        )
    }
}

/**
 * 预支内容区域 - 可嵌入统计页面的HorizontalPager中
 * 包含：三态UI（Loading/Success/Error）、人员筛选栏（资料员/管理员）、预支总额卡片、预支记录列表
 * @param state 预支列表UI状态
 * @param onRetry 错误重试回调
 * @param showFilter 是否显示人员筛选栏（资料员/管理员可按人员筛选）
 * @param constructors 施工人员列表（筛选用）
 * @param selectedUserId 当前选中的筛选人员ID（null表示显示全部）
 * @param onSelectUser 设置筛选人员回调
 */
@Composable
fun AdvanceContent(
    state: ListUiState<AdvanceItem>,
    onRetry: () -> Unit = {},
    showFilter: Boolean = false,
    constructors: List<com.salary.core.network.api.UserDto> = emptyList(),
    selectedUserId: Int? = null,
    onSelectUser: (Int?) -> Unit = {}
) {
    when (state) {
        is ListUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.Green400)
            }
        }
        is ListUiState.Success -> {
            val items = state.items
            Column(modifier = Modifier.fillMaxSize()) {
                // 人员筛选栏（资料员/管理员可按人员筛选）
                if (showFilter) {
                    UserFilterBar(
                        constructors = constructors,
                        selectedUserId = selectedUserId,
                        onSelectUser = onSelectUser
                    )
                }
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无预支记录", color = AppColors.TextTertiary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    // 预支总额卡片
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = AppColors.Green50)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("预支总额", fontSize = 13.sp, color = AppColors.TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    AmountFormatter.format(items.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }.toString()),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.Green400
                                )
                            }
                        }
                    }

                    items(items) { item ->
                        AdvanceCard(item)
                    }

                    // 底部间距
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
                }
            }
        }
        is ListUiState.Error -> {
            val errorMsg = state.message
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMsg, color = AppColors.TextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("请检查网络连接后重试", color = AppColors.TextTertiary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("重新加载", color = Color.White) }
                }
            }
        }
    }
}

/**
 * 预支记录卡片 - 展示单条预支信息
 * 显示：金额、备注、预支日期、创建时间、结算状态
 */
@Composable
fun AdvanceCard(item: AdvanceItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧信息列占主要宽度，长备注/人员名省略显示
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    AmountFormatter.format(item.amount),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Green400,
                    maxLines = 1
                )
                item.remark?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        it,
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 预支所属人员（资料员/管理员查看全部预支时显示，便于识别归属）
                item.userName?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "预支人：$it",
                        fontSize = 12.sp,
                        color = AppColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 创建人（管理员代建时显示）
                item.creatorName?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "创建人：$it",
                        fontSize = 12.sp,
                        color = AppColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 右侧日期/状态列占用固定宽度，避免被左侧长内容挤压
            Column(horizontalAlignment = Alignment.End) {
                // 预支日期（用户选择的预支日期）
                if (item.date.isNotBlank()) {
                    Text(
                        "预支日期：${DateFormatter.formatDate(item.date)}",
                        fontSize = 12.sp,
                        color = AppColors.TextTertiary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                // 创建时间
                Text(
                    "创建：${DateFormatter.formatDate(item.createdAt)}",
                    fontSize = 12.sp,
                    color = AppColors.TextTertiary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (item.settled) {
                    Text("已结算", fontSize = 12.sp, color = AppColors.Success, maxLines = 1)
                } else {
                    Text("未结算", fontSize = 12.sp, color = AppColors.Warning, maxLines = 1)
                }
            }
        }
    }
}

/**
 * 创建预支弹窗 - 输入金额、预支日期、备注
 * 对齐后端createAdvanceSchema的Joi校验：advanceAmount必填(>0,≤100000)、advanceDate必填(ISO日期)、remark可选(≤500字符)
 *
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调，参数：金额字符串、预支日期(yyyy-MM-dd)、备注(可空)
 * @param errorMessage 创建错误消息（可空）
 * @param isCreating 是否正在创建中（禁用按钮防止重复提交）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAdvanceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String?) -> Unit,
    errorMessage: String? = null,
    isCreating: Boolean = false
) {
    var amount by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }
    // 默认预支日期为今天
    val todayStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
    }
    var advanceDate by remember { mutableStateOf(todayStr) }
    var showDatePicker by remember { mutableStateOf(false) }

    // 使用 Dialog + usePlatformDefaultWidth=false 实现宽度自适应屏幕
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "创建预支",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.TextPrimary
                )
                // 预支金额输入框
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("预支金额（元）") },
                    supportingText = { Text("金额需大于0且不超过100000", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                // 预支日期选择器（点击弹DatePicker，对齐后端advanceDate必填字段）
                OutlinedTextField(
                    value = advanceDate,
                    onValueChange = { },
                    label = { Text("预支日期") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    shape = MaterialTheme.shapes.small,
                    trailingIcon = {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = "选择日期",
                            tint = AppColors.Green400,
                            modifier = Modifier.clickable { showDatePicker = true }
                        )
                    }
                )

                // 备注输入框（可选，自适应高度）
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注（选填）") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp),
                    minLines = 1,
                    shape = MaterialTheme.shapes.small
                )

                // 错误消息显示
                if (errorMessage != null) {
                    Text(
                        errorMessage,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 操作按钮右对齐（取消在左，确认在右）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = onDismiss, enabled = !isCreating) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(amount, advanceDate, remark.ifBlank { null }) },
                        // 客户端校验：金额>0且≤100000，日期不为空，不在创建中
                        enabled = !isCreating && amount.toDoubleOrNull()?.let { it > 0 && it <= 100000 } == true && advanceDate.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400)
                    ) {
                        Text(if (isCreating) "提交中..." else "确认")
                    }
                }
            }
        }
    }

    // 日期选择器弹窗
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            // 初始选中日期：解析advanceDate，失败则取今天
            initialSelectedDateMillis = runCatching {
                SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(advanceDate)?.time
            }.getOrNull() ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            advanceDate = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) { Text("确定", color = AppColors.Green400) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * 人员筛选栏 - 资料员/管理员按施工人员筛选预支记录
 * @param constructors 施工人员列表
 * @param selectedUserId 当前选中的筛选人员ID（null表示显示全部）
 * @param onSelectUser 设置筛选人员回调
 */
@Composable
fun UserFilterBar(
    constructors: List<com.salary.core.network.api.UserDto>,
    selectedUserId: Int?,
    onSelectUser: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // 当前选中人员的昵称（null=全部人员）
    val selectedName = constructors.find { it.id == selectedUserId }?.nickname ?: "全部人员"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "筛选：",
            fontSize = 13.sp,
            color = AppColors.TextSecondary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box {
            Surface(
                onClick = { expanded = true },
                shape = RoundedCornerShape(8.dp),
                color = AppColors.Green50,
                modifier = Modifier.padding(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedName,
                        fontSize = 13.sp,
                        color = AppColors.Green400,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("▾", fontSize = 12.sp, color = AppColors.Green400)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                // 全部人员选项
                DropdownMenuItem(
                    text = { Text("全部人员") },
                    onClick = {
                        onSelectUser(null)
                        expanded = false
                    }
                )
                // 各施工人员选项
                constructors.forEach { user ->
                    DropdownMenuItem(
                        text = { Text(user.nickname ?: "用户${user.id}") },
                        onClick = {
                            onSelectUser(user.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * 预支记录数据项
 * 对齐后端wage_advances表实际字段：advance_amount、advance_date、settled、remark、created_by
 */
data class AdvanceItem(
    val id: Int,
    /** 预支金额（字符串形式，便于格式化显示） */
    val amount: String,
    /** 备注 */
    val remark: String?,
    /** 预支日期（yyyy-MM-dd，用户选择的预支日期） */
    val date: String,
    /** 创建时间（ISO格式，用于显示创建时间） */
    val createdAt: String,
    /** 是否已结算 */
    val settled: Boolean,
    /** 预支所属人员昵称（资料员/管理员查看全部预支时显示，便于识别归属） */
    val userName: String? = null,
    /** 创建人昵称（管理员代建时显示） */
    val creatorName: String? = null
)
