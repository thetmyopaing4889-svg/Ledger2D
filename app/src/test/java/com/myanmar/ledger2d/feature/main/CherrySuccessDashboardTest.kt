package com.myanmar.ledger2d.feature.main

import org.junit.Assert.*
import org.junit.Test

class CherrySuccessDashboardTest {
    @Test fun recordedZeroIsNotMissingEvidence() {
        assertEquals("မှတ်တမ်းမရှိ",successCountLabel(0,0,false))
        assertEquals("0/7 မှန်",successCountLabel(0,7,false))
        assertEquals("2/14 မှန်",successCountLabel(2,14,false))
        assertEquals("No record",successCountLabel(0,0,true))
        assertEquals("0/3 hit",successCountLabel(0,3,true))
    }
}
