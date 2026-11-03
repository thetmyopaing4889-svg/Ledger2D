package com.myanmar.ledger2d.feature.main

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.text.NumberFormat
import java.util.Locale

/**
 * Mirrors CustomerSettlementJpegRenderer's 1080 x 1920 Cherry frame and money
 * summary, but draws exactly one row per customer for the ENTIRE selected range.
 */
internal class AgentSettlementJpegRenderer(val document: AgentSettlementDocument) {
    companion object {
        const val width = CustomerSettlementJpegRenderer.width
        const val height = CustomerSettlementJpegRenderer.height
        private const val sourceX = 131f
        private const val sourceWidth = 320
        private const val bodyTop = 560
        private const val bodyBottom = 1780
        private const val bodyCapacity = bodyBottom - bodyTop
        private const val totalHeight = 74
        private const val minRowHeight = 65
        private val background = Color.rgb(255, 249, 247)
        private val ink = Color.rgb(29, 27, 29)
        private val muted = Color.rgb(107, 100, 103)
        private val brand = Color.rgb(146, 20, 55)
        private val accent = Color.rgb(213, 59, 65)
        private val border = Color.rgb(242, 210, 220)
        private val rule = Color.rgb(213, 204, 207)
        private val grandBack = Color.rgb(252, 231, 236)
    }

    private sealed interface Item {
        val height: Int
        data class Customer(
            val row: AgentSettlementCustomerRow, val number: Int,
            val first: Int, val until: Int, override val height: Int,
        ) : Item
        data object Total : Item { override val height = totalHeight }
    }

    private val layouts = document.rows.associate { it.customerId to sourceLayout(it.name) }
    private val pages = paginate()
    val pageCount: Int get() = pages.size + 1
    fun isSummary(index: Int): Boolean = index == pageCount - 1
    internal fun pageCustomerIds(index: Int): List<Long> =
        pages[index].filterIsInstance<Item.Customer>().filter { it.first == 0 }
            .map { it.row.customerId }

