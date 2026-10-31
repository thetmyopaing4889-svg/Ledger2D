package com.myanmar.ledger2d.core.domain

/**
 * Read-only digit groups used by Analysis drill-down. The same saved-rate
 * DigitScenario payout and profit/loss drive both the overview and details.
 * Never recalculate from the current agent rate, or include commission.
 */
enum class AnalysisDigitGroup {
    UNSTAKED,
    STAKED_PROFIT,
    STAKED_LOSS,
    BREAK_EVEN,
}

object AnalysisDigitGroups {
    fun category(scenario: DigitScenario): AnalysisDigitGroup = when {
        scenario.stake == 0L -> AnalysisDigitGroup.UNSTAKED
        scenario.profitLoss > 0L -> AnalysisDigitGroup.STAKED_PROFIT
        scenario.profitLoss < 0L -> AnalysisDigitGroup.STAKED_LOSS
        else -> AnalysisDigitGroup.BREAK_EVEN
    }

    fun select(source: AnalysisResult, group: AnalysisDigitGroup): List<DigitScenario> =
        source.scenarios.filter { category(it) == group }
            .sortedWith(
                if (group == AnalysisDigitGroup.UNSTAKED) compareBy { it.digit }
                else compareByDescending<DigitScenario> { it.stake }.thenBy { it.digit }
            )

    fun counts(source: AnalysisResult): Map<AnalysisDigitGroup, Int> =
        AnalysisDigitGroup.entries.associateWith { group ->
            source.scenarios.count { category(it) == group }
        }
}
