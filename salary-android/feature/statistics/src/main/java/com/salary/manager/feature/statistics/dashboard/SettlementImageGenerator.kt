package com.salary.manager.feature.statistics.dashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.salary.core.network.api.ConstructionPlanDto
import com.salary.core.network.api.SettlementHistoryDto
import com.salary.core.network.api.SubprojectDto

/**
 * 结算单图片生成器
 * 使用Android Canvas在Bitmap上绘制结算单，样式与程序内显示的表格一致
 *
 * 绘制内容（从上到下）：
 * 1. 标题行（绿色渐变背景）：用户名 + 结算时间段
 * 2. 表头行：序号 | 工程名称 | 各施工方案 | 总额
 * 3. 工程数据行（含子项目明细）
 * 4. 合计行
 * 5. 单价行
 * 6. 总计行
 * 7. 预支行
 * 8. 总额行
 *
 * 样式参数对齐程序内Compose表格：
 * - 列宽：序号80px | 工程名260px | 方案列220px | 总额220px
 * - 行高：标题80px | 数据行56px
 * - 字号：标题32px | 表头26px | 数据24px
 * - 颜色：标题绿渐变 | 表头淡蓝#E3F2FD | 工程行淡绿#E8F5E9 | 数据行淡灰#F5F5F5
 *
 * 高清输出：基于物理像素直接绘制，避免模糊
 */
object SettlementImageGenerator {

    // ========== 列宽（px） ==========
    private const val COL_INDEX = 80f       // 序号列
    private const val COL_PROJECT = 260f    // 工程名称列
    private const val COL_PLAN = 220f       // 施工方案列
    private const val COL_TOTAL = 220f      // 总额列

    // ========== 行高（px） ==========
    private const val TITLE_HEIGHT = 80f
    private const val EMPTY_ROW_HEIGHT = 30f
    private const val ROW_HEIGHT = 56f

    // ========== 字号（px） ==========
    private const val TITLE_FONT_SIZE = 32f
    private const val HEADER_FONT_SIZE = 26f
    private const val DATA_FONT_SIZE = 24f

    // ========== 颜色 ==========
    private val COLOR_TITLE_BG_START = Color.parseColor("#84CC16")
    private val COLOR_TITLE_BG_END = Color.parseColor("#65A30D")
    private val COLOR_TITLE_TEXT = Color.WHITE
    private val COLOR_HEADER_BG = Color.parseColor("#E3F2FD")
    private val COLOR_PROJECT_BG = Color.parseColor("#E8F5E9")
    private val COLOR_DATA_BG = Color.parseColor("#F5F5F5")
    private val COLOR_TOTAL_BG = Color.parseColor("#E3F2FD")
    private val COLOR_BORDER = Color.parseColor("#666666")
    private val COLOR_TEXT = Color.parseColor("#333333")

    /**
     * 生成结算单图片
     *
     * @param settlement 结算历史数据
     * @param constructionPlans 施工方案列表
     * @param userName 用户名（用于标题）
     * @param getUnitName 单位名称转换函数
     * @param formatNumber 数字格式化函数
     * @return 生成的Bitmap
     */
    fun generate(
        settlement: SettlementHistoryDto,
        constructionPlans: List<ConstructionPlanDto>,
        userName: String,
        getUnitName: (String?) -> String,
        formatNumber: (Double?) -> String
    ): Bitmap {
        // 计算总行数
        val uniqueProjects = settlement.projects.distinctBy { it.id }
        var totalDataRows = 0
        totalDataRows += 1 // 表头
        uniqueProjects.forEach { project ->
            totalDataRows += 1 // 工程汇总行
            totalDataRows += project.subprojects.size // 子项目明细行
        }
        totalDataRows += 1 // 单价行
        totalDataRows += 1 // 合计行
        totalDataRows += 1 // 总计行
        totalDataRows += settlement.advances.size // 预支行
        totalDataRows += 1 // 总额行

        // 计算图片尺寸
        val planCount = constructionPlans.size
        val bitmapWidth = (COL_INDEX + COL_PROJECT + COL_PLAN * planCount + COL_TOTAL).toInt()
        val bitmapHeight = (TITLE_HEIGHT + EMPTY_ROW_HEIGHT + totalDataRows * ROW_HEIGHT).toInt()

        // 创建Bitmap和Canvas
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // 创建画笔
        val borderPaint = Paint().apply {
            color = COLOR_BORDER
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT
            textAlign = Paint.Align.CENTER
        }
        val bgPaint = Paint().apply { style = Paint.Style.FILL }

        var y = 0f

        // ========== 1. 标题行 ==========
        val titleGradient = LinearGradient(
            0f, 0f, bitmapWidth.toFloat(), TITLE_HEIGHT,
            COLOR_TITLE_BG_START, COLOR_TITLE_BG_END,
            Shader.TileMode.CLAMP
        )
        bgPaint.shader = titleGradient
        canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + TITLE_HEIGHT, bgPaint)
        bgPaint.shader = null

