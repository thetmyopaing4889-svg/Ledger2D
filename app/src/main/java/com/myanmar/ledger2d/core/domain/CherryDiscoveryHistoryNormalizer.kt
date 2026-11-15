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

    fun normalize(draws: List<HistoricDraw>): List<HistoricDraw> =
        draws.sortedWith(
            compareBy<HistoricDraw> { it.date }
                .thenBy { it.session.ordinal }
                .thenByDescending { priority(it.source) }
        ).distinctBy { it.date to it.session }
}
