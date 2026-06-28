package com.salary.manager.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onAddSpaceType: (name: String, description: String?, callback: (String?) -> Unit) -> Unit = { _, _, callback -> callback(null) },
    onDeleteSpaceType: (id: Int, callback: (String?) -> Unit) -> Unit = { _, callback -> callback(null) },
    onAddConstructionPlan: (name: String, description: String?, callback: (String?) -> Unit) -> Unit = { _, _, callback -> callback(null) },
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
                0 -> DictionaryListSection(
                    title = "空间类型",
                    items = spaceTypes,
                    onAdd = onAddSpaceType,
                    onDelete = onDeleteSpaceType
                )
                1 -> DictionaryListSection(
                    title = "施工方案",
                    items = constructionPlans,
                    onAdd = onAddConstructionPlan,
                    onDelete = onDeleteConstructionPlan
                )
                2 -> DictionaryListSection(
                    title = "工资分配类型",
                    items = wageDistributionTypes,
                    onAdd = { _, _, callback -> callback("工资分配类型暂不支持新增") },
                    onDelete = { _, callback -> callback("工资分配类型暂不支持删除") }
                )
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
    onAdd: (name: String, description: String?, callback: (String?) -> Unit) -> Unit,
    onDelete: (id: Int, callback: (String?) -> Unit) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            TextButton(onClick = { showAddDialog = true }) {
                Text("+ 新增", fontSize = 14.sp, color = AppColors.Green400, fontWeight = FontWeight.Bold)
            }
        }

        // 错误提示
        if (errorMessage != null) {
            Text(errorMessage!!, fontSize = 13.sp, color = AppColors.Error, modifier = Modifier.padding(vertical = 4.dp))
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
                                Text(item.name, fontSize = 15.sp, color = AppColors.TextPrimary)
                                val desc = item.description
                                if (!desc.isNullOrBlank()) {
                                    Text(desc, fontSize = 12.sp, color = AppColors.TextTertiary)
                                }
                            }
                            TextButton(onClick = {
                                onDelete(item.id) { error ->
                                    errorMessage = error
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
            onDismiss = { showAddDialog = false; errorMessage = null },
            onConfirm = { name, description ->
                onAdd(name, description) { error ->
                    if (error != null) {
                        errorMessage = error
                    } else {
                        showAddDialog = false
                        errorMessage = null
                    }
                }
            }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) return@TextButton
                onConfirm(name.trim(), description.ifBlank { null })
            }) {
                Text("确定", color = AppColors.Green400, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
