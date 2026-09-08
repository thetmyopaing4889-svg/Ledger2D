package com.myanmar.ledger2d.core.domain

import org.junit.Assert.*
import org.junit.Test

class FormatExpansionTest {
    private val engine=BetExpansionEngine()
    private fun success(raw:String,f:QuickFormat)=engine.expand(raw,f) as ParseResult.Success
    @Test fun power_is_exact(){assertEquals(listOf("05","16","27","38","49","50","61","72","83","94"),success("100",QuickFormat.POWER).bets.map{it.digit});assertEquals(1000,success("100",QuickFormat.POWER).total)}
    @Test fun astrology_is_exact(){assertEquals(setOf("07","70","18","81","24","42","35","53","69","96"),success("100",QuickFormat.ASTROLOGY).bets.map{it.digit}.toSet())}
    @Test fun doubles_are_exact(){assertEquals((0..9).map{"$it$it"},success("50",QuickFormat.DOUBLES).bets.map{it.digit})}
    @Test fun siblings_are_exact_20(){val r=success("25",QuickFormat.SIBLINGS);assertEquals(20,r.bets.size);assertEquals(500,r.total);assertTrue(r.bets.any{it.digit=="09"})}
    @Test fun combination_345(){assertEquals(setOf("34","43","45","54","35","53"),success("345.100",QuickFormat.COMBINATION).bets.map{it.digit}.toSet())}
    @Test fun combination_doubles_345(){assertEquals(setOf("34","43","45","54","35","53","33","44","55"),success("345.100",QuickFormat.COMBINATION_DOUBLES).bets.map{it.digit}.toSet())}
    @Test fun combination_requires_three_positions(){assertTrue(engine.expand("34.100",QuickFormat.COMBINATION) is ParseResult.Error)}
    @Test fun round_9_is_19_unique(){val r=success("9.100",QuickFormat.ROUND);assertEquals(19,r.bets.size);assertEquals(1900,r.total);assertTrue(r.bets.any{it.digit=="99"})}
    @Test fun head_9(){assertEquals((0..9).map{"9$it"},success("9.100",QuickFormat.HEAD).bets.map{it.digit})}
    @Test fun tail_9(){assertEquals((0..9).map{"${it}9"},success("9.100",QuickFormat.TAIL).bets.map{it.digit})}
    @Test fun repeated_source_positions_aggregate_deterministically(){val r=success("334.100",QuickFormat.COMBINATION);assertEquals(200,r.bets.first{it.digit=="33"}.amount)}
}
