package com.salary.manager.feature.ai.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.MaterialDto

/**
 * 排料输入页面
 *
 * 功能:
 * - 输入房间尺寸（长x宽）
 * - 选择面材、龙骨、收边条
 * - 点击计算 → 跳转排料可视化页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialLayoutScreen(
    onNavigateToPreview: () -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: MaterialLayoutViewModel = hiltViewModel()
) {
    val roomLength by viewModel.roomLength.collectAsStateWithLifecycle()
    val roomWidth by viewModel.roomWidth.collectAsStateWithLifecycle()
    val materials by viewModel.materials.collectAsStateWithLifecycle()
    val selectedPanelId by viewModel.selectedPanelId.collectAsStateWithLifecycle()
    val selectedMainKeelId by viewModel.selectedMainKeelId.collectAsStateWithLifecycle()
    val selectedSubKeelId by viewModel.selectedSubKeelId.collectAsStateWithLifecycle()
    val selectedTrimId by viewModel.selectedTrimId.collectAsStateWithLifecycle()
    val isCalculating by viewModel.isCalculating.collectAsStateWithLifecycle()
    val layoutResult by viewModel.layoutResult.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // 计算完成后跳转预览页
    if (layoutResult != null) {
        onNavigateToPreview()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("排料计算", fontSize = 20.sp, color = AppColors.TextPrimary) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 房间尺寸输入
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("房间尺寸", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = roomLength,
                            onValueChange = { viewModel.updateRoomLength(it) },
                            label = { Text("长度(cm)", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Green400,
                                unfocusedBorderColor = AppColors.Green100,
                                cursorColor = AppColors.Green400
                            ),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = roomWidth,
                            onValueChange = { viewModel.updateRoomWidth(it) },
                            label = { Text("宽度(cm)", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AppColors.Green400,
                                unfocusedBorderColor = AppColors.Green100,
                                cursorColor = AppColors.Green400
                            ),
                            singleLine = true
                        )
                    }
                }
            }

            // 面材选择
            MaterialSelectSection(
                title = "面材",
                items = viewModel.getMaterialsByCategory("面材"),
                selectedId = selectedPanelId,
                onSelect = { viewModel.selectPanel(it) }
            )

            // 主龙骨选择
            MaterialSelectSection(
                title = "主龙骨",
                items = viewModel.getMaterialsByCategory("主龙骨"),
                selectedId = selectedMainKeelId,
                onSelect = { viewModel.selectMainKeel(it) }
            )

            // 副龙骨选择
            MaterialSelectSection(
                title = "副龙骨",
                items = viewModel.getMaterialsByCategory("副龙骨"),
                selectedId = selectedSubKeelId,
                onSelect = { viewModel.selectSubKeel(it) }
            )

            // 收边条选择
            MaterialSelectSection(
                title = "收边条",
                items = viewModel.getMaterialsByCategory("收边条"),
                selectedId = selectedTrimId,
                onSelect = { viewModel.selectTrim(it) }
            )

            // 错误提示
            if (error != null) {
                Text(error!!, fontSize = 13.sp, color = AppColors.Error)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 计算按钮
            Button(
                onClick = { viewModel.calculateLayout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Green400,
                    disabledContainerColor = AppColors.Green100
                ),
                enabled = !isCalculating
            ) {
                if (isCalculating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("计算中...", fontSize = 16.sp)
                } else {
                    Text("开始排料计算", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 材料选择区域
 */
@Composable
private fun MaterialSelectSection(
    title: String,
    items: List<MaterialDto>,
    selectedId: Int?,
    onSelect: (Int) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
            Spacer(modifier = Modifier.height(8.dp))

            items.forEach { material ->
                Surface(
                    onClick = { onSelect(material.id) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (material.id == selectedId) AppColors.Green50 else AppColors.Background,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${material.name} ${material.brand ?: ""}",
                                fontSize = 14.sp,
                                color = AppColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                material.specification ?: "",
                                fontSize = 12.sp,
                                color = AppColors.TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            "¥${material.unitPrice}/${material.unit}",
                            fontSize = 13.sp,
                            color = AppColors.Green500,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (material.id == selectedId) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "已选",
                                modifier = Modifier.size(20.dp),
                                tint = AppColors.Green400
                            )
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                Text("暂无材料数据", fontSize = 13.sp, color = AppColors.TextTertiary)
            }
        }
    }
}
