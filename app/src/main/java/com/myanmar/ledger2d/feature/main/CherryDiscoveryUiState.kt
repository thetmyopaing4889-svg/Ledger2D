package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryDiscoverySearchCursor

/**
 * Presentation-only state: a newly FOUND formula is not automatically
 * SELECTED for a future result lock. Display and acceptance are independent.
 *
 * Search requests must capture their original kind. Applying a result to its
 * requested kind prevents a completed LONE search landing under WAM/BREAK.
 */
internal data class CherryDiscoveryUiState(
    val cursors: Map<CherryDiscoveryKind, CherryDiscoverySearchCursor?> = emptyMap(),
    val displayedIds: Map<CherryDiscoveryKind, String> = emptyMap(),
    val selectedIds: Map<CherryDiscoveryKind, String?> = emptyMap(),
) {
    fun restore(saved: Map<CherryDiscoveryKind, CherryDiscoverySearchCursor?>) =
        copy(cursors = saved)

    fun searched(
        requestedKind: CherryDiscoveryKind,
        found: CherryDiscoverySearchCursor,
    ): CherryDiscoveryUiState {
        require(found.kind == requestedKind) {
            "Cannot show a result under a different category"
        }
        val freshId = found.newCandidate?.id
        return copy(
            cursors = cursors + (requestedKind to found),
            displayedIds = if (freshId == null) displayedIds
                else displayedIds + (requestedKind to freshId),
        )
    }

    fun displayId(kind: CherryDiscoveryKind): String? =
        displayedIds[kind] ?: cursors[kind]?.newCandidate?.id
            ?: cursors[kind]?.bestCandidate?.id

    fun select(kind: CherryDiscoveryKind, id: String?): CherryDiscoveryUiState {
        require(id == null || id in cursors[kind]?.offeredIds.orEmpty()) {
            "Only an offered formula can be selected"
        }
        return copy(
            selectedIds = selectedIds + (kind to id),
            displayedIds = if (id == null) displayedIds
                else displayedIds + (kind to id),
        )
    }
}
