package com.myanmar.ledger2d.feature.main

/**
 * Return original verbatim text only. Gemini experimental methods intentionally
 * have no fabricated DOC citation.
 */
internal object CherryMathSources {
    fun find(source:String?,id:String):CherryOriginalReference? {
        if(source.isNullOrBlank()||id.startsWith("research-"))return null
        val (section,phrase)=when(id){
            "w1"->7 to "* ဝမ်းချိန်း ပုံသေနည်း (၁):"
            "w2"->7 to "* ဝမ်းချိန်း ပုံသေနည်း (၂):"
            "l1"->7 to "* အဓိက လုံးပိုင် ပုံသေနည်း (၁):"
            "l2"->7 to "* အဓိက လုံးပိုင် ပုံသေနည်း (၂):"
            "b1"->7 to "* ဘရိတ် ပုံသေနည်း (၁):"
            "b2"->7 to "* ဘရိတ် ပုံသေနည်း (၂):"
            "cycle"->7 to "* ၃ လျှော့ ဘရိတ်စက်ဝန်းမူ:"
            "wl"->9 to "* မနေ့က ထွက်သွားသော မနက်ဂဏန်း"
            "formula17"->9 to "* တစ်ပတ်အတွင်း တနင်္လာနေ့ မနက်"
            "m44"->7 to "* ညနေ အပူး (44) အပြီး"
            "m05"->7 to "* မနက် 05 အပြီး"
            else->return null
        }
        val text=source.lineSequence().map{it.trim()}
            .firstOrNull{it.startsWith(phrase)}?:return null
        return CherryOriginalReference(section,"အပိုင်း ($section) · မူရင်းတွက်နည်း",text)
    }
}
