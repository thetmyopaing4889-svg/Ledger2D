package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import kotlin.math.abs

/**
 * Better Data preview. Independent of Analysis and Research; never writes ledger data.
 *
 * A recommendation is a traceable rule/formula output, NOT a forecast probability.
 * Values the original 2D text does not define unambiguously remain hints, not made-up pairs.
 */
enum class BetterMethod { RULE, FORMULA }
enum class BetterStrategy { CURRENT, ALIVE }
enum class BetterMode { OVERLAP, RULES, FORMULAS }

data class BetterTrace(
    val id: String,
    val name: String,
    val method: BetterMethod,
    val number: String,
    val explanation: String,
)

data class BetterHint(val name: String, val value: String, val explanation: String)

data class BetterRulePerformance(
    val id: String,
    val samples: Int,
    val hits: Int,
    val currentStreak: Int,
)

data class BetterPick(
    val number: String,
    val traces: List<BetterTrace>,
    val cuts: List<String>,
    val supportScore: Double,
    val performance: List<BetterRulePerformance>,
) {
    val hasRule: Boolean get() = traces.any { it.method == BetterMethod.RULE }
    val hasFormula: Boolean get() = traces.any { it.method == BetterMethod.FORMULA }
    val hasConflict: Boolean get() = cuts.isNotEmpty()
    val traceIds: Set<String> get() = traces.map { it.id }.toSet()
}

data class BetterDataReport(
    val target: DrawIdentity,
    val observedDraws: Int,
    val current: List<BetterPick>,
    val alive: List<BetterPick>,
    val hints: List<BetterHint>,
    val aliveIds: List<String>,
    val has930: Boolean,
) {
    fun select(strategy: BetterStrategy, mode: BetterMode): List<BetterPick> {
        val source = if (strategy == BetterStrategy.CURRENT) current else alive
        return source.filter { pick ->
            !pick.hasConflict && when (mode) {
                BetterMode.OVERLAP -> pick.hasRule && pick.hasFormula
                BetterMode.RULES -> pick.hasRule
                BetterMode.FORMULAS -> pick.hasFormula
            }
        }.take(3)
    }
    fun cuts(): List<BetterPick> = current.filter { it.hasConflict }
}

object BetterDataQuantEngine {
    private val born = mapOf(
        "01" to listOf("68", "46"),
        "04" to listOf("11"),
        "07" to listOf("49", "29"),
        "09" to listOf("68"),
        "14" to listOf("17"),
        "17" to listOf("14"),
        "23" to listOf("79"),
        "27" to listOf("47", "37"),
        "43" to listOf("89"),
        "49" to listOf("60"),
        "54" to listOf("04"),
        "59" to listOf("47", "79"),
        "64" to listOf("65"),
        "65" to listOf("27", "64"),
        "69" to listOf("09"),
        "78" to listOf("72", "50", "61", "83", "13", "41", "85", "63"),
        "79" to listOf("47", "29"),
        "83" to listOf("43"),
        "84" to listOf("13", "10"),
        "89" to listOf("49", "76"),
        "92" to listOf("90"),
    )
    // Explicit 'born numbers' cells of section 4. The four-digit wheel is not
    // automatically expanded into 12/16 unordered predictions.
    private val doubleBorn = mapOf(
        "00" to listOf("77", "75", "55"),
        "11" to listOf("76", "64", "78", "84", "70", "71"),
        "22" to listOf("07", "48", "64", "82", "34", "79", "72", "49", "42"),
        "33" to listOf("81", "89", "51", "59"),
        "44" to listOf("83", "81", "31", "88", "33", "91", "93", "21", "23", "27", "38", "49"),
        "55" to listOf("17", "36", "37", "35", "07", "05"),
        "66" to listOf("17", "14", "97", "94"),
        "77" to listOf("09", "02", "29", "22"),
        "88" to listOf("31", "39", "11", "19"),
        "99" to listOf("61", "63", "41", "43"),
    )
    private val threeDigitCuts = mapOf(
        '0' to "795", '1' to "608", '2' to "714", '3' to "825", '4' to "923",
        '5' to "043", '6' to "195", '7' to "260", '8' to "371", '9' to "486",
    )

