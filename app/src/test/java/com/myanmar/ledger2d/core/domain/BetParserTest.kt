package com.myanmar.ledger2d.core.domain

import org.junit.Assert.*
import org.junit.Test

class BetParserTest {
    private val parser=BetParser()
    private fun success(raw:String)=parser.parse(raw) as ParseResult.Success
    @Test fun dot_separator(){assertEquals(mapOf("10" to 100L),success("10.100").bets.associate{it.digit to it.amount})}
    @Test fun dash_separator(){assertEquals(100,success("10-100").total)}
    @Test fun space_separator(){assertEquals(100,success("10 100").total)}
    @Test fun slash_separator(){assertEquals(100,success("10/100").total)}
    @Test fun multiple_digits(){val r=success("10.13.14 100");assertEquals(300,r.total);assertEquals(listOf("10","13","14"),r.bets.map{it.digit})}
    @Test fun reverse_compact(){val r=success("10R100");assertEquals(setOf("10","01"),r.bets.map{it.digit}.toSet());assertEquals(200,r.total)}
    @Test fun reverse_lowercase(){assertEquals(200,success("10r100").total)}
    @Test fun reverse_spaced(){assertEquals(200,success("10 R 100").total)}
    @Test fun reverse_multiple(){val r=success("10.20.30R100");assertEquals(setOf("10","01","20","02","30","03"),r.bets.map{it.digit}.toSet());assertEquals(600,r.total)}
    @Test fun duplicate_reverse_is_aggregated(){val r=success("11R100");assertEquals(listOf("11"),r.bets.map{it.digit});assertEquals(200,r.total)}
    @Test fun multiple_lines_are_supported(){assertEquals(300,success("10.100\n11 200").total)}
    @Test fun comma_separated_entries_are_supported(){val r=success("34 100, 56 100");assertEquals(200,r.total);assertEquals(mapOf("34" to 100L,"56" to 100L),r.bets.associate{it.digit to it.amount})}
    @Test fun comma_entries_allow_extra_spaces_and_trailing_comma(){assertEquals(200,success("  34   100  ,   56  100, ").total)}
    @Test fun comma_entries_reject_empty_middle_segment(){assertTrue(parser.parse("34 100,,56 100") is ParseResult.Error)}
    @Test fun comma_entries_reject_leading_empty_segment(){assertTrue(parser.parse(",34 100") is ParseResult.Error)}
    @Test fun comma_entries_reject_missing_amount(){assertTrue(parser.parse("34 100,56") is ParseResult.Error)}
    @Test fun comma_entries_reject_decimal_amount(){assertTrue(parser.parse("34 100,56 10.5") is ParseResult.Error)}
    @Test fun comma_entries_reject_negative_amount(){assertTrue(parser.parse("34 100,56 -10") is ParseResult.Error)}
    @Test fun comma_entries_aggregate_duplicate_digits(){assertEquals(200,success("34 100,34 100").total)}
    @Test fun comma_separated_combination_entries_are_supported(){val r=BetExpansionEngine().expand("345 100, 456 100",QuickFormat.COMBINATION) as ParseResult.Success;assertEquals(1200,r.total)}
    @Test fun rejects_invalid_digit(){assertTrue(parser.parse("1 100") is ParseResult.Error)}
    @Test fun valid_digit_is_ascii_00_to_99(){assertTrue(BetParser.validDigit("00"));assertTrue(BetParser.validDigit("99"));assertFalse(BetParser.validDigit("၀၁"));assertFalse(BetParser.validDigit("١٢"))}
    @Test fun rejects_non_positive_amount(){assertTrue(parser.parse("10 0") is ParseResult.Error)}
    @Test fun rejects_decimal_money(){assertTrue(parser.parse("10 10.5") is ParseResult.Error)}

    @Test fun smart_mixed_message_detects_inline_formats() {
        val r = BetExpansionEngine().smartExpand("23R1000,123အခေပူး1000,11 500,456အခွေ1000")
        assertEquals(4, r.lines.size)
        assertEquals(QuickFormat.MANUAL, r.lines[0].format)
        assertEquals(QuickFormat.COMBINATION_DOUBLES, r.lines[1].format)
        assertEquals(QuickFormat.COMBINATION, r.lines[3].format)
        assertTrue(r.lines.all { it.result is ParseResult.Success })
        assertEquals(17500, r.total)
    }

    @Test fun smart_combination_doubles_accepts_all_separators() {
        val engine = BetExpansionEngine()
        listOf("123.1000", "123/1000", "123r1000", "123R1000", "123 1000").forEach { raw ->
            val r = engine.smartExpand(raw, QuickFormat.COMBINATION_DOUBLES)
            assertEquals(9000, r.total)
            assertEquals(9, r.bets.size)
        }
    }

    @Test fun smart_round_accepts_all_separators() {
        val engine = BetExpansionEngine()
        listOf("9.1000", "9/1000", "9r1000", "9R1000", "9 1000").forEach { raw ->
            val r = engine.smartExpand(raw, QuickFormat.ROUND)
            assertEquals(19000, r.total)
            assertEquals(19, r.bets.size)
        }
    }

    @Test fun smart_reverse_keeps_r_as_reverse_operator() {
        val r = BetExpansionEngine().smartExpand("12.13.14R1000")
        assertEquals(6000, r.total)
        assertEquals(setOf("12", "21", "13", "31", "14", "41"), r.bets.map { it.digit }.toSet())
    }

    @Test fun smart_burmese_comma_supports_multiple_lines() {
        val r = BetExpansionEngine().smartExpand("11 500၊ 22 500")
        assertEquals(1000, r.total)
        assertEquals(2, r.lines.size)
    }

    @Test fun smart_does_not_silently_skip_empty_middle_entry() {
        val r = BetExpansionEngine().smartExpand("11 500,,22 500")
        assertEquals(3, r.lines.size)
        assertTrue(r.lines[1].result is ParseResult.Error)
        assertTrue(r.hasErrors)
    }

    @Test fun smart_preserves_trailing_separator_as_harmless_paste_noise() {
        val r = BetExpansionEngine().smartExpand("11 500,")
        assertEquals(1, r.lines.size)
        assertEquals(500, r.total)
    }
}
