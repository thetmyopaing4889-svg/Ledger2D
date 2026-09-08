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
    @Test fun rejects_invalid_digit(){assertTrue(parser.parse("1 100") is ParseResult.Error)}
    @Test fun rejects_non_positive_amount(){assertTrue(parser.parse("10 0") is ParseResult.Error)}
    @Test fun rejects_decimal_money(){assertTrue(parser.parse("10 10.5") is ParseResult.Error)}
}
