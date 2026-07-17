package com.salary.manager.feature.home.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.common.util.AmountFormatter
import com.salary.core.common.util.DateFormatter
import com.salary.core.design.theme.AppColors

/**
 * 工程卡片组件 - 参考HTML设计稿
 * 样式要点：
 * - 圆角14dp，柔和阴影
 * - 头部：工程名(18sp SemiBold) + 日期/单号(右对齐)，下方分隔线
 * - 金额区：左右两栏(工费总额 / 人均工费)，大字绿色(24sp Bold)，无背景框
 * - 信息区：人员/分配 单行紧凑布局(15sp)
 * - 按钮区：两按钮并排(各weight 1f)，圆角8dp
 *
 * @param project 工程UI数据模型
 * @param onNavigateToProject 点击查看详情回调
 * @param onConfirmComplete 点击确认完工回调
 * @param onSettlingClick 点击统计中回调
 * @param onSettledClick 点击已结算回调
 */
@Composable
fun ProjectCard(
    project: ProjectUiModel,
    canConfirmComplete: Boolean = true,
    onNavigateToProject: () -> Unit,
    onConfirmComplete: () -> Unit = {},
    onSettlingClick: () -> Unit = {},
    onSettledClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppColors.Surface
        ),
        // 柔和阴影：0 1px 6px rgba(0,0,0,0.08) 对应 elevation约2dp
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 卡片头部：工程名称 + 日期/单号（下方分隔线）
            CardHeader(project = project)

            // 金额区：工费总额 + 人均工费（左右两栏，无背景框）
            AmountSection(project = project)

            // 信息区：人员 + 分配方式（单行紧凑布局）
            InfoSection(project = project)

            // 工程备注预览（单行省略，空备注不显示，保持卡片紧凑）
            if (!project.remark.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "📝", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = project.remark,
                        fontSize = 12.sp,
                        color = AppColors.TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 按钮区：状态按钮 + 查看详情（并排两按钮）
            ButtonSection(
                project = project,
                canConfirmComplete = canConfirmComplete,
                onViewDetail = onNavigateToProject,
                onConfirmComplete = onConfirmComplete,
                onSettlingClick = onSettlingClick,
                onSettledClick = onSettledClick
            )
        }
    }
}

/**
 * 卡片头部 - 工程名称(左) + 日期/单号(右) + 底部分隔线
 * 布局参考HTML .card-header：space-between + 底部1px分隔线
 */
@Composable
private fun CardHeader(project: ProjectUiModel) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // 工程名称（18sp SemiBold）
            Text(
                text = project.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 日期和工程ID（右对齐）
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = DateFormatter.formatDate(project.createdAt),
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 工程ID：浅绿背景胶囊（替代原硬编码假单号）
                Text(
                    text = "#${project.id}",
                    fontSize = 12.sp,
                    color = AppColors.TextSecondary,
                    modifier = Modifier
                        .background(AppColors.Green50, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        // 底部分隔线：对齐HTML .card-header border-bottom 1px solid #eee
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = AppColors.Green100.copy(alpha = 0.5f), thickness = 1.dp)
    }
}

/**
 * 金额区 - 工费总额(左) + 人均/日均工费(右)
 * 布局参考HTML .salary-row：左右两栏，大字绿色(24sp Bold)
 * 无背景框，通过间距区分
 *
 * 右侧根据分配方式适配显示：
 * - 平均分配(average)：显示"人均工费"= 总额 / 人数
 * - 按工日分配(work_days)：显示"日均工费"= 总额 / 总工日，下方追加各人员日均工费明细和总工日
 */
