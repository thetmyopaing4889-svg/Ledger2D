package com.myanmar.ledger2d.feature.main

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryDisplayTest {
    @Test fun mixedFormatsPreserveExactlyWhatCustomerPasted() {
        val input = "4567အခွေပူး2000\n8ပတ်သီး3000"
        assertEquals(input, transactionDisplayText("POWER", input))
    }

    @Test fun explicitFormatMarkerDoesNotGetDuplicated() {
        assertEquals("အပူး 1000", transactionDisplayText("DOUBLES", "အပူး 1000"))
    }

    @Test fun unlabeledInputRetainsTheFormatChipDescription() {
        assertEquals("ပါဝါ 1000", transactionDisplayText("POWER", "1000"))
    }

    @Test fun allChipFormatsRetainTheirLabelsInHistory() {
        val formats = mapOf(
            "POWER" to "ပါဝါ", "ASTROLOGY" to "နက္ခတ်",
            "DOUBLES" to "အပူး", "SIBLINGS" to "ညီအကို",
            "ROUND" to "ပတ်သီး", "HEAD" to "ထိပ်စည်း",
            "TAIL" to "နောက်ပိတ်", "COMBINATION" to "အခွေ",
            "COMBINATION_DOUBLES" to "အခွေပူး",
            "EVEN_ODD" to "စုံမ", "ODD_EVEN" to "မစုံ",
            "EVEN_EVEN" to "စုံစုံ", "ODD_ODD" to "မမ",
        )
        formats.forEach { (format, label) ->
            assertEquals("$label 1000", transactionDisplayText(format, "1000"))
        }
    }

    @Test fun formattedHistoryPreservesLineBreaks() {
        assertEquals("ပါဝါ 1000\nပါဝါ 2000", transactionDisplayText("POWER", "1000\n2000"))
    }

    @Test fun mixedExplicitAndFallbackFormatsRemainUnambiguous() {
        assertEquals(
            "9ပတ်သီး1000\nပါဝါ 1000",
            transactionDisplayText("POWER", "9ပတ်သီး1000\n1000"),
        )
    }

    @Test fun reverseCommandOverridesSelectedChip() {
        assertEquals("20.39R2000", transactionDisplayText("POWER", "20.39R2000"))
        assertEquals(
            "20.39R2000\nပါဝါ 1000",
            transactionDisplayText("POWER", "20.39R2000\n1000"),
        )
    }

    @Test fun eachUnlabeledFormatLineShowsItsOwnLabel() {
        assertEquals("ပါဝါ 1000\nပါဝါ 2000", transactionDisplayText("POWER", "1000\n2000"))
    }

    @Test fun manualHistoryNeverGetsPrefixed() {
        assertEquals("20 1000", transactionDisplayText("MANUAL", "20 1000"))
    }
}
