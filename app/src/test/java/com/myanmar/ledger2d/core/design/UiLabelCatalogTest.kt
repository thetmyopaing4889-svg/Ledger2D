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

    @Test fun compactLabelsAndWarningsHaveEnglishTranslations() {
        assertEquals("Mon", UiLabelCatalog.lookup("en", "တနင်္လာ"))
        assertEquals("Over", UiLabelCatalog.lookup("en", "ကျော်"))
        assertEquals("Saving…", UiLabelCatalog.lookup("en", "သိမ်းဆည်းနေသည်…"))
        assertEquals("New winning digit", UiLabelCatalog.lookup("en", "ပေါက်ဂဏန်းအသစ်"))
        assertEquals("Recalculate this session for all customers",
            UiLabelCatalog.lookup("en", "Customer အားလုံး ဒီ Session ပြန်တွက်မည်"))
    }

    @Test fun myanmarModeKeepsShortActionsInMyanmar() {
        assertEquals("အတည်ပြုမည်", UiLabelCatalog.lookup("my", "Confirm"))
        assertEquals("အဆင်ပြေ", UiLabelCatalog.lookup("my", "OK"))
        assertEquals("ဒိုင်", UiLabelCatalog.lookup("my", "Agent"))
        assertEquals("ထိုးသား", UiLabelCatalog.lookup("my", "Customer"))
    }

    @Test fun bilingualScreenTitlesUseLanguageNotLineOrder() {
        assertEquals("2D Form", UiLabelCatalog.preferredTitlePart("en", "2D Form\nစာရင်းသွင်းရန်"))
        assertEquals("စာရင်းသွင်းရန်", UiLabelCatalog.preferredTitlePart("my", "2D Form\nစာရင်းသွင်းရန်"))
        assertEquals("Before", UiLabelCatalog.preferredTitlePart("en", "ဂဏန်းမထွက်ခင်\nBefore"))
        assertEquals("ဂဏန်းမထွက်ခင်", UiLabelCatalog.preferredTitlePart("my", "ဂဏန်းမထွက်ခင်\nBefore"))
    }

    @Test fun multilineEntryWarningIsTranslated() {
        val warning = "ဂဏန်းထွက်ပြီးချိန်ဖြစ်ပါသဖြင့် Preview မပြနိုင်သေးပါ။\nနောက်ကြောင်းပြန်စာရင်းသွင်းခြင်းကို အမှန်ခြစ်ပါ။"
        assertEquals("The result is already available. Enable backdated entry to preview this bet.",
            UiLabelCatalog.lookup("en", warning))
    }

    @Test fun riskTabsAndReportNumbersSwitchLanguageWithoutChangingAmounts() {
        assertEquals("Current", UiLabelCatalog.lookup("en", "လက်ရှိ"))
        assertEquals("Guaranteed profit", UiLabelCatalog.lookup("en", "အမြတ်သေချာ"))
        assertEquals("80,000 MMK", UiLabelCatalog.reportValue("en", "80,000 ကျပ်"))
        assertEquals("80,000 ကျပ်", UiLabelCatalog.reportValue("my", "80,000 ကျပ်"))
        assertEquals("80x", UiLabelCatalog.reportValue("en", "80 ဆ"))
        assertEquals("12 digits", UiLabelCatalog.reportValue("en", "12 ကွက်"))
        assertEquals("2 Nov AM", UiLabelCatalog.statementTitle("en", "2 Nov မနက်"))
        assertEquals("2 Nov မနက်", UiLabelCatalog.statementTitle("my", "2 Nov မနက်"))
    }

    @Test fun drawLabelsAreLocalizedButTechnicalBrandsAreUntouched() {
        assertEquals("Morning", UiLabelCatalog.lookup("en", "မနက်"))
        assertEquals("Evening", UiLabelCatalog.lookup("en", "ညနေ"))
        assertEquals("Morning session", UiLabelCatalog.lookup("en", "မနက်အကြိမ်"))
        assertEquals("Evening session", UiLabelCatalog.lookup("en", "ညနေအကြိမ်"))
        assertNull(UiLabelCatalog.lookup("en", "2D LIVE"))
        assertEquals("Better Data", UiLabelCatalog.lookup("my", "Better Data"))
    }

    @Test fun neverMutatesUnrecognizedCustomerNamesOrDigits() {
        assertNull(UiLabelCatalog.lookup("en", "မောင်မောင်"))
        assertNull(UiLabelCatalog.lookup("my", "Custom agent name"))
        assertNull(UiLabelCatalog.lookup("en", "04"))
    }
}
