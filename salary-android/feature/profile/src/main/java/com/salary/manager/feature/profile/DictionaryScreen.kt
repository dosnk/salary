package com.salary.manager.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.DictionaryItemDto

/**
 * 字典管理页面（仅admin）
 *
 * 管理空间类型、施工方案等字典数据
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    onBack: () -> Unit = {},
    spaceTypes: List<DictionaryItemDto> = emptyList(),
    constructionPlans: List<DictionaryItemDto> = emptyList(),
    wageDistributionTypes: List<DictionaryItemDto> = emptyList(),
    onAddSpaceType: (name: String, description: String?, shape: String?, callback: (String?) -> Unit) -> Unit = { _, _, _, callback -> callback(null) },
    onDeleteSpaceType: (id: Int, callback: (String?) -> Unit) -> Unit = { _, callback -> callback(null) },
    // 施工方案需要单位与单价，签名与空间类型不同
    onAddConstructionPlan: (name: String, unit: String, price: Double, callback: (String?) -> Unit) -> Unit = { _, _, _, callback -> callback(null) },
    onDeleteConstructionPlan: (id: Int, callback: (String?) -> Unit) -> Unit = { _, callback -> callback(null) }
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("空间类型", "施工方案", "工资分配类型")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("字典管理", fontSize = 20.sp, color = AppColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = AppColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Tab切换
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = AppColors.Green400
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            // 内容区域
            when (selectedTab) {
                0 -> SpaceTypeSection(
                    items = spaceTypes,
                    onAdd = onAddSpaceType,
                    onDelete = onDeleteSpaceType
                )
                1 -> ConstructionPlanSection(
                    items = constructionPlans,
                    onAdd = onAddConstructionPlan,
                    onDelete = onDeleteConstructionPlan
                )
                2 -> DictionaryListSection(
                    title = "工资分配类型",
                    items = wageDistributionTypes,
                    // 工资分配类型属于系统内建枚举，不支持新增/删除，隐藏新增按钮
                    canAdd = false,
                    onAdd = { _, _, callback -> callback("工资分配类型暂不支持新增") },
                    onDelete = { _, callback -> callback("工资分配类型暂不支持删除") }
                )
            }
        }
    }
}

/**
 * 空间类型列表区域
 *
 * 与通用字典列表区别：
 * 1) 列表项需展示 shape（空间形状）标签；
 * 2) 新增弹窗需选择空间形状，且无需 description 字段。
 */
