package com.salary.manager.feature.ai.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.KnowledgeItemDto
import com.salary.core.network.api.MaterialCategoryDto
import com.salary.core.network.api.MaterialDto
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 知识库浏览页面
 *
 * 功能:
 * - Tab切换: 材料库 / 知识文档
 * - 材料库Tab: 分类标签、搜索框、材料卡片列表、详情查看（所有角色可查看，admin可增删改）
 * - 知识文档Tab: 仅admin可用，支持列表查看、搜索、录入、详情查看、删除
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    onBack: () -> Unit = {},
    viewModel: KnowledgeViewModel = hiltViewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    val role by viewModel.roleFlow.collectAsStateWithLifecycle()

    // 材料库状态
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredMaterials by viewModel.filteredMaterials.collectAsStateWithLifecycle()
    val selectedMaterial by viewModel.selectedMaterial.collectAsStateWithLifecycle()
    val showMaterialCreateDialog by viewModel.showMaterialCreateDialog.collectAsStateWithLifecycle()
    val editingMaterial by viewModel.editingMaterial.collectAsStateWithLifecycle()
    val pendingDeleteMaterialId by viewModel.pendingDeleteMaterialId.collectAsStateWithLifecycle()
    val isAdmin = role == "admin"

    // 材料表单状态
    val formCategoryId by viewModel.formCategoryId.collectAsStateWithLifecycle()
    val formName by viewModel.formName.collectAsStateWithLifecycle()
    val formBrand by viewModel.formBrand.collectAsStateWithLifecycle()
    val formSpec by viewModel.formSpec.collectAsStateWithLifecycle()
    val formUnit by viewModel.formUnit.collectAsStateWithLifecycle()
    val formPrice by viewModel.formPrice.collectAsStateWithLifecycle()
    val formWidthCm by viewModel.formWidthCm.collectAsStateWithLifecycle()
    val formLengthCm by viewModel.formLengthCm.collectAsStateWithLifecycle()
    val formThicknessCm by viewModel.formThicknessCm.collectAsStateWithLifecycle()
    val formCoverageArea by viewModel.formCoverageArea.collectAsStateWithLifecycle()
    val formKeelSpacingCm by viewModel.formKeelSpacingCm.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()

    // 知识文档状态
    val filteredKnowledgeList by viewModel.filteredKnowledgeList.collectAsStateWithLifecycle()
    val knowledgeSearchQuery by viewModel.knowledgeSearchQuery.collectAsStateWithLifecycle()
    val selectedKnowledge by viewModel.selectedKnowledge.collectAsStateWithLifecycle()
    val showCreateDialog by viewModel.showCreateDialog.collectAsStateWithLifecycle()
    val inputTitle by viewModel.inputTitle.collectAsStateWithLifecycle()
    val inputContent by viewModel.inputContent.collectAsStateWithLifecycle()
    val pendingDeleteTitle by viewModel.pendingDeleteTitle.collectAsStateWithLifecycle()

    // 成功提示自动消失
    LaunchedEffect(success) {
        if (success != null) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSuccess()
        }
    }

    // 错误提示自动消失
    LaunchedEffect(error) {
        if (error != null) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识库", fontSize = 20.sp, color = AppColors.TextPrimary) },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab切换
            KnowledgeTabRow(
                selectedTab = selectedTab,
                onSelectTab = { viewModel.selectTab(it) },
                showKnowledgeTab = isAdmin
            )

            // 提示信息
            error?.let { msg ->
                InfoBar(text = msg, color = Color(0xFFD32F2F), bgColor = Color(0xFFFFEBEE))
            }
            success?.let { msg ->
                InfoBar(text = msg, color = AppColors.Green600, bgColor = Color(0xFFE8F5E9))
            }

            // Tab内容
            when (selectedTab) {
                0 -> MaterialTabContent(
                    isLoading = isLoading,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    categories = categories,
                    selectedCategoryId = selectedCategoryId,
                    onSelectCategory = { viewModel.selectCategory(it) },
                    filteredMaterials = filteredMaterials,
                    onSelectMaterial = { viewModel.selectMaterial(it) },
                    isAdmin = isAdmin,
                    onAddMaterial = { viewModel.openMaterialCreateDialog() },
                    onEditMaterial = { viewModel.openMaterialEditDialog(it) },
                    onDeleteMaterial = { viewModel.requestDeleteMaterial(it) }
                )
                1 -> {
                    if (isAdmin) {
                        KnowledgeTabContent(
                            isLoading = isLoading,
                            searchQuery = knowledgeSearchQuery,
                            onSearchQueryChange = { viewModel.updateKnowledgeSearchQuery(it) },
                            filteredKnowledgeList = filteredKnowledgeList,
                            onCreateClick = { viewModel.openCreateDialog() },
                            onItemClick = { viewModel.viewKnowledgeDetail(it.title) },
                            onDeleteClick = { viewModel.requestDelete(it.title) }
                        )
                    } else {
                        // 非admin无权限提示
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "您没有权限管理知识文档",
                                fontSize = 16.sp,
                                color = AppColors.TextTertiary
                            )
                        }
                    }
                }
            }
        }
    }

    // 材料详情弹窗
    if (selectedMaterial != null) {
        MaterialDetailDialog(
            material = selectedMaterial!!,
            isAdmin = isAdmin,
            onDismiss = { viewModel.clearSelectedMaterial() },
            onEdit = {
                val mat = selectedMaterial!!
                viewModel.clearSelectedMaterial()
                viewModel.openMaterialEditDialog(mat)
            },
            onDelete = {
                val id = selectedMaterial!!.id
                viewModel.clearSelectedMaterial()
                viewModel.requestDeleteMaterial(id)
            }
        )
    }

    // 材料录入/编辑弹窗
    if (showMaterialCreateDialog) {
        MaterialFormDialog(
            categories = categories,
            editingMaterial = editingMaterial,
            formCategoryId = formCategoryId,
            formName = formName,
            formBrand = formBrand,
            formSpec = formSpec,
            formUnit = formUnit,
            formPrice = formPrice,
            formWidthCm = formWidthCm,
            formLengthCm = formLengthCm,
            formThicknessCm = formThicknessCm,
            formCoverageArea = formCoverageArea,
            formKeelSpacingCm = formKeelSpacingCm,
            isSubmitting = isSubmitting,
            onCategoryIdChange = { viewModel.updateFormCategoryId(it) },
            onNameChange = { viewModel.updateFormName(it) },
            onBrandChange = { viewModel.updateFormBrand(it) },
            onSpecChange = { viewModel.updateFormSpec(it) },
            onUnitChange = { viewModel.updateFormUnit(it) },
            onPriceChange = { viewModel.updateFormPrice(it) },
            onWidthCmChange = { viewModel.updateFormWidthCm(it) },
            onLengthCmChange = { viewModel.updateFormLengthCm(it) },
            onThicknessCmChange = { viewModel.updateFormThicknessCm(it) },
            onCoverageAreaChange = { viewModel.updateFormCoverageArea(it) },
            onKeelSpacingCmChange = { viewModel.updateFormKeelSpacingCm(it) },
            onCancel = { viewModel.closeMaterialDialog() },
            onSubmit = { viewModel.submitMaterial() }
        )
    }

    // 材料删除确认弹窗
    pendingDeleteMaterialId?.let { id ->
        DeleteConfirmDialog(
            title = "确认删除",
            message = "确定要删除该材料吗？\n删除后将无法恢复，相关排料计算将不再使用此材料。",
            confirmText = "删除",
            isSubmitting = isSubmitting,
            onConfirm = { viewModel.confirmDeleteMaterial() },
            onCancel = { viewModel.cancelDeleteMaterial() }
        )
    }

    // 知识文档详情弹窗
    if (selectedKnowledge != null) {
        KnowledgeDetailDialog(
            detail = selectedKnowledge!!,
            onDismiss = { viewModel.clearSelectedKnowledge() },
            onDelete = { viewModel.requestDelete(selectedKnowledge!!.title) }
        )
    }

    // 录入知识文档弹窗
    if (showCreateDialog) {
        CreateKnowledgeDialog(
            title = inputTitle,
            content = inputContent,
            isSubmitting = isSubmitting,
            onTitleChange = { viewModel.updateInputTitle(it) },
            onContentChange = { viewModel.updateInputContent(it) },
            onCancel = { viewModel.closeCreateDialog() },
            onSubmit = { viewModel.submitCreate() }
        )
    }

    // 知识文档删除确认弹窗
    pendingDeleteTitle?.let { title ->
        DeleteConfirmDialog(
            title = "确认删除",
            message = "确定要删除知识文档「$title」吗？\n删除后将无法恢复，AI将无法再检索到该文档的内容。",
            confirmText = "删除",
            isSubmitting = isSubmitting,
            onConfirm = { viewModel.confirmDelete() },
            onCancel = { viewModel.cancelDelete() }
        )
    }
}