    private fun before(a: HistoricDraw, b: DrawIdentity): Boolean =
        a.date < b.date || (a.date == b.date && a.session.ordinal < b.session.ordinal)

    private fun ordered(draws: List<HistoricDraw>): List<HistoricDraw> =
        draws.asSequence().filter { validHistoric2D(it.digit) }
            .sortedWith(compareBy<HistoricDraw> { it.date }.thenBy { it.session.ordinal })
            .distinctBy { it.date to it.session }.toList()

    private fun traces(
        prior: List<HistoricDraw>,
        target: DrawIdentity,
        modern930: String?,
        internet930: String?,
    ): Pair<List<BetterTrace>, List<BetterHint>> {
        val last = prior.lastOrNull() ?: return emptyList<BetterTrace>() to emptyList()
        val result = mutableListOf<BetterTrace>()
        val hints = mutableListOf<BetterHint>()
        fun add(id: String, name: String, method: BetterMethod, values: List<String>, explanation: String) {
            values.filter(::validHistoric2D).distinct().forEach { n ->
                result += BetterTrace(id, name, method, n, explanation)
            }
        }
        born[last.digit]?.let {
            add("s6-${last.digit}", "အပိုင်း ၆ • ${last.digit} မွေးဂဏန်း", BetterMethod.RULE,
                it, "အရင်အကြိမ် ${last.digit} → မူရင်းစာတမ်း အပိုင်း (၆) မွေးဂဏန်း")
        }
        doubleBorn[last.digit]?.let {
            add("s4-${last.digit}", "အပိုင်း ၄ • အပူးမွေး", BetterMethod.RULE,
                it, "အရင်အကြိမ် ${last.digit} အပူး → အပိုင်း (၄) မွေးဂဏန်းဇယား။ ၅ ရက်သက်တမ်းမူကို နောက်ဆက်တွဲဗားရှင်းတွင် တိုးချဲ့ရန် လိုသေးသည်။")
        }
        if (last.digit == "70") {
            add("s6-70", "70 R အပြီး ပါဝါတွဲ", BetterMethod.RULE,
                listOf("05", "16", "27", "38", "49"),
                "အပိုင်း (၆) • 70 R အပြီး ပါဝါတွဲ အခြေခံ ၅ ကွက်")
        }
        if (target.session == DrawSession.EVENING) {
            val morning = prior.lastOrNull { it.date == target.date && it.session == DrawSession.MORNING }
            if (morning?.digit == "05") {
                add("s7-am05", "မနက် 05 → ညနေ 7-2 ဝမ်းချိန်း", BetterMethod.RULE,
                    listOf("52", "25", "27", "72"),
                    "အပိုင်း (၇) • ဒီနေ့မနက် 05 အပြီး ညနေရလဒ်အတွက် အထူးသတ်မှတ်ချက်")
            }
            if (morning?.digit == "92") {
                add("s6-am92", "မနက် 92 → ညနေ 7 ဘရိတ်", BetterMethod.RULE,
                    listOf("43", "07"),
                    "အပိုင်း (၆) • မနက် 92 အပြီး ညနေ 7 ဘရိတ် (43/07 အထူး)")
            }
        }
        if (last.digit == "80" || last.digit == "67") {
            val wheel = if (last.digit == "80") "1264" else "8123"
            hints += BetterHint("အပိုင်း ၈ • အပြီးခွေ", wheel,
                "${last.digit} R အပြီး ${wheel} ခွေ — အတွဲများအားလုံးကို စာတမ်းက တိတိကျကျ မရွေးထားသဖြင့် အလိုအလျောက် မချဲ့ထားပါ။")
        }
        val am = prior.takeLast(3)
        if (am.size >= 2) {
            val prev = am[am.lastIndex - 1].digit
            val cur = last.digit
            val w1 = (prev[1].digitToInt() * 2 + cur[1].digitToInt() + 4) % 10
            val power = (w1 + 5) % 10
            add("s7-w1", "အပိုင်း ၇ • ဝမ်းချိန်း ပုံသေနည်း ၁", BetterMethod.FORMULA,
                listOf("$w1$power", "$power$w1"),
                "(${prev[1]} × ၂) + ${cur[1]} + ၄ → နောက်ဆုံးတစ်လုံး ${w1}၊ ပါဝါ ${power}။ ပါဝါတွဲအဖြစ် စမ်းသပ်တွက်ချက်ထားသည်။")
            val lone = (cur[1].digitToInt() * 2 + 2) % 10
            hints += BetterHint("လုံးပိုင် ပုံသေနည်း ၁", lone.toString(),
                "(${cur[1]} × ၂) + ၂ → နောက်ဆုံးတစ်လုံး ${lone}။ တွဲစပ်စည်းကမ်းမသတ်မှတ်ရသေး၍ 2D အတွဲအဖြစ် မချဲ့ထားပါ။")
            val bk1 = (prev[0].digitToInt() * 2 + cur[0].digitToInt() +
                cur[1].digitToInt() * 3) % 10
            hints += BetterHint("ဘရိတ် ပုံသေနည်း ၁", bk1.toString(),
                "(${prev[0]} × ၂) + ${cur[0]} + (${cur[1]} × ၃) → ဘရိတ် ${bk1} (mod 10)")
            val diff = abs(prev[0].digitToInt() - prev[1].digitToInt())
            val bk2 = ((prev[0].digitToInt() + diff) * 4 + 1) % 10
            hints += BetterHint("ဘရိတ် ပုံသေနည်း ၂", bk2.toString(),
                "(${prev[0]} + |${prev[0]}−${prev[1]}|) × ၄ + ၁ → ဘရိတ် ${bk2} (mod 10)")
        }
        if (am.size >= 3) {
            val a = am[am.size - 3].digit
            val b = am[am.size - 2].digit
            val c = last.digit
            val w2 = ((a[1].digitToInt() + b[1].digitToInt()) * 2 +
                (b[0].digitToInt() + c[1].digitToInt()) * 3 + 2) % 10
            val power = (w2 + 5) % 10
            add("s7-w2", "အပိုင်း ၇ • ဝမ်းချိန်း ပုံသေနည်း ၂", BetterMethod.FORMULA,
                listOf("$w2$power", "$power$w2"),
                "ရှေ့ ၂ ကြိမ် ပိတ်: ${a[1]}, ${b[1]} • ရှေ့ထိပ် ${b[0]} • လက်ရှိပိတ် ${c[1]} → ${w2}/${power}")
            val lone2 = (a[1].digitToInt() * 2 + b[0].digitToInt() * 4 +
                c[0].digitToInt() * 2 + c[1].digitToInt() * 3 + 1) % 10
            hints += BetterHint("လုံးပိုင် ပုံသေနည်း ၂", lone2.toString(),
                "အပိုင်း (၇) • ရှေ့ ၂ ကြိမ် ပိတ်/ထိပ်နှင့် လက်ရှိ ထိပ်ပိတ် ပုံသေနည်း → ${lone2}")
        }
        val previousDate = prior.filter { it.date < target.date }.lastOrNull()?.date
        if (previousDate != null) {
            val dayDraws = prior.filter { it.date == previousDate }
            val previousAm = dayDraws.firstOrNull { it.session == DrawSession.MORNING }
            val previousPm = dayDraws.firstOrNull { it.session == DrawSession.EVENING }
            if (previousAm != null && previousPm != null) {
                val digit = (previousAm.digit[0].digitToInt() +
                    previousPm.digit[0].digitToInt() + previousPm.digit[1].digitToInt()) % 10
                hints += BetterHint("အပိုင်း ၉ • WaiLin လုံးပိုင်", digit.toString(),
                    "ရှေ့ရက် မနက်ထိပ် ${previousAm.digit[0]} + ညနေထိပ် ${previousPm.digit[0]} + ညနေပိတ် ${previousPm.digit[1]} → ${digit}")
            }
        }
        if (modern930?.matches(Regex("[0-9]{2}")) == true &&
            internet930?.matches(Regex("[0-9]{2}")) == true) {
            if (modern930[0] == internet930[0] || modern930[1] == internet930[1]) {
                hints += BetterHint("9:30 Modern / Internet", "0 / အပူး",
                    "9:30 Modern ${modern930} နှင့် Internet ${internet930} တူညီမှုမူ။ အတိအကျဂဏန်းတွဲ မသတ်မှတ်ထား၍ Hint အဖြစ်သာ ထားသည်။")
            }
        }
        return result.distinctBy { it.id to it.number } to hints.distinctBy { it.name }
    }

