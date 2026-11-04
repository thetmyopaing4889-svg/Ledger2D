package com.myanmar.ledger2d.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.NumberFormat
import java.util.Locale

class HomeCommissionPresentationTest {
    @Test fun ordinaryValuesKeepExistingCompactTwoColumnLayout() {
        assertFalse(homeCommissionNeedsWideRows(0L, 0L, 0L, 0L))
        assertFalse(homeCommissionNeedsWideRows(250_000L, 100_000L, 350_000L, 9_999_999L))
    }

    @Test fun largeCumulativeOrSessionAmountsSwitchToFullWidthRows() {
        assertTrue(homeCommissionNeedsWideRows(0L, 0L, 0L, 10_000_000L))
        assertTrue(homeCommissionNeedsWideRows(100_000_000L, 0L, 100_000_000L, 0L))
        assertTrue(homeCommissionNeedsWideRows(0L, 0L, 0L, Long.MAX_VALUE))
        assertTrue(homeCommissionNeedsWideRows(0L, 0L, 0L, Long.MIN_VALUE))
    }

    @Test fun wrappedNumberPreservesEveryDigitCommaAndMinusSign() {
        for (amount in listOf(0L, 1L, 1234L, 123_456_789L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            val normal = NumberFormat.getIntegerInstance(Locale.US).format(amount)
            val wrapped = homeCommissionWrapNumber(amount)
            assertEquals(normal, wrapped.replace("\u200B", ""))
            assertEquals(normal.count { it == ',' }, wrapped.count { it == '\u200B' })
        }
        assertEquals("1,\u200B234,\u200B567", homeCommissionWrapNumber(1_234_567L))
    }
}