// ========== Tab栏 ==========

/**
 * 知识库Tab栏
 * @param showKnowledgeTab 是否显示知识文档Tab（仅admin可见）
 */
@Composable
private fun KnowledgeTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    showKnowledgeTab: Boolean
) {
    if (!showKnowledgeTab) return
    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.White,
        contentColor = AppColors.Green400
    ) {
        Tab(
            selected = selectedTab == 0,
            onClick = { onSelectTab(0) },
            text = { Text("材料库", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onSelectTab(1) },
            text = { Text("知识文档", fontSize = 15.sp, fontWeight = FontWeight.Medium) }
        )
    }
}

// ========== 材料库Tab内容 ==========

/**
 * 材料库Tab内容
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialTabContent(
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    categories: List<MaterialCategoryDto>,
    selectedCategoryId: Int?,
    onSelectCategory: (Int?) -> Unit,
    filteredMaterials: List<MaterialDto>,
    onSelectMaterial: (MaterialDto) -> Unit,
    isAdmin: Boolean,
    onAddMaterial: () -> Unit,
    onEditMaterial: (MaterialDto) -> Unit,
    onDeleteMaterial: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索框 + 添加按钮（admin显示添加按钮）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("搜索材料名称、品牌、规格...", fontSize = 14.sp, color = AppColors.TextPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.TextTertiary) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Green400,
                    unfocusedBorderColor = AppColors.Green100,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = AppColors.Green400
                ),
                singleLine = true
            )
            // admin显示添加材料按钮
            if (isAdmin) {
                Surface(
                    onClick = onAddMaterial,
                    shape = RoundedCornerShape(24.dp),
                    color = AppColors.Green400
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(2.dp))
                        Text("添加", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        // 分类标签
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            item {
                CategoryChip(
                    name = "全部",
                    isSelected = selectedCategoryId == null,
                    onClick = { onSelectCategory(null) }
                )
            }
            items(categories) { category ->
                CategoryChip(
                    name = category.name,
                    isSelected = selectedCategoryId == category.id,
                    onClick = { onSelectCategory(category.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 材料列表
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.Green400)
            }
        } else if (filteredMaterials.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无材料数据", fontSize = 16.sp, color = AppColors.TextTertiary)
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isAdmin) {
                        Text("点击右上角「添加」录入常用材料", fontSize = 13.sp, color = AppColors.TextTertiary)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp
                )
            ) {
                items(filteredMaterials, key = { it.id }) { material ->
                    MaterialCard(
                        material = material,
                        isAdmin = isAdmin,
                        onClick = { onSelectMaterial(material) },
                        onEdit = { onEditMaterial(material) },
                        onDelete = { onDeleteMaterial(material.id) }
                    )
                }
            }
        }
    }
}

/**
 * 分类标签
 */
