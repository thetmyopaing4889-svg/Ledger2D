package com.myanmar.ledger2d.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiLabelCatalogTest {
    @Test fun translatesCustomerAllAndAgentAllBothWays() {
        assertEquals("All customers", UiLabelCatalog.lookup("en", "Customer အားလုံး"))
        assertEquals("ထိုးသားအားလုံး", UiLabelCatalog.lookup("my", "Customer အားလုံး"))
        assertEquals("All agents", UiLabelCatalog.lookup("en", "ဒိုင်အားလုံး"))
        assertEquals("ဒိုင်အားလုံး", UiLabelCatalog.lookup("my", "All agents"))
    }

    @Test fun usesShortConsistentLabelsForMoneyAndScope() {
        assertEquals("Total commission to date", UiLabelCatalog.lookup("en", "ယနေ့အထိ ကော်မရှင်ရငွေစုစုပေါင်း"))
        assertEquals("Stake limit", UiLabelCatalog.lookup("en", "ကန့်သတ်ပမာဏ"))
        assertEquals("ထိုးသားစာရင်း", UiLabelCatalog.lookup("my", "Customer List"))
        assertEquals("Select customer", UiLabelCatalog.lookup("en", "ထိုးသားရွေးရန်"))
    }

    @Test fun neverMutatesUnrecognizedCustomerNamesOrDigits() {
        assertNull(UiLabelCatalog.lookup("en", "မောင်မောင်"))
        assertNull(UiLabelCatalog.lookup("my", "Custom agent name"))
        assertNull(UiLabelCatalog.lookup("en", "04"))
    }
}
