package com.myanmar.ledger2d.feature.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.LocalLanguage
import androidx.compose.ui.Modifier
import com.myanmar.ledger2d.core.domain.StatementPeriod
import com.myanmar.ledger2d.core.domain.StatementRange
import com.myanmar.ledger2d.core.model.DrawSession

/**
 * Compatibility entry for the existing Customer dashboard/report navigation.
 * Selection remains separate. The actual result UI lives in CustomerLedgerReportResults.
 */
@Composable
fun CustomerStatementResults(
    vm: LedgerViewModel,
    agentId: Long,
    customerId: Long,
    period: StatementPeriod,
    range: StatementRange,
    session: DrawSession?,
    subtitle: String,
    onReselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = LocalLanguage.current
    val agent by vm.agent(agentId).collectAsStateWithLifecycle(initialValue = null)
    CustomerLedgerReportResults(
        vm, agentId, customerId, period, range, session, subtitle, onReselect, modifier = modifier,
        agentName = agent?.name ?: lang.text("ဒိုင်", "Agent"),
    )
}