@Composable
private fun CategoryChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) AppColors.Green400 else AppColors.SurfaceVariant
    ) {
        Text(
            text = name,
            fontSize = 14.sp,
            color = if (isSelected) Color.White else AppColors.TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * 材料卡片
 */
@Composable
private fun MaterialCard(
    material: MaterialDto,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${material.name} ${material.brand ?: ""}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "¥${material.unitPrice}/${material.unit}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Green500
                )
                // admin显示编辑/删除按钮
                if (isAdmin) {
                    Spacer(modifier = Modifier.size(4.dp))
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, "编辑", tint = AppColors.Green400, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, "删除", tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                    }
                }
            }

            if (!material.specification.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    material.specification!!,
                    fontSize = 13.sp,
                    color = AppColors.TextTertiary
                )
            }

            // 材料参数标签
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val catName = material.categoryName
                if (catName != null) {
                    ParamTag(catName)
                }
                if (material.widthCm != null && material.lengthCm != null) {
                    ParamTag("${material.widthCm}×${material.lengthCm}cm")
                }
                if (material.coverageArea != null) {
                    ParamTag("${material.coverageArea}㎡/张")
                }
            }
        }
    }
}

/**
 * 参数标签
 */
@Composable
private fun ParamTag(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = AppColors.Green50
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = AppColors.Green600,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 材料详情弹窗
 */
@Composable
private fun MaterialDetailDialog(
    material: MaterialDto,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${material.name} ${material.brand ?: ""}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!material.specification.isNullOrEmpty()) {
                    DetailRow("规格", material.specification!!)
                }
                DetailRow("单位", material.unit)
                DetailRow("单价", "¥${material.unitPrice}")
                if (material.widthCm != null) DetailRow("宽度", "${material.widthCm}cm")
                if (material.lengthCm != null) DetailRow("长度", "${material.lengthCm}cm")
                if (material.thicknessCm != null) DetailRow("厚度", "${material.thicknessCm}cm")
                if (material.coverageArea != null) DetailRow("覆盖面积", "${material.coverageArea}㎡")
                if (material.keelSpacingCm != null) DetailRow("间距", "${material.keelSpacingCm}cm")
                val categoryName = material.categoryName
                if (categoryName != null) DetailRow("分类", categoryName)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // admin显示编辑/删除按钮
                if (isAdmin) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = Color(0xFFD32F2F), fontWeight = FontWeight.Medium)
                    }
                    TextButton(onClick = onEdit) {
                        Text("编辑", color = AppColors.Green400, fontWeight = FontWeight.Medium)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭", color = AppColors.TextTertiary, fontWeight = FontWeight.Medium)
                }
            }
        }
    )
}

