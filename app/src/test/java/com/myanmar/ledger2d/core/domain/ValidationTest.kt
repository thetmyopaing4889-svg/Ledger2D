package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.EffectiveLimits
import com.myanmar.ledger2d.core.model.ExpandedBet
import org.junit.Assert.*
import org.junit.Test

class ValidationTest {
    private val validator=LimitValidator()
    @Test fun existing_plus_new_equal_limit_allowed(){assertTrue(validator.validate(listOf(ExpandedBet("00",50)),mapOf("00" to 50),EffectiveLimits(100,emptyMap())).canConfirm)}
    @Test fun existing_plus_new_over_limit_rejected(){val r=validator.validate(listOf(ExpandedBet("00",60)),mapOf("00" to 50),EffectiveLimits(100,emptyMap()));assertFalse(r.canConfirm);assertTrue(r.rows.single().exceedsLimit)}
    @Test fun all_limit_applies_to_every_digit(){val r=validator.validate(listOf(ExpandedBet("99",101)),emptyMap(),EffectiveLimits(100,emptyMap()));assertFalse(r.canConfirm)}
    @Test fun special_limit_has_precedence(){val limits=EffectiveLimits(100,mapOf("00" to 200));assertTrue(validator.validate(listOf(ExpandedBet("00",150)),emptyMap(),limits).canConfirm);assertFalse(validator.validate(listOf(ExpandedBet("01",150)),emptyMap(),limits).canConfirm)}
    @Test fun no_limit_allows_positive_input(){assertTrue(validator.validate(listOf(ExpandedBet("42",999999)),emptyMap(),EffectiveLimits(null,emptyMap())).canConfirm)}
    @Test fun closed_manual_digit_blocked(){assertFalse(validator.validate(listOf(ExpandedBet("00",100)),emptyMap(),EffectiveLimits(null,emptyMap()),setOf("00")).canConfirm)}
    @Test fun closed_reverse_expansion_blocked(){val bets=(BetParser().parse("10R100") as ParseResult.Success).bets;assertEquals(setOf("01"),ClosedNumberValidator().blockedDigits(bets,setOf("01")));assertFalse(validator.validate(bets,emptyMap(),EffectiveLimits(null,emptyMap()),setOf("01")).canConfirm)}
    @Test fun closed_format_expansion_blocked(){val bets=(BetExpansionEngine().expand("100",QuickFormat.POWER) as ParseResult.Success).bets;assertEquals(setOf("94"),ClosedNumberValidator().blockedDigits(bets,setOf("94")))}
    @Test fun duplicate_input_is_checked_cumulatively(){val parsed=(BetParser().parse("10.10.100") as ParseResult.Success);assertFalse(validator.validate(parsed.bets,emptyMap(),EffectiveLimits(150,emptyMap())).canConfirm)}
}
