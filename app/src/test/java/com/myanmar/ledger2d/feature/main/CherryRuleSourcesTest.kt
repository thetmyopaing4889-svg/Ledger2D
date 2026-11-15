package com.myanmar.ledger2d.feature.main

import org.junit.Assert.*
import org.junit.Test

class CherryRuleSourcesTest {
    private val source = """
        အပိုင်း (၂) - ဂဏန်းတစ်လုံးချင်းဇယား
        ဂဏန်း
        နောက်ပိတ်မှ အသေယူနည်း (+7 မူ)
        ကျခဲ့လျှင် လိုက်ရမည့် ပတ်သီး (+4 မူ)
        တည်ဘောနှင့် ထိပ်စီးများ
        ပျောက်ပြီး ပြန်လာပါက သတိထားရန်
        တစ်လုံးချင်းလာပြီး လိုက်ရမည့်အတွဲ
        8
        5 ပိတ် သေ
        2 ပတ်သီး
        1 - 2 - 3 ထိပ်
        84 - 86 - 89
        0 - 2 လိုက်ပါ (8 ထိပ် -> 01234 ပူးခွေ)
        ________________
        အပိုင်း (၄) - အပူးမွေးမူ
        (က) အပူး
        (22) ထွက်လျှင်
        2479 အပြီးခွေ
        07 - 48 - 64 - 82 - 34
        ________________
        အပိုင်း (၈) - အထူးအလိုက်မူ
        * သီးသန့် အပြီးခွေ အလိုက်မူများ:
        * 80 R ထွက်လျှင် ➔ 1264 ခွေ (အပူးပါ) မွေးပါ။
        * 67 R ထွက်လျှင် ➔ 8123 ခွေ (အပူးပါ) မွေးပါ။
    """.trimIndent()

    @Test fun digitPattReferenceUsesOnlyOriginalPattColumn(){
        val ref=requireNotNull(CherryRuleSources.lookup(source,"s2-p-8"))
        assertEquals(2,ref.section)
        assertTrue(ref.label.contains("+4 ပတ်သီး"))
        assertTrue(ref.quote.contains("ဂဏန်း 8"))
        assertTrue(ref.quote.contains("2 ပတ်သီး"))
        assertFalse(ref.quote.contains("84 - 86"))
        assertFalse(ref.quote.contains("5 ပိတ် သေ"))
    }

    @Test fun headReferenceLinksToSameSourceRowWithoutInventingACell(){
        val ref=requireNotNull(CherryRuleSources.lookup(source,"s2-h-8-2"))
        assertTrue(ref.quote.contains("1 - 2 - 3 ထိပ်"))
        assertFalse(ref.quote.contains("2 ပတ်သီး"))
    }

    @Test fun originalWheelAndActiveBornResolveToRealSourceLines(){
        val wheel=requireNotNull(CherryRuleSources.lookup(source,"s8-80"))
        assertEquals(8,wheel.section)
        assertTrue(wheel.quote.contains("1264 ခွေ"))
        val born=requireNotNull(CherryRuleSources.lookup(
            source,"s4-wheel-22-0@2026-11-11-EVENING"))
        assertEquals(4,born.section)
        assertTrue(born.quote.contains("2479 အပြီးခွေ"))
    }

    @Test fun missingReferencesAreHiddenRatherThanFabricated(){
        assertNull(CherryRuleSources.lookup(source,"not-a-rule"))
        assertNull(CherryRuleSources.lookup(source,"s6-80"))
        assertNull(CherryRuleSources.lookup(null,"s8-80"))
    }
}
