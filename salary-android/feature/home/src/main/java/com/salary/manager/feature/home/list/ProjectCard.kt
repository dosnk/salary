package com.salary.manager.feature.home.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.common.util.AmountFormatter
import com.salary.core.common.util.DateFormatter
import com.salary.core.design.theme.AppColors

/**
 * 工程卡片组件 - 对齐Vue前端Projects.vue卡片设计
 * 包含：工程名称+日期+单号、工费总额+人均工费、人员信息、分配方式、状态按钮+查看详情
 * @param project 工程UI数据模型
 * @param onNavigateToProject 点击查看详情回调
 * @param onConfirmComplete 点击确认完工回调
 * @param onSettlingClick 点击统计中回调
 * @param onSettledClick 点击已结算回调
 */
@Composable
fun ProjectCard(
    project: ProjectUiModel,
    onNavigateToProject: () -> Unit,
    onConfirmComplete: () -> Unit = {},
    onSettlingClick: () -> Unit = {},
    onSettledClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppColors.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 卡片头部：工程名称 + 创建日期 + 单号
            CardHeader(project = project)

            Spacer(modifier = Modifier.height(12.dp))

            // 金额区：工费总额 + 人均工费
            AmountSection(project = project)

            Spacer(modifier = Modifier.height(12.dp))

            // 信息区：人员 + 分配方式
            InfoSection(project = project)

            Spacer(modifier = Modifier.height(16.dp))

            // 按钮区：状态按钮 + 查看详情
            ButtonSection(
                project = project,
                onViewDetail = onNavigateToProject,
                onConfirmComplete = onConfirmComplete,
                onSettlingClick = onSettlingClick,
                onSettledClick = onSettledClick
            )
        }
    }
}

/**
 * 卡片头部 - 工程名称、创建日期、单号
 */
@Composable
private fun CardHeader(project: ProjectUiModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // 工程名称
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
        // 日期和单号
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = DateFormatter.formatDate(project.createdAt),
                fontSize = 13.sp,
                color = AppColors.TextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "单号:668${project.id}",
                fontSize = 10.sp,
                color = AppColors.TextTertiary,
                modifier = Modifier
                    .background(AppColors.Green50, RoundedCornerShape(12.dp))
                    .border(1.dp, AppColors.Green100, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

/**
 * 金额区 - 工费总额（大字绿色）+ 人均工费（浅绿背景）
 */
@Composable
private fun AmountSection(project: ProjectUiModel) {
    Column {
        HorizontalDivider(color = AppColors.Green100, thickness = 1.dp)
        Spacer(modifier = Modifier.height(12.dp))
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
                    color = AppColors.TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = AmountFormatter.format(project.totalAmount),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Green400
                )
            }
            // 人均工费
            Box(
                modifier = Modifier
                    .background(AppColors.Green50, RoundedCornerShape(12.dp))
                    .border(1.dp, AppColors.Green100, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = "人均工费",
                        fontSize = 13.sp,
                        color = AppColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val perPerson = if (project.workerCount > 0) {
                        val amount = project.totalAmount.toDoubleOrNull() ?: 0.0
                        AmountFormatter.formatPlain(String.format("%.2f", amount / project.workerCount))
                    } else "0.00"
                    Text(
                        text = "¥$perPerson",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.Green400
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = AppColors.Green100, thickness = 1.dp)
    }
}

/**
 * 信息区 - 人员信息 + 分配方式
 */
@Composable
private fun InfoSection(project: ProjectUiModel) {
    // 人员信息
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = "👷", fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "人员：${if (project.workerNames.isNotEmpty()) project.workerNames.joinToString("、") else "暂无"}",
            fontSize = 14.sp,
            color = AppColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }

    // 分配方式
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = "⚖️", fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "分配：${if (project.salaryDistribution == "average") "平均分配" else "按工时分配"}",
            fontSize = 14.sp,
            color = AppColors.TextPrimary
        )
    }
}

/**
 * 按钮区 - 状态操作按钮 + 查看详情按钮
 * 对齐Vue前端：施工中→确认完工，已完工+统计中→统计中，已完工+已结算→已结算，其他→已完工
 */
@Composable
private fun ButtonSection(
    project: ProjectUiModel,
    onViewDetail: () -> Unit,
    onConfirmComplete: () -> Unit,
    onSettlingClick: () -> Unit,
    onSettledClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 状态操作按钮
        when {
            project.status == "constructing" -> {
                // 施工中 → 确认完工
                Button(
                    onClick = onConfirmComplete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Green400,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text("确认完工", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            project.status == "completed" && project.settlementStatus == "settling" -> {
                // 已完工 + 统计中
                OutlinedButton(
                    onClick = onSettlingClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Text("统计中", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            project.status == "completed" && project.settlementStatus == "settled" -> {
                // 已完工 + 已结算
                OutlinedButton(
                    onClick = onSettledClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextPrimary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(enabled = true)
                ) {
                    Text("已结算", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            else -> {
                // 其他状态
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = false,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = AppColors.TextTertiary
                    )
                ) {
                    Text("已完工", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 查看详情按钮
        OutlinedButton(
            onClick = onViewDetail,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = AppColors.TextPrimary
            ),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true)
        ) {
            Text("查看详情", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
