package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.CherryEvidence
import com.myanmar.ledger2d.core.domain.CherryTab
import org.junit.Assert.*
import org.junit.Test

class CherryRuleWordingTest {
    @Test fun pattRuleUsesDirectMyanmarTitleAndPlainArithmetic() {
        val title=CherryRuleWording.title("s2-p-8","အပိုင်း (၂) +4 ပတ်သီး")
        assertEquals("8 လုံးမှ 2 ပတ်သီးမူ",title)
        assertFalse(title.contains("အပိုင်း"))
        val explanation=CherryRuleWording.how("s2-p-8","အပိုင်း (၂)",emptyList())
        assertTrue(explanation.contains("8 + 4 = 12"))
        assertTrue(explanation.contains("19 တွဲ"))
    }

    @Test fun headRuleIsSpecificRatherThanRawSectionTitle() {
        val title=CherryRuleWording.title("s2-h-8-2","အပိုင်း (၂) • 2 ထိပ်စီး")
        assertEquals("8 လုံးအလိုက် 2 ထိပ်စီးမူ",title)
        val explanation=CherryRuleWording.how("s2-h-8-2","",emptyList())
        assertTrue(explanation.contains("20 မှ 29"))
    }

    @Test fun pairReasonExplainsHow24WasProduced() {
        val evidence=CherryEvidence(
            id="s2-p-8",family="s2-p-8",
            name="အပိုင်း (၂) +4 ပတ်သီး",
            detail="နောက်ဆုံးထွက် 80 ကြောင့် အကျုံးဝင်သည်။",
            pair="24",tab=CherryTab.CURRENT)
        val reason=CherryRuleWording.pairReason(evidence,"24")
        assertTrue(reason.contains("80"))
        assertTrue(reason.contains("2"))
        assertTrue(reason.contains("24"))
        assertFalse(reason.contains("အပိုင်း"))
    }

    @Test fun bornAndCutLabelsDoNotExposeDocumentSectionNumbers() {
        val born=CherryRuleWording.title("s4-wheel-22-0@2026-11-13-EVENING",
            "22 အပူး → 2479 ခွေ")
        assertTrue(born.contains("2479"))
        assertFalse(born.contains("အပိုင်း"))
        val cut=CherryRuleWording.cutReason("အပိုင်း (၈) 795 (ဒဲ့/R) ဖြတ်မူ")
        assertFalse(cut.contains("အပိုင်း"))
        assertTrue(cut.contains("795"))
    }
}
