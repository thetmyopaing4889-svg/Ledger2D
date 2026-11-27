package com.myanmar.ledger2d.feature.main

import org.junit.Assert.*
import org.junit.Test

class Cherry930DisplayTest {
    @Test fun sameDayMorningEvidencePreferredForReadOnlyDisplay() {
        val evidence="previous=none;modern930=04;internet930=96"
        assertEquals("04",cherry930DisplayValue(evidence,"modern930","55"))
        assertEquals("96",cherry930DisplayValue(evidence,"internet930","66"))
    }
    @Test fun missingOrInvalidEvidenceDoesNotInventDigits() {
        assertEquals("07",cherry930DisplayValue("modern930=none","modern930","07"))
        assertEquals("19",cherry930DisplayValue(null,"internet930","19"))
        assertNull(cherry930DisplayValue("modern930=bad","modern930",null))
        assertNull(cherry930DisplayValue(null,"internet930","9"))
    }
}
