package com.salary.manager.feature.home.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salary.core.design.theme.AppColors

/**
 * 筛选栏组件 - 支持状态+结算状态级联筛选
 * @param selectedStatus 当前选中的工程状态
 * @param selectedSettlementStatus 当前选中的结算状态
 * @param onStatusChange 工程状态变更回调
 * @param onSettlementStatusChange 结算状态变更回调
 */
@Composable
fun FilterBar(
    selectedStatus: String?,
    selectedSettlementStatus: String?,
    onStatusChange: (String?) -> Unit,
    onSettlementStatusChange: (String?) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // 工程状态筛选
        StatusFilterRow(
            items = listOf(null to "全部", "preparing" to "备料中", "constructing" to "施工中", "completed" to "已完工", "canceled" to "已取消"),
            selected = selectedStatus,
            onSelect = onStatusChange
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 结算状态筛选
        StatusFilterRow(
            items = listOf(null to "全部结算", "unsettled" to "未结算", "settling" to "统计中", "settled" to "已结算"),
            selected = selectedSettlementStatus,
            onSelect = onSettlementStatusChange
        )
    }
}

/**
 * 单行筛选标签
 */
@Composable
private fun StatusFilterRow(
    items: List<Pair<String?, String>>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (value, label) ->
            val isSelected = selected == value
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(value) },
                label = {
                    Text(
                        label,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.Green50,
                    selectedLabelColor = AppColors.Green500,
                    containerColor = AppColors.SurfaceVariant,
                    labelColor = AppColors.TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = AppColors.SurfaceVariant,
                    selectedBorderColor = AppColors.Green400,
                    enabled = true,
                    selected = isSelected
                )
            )
        }
    }
}
