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
import com.myanmar.ledger2d.core.model.DrawSession
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/**
 * Same 1080x1920 portrait page and Cherry branding as VoucherRenderer.
 * Independent renderer means the existing selected-transaction voucher is unchanged.
 */
internal class CustomerSettlementJpegRenderer(val document: CustomerSettlementDocument) {
    companion object {
        const val width = 1080
        const val height = 1920
        private const val sourceX = 131f
        private const val sourceWidth = 320
        private const val bodyTop = 560
        private const val bodyBottom = 1780
        private const val bodyCapacity = bodyBottom - bodyTop
        private const val headerHeight = 68
        private const val footerHeight = 62
        private const val totalHeight = 74
        private const val minRowHeight = 65
        private val background = Color.rgb(255, 249, 247)
        private val ink = Color.rgb(29, 27, 29)
        private val muted = Color.rgb(107, 100, 103)
        private val brand = Color.rgb(146, 20, 55)
        private val accent = Color.rgb(213, 59, 65)
        private val border = Color.rgb(242, 210, 220)
        private val rule = Color.rgb(213, 204, 207)
        private val subtotalBack = Color.rgb(255, 243, 217)
        private val subtotalText = Color.rgb(149, 96, 27)
        private val grandBack = Color.rgb(252, 231, 236)
    }

    private sealed interface Item {
        val height: Int
        data class Session(val draw: CustomerSettlementDraw, val continued: Boolean) : Item {
            override val height = headerHeight
        }
        data class Entry(
            val row: CustomerSettlementRow, val number: Int,
            val first: Int, val until: Int, override val height: Int,
        ) : Item
        data class Subtotal(val draw: CustomerSettlementDraw) : Item {
            override val height = footerHeight
        }
        data object Total : Item {
            override val height = totalHeight
        }
    }

    private val sourcePaint = textPaint(24f, ink)
    private val textLayouts: Map<Long, StaticLayout> = document.draws.flatMap { it.rows }
        .associate { it.id to sourceLayout(it.source) }
    private val pages: List<List<Item>> = paginate()
    /** Last page is always a separate money-settlement summary. */
    val pageCount: Int get() = pages.size + 1

    internal fun isSummary(page: Int) = page == pageCount - 1
    internal fun pageEntryIds(page: Int): List<Long> = pages[page]
        .filterIsInstance<Item.Entry>().filter { it.first == 0 }.map { it.row.id }

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
        fun add(item: Item) {
            require(item.height + used <= bodyCapacity)
            page += item
            used += item.height
        }
        fun ensureSpace(height: Int, draw: CustomerSettlementDraw? = null) {
            if (used + height > bodyCapacity) {
                nextPage()
                if (draw != null) add(Item.Session(draw, continued = true))
            }
        }

