package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.CherryEvidence

/**
 * Reader-facing wording only. Original rule IDs and calculation evidence stay intact.
 * No raw document section numbers or internal scoring terms are shown to the user.
 */
internal object CherryRuleWording {
    private fun code(id:String)=id.substringBefore('@')
    private fun last(detail:String):String? =
        Regex("နောက်ဆုံးထွက် ([0-9]{2})").find(detail)?.groupValues?.getOrNull(1)
    private fun patt(id:String):Int? =
        code(id).takeIf{it.startsWith("s2-p-")}?.substringAfterLast('-')?.toIntOrNull()
    private fun head(id:String):Pair<Int,Int>? {
        val bits=code(id).split("-")
        if(bits.size!=4 || bits[0]!="s2" || bits[1]!="h")return null
        return (bits[2].toIntOrNull()?:return null) to
            (bits[3].toIntOrNull()?:return null)
    }

    fun title(id:String,raw:String):String {
        val key=code(id)
        val p=patt(id); val h=head(id)
        return when {
            p!=null -> "$p လုံးမှ ${(p+4)%10} ပတ်သီးမူ"
            h!=null -> "${h.first} လုံးအလိုက် ${h.second} ထိပ်စီးမူ"
            key.startsWith("s2-f-") -> "${key.substringAfterLast('-')} လုံး၏ တွဲဖက်ဂဏန်းမူ"
            key=="s2-8head" -> "8 ထိပ်နောက် 01234 အပူးခွေမူ"
            key=="s8-80" -> "80 နောက် 1264 အပူးခွေမူ"
            key=="s8-67" -> "67 နောက် 8123 အပူးခွေမူ"
            key=="s8-00-bk" -> "00 အပူးနောက် 5 ဘရိတ်မွေးမူ"
            key.startsWith("s4-wheel-") -> raw.replace(" → "," နောက် ")+"မူ"
            key.startsWith("s4-born-") -> raw+"မူ"
            key=="s5-fri" -> "သောကြာ ပါဝါမွေးမူ"
            key=="s5-wed" -> "ဗုဒ္ဓဟူးညနေ အပူးမွေးမူ"
            key=="s5-mon" -> "တနင်္လာမနက် ဘရိတ်မွေးမူ"
            key=="s3-week" -> "အပတ်စဉ် စောင့်ကြည့်ဂဏန်းများ"
            key=="s3-six" -> "အပတ်စဉ် ဆတိုးအုပ်စု"
            key=="s3-ten" -> "ဆယ်ပြည့်ဂဏန်း လိုက်မူ"
            key=="s3-sib" -> "ညီအစ်ကိုဂဏန်း လိုက်မူ"
            key=="s3-power" -> "ပါဝါဂဏန်း လိုက်မူ"
            key=="s3-astro" -> "နက္ခတ်ဂဏန်း လိုက်မူ"
            key=="s3-double" -> "အပူးဂဏန်း လိုက်မူ"
            key=="s3-9to7" -> "9 ဘရိတ်နောက် 7 ဘရိတ်မူ"
            key=="s3-7to4" -> "7 ဘရိတ်နောက် 4 ဘရိတ်မူ"
            key=="s1-930" -> "9:30 ထိပ်/ပိတ်တူမူ"
            key=="s1-930bk" -> "9:30 ဘရိတ်တူမူ"
            key=="s8-48" -> "48 နောက် 6 ပိတ်မူ"
            key=="s8-13" -> "13 နောက် 3 ဘရိတ်မူ"
            key=="s8-74" -> "74 နောက် 8 ပတ်သီးမူ"
            key=="s8-71" -> "71 နောက် 2 ပတ်သီးမူ"
            key=="s8-15" -> "15 နောက် 4 ပတ်သီးမူ"
            key=="s8-88" -> "88 အပူးနောက် 3 ပတ်သီးမူ"
            key.startsWith("s6-") -> raw.replace(Regex("အပိုင်း\\s*\\([^)]+\\)\\s*"),"")
                .replace("သီးသန့်မွေး","သီးသန့်မွေးဂဏန်း").trim()+"မူ"
            else -> raw.replace(Regex("အပိုင်း\\s*\\([^)]+\\)\\s*"),"")
                .replace(" → "," နောက် ").trim()
                .let{if(it.endsWith("မူ"))it else it+"မူ"}
        }
    }