    fun render(pageIndex: Int): Bitmap {
        require(pageIndex in 0 until pageCount)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(background)
            drawWatermarks(canvas)
            drawHeader(canvas, isSummary(pageIndex))
            if (isSummary(pageIndex)) drawSummary(canvas) else drawRows(canvas, pages[pageIndex])
            drawPageIndex(canvas, pageIndex)
            return bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun paginate(): List<List<Item>> {
        val result = mutableListOf<List<Item>>()
        var page = mutableListOf<Item>()
        var used = 0
        fun nextPage() {
            if (page.isNotEmpty()) result += page.toList()
            page = mutableListOf()
            used = 0
        }
        fun append(item: Item) {
            require(item.height <= bodyCapacity)
            if (used + item.height > bodyCapacity) nextPage()
            page += item
            used += item.height
        }
        document.rows.forEachIndexed { i, row ->
            val layout = requireNotNull(layouts[row.customerId])
            val full = rowHeight(layout, 0, layout.lineCount)
            if (full <= bodyCapacity) {
                append(Item.Customer(row, i + 1, 0, layout.lineCount, full))
            } else {
                // Even an abnormally long customer name must not crash JPEG preview,
                // silently truncate the name, or count the customer's totals twice.
                var first = 0
                while (first < layout.lineCount) {
                    var until = first
                    while (until < layout.lineCount &&
                        rowHeight(layout, first, until + 1) <= bodyCapacity - used
                    ) until++
                    if (until == first) {
                        nextPage()
                        continue
                    }
                    append(Item.Customer(row, i + 1, first, until,
                        rowHeight(layout, first, until)))
                    first = until
                }
            }
        }
        append(Item.Total)
        nextPage()
        return result
    }

    private fun rowHeight(layout: StaticLayout, first: Int, until: Int): Int {
        require(first < until)
        val textHeight = layout.getLineBottom(until - 1) - layout.getLineTop(first)
        return maxOf(minRowHeight, 14 + textHeight + 16)
    }

    private fun drawWatermarks(canvas: Canvas) {
        val mark = textPaint(39f, Color.argb(26, 135, 108, 120), true)
        canvas.save()
        canvas.rotate(-21f, width / 2f, height / 2f)
        for (y in -300..2400 step 460) for (x in -520..1500 step 920)
            canvas.drawText("CHERRY'S 2D LEDGER", x.toFloat(), y.toFloat(), mark)
        canvas.restore()
    }

    private fun drawHeader(canvas: Canvas, summary: Boolean) {
        canvas.drawText("Cherry's 2D Ledger", 44f, 125f, textPaint(59f, brand, true))
        canvas.drawText(if (summary) "ငွေစာရင်းရှင်းတမ်း" else "ထိုးသားစာရင်းချုပ်",
            44f, 203f, textPaint(if (summary) 39f else 34f, ink, true))
        val label = "Agent: ${document.agentName}"
        var size = 34f
        var layout: StaticLayout
        do {
            val style = textPaint(size, ink, true)
            layout = StaticLayout.Builder.obtain(label, 0, label.length, style, 986)
                .setIncludePad(false).build()
            if (layout.height <= 92 || size <= 18f) break
            size -= 1f
        } while (true)
        canvas.save()
        canvas.translate(44f, 232f)
        if (layout.height > 92) canvas.scale(1f, 92f / layout.height)
        layout.draw(canvas)
        canvas.restore()
        stroke(canvas, 44f, 333f, 1036f, 333f, rule, 2f)
        val range = document.range
        val dates = if (range.start == range.end) niceDate(range.start)
            else "${niceDate(range.start)} မှ ${niceDate(range.end)} အထိ"
        fittedText(canvas, dates, 44f, 411f, 986f, textPaint(41f, accent, true), 27f)
    }

    private fun drawRows(canvas: Canvas, items: List<Item>) {
        val card = RectF(44f, 435f, 1036f, 1825f)
        canvas.drawRoundRect(card, 38f, 38f, paint(Color.WHITE))
        canvas.drawRoundRect(card, 38f, 38f, paint(border, 2f))
        val left = textPaint(24f, muted, true)
        val right = textPaint(23f, muted, true, rightAligned = true)
        canvas.drawText("စဉ်", 64f, 514f, left)
        canvas.drawText("ထိုးသား", sourceX, 514f, left)
        canvas.drawText("ကွက်", 520f, 514f, right)
        canvas.drawText("ထိုးငွေ", 658f, 514f, right)
        fittedRightText(canvas, "ပေါက်ကြေး", 857f, 514f, 185f, right, 17f)
        canvas.drawText("လျော်ငွေ", 1015f, 514f, right)
        stroke(canvas, 60f, 541f, 1018f, 541f, rule, 2f)
        var y = bodyTop.toFloat()
        items.forEach { item ->
            when (item) {
                is Item.Customer -> {
                    if (item.first == 0)
                        canvas.drawText(item.number.toString(), 68f, y + 40f,
                            textPaint(23f, ink, true))
                    else canvas.drawText("ဆက်", 62f, y + 39f, textPaint(20f, muted))
                    val layout = requireNotNull(layouts[item.row.customerId])
                    val textTop = layout.getLineTop(item.first)
                    val textBottom = layout.getLineBottom(item.until - 1)
                    canvas.save()
                    canvas.clipRect(sourceX, y + 14f, sourceX + sourceWidth,
                        y + 14f + textBottom - textTop)
                    canvas.translate(sourceX, y + 14f - textTop)
                    layout.draw(canvas)
                    canvas.restore()
                    if (item.until == layout.lineCount) {
                        val baseline = y + item.height / 2f + 8f
                        val rightData = textPaint(22f, ink, rightAligned = true)
                        val bold = textPaint(22f, ink, true, rightAligned = true)
                        canvas.drawText(number(item.row.digits), 520f, baseline, rightData)
                        fittedRightText(canvas, number(item.row.bet), 658f, baseline, 130f, bold, 17f)
                        fittedRightText(canvas, number(item.row.winningStake), 857f, baseline, 186f, rightData, 15f)
                        fittedRightText(canvas, number(item.row.payout), 1015f, baseline, 150f, bold, 15f)
                    }
                    stroke(canvas, 60f, y + item.height, 1018f, y + item.height, rule, 1.3f)
                }
                Item.Total -> {
                    canvas.drawRect(58f, y, 1022f, y + item.height, paint(grandBack))
                    val base = y + 46f
                    canvas.drawText("TOTAL", 75f, base, textPaint(25f, brand, true))
                    val rightTotal = textPaint(23f, brand, true, rightAligned = true)
                    canvas.drawText(number(document.digits), 520f, base, rightTotal)
                    fittedRightText(canvas, number(document.totalBet), 658f, base, 130f, rightTotal, 16f)
                    fittedRightText(canvas, number(document.totalWinningStake), 857f, base, 186f, rightTotal, 16f)
                    fittedRightText(canvas, number(document.totalPayout), 1015f, base, 150f, rightTotal, 15f)
                }
            }
            y += item.height
        }
    }

    private fun drawSummary(canvas: Canvas) {
        canvas.drawRoundRect(RectF(44f, 455f, 1036f, 1422f), 40f, 40f, paint(Color.WHITE))
        canvas.drawRoundRect(RectF(44f, 455f, 1036f, 1422f), 40f, 40f, paint(border, 2f))
        summaryLine(canvas, "စုစုပေါင်းထိုးကြေး", number(document.totalBet), 578f)
        canvas.drawText("ပေါက်ကြေး (" + number(document.totalWinningStake) + ") အတွက်",
            80f, 703f, textPaint(30f, ink, true))
        canvas.drawText("လျော်ငွေ (−)", 80f, 746f, textPaint(30f, ink, true))
        fittedRightText(canvas, number(document.totalPayout), 1000f, 730f,
            360f, textPaint(38f, ink, true, true), 24f)
        // Always include saved NET commission, matching Agent Report, without a toggle.
        summaryLine(canvas, "ကော်မရှင်ခ (−)", number(document.commission), 850f)
        stroke(canvas, 80f, 925f, 1000f, 925f, rule, 2f)
        summaryLine(canvas, "စုစုပေါင်းနှုတ်ငွေ (−)", number(document.totalDeduction), 987f)
        stroke(canvas, 80f, 1045f, 1000f, 1045f, rule, 2f)
        summaryLine(canvas, "စာရင်းရှင်းလင်းရန်ငွေ",
            (if (document.settlement < 0L) "−" else "") + numberAbsolute(document.settlement),
            1115f, brand)
        canvas.drawRoundRect(RectF(73f, 1175f, 1007f, 1362f), 28f, 28f, paint(grandBack))
        val label = when {
            document.settlement < 0L -> "ဒိုင်မှ ပေးရန်"
            document.settlement > 0L -> "ဒိုင်ကို ပေးရန်"
            else -> "ရရန်/ပေးရန် မရှိ"
        }
        canvas.drawText(label, 101f, 1245f, textPaint(37f, brand, true))
        fittedRightText(canvas, numberAbsolute(document.settlement) + " ကျပ်",
            976f, 1320f, 845f, textPaint(55f, brand, true, true), 33f)
    }

    private fun summaryLine(canvas: Canvas, label: String, value: String, baseline: Float, color: Int = ink) {
        canvas.drawText(label, 80f, baseline, textPaint(31f, color, true))
        fittedRightText(canvas, value, 1000f, baseline, 360f,
            textPaint(39f, color, true, true), 25f)
    }
    private fun drawPageIndex(canvas: Canvas, index: Int) {
        val p = textPaint(26f, muted)
        p.textAlign = Paint.Align.CENTER
        canvas.drawText("စာမျက်နှာ ${index + 1} / $pageCount", width / 2f, 1892f, p)
    }
    private fun sourceLayout(name: String): StaticLayout {
        val p = textPaint(24f, ink)
        return StaticLayout.Builder.obtain(name, 0, name.length, p, sourceWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false).setLineSpacing(5f, 1f).build()
    }
    private fun textPaint(size: Float, color: Int, bold: Boolean = false, rightAligned: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = if (rightAligned) Paint.Align.RIGHT else Paint.Align.LEFT
            isSubpixelText = true
        }
    private fun paint(color: Int, lineWidth: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = if (lineWidth == 0f) Paint.Style.FILL else Paint.Style.STROKE
        strokeWidth = lineWidth
    }
    private fun stroke(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, width: Float) {
        canvas.drawLine(x1, y1, x2, y2, paint(color, width))
    }
    private fun fittedText(canvas: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float, style: TextPaint, minSize: Float) {
        while (style.measureText(text) > maxWidth && style.textSize > minSize) style.textSize -= 1f
        canvas.drawText(text, x, baseline, style)
    }
    private fun fittedRightText(canvas: Canvas, text: String, right: Float, baseline: Float, maxWidth: Float, style: TextPaint, minSize: Float) =
        fittedText(canvas, text, right, baseline, maxWidth, style, minSize)
    private fun niceDate(date: java.time.LocalDate) = "${date.dayOfMonth}.${date.monthValue}.${date.year % 100}"
    private fun number(amount: Long) = NumberFormat.getIntegerInstance(Locale.US).format(amount)
    private fun numberAbsolute(amount: Long) =
        NumberFormat.getIntegerInstance(Locale.US).format(java.math.BigInteger.valueOf(amount).abs())
}