/**
 * 详情行
 */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = AppColors.TextSecondary)
        Text(value, fontSize = 14.sp, color = AppColors.TextPrimary, fontWeight = FontWeight.Medium)
    }
}

/**
 * 材料录入/编辑弹窗
 * 支持新建和编辑两种模式（根据editingMaterial是否为null区分）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialFormDialog(
    categories: List<MaterialCategoryDto>,
    editingMaterial: MaterialDto?,
    formCategoryId: Int?,
    formName: String,
    formBrand: String,
    formSpec: String,
    formUnit: String,
    formPrice: String,
    formWidthCm: String,
    formLengthCm: String,
    formThicknessCm: String,
    formCoverageArea: String,
    formKeelSpacingCm: String,
    isSubmitting: Boolean,
    onCategoryIdChange: (Int) -> Unit,
    onNameChange: (String) -> Unit,
    onBrandChange: (String) -> Unit,
    onSpecChange: (String) -> Unit,
    onUnitChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onWidthCmChange: (String) -> Unit,
    onLengthCmChange: (String) -> Unit,
    onThicknessCmChange: (String) -> Unit,
    onCoverageAreaChange: (String) -> Unit,
    onKeelSpacingCmChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    // 分类下拉菜单展开状态
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == formCategoryId }
    val isEditMode = editingMaterial != null

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onCancel() },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        title = {
            Text(
                if (isEditMode) "编辑材料" else "添加材料",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 450.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 材料分类（下拉选择）
                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "请选择分类",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("材料分类", fontSize = 13.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.Green400,
                            unfocusedBorderColor = AppColors.Green100,
                            cursorColor = AppColors.Green400
                        ),
                        enabled = !isSubmitting
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    onCategoryIdChange(category.id)
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // 材料名称（必填）
                FormTextField(
                    label = "材料名称",
                    value = formName,
                    onChange = onNameChange,
                    placeholder = "如：标准石膏板",
                    enabled = !isSubmitting,
                    required = true
                )

                // 品牌
                FormTextField(
                    label = "品牌",
                    value = formBrand,
                    onChange = onBrandChange,
                    placeholder = "如：泰山",
                    enabled = !isSubmitting
                )

                // 规格
                FormTextField(
                    label = "规格",
                    value = formSpec,
                    onChange = onSpecChange,
                    placeholder = "如：1200×2400×9.5mm",
                    enabled = !isSubmitting
                )

                // 单位 + 单价（同一行）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormTextField(
                        label = "单位",
                        value = formUnit,
                        onChange = onUnitChange,
                        placeholder = "张",
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f)
                    )
                    FormTextField(
                        label = "单价",
                        value = formPrice,
                        onChange = onPriceChange,
                        placeholder = "0.00",
                        enabled = !isSubmitting,
                        required = true,
                        modifier = Modifier.weight(1f),
                        keyboardNumber = true
                    )
                }

                HorizontalDivider(color = AppColors.Green100, thickness = 0.5.dp)
                Text("尺寸参数（可选）", fontSize = 12.sp, color = AppColors.TextTertiary)

                // 宽度 + 长度（同一行）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormTextField(
                        label = "宽度(cm)",
                        value = formWidthCm,
                        onChange = onWidthCmChange,
                        placeholder = "120",
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        keyboardNumber = true
                    )
                    FormTextField(
                        label = "长度(cm)",
                        value = formLengthCm,
                        onChange = onLengthCmChange,
                        placeholder = "240",
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        keyboardNumber = true
                    )
                }

                // 厚度 + 覆盖面积（同一行）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FormTextField(
                        label = "厚度(cm)",
                        value = formThicknessCm,
                        onChange = onThicknessCmChange,
                        placeholder = "0.95",
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        keyboardNumber = true
                    )
                    FormTextField(
                        label = "覆盖面积(㎡)",
                        value = formCoverageArea,
                        onChange = onCoverageAreaChange,
                        placeholder = "2.88",
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        keyboardNumber = true
                    )
                }

                // 龙骨间距
                FormTextField(
                    label = "龙骨间距(cm)",
                    value = formKeelSpacingCm,
                    onChange = onKeelSpacingCmChange,
                    placeholder = "如：100（仅龙骨类材料）",
                    enabled = !isSubmitting,
                    keyboardNumber = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting && formName.isNotBlank() && formPrice.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Green400)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isEditMode) "保存" else "添加", color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isSubmitting
            ) {
                Text("取消", color = AppColors.TextTertiary)
            }
        }
    )
}

/**
 * 表单输入框组件（带标签）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormTextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    required: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardNumber: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(if (required) "$label *" else label, fontSize = 13.sp) },
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.Green400,
            unfocusedBorderColor = AppColors.Green100,
            cursorColor = AppColors.Green400
        ),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = if (keyboardNumber) {
            androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
            )
        } else {
            androidx.compose.foundation.text.KeyboardOptions.Default
        }
    )
}

// ========== 知识文档Tab内容 ==========

/**
 * 知识文档Tab内容（含搜索功能）
 */
