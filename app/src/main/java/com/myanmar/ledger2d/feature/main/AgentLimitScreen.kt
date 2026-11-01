package com.myanmar.ledger2d.feature.main

import androidx.compose.runtime.Composable

/** Existing agent-limit route now shares the same format selector and session rules. */
@Composable
fun AgentLimitScreen(vm: LedgerViewModel, agentId: Long, onBack: () -> Unit) {
    LimitPatternStandaloneScreen(vm, agentId, onBack)
}