@Composable
private fun SpaceTypeSection(
    items: List<DictionaryItemDto>,
    onAdd: (name: String, description: String?, shape: String?, callback: (String?) -> Unit) -> Unit,
    onDelete: (id: Int, callback: (String?) -> Unit) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var errorDialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("空间类型", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            TextButton(onClick = { showAddDialog = true }) {
                Text("+ 新增", fontSize = 14.sp, color = AppColors.Green400, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无数据", fontSize = 14.sp, color = AppColors.TextTertiary)
                }
            } else {
                LazyColumn {
                    items(items, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    fontSize = 15.sp,
                                    color = AppColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // 展示空间形状标签，便于管理员核对计算规则
                                val shapeLabel = when (item.shape) {
                                    "rectangle" -> "矩形"
                                    "right_triangle" -> "直角三角形"
                                    "trapezoid" -> "梯形"
                                    "circle" -> "圆形"
                                    else -> "矩形"
                                }
                                Text(
                                    "形状：$shapeLabel",
                                    fontSize = 12.sp,
                                    color = AppColors.TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = {
                                onDelete(item.id) { error ->
                                    if (error != null) errorDialog = error
                                }
                            }) {
                                Text("删除", fontSize = 13.sp, color = AppColors.Error)
                            }
                        }
                        if (item != items.last()) {
                            HorizontalDivider(color = AppColors.SurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSpaceTypeDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, shape ->
                onAdd(name, null, shape) { error ->
                    if (error != null) {
                        showAddDialog = false
                        errorDialog = error
                    } else {
                        showAddDialog = false
                    }
                }
            }
        )
    }

    val err = errorDialog
    if (err != null) {
        DictionaryErrorDialog(
            message = err,
            onDismiss = { errorDialog = null }
        )
    }
}

/**
 * 新增空间类型弹窗
 *
 * 字段：名称、空间形状（rectangle/right_triangle/trapezoid/circle）。
 * 形状决定录入表单的参数组与计算公式。
 */
@Composable
private fun AddSpaceTypeDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, shape: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    // 默认矩形，与后端 DEFAULT 'rectangle' 保持一致
    var selectedShape by remember { mutableStateOf("rectangle") }

    val shapeOptions = listOf(
        "rectangle" to "矩形（长×宽）",
        "right_triangle" to "直角三角形（底×高/2）",
        "trapezoid" to "梯形（(上底+下底)×高/2）",
        "circle" to "圆形（π×r²）"
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "新增空间类型",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                Text(
                    "空间形状",
                    fontSize = 13.sp,
                    color = AppColors.TextSecondary
                )
                shapeOptions.forEach { (code, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedShape = code }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedShape == code,
                            onClick = { selectedShape = code },
                            colors = RadioButtonDefaults.colors(selectedColor = AppColors.Green400)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontSize = 14.sp, color = AppColors.TextPrimary)
                    }
                }
                // 操作按钮右对齐
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        if (name.isBlank()) return@TextButton
                        onConfirm(name.trim(), selectedShape)
                    }) {
                        Text("确定", color = AppColors.Green400, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 字典列表区域
 */
@Composable
private fun DictionaryListSection(
    title: String,
    items: List<DictionaryItemDto>,
    // 是否显示"+ 新增"按钮；工资分配类型等系统内建字典不支持新增
    canAdd: Boolean = true,
    onAdd: (name: String, description: String?, callback: (String?) -> Unit) -> Unit,
    onDelete: (id: Int, callback: (String?) -> Unit) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    // 错误消息改用弹窗展示：新增/删除失败时统一走 errorDialog，避免原有的一行小字容易被忽略
    var errorDialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            if (canAdd) {
                TextButton(onClick = { showAddDialog = true }) {
                    Text("+ 新增", fontSize = 14.sp, color = AppColors.Green400, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无数据", fontSize = 14.sp, color = AppColors.TextTertiary)
                }
            } else {
                LazyColumn {
                    items(items, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    fontSize = 15.sp,
                                    color = AppColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val desc = item.description
                                if (!desc.isNullOrBlank()) {
                                    Text(
                                        desc,
                                        fontSize = 12.sp,
                                        color = AppColors.TextTertiary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            TextButton(onClick = {
                                onDelete(item.id) { error ->
                                    // 删除失败：改为弹窗提示
                                    if (error != null) errorDialog = error
                                }
                            }) {
                                Text("删除", fontSize = 13.sp, color = AppColors.Error)
                            }
                        }
                        if (item != items.last()) {
                            HorizontalDivider(color = AppColors.SurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }

    // 新增弹窗
    if (showAddDialog) {
        AddDictionaryItemDialog(
            title = "新增$title",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, description ->
                onAdd(name, description) { error ->
                    if (error != null) {
                        // 新增失败：关闭确认弹窗，改为弹窗展示错误
                        showAddDialog = false
                        errorDialog = error
                    } else {
                        showAddDialog = false
                    }
                }
            }
        )
    }

    // 通用错误提示弹窗（新增失败/删除失败共用）
    val err = errorDialog
    if (err != null) {
        DictionaryErrorDialog(
            message = err,
            onDismiss = { errorDialog = null }
        )
    }
}

/**
 * 新增字典项弹窗
 */
@Composable
private fun AddDictionaryItemDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

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
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("描述（可选）", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
                // 操作按钮右对齐
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        if (name.isBlank()) return@TextButton
                        onConfirm(name.trim(), description.ifBlank { null })
                    }) {
                        Text("确定", color = AppColors.Green400, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 字典操作错误提示弹窗
 *
 * 用于新增/删除字典失败时的统一弹窗提示，替代原来的内嵌一行小字。
 * 弹窗出现前调用方会先关闭确认/新增弹窗，避免视觉遮挡。
 */
@Composable
private fun DictionaryErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White,
        title = { Text("操作失败", fontWeight = FontWeight.Bold, color = AppColors.Error) },
        text = {
            Text(
                message,
                fontSize = 14.sp,
                color = AppColors.TextPrimary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = AppColors.Green400, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * 施工方案专属列表区域
 *
 * 与空间类型区别：
 * 1) 列表项需展示 unit / price；
 * 2) 新增弹窗需要选择计量单位与单价，而不是描述。
 */
@Composable
private fun ConstructionPlanSection(
    items: List<DictionaryItemDto>,
    onAdd: (name: String, unit: String, price: Double, callback: (String?) -> Unit) -> Unit,
    onDelete: (id: Int, callback: (String?) -> Unit) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var errorDialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("施工方案", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            TextButton(onClick = { showAddDialog = true }) {
                Text("+ 新增", fontSize = 14.sp, color = AppColors.Green400, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无数据", fontSize = 14.sp, color = AppColors.TextTertiary)
                }
            } else {
                LazyColumn {
                    items(items, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    fontSize = 15.sp,
                                    color = AppColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // 展示计量单位与单价，方便管理员核对
                                val unitLabel = when (item.unit) {
                                    "area" -> "面积(㎡)"
                                    "perimeter" -> "周长(米)"
                                    "length" -> "长度(米)"
                                    else -> item.unit ?: "-"
                                }
                                val priceLabel = item.price?.let { "¥$it" } ?: "-"
                                Text(
                                    "$unitLabel · $priceLabel",
                                    fontSize = 12.sp,
                                    color = AppColors.TextTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = {
                                onDelete(item.id) { error ->
                                    if (error != null) errorDialog = error
                                }
                            }) {
                                Text("删除", fontSize = 13.sp, color = AppColors.Error)
                            }
                        }
                        if (item != items.last()) {
                            HorizontalDivider(color = AppColors.SurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddConstructionPlanDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, unit, price ->
                onAdd(name, unit, price) { error ->
                    if (error != null) {
                        showAddDialog = false
                        errorDialog = error
                    } else {
                        showAddDialog = false
                    }
                }
            }
        )
    }

    val err = errorDialog
    if (err != null) {
        DictionaryErrorDialog(
            message = err,
            onDismiss = { errorDialog = null }
        )
    }
}

/**
 * 新增施工方案弹窗
 *
 * 字段：名称、计量单位（area/perimeter/length）、单价。
 * 单价校验：正数；单位固定枚举，通过下拉选择。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddConstructionPlanDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, unit: String, price: Double) -> Unit
) {
    // 单位枚举：与后端 controller/dictionary.js 中 getConstructionUnits 保持一致
    val unitOptions = listOf(
        "area" to "面积（㎡）",
        "perimeter" to "周长（米）",
        "length" to "长度（米）"
    )

    var name by remember { mutableStateOf("") }
    var unitCode by remember { mutableStateOf(unitOptions.first().first) }
    var priceText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val unitLabel = unitOptions.firstOrNull { it.first == unitCode }?.second ?: ""

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
                    "新增施工方案",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("名称", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                // 计量单位下拉
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = unitLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("计量单位", fontSize = 13.sp) },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "选择单位")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        unitOptions.forEach { (code, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    unitCode = code
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = priceText,
                    onValueChange = { input ->
                        // 只允许数字与小数点
                        if (input.isEmpty() || input.matches(Regex("^\\d*(\\.\\d*)?$"))) {
                            priceText = input
                        }
                    },
                    label = { Text("单价（元）", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                localError?.let {
                    Text(it, fontSize = 12.sp, color = AppColors.Error)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isEmpty()) { localError = "名称不能为空"; return@TextButton }
                        val price = priceText.toDoubleOrNull()
                        if (price == null || price <= 0.0) { localError = "单价必须为正数"; return@TextButton }
                        localError = null
                        onConfirm(trimmed, unitCode, price)
                    }) {
                        Text("确定", color = AppColors.Green400, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
