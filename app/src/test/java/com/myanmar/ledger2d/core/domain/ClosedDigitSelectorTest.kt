package com.myanmar.ledger2d.core.domain

import org.junit.Assert.*
import org.junit.Test

class ClosedDigitSelectorTest {
    private val engine = BetExpansionEngine()

    private fun digits(format: QuickFormat, source: String = "") =
        (ClosedDigitSelector.select(format, source) as ClosedDigitSelection.Valid).digits

    private fun quickDigits(format: QuickFormat, source: String = ""): List<String> {
        val input = when (format) {
            QuickFormat.MANUAL -> return digits(format, source)
            QuickFormat.HEAD, QuickFormat.TAIL, QuickFormat.ROUND,
            QuickFormat.COMBINATION, QuickFormat.COMBINATION_DOUBLES -> "$"+"source.1"
            else -> "1"
        }
        return (engine.expand(input, format) as ParseResult.Success).bets
            .map { it.digit }.distinct().sorted()
    }

    @Test fun everyQuickEntryPatternUsesTheExactSameDigitSet() {
        QuickFormat.entries.filter { it != QuickFormat.MANUAL }.forEach { format ->
            val source = when(format) {
                QuickFormat.HEAD, QuickFormat.TAIL, QuickFormat.ROUND -> "9"
                QuickFormat.COMBINATION, QuickFormat.COMBINATION_DOUBLES -> "345"
                else -> ""
            }
            assertEquals(format.name, quickDigits(format,source), digits(format,source))
        }
    }

    @Test fun fixedAndParitySetsHaveExpectedCounts() {
        assertEquals(10, digits(QuickFormat.POWER).size)
        assertEquals(10, digits(QuickFormat.ASTROLOGY).size)
        assertEquals(10, digits(QuickFormat.DOUBLES).size)
        assertEquals(20, digits(QuickFormat.SIBLINGS).size)
        assertEquals(6, digits(QuickFormat.COMBINATION,"345").size)
        assertEquals(9, digits(QuickFormat.COMBINATION_DOUBLES,"345").size)
        assertEquals(19, digits(QuickFormat.ROUND,"9").size)
        assertEquals(10, digits(QuickFormat.HEAD,"9").size)
        assertEquals(10, digits(QuickFormat.TAIL,"9").size)
        listOf(QuickFormat.EVEN_ODD, QuickFormat.ODD_EVEN,
            QuickFormat.EVEN_EVEN, QuickFormat.ODD_ODD).forEach {
                assertEquals(25,digits(it).size)
            }
    }

    @Test fun manualMultipleDigitsAndReversalDeduplicateForClosure() {
        assertEquals(listOf("20","39","45"),digits(QuickFormat.MANUAL,"20.39/45"))
        assertEquals(listOf("02","20","39","93"),digits(QuickFormat.MANUAL,"20.39R"))
        assertEquals(listOf("11"),digits(QuickFormat.MANUAL,"11R"))
        assertEquals(listOf("05","16"),digits(QuickFormat.MANUAL,"၀၅.၁၆"))
    }

    @Test fun invalidInputsAreRejectedInFull() {
        assertTrue(ClosedDigitSelector.select(QuickFormat.MANUAL,"20.7") is ClosedDigitSelection.Invalid)
        assertTrue(ClosedDigitSelector.select(QuickFormat.HEAD,"99") is ClosedDigitSelection.Invalid)
        assertTrue(ClosedDigitSelector.select(QuickFormat.COMBINATION,"334") is ClosedDigitSelection.Invalid)
        assertTrue(ClosedDigitSelector.select(QuickFormat.MANUAL,"") is ClosedDigitSelection.Invalid)
    }
}
