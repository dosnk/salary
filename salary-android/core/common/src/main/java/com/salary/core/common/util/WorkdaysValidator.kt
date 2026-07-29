package com.salary.core.common.util

import kotlin.math.abs

/**
 * 工日校验工具
 * 校验各施工人员工日之和与总工日输入是否一致（允许 0.01 浮点误差）
 */
object WorkdaysValidator {

    /**
     * 计算工日校验提示文案
     *
     * @param salaryDistribution 分配方式（仅 "work_days" 时校验，其他返回空）
     * @param totalWorkdaysInput 总工日输入字符串
     * @param selectedConstructorIds 已选施工人员ID集合
     * @param workerWorkdays 施工人员工日映射（ID → 工日字符串，空值按 1 计算）
     * @return 校验提示文案（空字符串表示无需提示）
     */
    fun validate(
        salaryDistribution: String,
        totalWorkdaysInput: String,
        selectedConstructorIds: Collection<Int>,
        workerWorkdays: Map<Int, String>
    ): String {
        if (salaryDistribution != "work_days") return ""
        val input = totalWorkdaysInput.trim()
        if (input.isEmpty()) return ""
        val targetTotal = input.toDoubleOrNull()
        if (targetTotal == null || targetTotal <= 0) return "总工日输入无效"
        if (selectedConstructorIds.isEmpty()) return ""
        // 计算各施工人员工日之和（空值按 1 计算）
        val sum = selectedConstructorIds.sumOf { id ->
            val v = workerWorkdays[id]?.trim()
            val parsed = v?.toDoubleOrNull()
            if (parsed != null && parsed > 0) parsed else 1.0
        }
        val diff = abs(sum - targetTotal)
        val sumStr = String.format("%.2f", sum)
        val targetStr = String.format("%.2f", targetTotal)
        return if (diff > 0.01) {
            "工日合计 $sumStr 与总工日 $targetStr 不一致"
        } else {
            "工日合计 $sumStr 与总工日一致 ✓"
        }
    }
}
