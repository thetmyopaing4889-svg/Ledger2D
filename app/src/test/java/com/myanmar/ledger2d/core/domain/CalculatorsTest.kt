package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.ExpandedBet
import org.junit.Assert.*
import org.junit.Test

class CalculatorsTest {
    @Test fun commission_uses_basis_points(){assertEquals(1500,CommissionCalculator().calculate(10_000,1500))}
    @Test fun commission_truncates_sub_mmk_fraction_deterministically(){assertEquals(3,CommissionCalculator().calculate(10,3333))}
    @Test fun payout_is_integer_multiplier(){assertEquals(80_000,PayoutCalculator().calculate(1_000,80))}
    @Test fun profit_loss_can_be_negative(){assertEquals(-70_000,ProfitLossCalculator().calculate(10_000,80_000))}
    @Test fun winning_aggregation_sums_all_matching_lines(){val bets=listOf(ExpandedBet("13",100),ExpandedBet("14",200),ExpandedBet("13",300));assertEquals(400,BetAggregator().winningStake(bets,"13"))}
    @Test fun report_calculation_uses_source_digit_totals(){val r=ReportCalculator().calculate(mapOf("13" to 1000,"14" to 9000),"13",80,1500);assertEquals(10_000,r.totalBet);assertEquals(2,r.distinctSlots);assertEquals(1_000,r.winningStake);assertEquals(80_000,r.payout);assertEquals(1_500,r.commission);assertEquals(-70_000,r.profitLoss);assertEquals(-68_500,r.netSettlement)}
    @Test fun before_report_without_winner_has_zero_winning_values(){val r=ReportCalculator().calculate(mapOf("13" to 1000),null,80,0);assertEquals(0,r.winningStake);assertEquals(0,r.payout);assertEquals(1000,r.profitLoss)}
    @Test(expected=ArithmeticException::class) fun overflow_is_not_silently_wrapped(){PayoutCalculator().calculate(Long.MAX_VALUE,2)}
}