    private fun cutReasons(prior: List<HistoricDraw>, target: DrawIdentity, pair: String): List<String> {
        val last = prior.lastOrNull() ?: return emptyList()
        val cuts = mutableListOf<String>()
        val dead = ((last.digit[1].digitToInt() + 7) % 10).digitToChar()
        if (pair[1] == dead) cuts += "အပိုင်း ၂ • +7 နောက်ပိတ်အသေ ($dead)"
        if (last.digit[0] == last.digit[1] && last.digit[0] in pair) {
            cuts += "အပိုင်း ၇ • အပူးအပြီး Cut Digit (${last.digit[0]})"
        }
        if (target.session == DrawSession.EVENING) {
            val morning = prior.lastOrNull {
                it.date == target.date && it.session == DrawSession.MORNING
            }
            if (morning != null && morning.digit[0] in pair) {
                cuts += "အပိုင်း ၇ • မနက်ထိပ် Cut Digit (${morning.digit[0]})"
            }
        }
        val triple = threeDigitCuts[last.digit[1]]
        if (triple != null) {
            val first = pair[0]
            val second = pair[1]
            if (first != second && first in triple && second in triple) {
                cuts += "အပိုင်း ၈ • ၆ ကွက်အသေမူ (${triple})"
            }
        }
        return cuts.distinct()
    }

