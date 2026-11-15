package com.myanmar.ledger2d.core.domain

/**
 * Discovery input contract: completed draw slots are unique, ordered and
 * precedence-aware. The operational Historic2DDataSource already merges
 * sources; this defensive normalization also covers research-only callers.
 *
 * Explicit verified WINNER > completed DAILY > historical import. For a
 * same-source conflict, retain the first provided record deterministically;
 * source conflicts should also be reviewed through HistoricDrawMerger.
 */
internal object CherryDiscoveryHistoryNormalizer {
    private fun priority(source: HistoricSource): Int = when (source) {
        HistoricSource.WINNER -> 3
        HistoricSource.DAILY -> 2
        HistoricSource.HISTORY -> 1
    }

    /**
     * Keep just the newest unique working draw slots from an arbitrarily
     * ordered history without allocating/sorting all historic rows.
     *
     * Complexity: O(N log limit) time, O(limit) extra memory. A later,
     * higher-authority correction replaces an existing slot, while a
     * same-source tie keeps the first record from the supplied history.
     * Reading N items from a large caller list is still unavoidable unless
     * its repository query already requests only the newest slots.
     */
    fun newestBefore(
        draws: List<HistoricDraw>,
        target: com.myanmar.ledger2d.core.model.DrawIdentity,
        closedDates: Set<java.time.LocalDate>,
        limit: Int,
        checkCancellation: () -> Unit = {},
    ): List<HistoricDraw> {
        require(limit > 0)
        val ordered = java.util.TreeMap<
            Pair<java.time.LocalDate, com.myanmar.ledger2d.core.model.DrawSession>,
            HistoricDraw
        >(
            compareBy<Pair<java.time.LocalDate,
                com.myanmar.ledger2d.core.model.DrawSession>> { it.first }
                .thenBy { it.second.ordinal }
        )
        draws.forEachIndexed { index, row ->
            if ((index and 127) == 0) checkCancellation()
            if (!validHistoric2D(row.digit) ||
                !DrawSchedule.isWorkingDate(row.date, closedDates) ||
                !(row.date < target.date ||
                    (row.date == target.date &&
                        row.session.ordinal < target.session.ordinal))) {
                return@forEachIndexed
            }
            val key = row.date to row.session
            val previous = ordered[key]
            if (previous == null || priority(row.source) > priority(previous.source)) {
                ordered[key] = row
            }
            if (ordered.size > limit) ordered.pollFirstEntry()
        }
        checkCancellation()
        return ordered.values.toList()
    }

    fun normalize(draws: List<HistoricDraw>): List<HistoricDraw> =
        draws.sortedWith(
            compareBy<HistoricDraw> { it.date }
                .thenBy { it.session.ordinal }
                .thenByDescending { priority(it.source) }
        ).distinctBy { it.date to it.session }
}
