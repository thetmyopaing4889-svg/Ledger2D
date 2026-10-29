package com.myanmar.ledger2d.core.domain

import org.junit.Assert.*
import org.junit.Test

class MessengerSmartInputTest {
    private val engine = BetExpansionEngine()
    private fun parse(input: String, format: QuickFormat = QuickFormat.MANUAL): ParseResult.Success {
        val smart = engine.smartExpand(input, format)
        assertFalse("No error allowed for $input: ${smart.lines}", smart.hasErrors)
        return ParseResult.Success(smart.bets, smart.total)
    }
    private fun amount(input: String): Long = parse(input).total

    @Test fun aliasesForRoundAreEquivalentWithMyanmarOrEnglishNumbers() {
        val samples = listOf("9ပါ2000", "9အပါ2000", "9ပတ်2000", "9ပတ်သီး2000",
            "၉ပါ၂၀၀၀", "၉အပါ၂၀၀၀", "၉ပတ်၂၀၀၀", "၉ပတ်သီး၂၀၀၀")
        samples.forEach { source ->
            assertEquals(source, 38000L, amount(source))
            assertEquals(19, parse(source).bets.size)
        }
    }

    @Test fun combinationAndDoublesAliasesAreEquivalent() {
        listOf("456ခွေ1000", "456အခွေ1000", "၄၅၆ခွေ၁၀၀၀").forEach {
            assertEquals(it, 6000L, amount(it))
        }
        listOf("456ခွေပူး1000", "456အခွေပူး1000", "၄၅၆ခွေပူး၁၀၀၀").forEach {
            assertEquals(it, 9000L, amount(it))
        }
        assertEquals(32000L, amount("4567ခွေပူး2000"))
    }

    @Test fun repeatedSourceDigitsRejectedForBothCombinationFormats() {
        listOf("334ခွေ1000", "334ခွေပူး1000", "455အခွေ1000",
            "၄၅၅အခွေပူး၁၀၀၀").forEach { input ->
            val result = engine.smartExpand(input)
            assertTrue(input, result.hasErrors)
            val error = result.lines.first().result as ParseResult.Error
            assertTrue(input, error.message.contains("ဂဏန်းထပ်"))
        }
    }

    @Test fun allFourParityFormatsExpandToTwentyFiveDistinctSlots() {
        val cases = mapOf(
            "စုံမ1000" to ("01" to "89"),
            "မစုံ1000" to ("10" to "98"),
            "စုံစုံ1000" to ("00" to "88"),
            "မမ1000" to ("11" to "99"),
        )
        for ((source, expected) in cases) {
            val parsed = parse(source)
            assertEquals(source, 25000L, parsed.total)
            assertEquals(source, 25, parsed.bets.size)
            assertTrue(parsed.bets.any { it.digit == expected.first })
            assertTrue(parsed.bets.any { it.digit == expected.second })
        }
        assertEquals(25000L, amount("စုံမ=၁၀၀၀"))
        QuickFormat.entries.filter { it.name.contains("ODD") }.forEach {
            assertEquals(25000L, parse("1000", it).total)
        }
    }

    @Test fun allManualSeparatorsAndRVariantsAreEquivalent() {
        listOf("01.02.03=1000", "01/02/03 1000", "01-02-03-1000",
            "01 02 03:1000", "၀၁.၀၂.၀၃=၁၀၀၀").forEach {
            assertEquals(it, 3000L, amount(it))
        }
        listOf("01.02.03R1000", "01/02/03 r 1000", "01-02-03r1000",
            "၀၁.၀၂.၀၃R၁၀၀၀", "01.02.03ပြန်1000", "01.02.03ပြောင်းပြန်1000").forEach {
            assertEquals(it, 6000L, amount(it))
        }
        val reversed = parse("11r1000")
        assertEquals(2000L, reversed.total)
        assertEquals(listOf("11"), reversed.bets.map { it.digit })
        assertEquals(2000L, reversed.bets.single().amount)
    }

    @Test fun amountCommaAndMultipleSameLineEntriesAreNotConfused() {
        assertEquals(1000L, amount("01=1,000"))
        assertEquals(1500L, amount("01=1,000,02=500"))
        assertEquals(1500L, amount("01=1000,02=500"))
        assertEquals(1700L, amount("01=1000 02=500 03=200"))
        assertEquals(1000L, amount("01=1k"))
        assertEquals(1000L, amount("၀၁=၁ထောင်"))
        assertEquals(250000L, amount("01=250k"))
    }

    @Test fun mixedMessengerPasteAggregatesOverlappingDigits() {
        val source = """
            ၄၅၆ခွေပူး၁၀၀၀
            20.39.45r2000
            ၉ပါ၂၀၀၀
            01.02.03=1000
            စုံမ100
            အပူး500
        """.trimIndent()
        val parsed = parse(source)
        assertEquals(69500L, parsed.total)
        assertEquals(500L, parsed.bets.first { it.digit == "11" }.amount)
    }

    @Test fun overlappingFormatsMergeOneDigitBeforeLimitsAreEvaluated() {
        val result = parse("11R1000\nအပူး500")
        assertEquals(7000L, result.total)
        assertEquals(2500L, result.bets.single { it.digit == "11" }.amount)
    }

    @Test fun aliasPrecedencePreventsPowerFromBecomingRoundOrDoublesFromCombination() {
        assertEquals(10000L, amount("ပါဝါ1000"))
        assertEquals(10000L, amount("Power1000"))
        assertEquals(9000L, amount("456အခွေပူး1000"))
        assertEquals(10000L, amount("ပူး1000"))
        assertEquals(10000L, amount("ညီကို500"))
        assertEquals(10000L, amount("နက်ခတ်1000"))
    }

    @Test fun extraMessengerHeadersAreIgnoredWithoutTurningTotalsIntoBets() {
        val input = "အမည်: မောင်မောင်\nမနက်\n01=1000\nစုစုပေါင်း 1000"
        val parsed = engine.smartExpand(input)
        assertEquals(1000L, parsed.total)
        assertFalse(parsed.hasErrors)
        assertEquals(1, parsed.lines.size)
    }

    @Test fun malformedLinesAreNotSilentlyAccepted() {
        listOf("01=1000\nမသိတဲ့format1000", "01=1000\n334ခွေ1000",
            "01=1000,,02=500", "0102031000", "01 0", "01=0").forEach { input ->
            assertTrue(input, engine.smartExpand(input).hasErrors)
        }
    }
}