    private fun recentPerformance(history: List<HistoricDraw>): Map<String, BetterRulePerformance> {
        val sample = history.takeLast(21)
        val opportunities = linkedMapOf<String, Int>()
        val hits = linkedMapOf<String, Int>()
        val sequences = mutableMapOf<String, MutableList<Boolean>>()
        for (i in 1 until sample.size) {
            val row = sample[i]
            val prefix = history.takeWhile { it.date < row.date ||
                (it.date == row.date && it.session.ordinal < row.session.ordinal) }
            val generated = traces(prefix, DrawIdentity(row.date, row.session), null, null).first
            generated.groupBy { it.id }.forEach { (id, entries) ->
                val ok = entries.any { it.number == row.digit }
                opportunities[id] = (opportunities[id] ?: 0) + 1
                if (ok) hits[id] = (hits[id] ?: 0) + 1
                sequences.getOrPut(id) { mutableListOf() }.add(ok)
            }
        }
        return opportunities.mapValues { (id, n) ->
            BetterRulePerformance(id, n, hits[id] ?: 0,
                sequences[id].orEmpty().asReversed().takeWhile { it }.size)
        }
    }

    fun calculate(
        draws: List<HistoricDraw>,
        target: DrawIdentity,
        modern930: String? = null,
        internet930: String? = null,
    ): BetterDataReport {
        val prior = ordered(draws).filter { before(it, target) }
        val generated = traces(prior, target, modern930, internet930)
        val performance = recentPerformance(prior)
        val aliveIds = performance.values.filter {
            it.samples >= 2 && it.currentStreak >= 1
        }.sortedWith(compareByDescending<BetterRulePerformance> { it.currentStreak }
            .thenByDescending { it.hits }.thenBy { it.id }).take(5).map { it.id }
        fun toPicks(entries: List<BetterTrace>): List<BetterPick> {
            return entries.groupBy { it.number }.map { (number, source) ->
                val cuts = cutReasons(prior, target, number)
                val math = source.count { it.method == BetterMethod.FORMULA }
                val rule = source.count { it.method == BetterMethod.RULE }
                val hit = source.sumOf { performance[it.id]?.hits ?: 0 }
                BetterPick(number, source, cuts,
                    math * 3.0 + rule * 2.0 + hit * 0.1,
                    source.mapNotNull { performance[it.id] }.distinctBy { it.id })
            }.sortedWith(compareBy<BetterPick> { it.hasConflict }
                .thenByDescending { it.hasRule && it.hasFormula }
                .thenByDescending { it.supportScore }.thenBy { it.number })
        }
        return BetterDataReport(target, prior.size, toPicks(generated.first),
            toPicks(generated.first.filter { it.id in aliveIds }),
            generated.second, aliveIds,
            modern930?.matches(Regex("[0-9]{2}")) == true &&
                internet930?.matches(Regex("[0-9]{2}")) == true)
    }
}