    fun why(id:String,name:String,detail:String):String {
        val key=code(id)
        val previous=last(detail)
        val p=patt(id);val h=head(id)
        return when {
            p!=null ->
                "နောက်ဆုံးထွက် ${previous?:"ဂဏန်း"} မှ $p လုံးကို တွေ့ထားပါတယ်။ " +
                "$p ကို 4 ပေါင်းပြီး ရလာတဲ့ ပတ်သီးလုံးကို လိုက်တာပါ။"
            h!=null ->
                "နောက်ဆုံးထွက် ${previous?:"ဂဏန်း"} မှ ${h.first} လုံးကို တွေ့ထားပါတယ်။ " +
                "မူဇယားအရ ${h.second} ထိပ်စီးကို လိုက်ထားတာပါ။"
            key.startsWith("s2-f-") ->
                "နောက်ဆုံးထွက် ${previous?:"ဂဏန်း"} မှ " +
                "${key.substringAfterLast('-')} လုံးကို တွေ့ထားလို့ " +
                "သက်ဆိုင်ရာ တွဲဖက်ဂဏန်းတွေကို လိုက်ထားတာပါ။"
            key=="s2-8head" ->
                "နောက်ဆုံးထွက် ${previous?:"ဂဏန်း"} ရဲ့ ထိပ်က 8 ဖြစ်လို့ " +
                "01234 အပူးခွေမူကို လိုက်ထားတာပါ။"
            key=="s8-80"||key=="s8-67" ->
                "နောက်ဆုံးရလဒ်က ${if(key=="s8-80")"80" else "67"} ဖြစ်လို့ " +
                "${if(key=="s8-80")"1264" else "8123"} အပူးခွေမူကို လိုက်ထားတာပါ။"
            key.startsWith("s4-wheel-")||key.startsWith("s4-born-") ->
                "အပူးထွက်ခဲ့ပြီးနောက် ဒီမွေးမူ အသက်ဝင်လာပါတယ်။ " +
                "သက်တမ်းမကုန်သေးသလို မွေးဂဏန်းလည်း ကြားပွဲတွေမှာ မထွက်သေးပါ။"
            key=="s8-00-bk" -> "00 အပူးထွက်ခဲ့ပြီး 5 ဘရိတ်မွေးမူ သက်တမ်းမကုန်သေးလို့ လိုက်ထားတာပါ။"
            key=="s5-fri" -> "သောကြာနေ့မှာ ပါဝါဂဏန်း ထွက်ခဲ့လို့ ဒီမွေးမူကို လိုက်ထားတာပါ။"
            key=="s5-wed" -> "ဗုဒ္ဓဟူးညနေမှာ အပူးထွက်ခဲ့လို့ ဒီမွေးမူကို လိုက်ထားတာပါ။"
            key=="s5-mon" -> "တနင်္လာမနက်ရလဒ်အလိုက် အပတ်စဉ်ဘရိတ်တွေကို လိုက်ထားတာပါ။"
            key=="s3-week" -> "ယခုအပတ်မှာ မထွက်ရသေးတဲ့ အပတ်စဉ်စောင့်ကြည့်ဂဏန်းတွေပါ။"
            key=="s3-six" -> "အပတ်စဉ် ဆတိုးအုပ်စုထဲက ဂဏန်းတစ်တွဲ ထွက်ပြီး ကျန်တဲ့အတွဲတွေကို စောင့်ကြည့်ထားတာပါ။"
            key.startsWith("s1-930") ->
                "9:30 Modern နဲ့ Internet ရဲ့ ထိပ်၊ ပိတ် သို့မဟုတ် ဘရိတ်တူညီမှုကြောင့် " +
                "ဒီမူကို ထည့်တွက်ထားတာပါ။"
            else -> if(previous!=null)
                "နောက်ဆုံးထွက် $previous က ${title(id,name)} နဲ့ " +
                "ကိုက်ညီလို့ ဒီမူကို လိုက်ထားတာပါ။"
            else "ရှေ့အကြိမ်တွေက ရလဒ်အရ ${title(id,name)} " +
                "အကျုံးဝင်နေသေးလို့ လိုက်ထားတာပါ။"
        }
    }

