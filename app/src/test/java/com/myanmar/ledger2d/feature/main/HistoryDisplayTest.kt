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

    @Test fun manualHistoryNeverGetsPrefixed() {
        assertEquals("20 1000", transactionDisplayText("MANUAL", "20 1000"))
    }
}
