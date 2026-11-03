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
import com.myanmar.ledger2d.core.database.BetEntryWithLines
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * The only source for both the on-screen preview and exported JPEGs.
 * Fixed 9:16 portrait page, irrespective of the phone's UI density.
 */
internal data class VoucherRecord(val id: Long, val source: String, val digits: Int, val amount: Long)

internal data class VoucherDocument(
    val customer: String,
    val date: LocalDate,
    val session: DrawSession,
    val records: List<VoucherRecord>,
) {
    val totalDigits: Long = records.sumOf { it.digits.toLong() }
    val totalAmount: Long = records.fold(0L) { sum, row -> Math.addExact(sum, row.amount) }
}

internal fun voucherDocument(
    name: String,
    date: LocalDate,
    session: DrawSession,
    records: List<BetEntryWithLines>,
): VoucherDocument {
    val ordered = betEntriesInSubmissionOrder(records)
    return VoucherDocument(
        customer = name,
        date = date,
        session = session,
        records = ordered.map { record ->
            VoucherRecord(
                id = record.entry.id,
                source = transactionDisplayText(record.entry.inputFormat, record.entry.sourceText),
                digits = record.lines.size,
                amount = betEntryAmount(record),
            )
        },
    )
}

internal class VoucherRenderer(val document: VoucherDocument) {
    companion object {
        const val width = 1080
        const val height = 1920
        private const val tableLeft = 44f
        private const val tableRight = 1036f
        private const val tableTop = 445f
        private const val tableBottom = 1830f
        private const val dataTop = 568
        private const val dataBottom = 1664
        private const val betTextX = 198f
        private const val betTextWidth = 495
        private const val qtyRight = 813f
        private const val amountRight = 1003f
        private val background = Color.rgb(255, 249, 247)
        private val ink = Color.rgb(29, 27, 29)
        private val muted = Color.rgb(107, 100, 103)
        private val brand = Color.rgb(146, 20, 55)
        private val accent = Color.rgb(213, 59, 65)
        private val border = Color.rgb(242, 210, 220)
        private val rule = Color.rgb(213, 204, 207)
    }

    private val betTextPaint = textPaint(30f, ink)
    private val rows: List<StaticLayout> = document.records.map { sourceLayout(it.source) }
    val pages: List<VoucherPage> = VoucherPaginator.paginate(
        rows.map { layout ->
            VoucherMeasuredRow((0 until layout.lineCount).map { line ->
                layout.getLineBottom(line) - layout.getLineTop(line)
            })
        },
        capacity = dataBottom - dataTop,
    )

    val pageCount: Int get() = pages.size

