package com.salary.manager.feature.ai.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pinch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salary.core.design.theme.AppColors
import com.salary.core.network.api.LayoutResponse

/**
 * 排料可视化预览页面
 *
 * 功能:
 * - Canvas渲染排料布局
 * - 双指缩放+拖动手势
 * - 材料清单汇总
 * - 金额统计
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutPreviewScreen(
    onBack: () -> Unit = {},
    viewModel: MaterialLayoutViewModel = hiltViewModel()
) {
    val layoutResult by viewModel.layoutResult.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("排料结果", fontSize = 20.sp, color = AppColors.TextPrimary) },
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
        if (layoutResult == null) {
            // 无数据时显示提示
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("暂无排料数据", color = AppColors.TextTertiary, fontSize = 16.sp)
            }
            return@Scaffold
        }

        val result = layoutResult!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 排料Canvas可视化
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // 缩放提示
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Pinch,
                            contentDescription = "缩放",
                            modifier = Modifier.size(16.dp),
                            tint = AppColors.TextTertiary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("双指缩放 \u00B7 单指拖动", fontSize = 12.sp, color = AppColors.TextTertiary)
                    }

                    // Canvas渲染区域 - 高度根据 SVG 宽高比自适应屏幕宽度
                    // 原理：按容器宽度等比例缩放 SVG 高度，并限制在 200dp~500dp 范围
                    // 避免 SVG 过扁（横向狭长）导致高度过小看不清
                    // 避免 SVG 过高（纵向狭长）导致高度过大占据过多屏幕
                    val aspectRatio = remember(result.layout) {
                        val w = result.layout.svgWidth.toFloat()
                        val h = result.layout.svgHeight.toFloat()
                        if (w > 0f && h > 0f) w / h else 1f
                    }
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 按当前容器宽度等比例计算 SVG 渲染高度，并限制范围
                        val adaptiveHeight = (maxWidth / aspectRatio).coerceIn(200.dp, 500.dp)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.Background,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(adaptiveHeight)
                        ) {
                            LayoutCanvas(layoutData = result.layout)
                        }
                    }

                    // 图例
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        LegendItem("整板", AppColors.Green200)
                        LegendItem("裁切板", Color(0xFFFFF3CD))
                    }
                }
            }

            // 房间信息
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("房间信息", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow("面积", "${result.room.area} \u33A1")
                    InfoRow("计算耗时", result.calculationTime)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 材料清单
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("材料清单", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    // 面材
                    val panel = result.materials.panel
                    MaterialItemRow(panel.name, "${panel.totalPanels}张(整板${panel.fullPanels}+裁切${panel.cutPanels})", "\u00A5${panel.amount}")
                    MaterialItemRow("损耗率", "${panel.wasteRate}%", "")

                    // 龙骨
                    MaterialItemRow(result.materials.mainKeel.name, "${result.materials.mainKeel.count}根", "\u00A5${result.materials.mainKeel.amount}")
                    MaterialItemRow(result.materials.subKeel.name, "${result.materials.subKeel.count}根", "\u00A5${result.materials.subKeel.amount}")

                    // 收边条
                    MaterialItemRow(result.materials.trim.name, "${result.materials.trim.count}根", "\u00A5${result.materials.trim.amount}")

                    // 配件
                    result.materials.accessories.forEach { acc ->
                        MaterialItemRow(acc.name, "${acc.count}${acc.unit}", "\u00A5${acc.amount}")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 总金额
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Green400),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "材料总计",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        "\u00A5${result.totalAmount}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 图例项
 */
@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(2.dp),
            color = color,
            modifier = Modifier.size(12.dp)
        ) {}
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, color = AppColors.TextSecondary)
    }
}

/**
 * 信息行 - 左标签右值，长内容单行省略号
 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = AppColors.TextSecondary,
            maxLines = 1
        )
        Text(
            value,
            fontSize = 14.sp,
            color = AppColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 材料项行 - 名称、数量、金额三列布局，长内容单行省略号
 */
@Composable
private fun MaterialItemRow(name: String, quantity: String, amount: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            fontSize = 14.sp,
            color = AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            quantity,
            fontSize = 13.sp,
            color = AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (amount.isNotEmpty()) {
            Text(
                amount,
                fontSize = 14.sp,
                color = AppColors.Green500,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
