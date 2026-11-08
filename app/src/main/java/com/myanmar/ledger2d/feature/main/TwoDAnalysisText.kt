
package com.myanmar.ledger2d.feature.main

/**
 * Single source of truth for the eight approved 2D analysis check names.
 * The compact subtitle is only a navigation hint; the description retains
 * the approved explanation instead of replacing it with a shorter phrase.
 */
internal data class TwoDCheckText(
    val title: String,
    val subtitle: String,
    val description: String,
)

internal object TwoDAnalysisText {
    val checkIds = setOf("02", "03", "06", "08", "05", "07", "04", "01")

    private data class Copy(
        val myTitle: String, val enTitle: String,
        val myShort: String, val enShort: String,
        val myExplanation: String,
        val enExplanation: String,
    )

    private val approved: Map<String, Copy> = mapOf(
        "02" to Copy(
            "လက်ရှိအကြိမ်အတွက် ယခင်ထွက်ရှိမှုမှတ်တမ်း",
            "Same weekday and draw history",
            "နေ့တူ၊ အကြိမ်တူ ယခင်မှတ်တမ်း", "Matching weekday and draw",
            "နေ့တူ အကြိမ်တူ ယခင်က ထွက်ရှိခဲ့သော ဂဏန်းတွဲများအား စစ်ဆေးထားခြင်း ဖြစ်ပါသည်။",
            "Shows recorded 2D results from past matching weekdays and draw sessions.",
        ),
        "03" to Copy(
            "ယခုအပတ်အတွက် ယခင် စုံ/မ ထွက်ရှိမှု",
            "Historical parity sequence",
            "စုံ/မ ပုံစံတူ အပတ်များ", "Matching parity patterns",
            "ယခုအပတ်အတွင်း စုံ/မ ထွက်ရှိမှုအား ယခင်ထွက်ရှိခဲ့သော စုံ/မ ထွက်ရှိမှု ပုံစံတူစစ်ဆေးထားခြင်း ဖြစ်ပါသည်။",
            "Compares this week's recorded even/odd sequence with historical matching patterns.",
        ),
        "06" to Copy(
            "ပျောက်ဂဏန်းနှင့် ပျောက်ဂဏန်းတွဲများ",
            "Long-absent digits and pairs",
            "ပျောက်ဂဏန်းနှင့် ပျောက်ဂဏန်းတွဲ အကြိမ်ရေ", "Digit and pair absence streaks",
            "ပျောက်ဂဏန်းတွဲနှင့် ပျောက်ဂဏန်းများကို မနက်၊ ညနေ၊ နှစ်ကြိမ်ပေါင်း သီးခြားစစ်ဆေးထားခြင်းဖြစ်ပါသည်။",
            "Shows recorded absence streaks for morning, evening and combined results.",
        ),
        "08" to Copy(
            "ယခင်ထွက်ဂဏန်းပေါ် အခြေခံ၍ လက်ရှိအကြိမ်အတွက် စစ်ဆေးခြင်း",
            "Match previous results",
            "ရှေ့အကြိမ်ထွက်ဂဏန်းများနဲ့ တိုက်စစ်", "Compare preceding results",
            "ယခင်ထွက်ဂဏန်းနှစ်ကြိမ်နှင့် တူညီခဲ့သော မှတ်တမ်းများကို စစ်ဆေးထားခြင်း ဖြစ်ပါသည်။",
            "Compares recorded outcomes following the two preceding results.",
        ),
        "05" to Copy(
            "အသစ်/အဟောင်း ဂဏန်းစီးဆင်းမှု",
            "New / old digit flow",
            "ဒီအပတ် ထွက်ပြီး/မထွက်သေးသောဂဏန်း", "Observed and unseen digits",
            "ဒီအပတ် ထွက်ဂဏန်းတစ်ကြိမ်ချင်းအလိုက် ဂဏန်းအသစ်/အဟောင်းကို စစ်ဆေးထားခြင်း ဖြစ်ပါသည်။",
            "Shows newly observed and previously observed digits this week.",
        ),
        "07" to Copy(
            "အထူးဂဏန်းအုပ်စုများ",
            "Special number groups",
            "အပူး၊ ညီအကို၊ ပါဝါ၊ နက္ခတ်", "Doubles, siblings, power, astrology",
            "ဒီအပတ် ထွက်ရှိခဲ့သော အထူးဂဏန်းအုပ်စုများကို စစ်ဆေးထားခြင်း ဖြစ်ပါသည်။",
            "Shows recorded appearances of special number groups.",
        ),
        "04" to Copy(
            "ပြီးခဲ့တဲ့အပတ် ထွက်ဂဏန်းစာရင်းချုပ်",
            "Previous week's results summary",
            "ထွက်ဂဏန်းအကြိမ်ရေနှင့် ပျောက်ဂဏန်း", "Frequencies and missing digits",
            "ပြီးခဲ့တဲ့အပတ် ရှိထားသောထွက်ဂဏန်းမှတ်တမ်းများအပေါ် အခြေခံ၍ စစ်ဆေးထားခြင်း ဖြစ်ပါသည်။",
            "Summarizes recorded results from the previous week.",
        ),
        "01" to Copy(
            "ပြီးခဲ့တဲ့အပတ် ပျောက်ဂဏန်းထွက်ရှိမှု",
            "Missing-digit return history",
            "ပျောက်ဂဏန်းတွေ ဒီအပတ် ပြန်ဆင်းမှု", "Observed missing-digit returns",
            "ပြီးခဲ့တဲ့အပတ် ပျောက်ဂဏန်းများ ဒီအပတ် ပြန်ဆင်းခဲ့မှုနှင့် ယခင်မှတ်တမ်းများကို စစ်ဆေးထားခြင်း ဖြစ်ပါသည်။",
            "Reviews this week's returns of digits absent in recorded previous-week draws.",
        ),
    )

    fun get(id: String, en: Boolean): TwoDCheckText {
        val copy = requireNotNull(approved[id]) { "Unknown 2D check: " + id }
        return if (en) TwoDCheckText(copy.enTitle, copy.enShort, copy.enExplanation)
        else TwoDCheckText(copy.myTitle, copy.myShort, copy.myExplanation)
    }

    fun title(id: String, en: Boolean) = get(id, en).title
    fun subtitle(id: String, en: Boolean) = get(id, en).subtitle
    fun description(id: String, en: Boolean) = get(id, en).description
}