    fun render(pageIndex: Int): Bitmap {
        require(pageIndex in pages.indices)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(background)
            drawWatermarks(canvas)
            drawHeader(canvas)
            drawTable(canvas, pageIndex)
            drawPageIndex(canvas, pageIndex)
            return bitmap
        } catch (error: Throwable) {
            bitmap.recycle()
            throw error
        }
    }

    private fun drawWatermarks(canvas: Canvas) {
        val watermark = textPaint(39f, Color.argb(26, 135, 108, 120), bold = true)
        canvas.save()
        canvas.rotate(-21f, width / 2f, height / 2f)
        for (y in -300..2400 step 460) {
            for (x in -520..1500 step 920) {
                canvas.drawText("CHERRY'S 2D LEDGER", x.toFloat(), y.toFloat(), watermark)
            }
        }
        canvas.restore()
    }

    private fun drawHeader(canvas: Canvas) {
        canvas.drawText("Cherry's 2D Ledger", 44f, 125f, textPaint(59f, brand, bold = true))
        canvas.drawText("ဘောင်ချာဖြတ်ပိုင်း", 44f, 203f, textPaint(38f, ink, bold = true))
        val customerLabel = "Customer: ${document.customer}"
        val customerPaint = textPaint(34f, ink, bold = true)
        val customerLayout = StaticLayout.Builder.obtain(
            customerLabel, 0, customerLabel.length, customerPaint, 986,
        ).setIncludePad(false).setMaxLines(2).build()
        canvas.save()
        canvas.translate(44f, 232f)
        customerLayout.draw(canvas)
        canvas.restore()

        stroke(canvas, 44f, 333f, 1036f, 333f, rule, 2f)
        val month = document.date.monthValue
        val title = "${document.date.dayOfMonth}.$month.${document.date.year % 100} " +
            if (document.session == DrawSession.MORNING) "မနက်အကြိမ် စာရင်း" else "ညနေအကြိမ် စာရင်း"
        fittedText(canvas, title, 44f, 413f, 986f, textPaint(42f, accent, bold = true), 30f)
    }

    private fun drawTable(canvas: Canvas, pageIndex: Int) {
        val card = RectF(tableLeft, tableTop, tableRight, tableBottom)
        canvas.drawRoundRect(card, 46f, 46f, paint(Color.WHITE))
        canvas.drawRoundRect(card, 46f, 46f, paint(border, strokeWidth = 2f))

        val heading = textPaint(31f, muted, bold = true)
        canvas.drawText("စဉ်", 87f, 518f, heading)
        canvas.drawText("ထိုးကွက်", betTextX, 518f, heading)
        val headRight = textPaint(29f, muted, bold = true, rightAligned = true)
        canvas.drawText("ကွက်ရေ", qtyRight, 518f, headRight)
        canvas.drawText("ထိုးကြေး", amountRight, 518f, headRight)
        stroke(canvas, tableLeft + 25, 549f, tableRight - 25, 549f, rule, 2f)

        var y = dataTop.toFloat()
        for (slice in pages[pageIndex].slices) {
            val record = document.records[slice.rowIndex]
            val number = if (slice.firstLine == 0) (slice.rowIndex + 1).toString() else "ဆက်"
            canvas.drawText(number, 91f, y + 52f, textPaint(30f, ink, bold = slice.firstLine == 0))
            val layout = rows[slice.rowIndex]
            val textTop = y + VoucherPaginator.rowTopPadding
            val visualTop = layout.getLineTop(slice.firstLine)
            val visualBottom = layout.getLineBottom(slice.untilLine - 1)
            canvas.save()
            canvas.clipRect(betTextX, textTop, betTextX + betTextWidth, textTop + visualBottom - visualTop)
            canvas.translate(betTextX, textTop - visualTop)
            layout.draw(canvas)
            canvas.restore()

            // Only the final fragment of a very long entry shows Qty and Amount.
            if (slice.untilLine == layout.lineCount) {
                val baseline = y + slice.height / 2f + 10f
                val digits = textPaint(29f, ink, rightAligned = true)
                val amount = textPaint(29f, ink, bold = true, rightAligned = true)
                canvas.drawText(record.digits.toString(), qtyRight, baseline, digits)
                fittedRightText(canvas, number(record.amount), amountRight, baseline, 170f, amount, 23f)
            }
            y += slice.height
            stroke(canvas, tableLeft + 27, y, tableRight - 27, y, rule, 1.7f)
        }
        if (pageIndex == pages.lastIndex) drawTotals(canvas)
    }

    private fun drawTotals(canvas: Canvas) {
        val area = RectF(72f, 1710f, 1008f, 1807f)
        canvas.drawRoundRect(area, 34f, 34f, paint(Color.rgb(255, 246, 248)))
        canvas.drawRoundRect(area, 34f, 34f, paint(border, strokeWidth = 2.5f))
        canvas.drawText("စုစုပေါင်း", 103f, 1751f, textPaint(30f, ink, bold = true))
        canvas.drawText("ကွက်ရေ ${document.totalDigits}", 103f, 1791f, textPaint(26f, muted))
        val right = textPaint(27f, ink, bold = true, rightAligned = true)
        canvas.drawText("စုစုပေါင်းထိုးကြေး", 975f, 1751f, textPaint(26f, muted, rightAligned = true))
        fittedRightText(canvas, "${number(document.totalAmount)} ကျပ်", 975f, 1793f, 470f, right, 23f)
    }

    private fun drawPageIndex(canvas: Canvas, pageIndex: Int) {
        val footer = textPaint(26f, muted)
        footer.textAlign = Paint.Align.CENTER
        canvas.drawText("စာမျက်နှာ ${pageIndex + 1} / $pageCount", width / 2f, 1892f, footer)
    }

    private fun sourceLayout(value: String): StaticLayout =
        StaticLayout.Builder.obtain(value, 0, value.length, betTextPaint, betTextWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(8f, 1f)
            .build()

    private fun textPaint(size: Float, color: Int, bold: Boolean = false, rightAligned: Boolean = false): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = if (rightAligned) Paint.Align.RIGHT else Paint.Align.LEFT
            isSubpixelText = true
        }

    private fun paint(color: Int, strokeWidth: Float = 0f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = if (strokeWidth == 0f) Paint.Style.FILL else Paint.Style.STROKE
        this.strokeWidth = strokeWidth
    }

    private fun stroke(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, thickness: Float) {
        canvas.drawLine(x1, y1, x2, y2, paint(color, thickness))
    }

    private fun fittedText(canvas: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float, style: TextPaint, minSize: Float) {
        while (style.measureText(text) > maxWidth && style.textSize > minSize) style.textSize -= 1f
        canvas.drawText(text, x, baseline, style)
    }

    private fun fittedRightText(canvas: Canvas, text: String, right: Float, baseline: Float, maxWidth: Float, style: TextPaint, minSize: Float) {
        fittedText(canvas, text, right, baseline, maxWidth, style, minSize)
    }

    private fun number(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)
}