        var sequence = 0
        document.draws.forEach { draw ->
            ensureSpace(headerHeight + minRowHeight)
            add(Item.Session(draw, continued = false))
            draw.rows.forEach { row ->
                sequence++
                val layout = requireNotNull(textLayouts[row.id])
                val full = rowHeight(layout, 0, layout.lineCount)
                if (full <= bodyCapacity - headerHeight) {
                    ensureSpace(full, draw)
                    add(Item.Entry(row, sequence, 0, layout.lineCount, full))
                } else {
                    // A single Messenger entry may be longer than a whole JPEG page.
                    if (used > headerHeight) {
                        nextPage()
                        add(Item.Session(draw, continued = true))
                    }
                    var first = 0
                    while (first < layout.lineCount) {
                        var until = first
                        while (until < layout.lineCount &&
                            rowHeight(layout, first, until + 1) <= bodyCapacity - used
                        ) until++
                        if (until == first) {
                            nextPage()
                            add(Item.Session(draw, continued = true))
                            continue
                        }
                        add(Item.Entry(row, sequence, first, until, rowHeight(layout, first, until)))
                        first = until
                        if (first < layout.lineCount) {
                            nextPage()
                            add(Item.Session(draw, continued = true))
                        }
                    }
                }
            }
            ensureSpace(footerHeight, draw)
            add(Item.Subtotal(draw))
        }
        ensureSpace(totalHeight)
        add(Item.Total)
        nextPage()
        return result
    }

    private fun rowHeight(layout: StaticLayout, first: Int, until: Int): Int {
        require(first < until)
        val textHeight = layout.getLineBottom(until - 1) - layout.getLineTop(first)
        return maxOf(minRowHeight, 14 + textHeight + 16)
    }

    private fun drawWatermarks(canvas: Canvas) {
        val watermark = textPaint(39f, Color.argb(26, 135, 108, 120), bold = true)
        canvas.save()
        canvas.rotate(-21f, width / 2f, height / 2f)
        for (y in -300..2400 step 460) for (x in -520..1500 step 920)
            canvas.drawText("CHERRY'S 2D LEDGER", x.toFloat(), y.toFloat(), watermark)
        canvas.restore()
    }

    private fun drawHeader(canvas: Canvas, summary: Boolean) {
        canvas.drawText("Cherry's 2D Ledger", 44f, 125f, textPaint(59f, brand, true))
        canvas.drawText(if (summary) "ငွေစာရင်းရှင်းတမ်း" else "စာရင်းရှင်းတမ်း — ထိုးကွက်စာရင်း",
            44f, 203f, textPaint(if (summary) 39f else 34f, ink, true))
        val label = "Customer: ${document.customerName}"
        var size = 34f
        var layout: StaticLayout
        do {
            val p = textPaint(size, ink, true)
            layout = StaticLayout.Builder.obtain(label, 0, label.length, p, 986)
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
        canvas.drawText("ထိုးကွက်", sourceX, 514f, left)
        canvas.drawText("ကွက်", 520f, 514f, right)
        canvas.drawText("ထိုးငွေ", 658f, 514f, right)
        fittedRightText(canvas, "ပေါက်ကြေး(ထွက်)", 857f, 514f, 185f, right, 17f)
        canvas.drawText("လျော်ငွေ", 1015f, 514f, right)
        stroke(canvas, 60f, 541f, 1018f, 541f, rule, 2f)

        var y = bodyTop.toFloat()
        items.forEach { item ->
            when (item) {
                is Item.Session -> {
                    canvas.drawRoundRect(RectF(58f, y + 3f, 1022f, y + item.height - 5f),
                        13f, 13f, paint(Color.rgb(255, 243, 244)))
                    val text = niceDate(item.draw.date) + " " +
                        if (item.draw.session == DrawSession.MORNING) "မနက်အကြိမ်" else "ညနေအကြိမ်"
                    canvas.drawText(text + if (item.continued) " (ဆက်)" else "",
                        76f, y + 44f, textPaint(28f, brand, true))
                }
                is Item.Entry -> {
                    val layout = requireNotNull(textLayouts[item.row.id])
                    if (item.first == 0) {
                        canvas.drawText(item.number.toString(), 68f, y + 40f, textPaint(23f, ink, true))
                    } else canvas.drawText("ဆက်", 62f, y + 39f, textPaint(20f, muted))
                    val visibleTop = layout.getLineTop(item.first)
                    val visibleBottom = layout.getLineBottom(item.until - 1)
                    canvas.save()
                    canvas.clipRect(sourceX, y + 14f, sourceX + sourceWidth,
                        y + 14f + visibleBottom - visibleTop)
                    canvas.translate(sourceX, y + 14f - visibleTop)
                    layout.draw(canvas)
                    canvas.restore()
                    if (item.until == layout.lineCount) {
                        val baseline = y + item.height / 2f + 8f
                        val rightData = textPaint(22f, ink, rightAligned = true)
                        val boldData = textPaint(22f, ink, true, rightAligned = true)
                        canvas.drawText(item.row.digits.toString(), 520f, baseline, rightData)
                        fittedRightText(canvas, number(item.row.bet), 658f, baseline, 130f, boldData, 17f)
                        val winning = number(item.row.winningStake) + "(" + item.row.winner + ")"
                        fittedRightText(canvas, winning, 857f, baseline, 186f, rightData, 15f)
                        fittedRightText(canvas, number(item.row.payout), 1015f, baseline, 150f, boldData, 15f)
                    }
                    stroke(canvas, 60f, y + item.height, 1018f, y + item.height, rule, 1.3f)
                }
                is Item.Subtotal -> {
                    canvas.drawRect(58f, y, 1022f, y + item.height, paint(subtotalBack))
                    drawTotalsRow(canvas, "Subtotal", item.draw.digits, item.draw.bet,
                        item.draw.winningStake, item.draw.payout, y, subtotalText)
                }
                Item.Total -> {
                    canvas.drawRect(58f, y, 1022f, y + item.height, paint(grandBack))
                    drawTotalsRow(canvas, "TOTAL", document.digits, document.totalBet,
                        document.totalWinningStake, document.totalPayout, y + 5f, brand)
                }
            }
            y += item.height
        }
    }

    private fun drawTotalsRow(
        canvas: Canvas, label: String, digits: Long, bet: Long, winning: Long, payout: Long,
        top: Float, color: Int,
    ) {
        val baseline = top + 41f
        canvas.drawText(label, 75f, baseline, textPaint(25f, color, true))
        val right = textPaint(23f, color, true, rightAligned = true)
        canvas.drawText(digits.toString(), 520f, baseline, right)
        fittedRightText(canvas, number(bet), 658f, baseline, 130f, right, 16f)
        fittedRightText(canvas, number(winning), 857f, baseline, 186f, right, 16f)
        fittedRightText(canvas, number(payout), 1015f, baseline, 150f, right, 15f)
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

        val commissionY = 850f
        if (document.rebateBasisPoints != null) {
            val percent = BigDecimal.valueOf(document.rebateBasisPoints.toLong(), 2)
                .stripTrailingZeros().toPlainString()
            summaryLine(canvas, "ကော်မရှင်ခ (" + percent + "%) (−)",
                number(document.rebateAmount), commissionY)
        }

        stroke(canvas, 80f, 925f, 1000f, 925f, rule, 2f)
        summaryLine(canvas, "စုစုပေါင်းနှုတ်ငွေ (−)", number(document.totalDeduction), 987f)
        stroke(canvas, 80f, 1045f, 1000f, 1045f, rule, 2f)
        summaryLine(canvas, "စာရင်းရှင်းလင်းရန်ငွေ",
            (if (document.settlement < 0L) "−" else "") + numberAbsolute(document.settlement), 1115f, brand)

        canvas.drawRoundRect(RectF(73f, 1175f, 1007f, 1362f),
            28f, 28f, paint(grandBack))
        val label = when {
            document.settlement < 0L -> "Customer ကို ပေးရန်"
            document.settlement > 0L -> "Customer မှ ပေးရန်"
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

    private fun sourceLayout(source: String): StaticLayout =
        StaticLayout.Builder.obtain(source, 0, source.length, sourcePaint, sourceWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false).setLineSpacing(5f, 1f).build()

    private fun textPaint(
        size: Float, color: Int, bold: Boolean = false, rightAligned: Boolean = false,
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        textAlign = if (rightAligned) Paint.Align.RIGHT else Paint.Align.LEFT
        isSubpixelText = true
    }

    private fun paint(color: Int, strokeWidth: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = if (strokeWidth == 0f) Paint.Style.FILL else Paint.Style.STROKE
        this.strokeWidth = strokeWidth
    }

    private fun stroke(canvas: Canvas, a: Float, b: Float, c: Float, d: Float, color: Int, width: Float) {
        canvas.drawLine(a, b, c, d, paint(color, width))
    }

    private fun fittedText(
        canvas: Canvas, text: String, x: Float, baseline: Float,
        maxWidth: Float, style: TextPaint, minSize: Float,
    ) {
        while (style.measureText(text) > maxWidth && style.textSize > minSize) style.textSize -= 1f
        canvas.drawText(text, x, baseline, style)
    }

    private fun fittedRightText(
        canvas: Canvas, text: String, right: Float, baseline: Float,
        maxWidth: Float, style: TextPaint, minSize: Float,
    ) = fittedText(canvas, text, right, baseline, maxWidth, style, minSize)

    private fun niceDate(d: java.time.LocalDate) =
        "${d.dayOfMonth}.${d.monthValue}.${d.year % 100}"
    private fun number(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)
    private fun numberAbsolute(value: Long): String = NumberFormat.getIntegerInstance(Locale.US)
        .format(java.math.BigInteger.valueOf(value).abs())
}