@Composable
private fun KnowledgeTabContent(
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    filteredKnowledgeList: List<KnowledgeItemDto>,
    onCreateClick: () -> Unit,
    onItemClick: (KnowledgeItemDto) -> Unit,
    onDeleteClick: (KnowledgeItemDto) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索框 + 录入按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("搜索文档标题...", fontSize = 14.sp, color = AppColors.TextPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.TextTertiary) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.Green400,
                    unfocusedBorderColor = AppColors.Green100,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = AppColors.Green400
                ),
                singleLine = true
            )
            OutlinedButton(
                onClick = onCreateClick,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Green400),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Green400)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("录入", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        // 文档列表
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.Green400)
            }
        } else if (filteredKnowledgeList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        tint = AppColors.TextTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (searchQuery.isNotEmpty()) "未找到匹配的文档" else "暂无知识文档",
                        fontSize = 16.sp,
                        color = AppColors.TextTertiary
                    )
                    if (searchQuery.isEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "点击右上角「录入」添加文档",
                            fontSize = 13.sp,
                            color = AppColors.TextTertiary
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp
                )
            ) {
                items(filteredKnowledgeList, key = { it.title + it.createdAt }) { item ->
                    KnowledgeCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onDelete = { onDeleteClick(item) }
                    )
                }
            }
        }
    }
}

/**
 * 知识文档卡片
 */