    fun how(id:String,raw:String,pairs:List<String>):String {
        val key=code(id);val p=patt(id);val h=head(id)
        return when {
            p!=null ->
                "$p + 4 = ${p+4}။ နောက်ဆုံးတစ်လုံး ${(p+4)%10} ကိုယူပြီး " +
                "အဲဒီလုံးပါဝင်တဲ့ ထိပ်/ပိတ်အတွဲ 19 တွဲ ချဲ့ထားပါတယ်။"
            h!=null ->
                "${h.second} ထိပ်စီးကို ${h.second}0 မှ ${h.second}9 အထိ 10 တွဲ ချဲ့ထားပါတယ်။"
            key.startsWith("s2-f-") ->
                "သတ်မှတ်ထားတဲ့ တွဲဖက်ဂဏန်းနှစ်လုံးကို ဒဲ့/ပြန် တွဲထားပါတယ်။"
            key=="s8-80"||key=="s8-67"||key=="s2-8head" ->
                "ခွေထဲကဂဏန်းတွေကို အချင်းချင်း ဒဲ့/ပြန် တွဲထားပြီး အပူးတွေပါ ထည့်တွက်ထားပါတယ်။"
            key.startsWith("s4-wheel-") ->
                "ခွေထဲက ဂဏန်းတွေကို အချင်းချင်း ဒဲ့/ပြန် တွဲထားပါတယ်။"
            key=="s5-mon"||key=="s5-wed"||key=="s8-00-bk"||
                key=="s3-9to7"||key=="s3-7to4" ->
                "လိုက်ရမယ့်ဘရိတ်အတွက် ထိပ်နဲ့ပိတ်ပေါင်းလဒ် ကိုက်တဲ့အတွဲတွေကို ယူထားပါတယ်။"
            key=="s3-week"||key=="s3-six" ->
                "အပတ်စဉ်အုပ်စုထဲက ထွက်ပြီးသားတွေကို ဖယ်ပြီး ကျန်တဲ့အတွဲတွေကို ဖော်ပြထားပါတယ်။"
            key.startsWith("s1-930") ->
                "တူညီမှုမူအရ 0 ပါတဲ့အတွဲ၊ အပူးနဲ့ သက်ဆိုင်ရာ ပြန်ကျဂဏန်းတွေကို ယူထားပါတယ်။"
            else -> "ဒီမူအတွက် သတ်မှတ်ထားတဲ့ ဂဏန်းအတွဲ ${pairs.size} တွဲကို " +
                "အောက်မှာ အပြည့်အစုံ ဖော်ပြထားပါတယ်။"
        }
    }

    fun pairReason(e:CherryEvidence,pair:String):String {
        val p=patt(e.id);val h=head(e.id);val key=code(e.id)
        val previous=last(e.detail)
        return when {
            p!=null ->
                "${previous?:"ရှေ့ဂဏန်း"} မှ $p လုံးကို 4 ပေါင်းရင် " +
                "နောက်ဆုံးလုံး ${(p+4)%10} ရပါတယ်။ $pair မှာ ဒီပတ်သီးလုံးပါလို့ ကိုက်ညီပါတယ်။"
            h!=null ->
                "${previous?:"ရှေ့ဂဏန်း"} ထဲက ${h.first} လုံးကြောင့် " +
                "${h.second} ထိပ်ကို လိုက်တာပါ။ $pair က ဒီထိပ်စီးနဲ့ ကိုက်ညီပါတယ်။"
            key=="s8-80"||key=="s8-67"||key=="s2-8head"||
                key.startsWith("s4-wheel-") ->
                "သက်ဆိုင်ရာခွေထဲက ဂဏန်းတွေကို တွဲစပ်ရာမှာ $pair ပါဝင်လာတာပါ။"
            key.startsWith("s2-f-") ->
                "${key.substringAfterLast('-')} လုံးနောက် လိုက်ရတဲ့ တွဲဖက်ဂဏန်းအဖြစ် " +
                "$pair ကို သတ်မှတ်ထားပါတယ်။"
            key=="s3-week"||key=="s3-six" ->
                "$pair က ယခုအပတ် စောင့်ကြည့်စာရင်းထဲမှာ ကျန်နေသေးတဲ့အတွဲပါ။"
            else -> "ဒီမူရဲ့ ဂဏန်းစာရင်းထဲမှာ $pair ပါဝင်လို့ ထောက်ခံထားတာပါ။"
        }
    }

    fun cutReason(raw:String):String =
        raw.replace(Regex("အပိုင်း\\s*\\(၈\\)\\s*"),"")
            .replace("Cut Digit","ရှောင်ရန်ဂဏန်း")
            .trim()
}
