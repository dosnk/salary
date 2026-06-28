package com.salary.manager.feature.ai.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.MaterialCategoryDto
import com.salary.core.network.api.MaterialDto

/**
 * 知识库浏览页面
 *
 * 功能:
 * - 分类标签横向滚动
 * - 搜索框
 * - 材料卡片列表
 * - 点击卡片查看详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    onBack: () -> Unit = {},
    viewModel: KnowledgeViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedCategoryId by viewModel.selectedCategoryId.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredMaterials by viewModel.filteredMaterials.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val selectedMaterial by viewModel.selectedMaterial.collectAsStateWithLifecycle()

    // 材料详情弹窗
    if (selectedMaterial != null) {
        MaterialDetailDialog(
            material = selectedMaterial!!,
            onDismiss = { viewModel.clearSelectedMaterial() }
        )
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
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("搜索材料名称、品牌、规格...", fontSize = 14.sp, color = AppColors.TextPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.TextTertiary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

            // 分类标签
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // 全部标签
                item {
                    CategoryChip(
                        name = "全部",
                        isSelected = selectedCategoryId == null,
                        onClick = { viewModel.selectCategory(null) }
                    )
                }
                // 分类标签
                items(categories) { category ->
                    CategoryChip(
                        name = category.name,
                        isSelected = selectedCategoryId == category.id,
                        onClick = { viewModel.selectCategory(category.id) }
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
                    Text("暂无材料数据", fontSize = 16.sp, color = AppColors.TextTertiary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp
                    )
                ) {
                    items(filteredMaterials, key = { it.id }) { material ->
                        MaterialCard(
                            material = material,
                            onClick = { viewModel.selectMaterial(material) }
                        )
                    }
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
    onClick: () -> Unit
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
                    color = AppColors.TextPrimary
                )
                Text(
                    "¥${material.unitPrice}/${material.unit}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Green500
                )
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
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White,
        title = {
            Text(
                "${material.name} ${material.brand ?: ""}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary
            )
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
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                color = AppColors.Green400
            ) {
                Text(
                    "关闭",
                    fontSize = 14.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
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