@Composable
private fun KnowledgeCard(
    item: KnowledgeItemDto,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val inputFormat = remember { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()) }
    val outputFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val createdDate = remember(item.createdAt) {
        try {
            item.createdAt?.let { inputFormat.parse(it)?.let { d -> outputFormat.format(d) } }
        } catch (_: Exception) {
            item.createdAt?.substring(0, 10)
        }
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = AppColors.Green400,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifEmpty { "无标题" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KnowledgeMetaTag("分块 ${item.chunkCount}")
                    KnowledgeMetaTag("${item.totalChars}字")
                    if (createdDate != null) {
                        KnowledgeMetaTag(createdDate)
                    }
                }
            }
            // 删除按钮
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = AppColors.TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 知识元信息标签
 */
@Composable
private fun KnowledgeMetaTag(text: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = AppColors.Green50
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = AppColors.Green600,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

/**
 * 知识文档详情弹窗
 */
@Composable
private fun KnowledgeDetailDialog(
    detail: com.salary.core.network.api.KnowledgeDetailResponse,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    detail.title.ifEmpty { "无标题" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 元信息
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KnowledgeMetaTag("共${detail.chunkCount}个分块")
                    KnowledgeMetaTag("来源：${sourceTypeText(detail.sourceType)}")
                }
                HorizontalDivider()
                // 分块内容
                detail.chunks.forEachIndexed { index, chunk ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "片段 ${chunk.chunkIndex + 1}",
                            fontSize = 13.sp,
                            color = AppColors.Green600,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            chunk.content,
                            fontSize = 14.sp,
                            color = AppColors.TextPrimary,
                            lineHeight = 22.sp
                        )
                        if (index < detail.chunks.size - 1) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = AppColors.Green400, fontWeight = FontWeight.Medium)
            }
        }
    )
}

/**
 * 录入知识文档弹窗
 */
@Composable
private fun CreateKnowledgeDialog(
    title: String,
    content: String,
    isSubmitting: Boolean,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onCancel() },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        title = {
            Text(
                "录入知识文档",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 标题输入框
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("文档标题", fontSize = 13.sp) },
                    placeholder = { Text("请输入标题（1-200字符）", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Green400,
                        unfocusedBorderColor = AppColors.Green100,
                        cursorColor = AppColors.Green400
                    ),
                    singleLine = true,
                    enabled = !isSubmitting
                )

                // 内容输入框
                OutlinedTextField(
                    value = content,
                    onValueChange = onContentChange,
                    label = { Text("文档内容", fontSize = 13.sp) },
                    placeholder = { Text("请输入文档内容（10-50000字符）\n内容将自动分块并生成向量嵌入，用于AI对话时的知识检索", fontSize = 13.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 280.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Green400,
                        unfocusedBorderColor = AppColors.Green100,
                        cursorColor = AppColors.Green400
                    ),
                    enabled = !isSubmitting
                )

                // 字数提示
                Text(
                    text = "${content.length}/50000 字符",
                    fontSize = 11.sp,
                    color = AppColors.TextTertiary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = !isSubmitting && title.isNotBlank() && content.length >= 10
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AppColors.Green400,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("提交", color = AppColors.Green400, fontWeight = FontWeight.Medium)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isSubmitting
            ) {
                Text("取消", color = AppColors.TextTertiary)
            }
        }
    )
}

// ========== 通用弹窗组件 ==========

/**
 * 通用删除确认弹窗
 */
@Composable
private fun DeleteConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    isSubmitting: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onCancel() },
        shape = RoundedCornerShape(16.dp),
        containerColor = Color.White,
        title = {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
        },
        text = {
            Text(
                message,
                fontSize = 14.sp,
                color = AppColors.TextSecondary,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isSubmitting
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFFD32F2F),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(confirmText, color = Color(0xFFD32F2F), fontWeight = FontWeight.Medium)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isSubmitting
            ) {
                Text("取消", color = AppColors.TextTertiary)
            }
        }
    )
}

// ========== 工具函数 ==========

/**
 * 提示信息条
 */
@Composable
private fun InfoBar(
    text: String,
    color: Color,
    bgColor: Color
) {
    Surface(
        color = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * 来源类型文本转换
 */
private fun sourceTypeText(sourceType: String): String = when (sourceType) {
    "manual" -> "手动录入"
    "upload" -> "文件上传"
    "api" -> "API导入"
    else -> sourceType
}
