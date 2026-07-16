package com.salary.manager.feature.statistics.dashboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import com.salary.core.network.api.AdvanceDataDto
import com.salary.core.network.api.ConstructionPlanDto
import com.salary.core.network.api.SalaryProjectDto
import com.salary.core.network.api.SettlementHistoryDto
import com.salary.core.network.api.SubprojectDto

/**
 * 结算单图片生成器
 * 使用Android Canvas在Bitmap上绘制结算单，样式与程序内显示的表格一致
 *
 * 绘制内容（从上到下）：
 * 1. 标题行（绿色渐变背景）：用户名 + 结算时间段 + 页码
 * 2. 表头行：序号 | 工程名称 | 各施工方案 | 总额
 * 3. 工程数据行（含子项目明细）
 * 4. 合计行（仅最后一页）
 * 5. 单价行（仅最后一页）
 * 6. 总计行（仅最后一页）
 * 7. 预支行（仅最后一页）
 * 8. 总额行（仅最后一页）
 *
 * 样式参数对齐程序内Compose表格：
 * - 列宽：序号80px | 工程名260px | 方案列220px | 总额220px
 * - 行高：标题80px | 数据行56px
 * - 字号：标题32px | 表头26px | 数据24px
 * - 颜色：标题绿渐变 | 表头淡蓝#E3F2FD | 工程行淡绿#E8F5E9 | 数据行淡灰#F5F5F5
 *
 * 自动分页：按工程数量分页，每页最多30份工程，工程汇总行与其所有子项目明细行视为一个整体绝不拆分。
 * 每页都包含标题和表头，最后一页包含合计/单价/总计/预支/总额行。
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

    // ========== 内存保护 ==========
    // 单页 Bitmap 最大像素高度，超过此值自动分页（防止 OOM）
    private const val MAX_PAGE_HEIGHT = 3000
    // Bitmap 单边最大像素数，绝对上限（防止 OOM）
    private const val MAX_BITMAP_DIMENSION = 5000
    // Bitmap 预估内存阈值（字节），超过此值直接报错
    private const val MAX_BITMAP_BYTES = 40L * 1024 * 1024 // 40MB

    // ========== 分页规则 ==========
    // 每页最多工程数量：以"工程数量"为分页依据，避免同一工程的子项目被截断到不同页
    // 用户要求：30份工程后才分页，工程汇总行与其所有子项目明细行视为一个整体绝不拆分
    private const val MAX_PROJECTS_PER_PAGE = 30

    /**
     * 行数据类型（密封类）
     * 用于分页时统一处理不同类型的行
     */
    private sealed class RowData {
        /** 工程汇总行 */
        data class ProjectSummary(val index: Int, val project: SalaryProjectDto) : RowData()
        /** 子项目明细行 */
        data class SubprojectRow(val sub: SubprojectDto) : RowData()
        /** 合计行 */
        data object Total : RowData()
        /** 单价行 */
        data object Price : RowData()
        /** 总计行 */
        data object GrandTotal : RowData()
        /** 预支行 */
        data class AdvanceRow(val advance: AdvanceDataDto) : RowData()
        /** 总额行 */
        data object Final : RowData()
    }

    /**
     * 生成结算单图片（自动分页）
     *
     * 当数据行数超过单页容量时自动拆分为多页，每页都包含标题和表头。
     * 最后一页包含合计/单价/总计/预支/总额行。
     * 工程汇总行与子项目明细行尽量保持完整不拆分到不同页。
     *
     * @param settlement 结算历史数据
     * @param constructionPlans 施工方案列表
     * @param userName 用户名（用于标题）
     * @param getUnitName 单位名称转换函数
     * @param formatNumber 数字格式化函数
     * @return 生成的Bitmap列表（每页一个Bitmap，至少返回一个）
     */
    fun generate(
        settlement: SettlementHistoryDto,
        constructionPlans: List<ConstructionPlanDto>,
        userName: String,
        getUnitName: (String?) -> String,
        formatNumber: (Double?) -> String
    ): List<Bitmap> {
        // 1. 收集所有数据行
        val allRows = collectAllRows(settlement)

        // 2. 按工程数量分页：每页最多 MAX_PROJECTS_PER_PAGE（30）份工程
        //    以"工程数量"为分页依据，工程汇总行与其所有子项目明细行视为整体绝不拆分
        val pages = paginateRows(allRows)

        // 3. 计算图片宽度
        val planCount = constructionPlans.size
        val bitmapWidth = (COL_INDEX + COL_PROJECT + COL_PLAN * planCount + COL_TOTAL).toInt()

        // 4. 宽度预检
        if (bitmapWidth > MAX_BITMAP_DIMENSION) {
            throw IllegalStateException(
                "结算单图片宽度过大（${bitmapWidth}px），超过最大限制 $MAX_BITMAP_DIMENSION 像素。" +
                "请减少施工方案数量后重试。"
            )
        }

        // 5. 逐页生成 Bitmap
        val totalPages = pages.size
        return pages.mapIndexed { pageIndex, rows ->
            drawPage(
                rows = rows,
                pageIndex = pageIndex,
                totalPages = totalPages,
                bitmapWidth = bitmapWidth,
                settlement = settlement,
                constructionPlans = constructionPlans,
                userName = userName,
                getUnitName = getUnitName,
                formatNumber = formatNumber
            )
        }
    }

    /**
     * 收集所有数据行
     */
    private fun collectAllRows(settlement: SettlementHistoryDto): List<RowData> {
        val rows = mutableListOf<RowData>()
        val uniqueProjects = settlement.projects.distinctBy { it.id }
        var index = 1
        for (project in uniqueProjects) {
            rows.add(RowData.ProjectSummary(index, project))
            for (sub in project.subprojects) {
                rows.add(RowData.SubprojectRow(sub))
            }
            index++
        }
        // 汇总行（仅在最后一页绘制）
        rows.add(RowData.Total)
        rows.add(RowData.Price)
        rows.add(RowData.GrandTotal)
        for (advance in settlement.advances) {
            rows.add(RowData.AdvanceRow(advance))
        }
        rows.add(RowData.Final)
        return rows
    }

    /**
     * 按工程数量分页：每页最多 MAX_PROJECTS_PER_PAGE（30）份工程
     *
     * 规则：
     * 1. 以"工程数量"为分页依据，不关心子项目数量，避免同一工程的子项目被截断到不同页
     * 2. 每页最多 MAX_PROJECTS_PER_PAGE 份工程，超过则换页
     * 3. 工程汇总行 + 其所有子项目明细行视为一个整体，绝不拆分到不同页
     * 4. 兜底保护：若单个工程组本身超过单页最大可用高度，且当前页已有工程，
     *    则先换页让超大工程独占一页（此为极端情况，正常场景不会触发）
     * 5. 汇总行（合计/单价/总计/预支/总额）跟随最后一页的工程组
     */
    private fun paginateRows(allRows: List<RowData>): List<List<RowData>> {
        // 每页固定占用：标题(80) + 空行(30) + 表头(56) = 166px
        val pageFixedHeight = TITLE_HEIGHT + EMPTY_ROW_HEIGHT + ROW_HEIGHT
        // 单页可用于数据行的高度（兜底保护用）
        val pageAvailableHeight = MAX_PAGE_HEIGHT - pageFixedHeight

        val pages = mutableListOf<MutableList<RowData>>()
        var currentPage = mutableListOf<RowData>()
        var projectCountInCurrentPage = 0

        var i = 0
        while (i < allRows.size) {
            val row = allRows[i]

            if (row is RowData.ProjectSummary) {
                // 计算整个工程组的行数（工程汇总行 + 子项目明细行）
                var j = i + 1
                while (j < allRows.size && allRows[j] is RowData.SubprojectRow) {
                    j++
                }
                val groupSize = j - i

                // 判断是否需要换页：
                // 1. 当前页工程数已达上限 MAX_PROJECTS_PER_PAGE（30份）
                // 2. 兜底保护：单个工程组高度超过单页可用高度，且当前页已有工程，换页让大工程独占一页
                val groupExceedsPage = groupSize * ROW_HEIGHT > pageAvailableHeight
                val shouldBreak = (projectCountInCurrentPage >= MAX_PROJECTS_PER_PAGE) ||
                    (groupExceedsPage && projectCountInCurrentPage > 0)

                if (shouldBreak && currentPage.isNotEmpty()) {
                    pages.add(currentPage)
                    currentPage = mutableListOf()
                    projectCountInCurrentPage = 0
                }

                // 添加整个工程组（保持完整性，绝不拆分到不同页）
                for (k in i until j) {
                    currentPage.add(allRows[k])
                }
                projectCountInCurrentPage++
                i = j
            } else {
                // 汇总行（Total/Price/GrandTotal/AdvanceRow/Final）：跟随最后一页工程组，逐行处理
                currentPage.add(row)
                i++
            }
        }

        if (currentPage.isNotEmpty()) {
            pages.add(currentPage)
        }

        // 确保至少返回一页（空数据时返回空页）
        if (pages.isEmpty()) {
            pages.add(mutableListOf())
        }
        return pages
    }

    /**
     * 绘制单页
     */
    private fun drawPage(
        rows: List<RowData>,
        pageIndex: Int,
        totalPages: Int,
        bitmapWidth: Int,
        settlement: SettlementHistoryDto,
        constructionPlans: List<ConstructionPlanDto>,
        userName: String,
        getUnitName: (String?) -> String,
        formatNumber: (Double?) -> String
    ): Bitmap {
        val planCount = constructionPlans.size
        val bitmapHeight = (TITLE_HEIGHT + EMPTY_ROW_HEIGHT + ROW_HEIGHT + rows.size * ROW_HEIGHT).toInt()

        // ========== 内存预检 ==========
        if (bitmapHeight > MAX_BITMAP_DIMENSION) {
            throw IllegalStateException(
                "结算单图片高度过大（${bitmapHeight}px），超过最大限制 $MAX_BITMAP_DIMENSION 像素。"
            )
        }
        val estimatedBytes = bitmapWidth.toLong() * bitmapHeight.toLong() * 2L // RGB_565: 2字节/像素
        if (estimatedBytes > MAX_BITMAP_BYTES) {
            val mb = estimatedBytes / 1024 / 1024
            throw IllegalStateException(
                "结算单图片内存占用过大（约 ${mb}MB），超过安全阈值 ${MAX_BITMAP_BYTES / 1024 / 1024}MB。"
            )
        }

        // 创建Bitmap和Canvas
        // 使用 RGB_565（每像素 2 字节）替代默认 ARGB_8888（每像素 4 字节），内存减半
        // 表格图片无透明度需求，RGB_565 视觉效果无明显差异
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565)
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
        // 标题包含页码信息（多页时显示）
        val titleText = buildTitleText(
            userName,
            settlement.startMonth,
            settlement.endMonth,
            pageIndex + 1,
            totalPages
        )
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

        // ========== 4. 数据行 ==========
        textPaint.textSize = DATA_FONT_SIZE
        for (rowData in rows) {
            when (rowData) {
                is RowData.ProjectSummary -> {
                    // 工程汇总行（淡绿背景）
                    textPaint.typeface = Typeface.DEFAULT_BOLD
                    bgPaint.color = COLOR_PROJECT_BG
                    canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
                    drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)

                    x = 0f
                    drawTextInCell(canvas, textPaint, rowData.index.toString(), x, y, COL_INDEX, ROW_HEIGHT)
                    x += COL_INDEX
                    drawTextInCell(canvas, textPaint, rowData.project.projectName, x, y, COL_PROJECT, ROW_HEIGHT)
                    x += COL_PROJECT
                    constructionPlans.forEach { plan ->
                        val planQty = rowData.project.planQuantities[plan.id.toString()]
                        val cellText = if (planQty != null) {
                            "${formatNumber(planQty.totalQuantity)}${getUnitName(plan.unit)}"
                        } else "-"
                        drawTextInCell(canvas, textPaint, cellText, x, y, COL_PLAN, ROW_HEIGHT)
                        x += COL_PLAN
                    }
                    drawTextInCell(canvas, textPaint, "-", x, y, COL_TOTAL, ROW_HEIGHT)
                    y += ROW_HEIGHT
                }

                is RowData.SubprojectRow -> {
                    // 子项目明细行（淡灰背景）
                    textPaint.typeface = Typeface.DEFAULT
                    bgPaint.color = COLOR_DATA_BG
                    canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
                    drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)

                    x = 0f
                    drawTextInCell(canvas, textPaint, "", x, y, COL_INDEX, ROW_HEIGHT)
                    x += COL_INDEX
                    val subName = buildSubprojectName(rowData.sub)
                    drawTextInCell(canvas, textPaint, subName, x, y, COL_PROJECT, ROW_HEIGHT)
                    x += COL_PROJECT
                    constructionPlans.forEach { plan ->
                        val cellText = if (rowData.sub.planId == plan.id) {
                            "${formatNumber(rowData.sub.userQuantity)}${getUnitName(plan.unit)}"
                        } else "-"
                        drawTextInCell(canvas, textPaint, cellText, x, y, COL_PLAN, ROW_HEIGHT)
                        x += COL_PLAN
                    }
                    drawTextInCell(canvas, textPaint, "¥${formatNumber(rowData.sub.userAmount)}", x, y, COL_TOTAL, ROW_HEIGHT)
                    y += ROW_HEIGHT
                }

                is RowData.Total -> {
                    // 合计行
                    textPaint.typeface = Typeface.DEFAULT
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
                }

                is RowData.Price -> {
                    // 单价行
                    textPaint.typeface = Typeface.DEFAULT_BOLD
                    bgPaint.color = COLOR_DATA_BG
                    canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
                    drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)
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
                }

                is RowData.GrandTotal -> {
                    // 总计行
                    textPaint.typeface = Typeface.DEFAULT_BOLD
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
                }

                is RowData.AdvanceRow -> {
                    // 预支行
                    textPaint.typeface = Typeface.DEFAULT
                    bgPaint.color = COLOR_DATA_BG
                    canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
                    drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)
                    x = 0f
                    drawTextInCell(canvas, textPaint, "", x, y, COL_INDEX, ROW_HEIGHT)
                    x += COL_INDEX
                    drawTextInCell(canvas, textPaint, "${formatDate(rowData.advance.advanceDate)} 预支", x, y, COL_PROJECT, ROW_HEIGHT)
                    x += COL_PROJECT
                    constructionPlans.forEach { _ ->
                        drawTextInCell(canvas, textPaint, "-", x, y, COL_PLAN, ROW_HEIGHT)
                        x += COL_PLAN
                    }
                    drawTextInCell(canvas, textPaint, "¥${formatNumber(rowData.advance.advanceAmount)}", x, y, COL_TOTAL, ROW_HEIGHT)
                    y += ROW_HEIGHT
                }

                is RowData.Final -> {
                    // 总额行
                    textPaint.typeface = Typeface.DEFAULT_BOLD
                    bgPaint.color = COLOR_TOTAL_BG
                    canvas.drawRect(0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, bgPaint)
                    drawRowBorder(canvas, borderPaint, 0f, y, bitmapWidth.toFloat(), y + ROW_HEIGHT, planCount)
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
                    y += ROW_HEIGHT
                }
            }
        }

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
     * 构建标题文本：用户名 + 开始日期 至 结束日期 + 页码
     * 多页时在末尾追加 (第X页/共Y页)
     */
    private fun buildTitleText(
        userName: String,
        startMonth: String,
        endMonth: String,
        currentPage: Int,
        totalPages: Int
    ): String {
        val start = formatDate(startMonth)
        val end = formatDate(endMonth)
        val baseText = "$userName $start 至 $end"
        return if (totalPages > 1) {
            "$baseText（第${currentPage}页/共${totalPages}页）"
        } else {
            baseText
        }
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
