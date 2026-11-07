
package com.myanmar.ledger2d.feature.main

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoDAnalysisTextTest {
    @Test fun allEightChecksHaveBilingualCopy() {
        assertEquals(setOf("02", "03", "06", "08", "05", "07", "04", "01"),
            TwoDAnalysisText.checkIds)
        for (id in TwoDAnalysisText.checkIds) {
            for (en in listOf(false, true)) {
                val copy = TwoDAnalysisText.get(id, en)
                assertTrue(copy.title.isNotBlank())
                assertTrue(copy.subtitle.isNotBlank())
                assertTrue(copy.description.isNotBlank())
            }
        }
    }

    @Test fun approvedTitlesAndMissingDigitTermsAreNotLost() {
        assertEquals("လက်ရှိအကြိမ်အတွက် ယခင်ထွက်ရှိမှုမှတ်တမ်း",
            TwoDAnalysisText.title("02", false))
        assertEquals("ယခုအပတ်အတွက် ယခင် စုံ/မ ထွက်ရှိမှု",
            TwoDAnalysisText.title("03", false))
        assertEquals("မထွက်တာကြာနေသော ဂဏန်းနှင့် ဂဏန်းတွဲများ",
            TwoDAnalysisText.title("06", false))
        assertEquals("ယခင်ထွက်ဂဏန်းပေါ် အခြေခံ၍ လက်ရှိအကြိမ်အတွက် စစ်ဆေးခြင်း",
            TwoDAnalysisText.title("08", false))
        assertEquals("အသစ်/အဟောင်း ဂဏန်းစီးဆင်းမှု",
            TwoDAnalysisText.title("05", false))
        assertEquals("အထူးဂဏန်းအုပ်စုများ",
            TwoDAnalysisText.title("07", false))
        assertEquals("ပြီးခဲ့တဲ့အပတ် ထွက်ဂဏန်းစာရင်းချုပ်",
            TwoDAnalysisText.title("04", false))
        assertEquals("ပြီးခဲ့တဲ့အပတ် ပျောက်ဂဏန်းထွက်ရှိမှု",
            TwoDAnalysisText.title("01", false))
        val missing = TwoDAnalysisText.subtitle("06", false)
        assertTrue(missing.contains("ပျောက်ဂဏန်း"))
        assertFalse(missing.contains("တစ်လုံးဂဏန်း"))
    }

    @Test fun preservesPreviouslyApprovedA02Explanation() {
        assertEquals(
            "နေ့တူ အကြိမ်တူ ယခင်က ထွက်ရှိခဲ့သော ဂဏန်းတွဲများအား စစ်ဆေးထားခြင်း ဖြစ်ပါသည်။",
            TwoDAnalysisText.description("02", false))
    }
}