        textPaint.color = COLOR_TITLE_TEXT
        textPaint.textSize = TITLE_FONT_SIZE
        textPaint.typeface = Typeface.DEFAULT_BOLD
        val titleText = buildTitleText(userName, settlement.startMonth, settlement.endMonth)
        drawTextCentered(canvas, textPaint, titleText, bitmapWidth / 2f, y + TITLE_HEIGHT / 2f)
        y += TITLE_HEIGHT

        // ========== 2. 空行 ==========
        canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + EMPTY_ROW_HEIGHT, borderPaint)
        y += EMPTY_ROW_HEIGHT

        // ========== 3. 表头行 ==========
        bgPaint.color = COLOR_HEADER_BG
        canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
        drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)

        textPaint.color = COLOR_TEXT
        textPaint.textSize = HEADER_FONT_SIZE
        textPaint.typeface = Typeface.DEFAULT_BOLD
        var x = 0f
        drawTextInCell(canvas, textPaint, "序号", x, y, COL_INDEX, ROW_HEIGHT)
        x += COL_INDEX
        drawTextInCell(canvas, textPaint, "工程名称", x, y, COL_PROJECT, ROW_HEIGHT)
        x += COL_PROJECT
        constructionPlans.forEach { plan ->
            drawTextInCell(canvas, textPaint, plan.name, x, y, COL_PLAN, ROW_HEIGHT)
            x += COL_PLAN
        }
        drawTextInCell(canvas, textPaint, "总额", x, y, COL_TOTAL, ROW_HEIGHT)
        y += ROW_HEIGHT

        // ========== 4. 工程数据行 ==========
        textPaint.textSize = DATA_FONT_SIZE
        textPaint.typeface = Typeface.DEFAULT
        var index = 1
        for (project in uniqueProjects) {
            // 工程汇总行（淡绿背景）
            bgPaint.color = COLOR_PROJECT_BG
            canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
            drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)

            textPaint.typeface = Typeface.DEFAULT_BOLD
            x = 0f
            drawTextInCell(canvas, textPaint, index.toString(), x, y, COL_INDEX, ROW_HEIGHT)
            x += COL_INDEX
            drawTextInCell(canvas, textPaint, project.projectName, x, y, COL_PROJECT, ROW_HEIGHT)
            x += COL_PROJECT
            // 各方案数量
            constructionPlans.forEach { plan ->
                val planQty = project.planQuantities[plan.id.toString()]
                val cellText = if (planQty != null) {
                    "${formatNumber(planQty.totalQuantity)}${getUnitName(plan.unit)}"
                } else "-"
                drawTextInCell(canvas, textPaint, cellText, x, y, COL_PLAN, ROW_HEIGHT)
                x += COL_PLAN
            }
            drawTextInCell(canvas, textPaint, "-", x, y, COL_TOTAL, ROW_HEIGHT)
            y += ROW_HEIGHT

            // 子项目明细行（淡灰背景）
            textPaint.typeface = Typeface.DEFAULT
            for (sub in project.subprojects) {
                bgPaint.color = COLOR_DATA_BG
                canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
                drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)

                x = 0f
                drawTextInCell(canvas, textPaint, "", x, y, COL_INDEX, ROW_HEIGHT)
                x += COL_INDEX
                // 子项目显示：空间类型 - 方案名
                val subName = buildSubprojectName(sub)
                drawTextInCell(canvas, textPaint, subName, x, y, COL_PROJECT, ROW_HEIGHT)
                x += COL_PROJECT
                constructionPlans.forEach { plan ->
                    val cellText = if (sub.planId == plan.id) {
                        "${formatNumber(sub.userQuantity)}${getUnitName(sub.unit)}"
                    } else "-"
                    drawTextInCell(canvas, textPaint, cellText, x, y, COL_PLAN, ROW_HEIGHT)
                    x += COL_PLAN
                }
                drawTextInCell(canvas, textPaint, "¥${formatNumber(sub.userAmount)}", x, y, COL_TOTAL, ROW_HEIGHT)
                y += ROW_HEIGHT
            }
            index++
        }

        // ========== 5. 合计行 ==========
        bgPaint.color = COLOR_DATA_BG
        canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
        drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)
        x = 0f
        drawTextInCell(canvas, textPaint, "", x, y, COL_INDEX, ROW_HEIGHT)
        x += COL_INDEX
        drawTextInCell(canvas, textPaint, "合计", x, y, COL_PROJECT, ROW_HEIGHT)
        x += COL_PROJECT
        constructionPlans.forEach { plan ->
            val pt = settlement.planTotals[plan.id.toString()]
            val cellText = if (pt != null) "${formatNumber(pt.totalQuantity)}${getUnitName(plan.unit)}" else "-"
            drawTextInCell(canvas, textPaint, cellText, x, y, COL_PLAN, ROW_HEIGHT)
            x += COL_PLAN
        }
        drawTextInCell(canvas, textPaint, "-", x, y, COL_TOTAL, ROW_HEIGHT)
        y += ROW_HEIGHT

        // ========== 6. 单价行 ==========
        bgPaint.color = COLOR_DATA_BG
        canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
        drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        x = 0f
        drawTextInCell(canvas, textPaint, "", x, y, COL_INDEX, ROW_HEIGHT)
        x += COL_INDEX
        drawTextInCell(canvas, textPaint, "单价", x, y, COL_PROJECT, ROW_HEIGHT)
        x += COL_PROJECT
        constructionPlans.forEach { plan ->
            val cellText = "¥${formatNumber(plan.price)}/${getUnitName(plan.unit)}"
            drawTextInCell(canvas, textPaint, cellText, x, y, COL_PLAN, ROW_HEIGHT)
            x += COL_PLAN
        }
        drawTextInCell(canvas, textPaint, "-", x, y, COL_TOTAL, ROW_HEIGHT)
        y += ROW_HEIGHT

        // ========== 7. 总计行 ==========
        bgPaint.color = COLOR_TOTAL_BG
        canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
        drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)
        x = 0f
        drawTextInCell(canvas, textPaint, "", x, y, COL_INDEX, ROW_HEIGHT)
        x += COL_INDEX
        drawTextInCell(canvas, textPaint, "总计", x, y, COL_PROJECT, ROW_HEIGHT)
        x += COL_PROJECT
        constructionPlans.forEach { plan ->
            val pt = settlement.planTotals[plan.id.toString()]
            val cellText = if (pt != null) "¥${formatNumber(pt.totalAmount)}" else "-"
            drawTextInCell(canvas, textPaint, cellText, x, y, COL_PLAN, ROW_HEIGHT)
            x += COL_PLAN
        }
        drawTextInCell(canvas, textPaint, "¥${formatNumber(settlement.grandTotal)}", x, y, COL_TOTAL, ROW_HEIGHT)
        y += ROW_HEIGHT

        // ========== 8. 预支行 ==========
        textPaint.typeface = Typeface.DEFAULT
        for (advance in settlement.advances) {
            bgPaint.color = COLOR_DATA_BG
            canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
            drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)
            x = 0f
            drawTextInCell(canvas, textPaint, "", x, y, COL_INDEX, ROW_HEIGHT)
            x += COL_INDEX
            drawTextInCell(canvas, textPaint, "${formatDate(advance.advanceDate)} 预支", x, y, COL_PROJECT, ROW_HEIGHT)
            x += COL_PROJECT
            constructionPlans.forEach { _ ->
                drawTextInCell(canvas, textPaint, "-", x, y, COL_PLAN, ROW_HEIGHT)
                x += COL_PLAN
            }
            drawTextInCell(canvas, textPaint, "¥${formatNumber(advance.advanceAmount)}", x, y, COL_TOTAL, ROW_HEIGHT)
            y += ROW_HEIGHT
        }

        // ========== 9. 总额行 ==========
        bgPaint.color = COLOR_TOTAL_BG
        canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
        drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        x = 0f
        drawTextInCell(canvas, textPaint, "", x, y, COL_INDEX, ROW_HEIGHT)
        x += COL_INDEX
        drawTextInCell(canvas, textPaint, "总额", x, y, COL_PROJECT, ROW_HEIGHT)
        x += COL_PROJECT
        constructionPlans.forEach { _ ->
            drawTextInCell(canvas, textPaint, "-", x, y, COL_PLAN, ROW_HEIGHT)
            x += COL_PLAN
        }
        drawTextInCell(canvas, textPaint, "¥${formatNumber(settlement.finalTotal)}", x, y, COL_TOTAL, ROW_HEIGHT)

        return bitmap
    }

    /**
     * 在单元格内绘制居中文本
     */
    private fun drawTextInCell(
        canvas: Canvas,
        paint: Paint,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float
    ) {
        if (text.isEmpty()) return
        // 文本水平垂直居中
        val centerX = x + width / 2f
        val centerY = y + height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, centerX, centerY, paint)
    }

    /**
     * 绘制居中文本（绝对坐标）
     */
    private fun drawTextCentered(
        canvas: Canvas,
        paint: Paint,
        text: String,
        centerX: Float,
        centerY: Float
    ) {
        val adjustedY = centerY - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, centerX, adjustedY, paint)
    }

    /**
     * 绘制行边框（含列分隔线）
     */
    private fun drawRowBorder(
        canvas: Canvas,
        paint: Paint,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        planCount: Int
    ) {
        // 外边框
        canvas.drawRect(left, top, right, bottom, paint)
        // 列分隔线
        var x = left + COL_INDEX
        canvas.drawLine(x, top, x, bottom, paint)
        x += COL_PROJECT
        canvas.drawLine(x, top, x, bottom, paint)
        for (i in 0 until planCount) {
            x += COL_PLAN
            canvas.drawLine(x, top, x, bottom, paint)
        }
    }

    /**
     * 构建标题文本：用户名 + 开始日期 至 结束日期
     */
    private fun buildTitleText(userName: String, startMonth: String, endMonth: String): String {
        val start = formatDate(startMonth)
        val end = formatDate(endMonth)
        return "$userName $start 至 $end"
    }

    /**
     * 构建子项目名称：空间类型 - 方案名
     */
    private fun buildSubprojectName(sub: SubprojectDto): String {
        val parts = mutableListOf<String>()
        if (sub.spaceTypeName.isNotBlank()) parts.add(sub.spaceTypeName)
        if (sub.planName.isNotBlank()) parts.add(sub.planName)
        return parts.joinToString(" - ")
    }

    /**
     * 格式化日期：支持 YYYYMM 和 YYYY-MM-DD 格式
     */
    private fun formatDate(input: String): String {
        if (input.isBlank()) return ""
        return try {
            when {
                input.length == 6 && input.all { it.isDigit() } -> {
                    "${input.substring(0, 4)}-${input.substring(4, 6)}"
                }
                input.length >= 10 -> input.substring(0, 10)
                else -> input
            }
        } catch (e: Exception) {
            input
        }
    }
}
