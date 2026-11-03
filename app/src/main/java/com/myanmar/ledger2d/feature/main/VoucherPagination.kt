package com.myanmar.ledger2d.feature.main

/** Pure, pixel-based paginator shared by the JPEG renderer and preview. */
internal data class VoucherMeasuredRow(val lineHeights: List<Int>) {
    init {
        require(lineHeights.isNotEmpty() && lineHeights.all { it > 0 })
    }
}

internal data class VoucherRowSlice(
    val rowIndex: Int,
    val firstLine: Int,
    val untilLine: Int,
    val height: Int,
)

internal data class VoucherPage(val slices: List<VoucherRowSlice>)

internal object VoucherPaginator {
    const val rowTopPadding = 24
    const val rowBottomPadding = 26
    const val minRowHeight = 112

    fun rowHeight(lines: List<Int>): Int =
        maxOf(minRowHeight, rowTopPadding + lines.sum() + rowBottomPadding)

    fun paginate(rows: List<VoucherMeasuredRow>, capacity: Int): List<VoucherPage> {
        require(capacity >= minRowHeight)
        if (rows.isEmpty()) return listOf(VoucherPage(emptyList()))

        val pages = mutableListOf<VoucherPage>()
        var parts = mutableListOf<VoucherRowSlice>()
        var used = 0

        fun newPage() {
            if (parts.isNotEmpty()) pages += VoucherPage(parts.toList())
            parts = mutableListOf()
            used = 0
        }
        fun add(row: Int, first: Int, end: Int, height: Int) {
            require(height <= capacity - used)
            parts += VoucherRowSlice(row, first, end, height)
            used += height
        }

        rows.forEachIndexed { rowIndex, row ->
            val allHeight = rowHeight(row.lineHeights)
            if (allHeight <= capacity) {
                // Keep a normal transaction together. Only a transaction that is
                // taller than a whole page may be split between its visual lines.
                if (used + allHeight > capacity) newPage()
                add(rowIndex, 0, row.lineHeights.size, allHeight)
            } else {
                if (used > 0) newPage()
                var firstLine = 0
                while (firstLine < row.lineHeights.size) {
                    var untilLine = firstLine
                    var linesHeight = 0
                    while (untilLine < row.lineHeights.size) {
                        val candidate = rowHeightFromPixels(linesHeight + row.lineHeights[untilLine])
                        if (candidate > capacity - used) break
                        linesHeight += row.lineHeights[untilLine]
                        untilLine++
                    }
                    if (untilLine == firstLine) {
                        if (used > 0) {
                            newPage()
                            continue
                        }
                        throw IllegalArgumentException("A text line is taller than one voucher page")
                    }
                    add(rowIndex, firstLine, untilLine, rowHeightFromPixels(linesHeight))
                    firstLine = untilLine
                    if (firstLine < row.lineHeights.size) newPage()
                }
            }
        }
        newPage()
        return pages
    }

    private fun rowHeightFromPixels(linesHeight: Int): Int =
        maxOf(minRowHeight, rowTopPadding + linesHeight + rowBottomPadding)
}
