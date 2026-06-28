package com.salary.manager.feature.home.edit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.salary.core.design.theme.AppColors
import com.salary.core.ui.state.UiState

/**
 * 编辑工程页面 - 修改工程信息+管理子项目
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditScreen(
    projectId: Int,
    onBack: () -> Unit = {},
    viewModel: ProjectEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var remark by remember { mutableStateOf("") }

    // 同步加载的数据到表单
    LaunchedEffect(state) {
        if (state is UiState.Success) {
            val data = (state as UiState.Success<ProjectEditUiModel>).data
            name = data.name
            status = data.status
            remark = data.remark ?: ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑工程") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("取消") }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveProject(name, status, remark.ifBlank { null })
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text("保存", color = AppColors.Green400)
                    }
                }
            )
        }
    ) { padding ->
        when (state) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AppColors.Green400)
                }
            }
            is UiState.Success -> {
                val data = (state as UiState.Success<ProjectEditUiModel>).data
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 工程基本信息
                    item {
                        Text("基本信息", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }

                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("工程名称") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small
                        )
                    }

                    item {
                        // 状态选择
                        var statusExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = statusExpanded,
                            onExpandedChange = { statusExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = when (status) {
                                    "preparing" -> "备料中"
                                    "constructing" -> "施工中"
                                    "completed" -> "已完工"
                                    "canceled" -> "已取消"
                                    else -> status
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("工程状态") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = MaterialTheme.shapes.small
                            )
                            ExposedDropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false }
                            ) {
                                listOf("preparing" to "备料中", "constructing" to "施工中", "completed" to "已完工", "canceled" to "已取消").forEach { (value, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            status = value
                                            statusExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = remark,
                            onValueChange = { remark = it },
                            label = { Text("备注") },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small
                        )
                    }

                    // 子项目管理
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("子项目 (${data.subprojects.size})", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { /* TODO: 添加子项目 */ }) {
                                Icon(Icons.Default.Add, "添加子项目", tint = AppColors.Green400)
                            }
                        }
                    }

                    items(data.subprojects) { sub ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${sub.spaceTypeName} - ${sub.constructionPlanName}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${sub.length}×${sub.width}cm | ${sub.amount}",
                                        fontSize = 13.sp,
                                        color = AppColors.TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteSubproject(sub.id) }
                                ) {
                                    Icon(Icons.Default.Delete, "删除", tint = AppColors.Error)
                                }
                            }
                        }
                    }
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text((state as UiState.Error).message, color = AppColors.Error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadProject(projectId) }) { Text("重试") }
                    }
                }
            }
        }
    }
}