@Composable
private fun AmountSection(project: ProjectUiModel) {
    val isWorkDays = project.salaryDistribution == "work_days"
    // perAmountText、totalAmountText 已在 ViewModel 预计算，Composable 直接使用
    val label = if (isWorkDays) "日均工费" else "人均工费"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // 顶部行：工费总额(左) + 人均/日均工费(右)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            // 工费总额
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "工费总额",
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = project.totalAmountText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Green400
                )
            }
            // 右侧：人均/日均工费（右对齐）
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = AppColors.TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¥${project.perAmountText}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Green400
                )
            }
        }

        // 按工日分配模式下追加显示：各施工人员独立日均工费 + 总工日
        if (isWorkDays && project.workerWageDetails.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = AppColors.Green50
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 总工日行（使用预格式化文本）
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "总工日",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = AppColors.Green600
                        )
                        Text(
                            text = project.totalWorkdaysText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppColors.Green400
                        )
                    }
                    // 各施工人员日均工费明细（使用预格式化文本，无Composable内部计算）
                    project.workerWageDetails.forEach { detail ->
                        if (detail.workdays > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = detail.displayText,
                                    fontSize = 11.sp,
                                    color = AppColors.TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = detail.wageText,
                                    fontSize = 11.sp,
                                    color = AppColors.Green400,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 信息区 - 人员信息 + 分配方式
 * 布局参考HTML .info-row：纵向排列，单行15sp
 */
@Composable
private fun InfoSection(project: ProjectUiModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 人员信息（使用预格式化的 workerNamesText，避免Composable内部joinToString）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "👷", fontSize = 15.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "人员：${project.workerNamesText}",
                fontSize = 15.sp,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 分配方式
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "⚖️", fontSize = 15.sp)
            Spacer(modifier = Modifier.width(6.dp))
            // 后端字段值：average=平均分配，work_days=按工日分配
            // 总工日和各人员日均工费明细在按工日分配时于金额区下方展示，此处不重复
            val distributionText = when (project.salaryDistribution) {
                "average" -> "平均分配"
                "work_days" -> "按工日分配"
                null -> "待设置"
                else -> project.salaryDistribution
            }
            Text(
                text = "分配：$distributionText",
                fontSize = 15.sp,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 按钮区 - 状态操作按钮 + 查看详情按钮（水平并排）
 * 布局参考HTML .btn-row：两按钮并排各占一半，圆角8dp
 * 状态映射：施工中→确认完工(实心绿)，已完工+统计中→统计中(描边)，已完工+已结算→已结算(描边)，其他→已完工(描边禁用)
 */
@Composable
private fun ButtonSection(
    project: ProjectUiModel,
    canConfirmComplete: Boolean,
    onViewDetail: () -> Unit,
    onConfirmComplete: () -> Unit,
    onSettlingClick: () -> Unit,
    onSettledClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：状态操作按钮
        when {
            project.status == "constructing" && canConfirmComplete -> {
                // 施工中 → 确认完工（实心绿色按钮）—— 资料员不可操作，走只读分支
                Button(
                    onClick = onConfirmComplete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Green400,
                        contentColor = Color.White
                    )
                ) {
                    Text("确认完工", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            project.status == "constructing" && !canConfirmComplete -> {
                // 资料员查看施工中工程：显示只读"施工中"状态按钮（禁用，不可操作）
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    enabled = false,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextTertiary
                    )
                ) {
                    Text("施工中", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            project.status == "completed" && project.settlementStatus == "settling" -> {
                // 已完工 + 统计中（描边按钮）
                OutlinedButton(
                    onClick = onSettlingClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = AppColors.Green50,
                        contentColor = AppColors.TextPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Text("统计中", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            project.status == "completed" && project.settlementStatus == "settled" -> {
                // 已完工 + 已结算（灰色背景描边按钮，与统计中按钮区分）
                OutlinedButton(
                    onClick = onSettledClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF5F5F5),
                        contentColor = AppColors.TextSecondary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Text("已结算", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            else -> {
                // 其他状态（描边禁用按钮）
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    enabled = false,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextTertiary
                    )
                ) {
                    Text("已完工", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // 右侧：查看详情按钮（描边样式）
        OutlinedButton(
            onClick = onViewDetail,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = AppColors.TextPrimary
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
        ) {
            Text("查看详情", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}
