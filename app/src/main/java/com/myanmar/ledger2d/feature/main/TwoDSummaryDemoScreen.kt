package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.core.domain.TwoDigitGroups
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * Human-readable 2D Summary DEMO only. All data is illustrative and isolated:
 * no network, Room, ledger tables, real historical results or future predictions.
 *
 * Try five independent fixtures without changing the Analysis implementation.
 * The selected date/session are retained as navigation controls, but do NOT
 * change the sample data. Each check's lead is a sentence, not a raw data grid.
 */
private val ReportWine = Color(0xFF751B3C)
private val ReportPale = Color(0xFFFFEDF2)
private val ReportLine = Color(0xFFE8CED8)

private data class DemoWords(val mm: String, val en: String) {
    fun display(english: Boolean) = if (english) en else mm
}
private fun words(mm: String, en: String) = DemoWords(mm, en)
private data class DemoFinding(
    val id: String,
    val name: DemoWords,
    val lead: DemoWords,
    val details: List<DemoWords> = emptyList(),
)

private enum class DemoSituation(val label: DemoWords) {
    EARLY(words("အပတ်အစ", "Early")),
    MIDDLE(words("အပတ်လယ်", "Midweek")),
    FULL(words("အပတ်ဆုံး", "Full week")),
    TIES(words("အဖြေတူ", "Ties")),
    PARTIAL(words("မပြည့်စုံ", "Gaps")),
}

private fun weekSlots(s: DemoSituation): List<String?> = when (s) {
    DemoSituation.EARLY -> listOf("12")
    DemoSituation.MIDDLE -> listOf("12", "03", "02", "35")
    DemoSituation.FULL -> listOf("12", "03", "02", "35", "74", "11", "21", "90", "57", "66")
    DemoSituation.TIES -> listOf("12", "03", "02", "35", "74", "11", "21", "90")
    DemoSituation.PARTIAL -> listOf("12", null, "02", "35")
}

private val previousFull = listOf("17", "73", "24", "72", "35", "57", "35", "33", "28", "66")
private fun previousWeek(s: DemoSituation) =
    if (s == DemoSituation.PARTIAL) previousFull.take(6) else previousFull

private fun digitsSeen(draws: List<String>): Set<Char> = draws.flatMap { it.toList() }.toSet()
private fun unseenDigits(draws: List<String>): List<Char> =
    ('0'..'9').filter { it !in digitsSeen(draws) }
private fun joinedDigits(digits: Collection<Char>): String =
    if (digits.isEmpty()) "မရှိ" else digits.joinToString(" · ")
private fun joinedDigitsEnglish(digits: Collection<Char>): String =
    if (digits.isEmpty()) "none" else digits.joinToString(" · ")

/** Demo-only chronological observations; missing slots never become false old/new facts. */
private fun demoFlow(s: DemoSituation): DemoFinding {
    val slots = weekSlots(s)
    val observed = slots.mapIndexedNotNull { index, draw ->
        draw?.let { index to it }
    }
    val known = observed.map { it.second }
    val remaining = unseenDigits(known)
    val last = observed.lastOrNull()
    val latestIndex = last?.first ?: -1
    val completeBeforeLast = latestIndex >= 0 &&
        (0..latestIndex).all { it < slots.size && slots[it] != null }
    val before = observed.filter { it.first < latestIndex }.map { it.second }
    val earlierDigits = digitsSeen(before)
    val newest = last?.second.orEmpty()
    val newDigits = newest.toSet().filter { it !in earlierDigits }
    val slotNamesMm = listOf("တနင်္လာမနက်", "တနင်္လာညနေ", "အင်္ဂါမနက်",
        "အင်္ဂါညနေ", "ဗုဒ္ဓဟူးမနက်", "ဗုဒ္ဓဟူးညနေ", "ကြာသပတေးမနက်",
        "ကြာသပတေးညနေ", "သောကြာမနက်", "သောကြာညနေ")
    val slotNamesEn = listOf("Mon AM", "Mon PM", "Tue AM", "Tue PM", "Wed AM",
        "Wed PM", "Thu AM", "Thu PM", "Fri AM", "Fri PM")
    fun status(ch: Char, en: Boolean) =
        if (ch in earlierDigits) { if (en) "old" else "အဟောင်း" }
        else { if (en) "new" else "အသစ်" }
    val mmLead: String
    val enLead: String
    when {
        last == null -> {
            mmLead = "ယခုအပတ်အတွက် အတည်ပြုထားသောရလဒ် မရှိသေးပါ။"
            enLead = "There are no recorded results for this week yet."
        }
        !completeBeforeLast -> {
            mmLead = "နောက်ဆုံးမှတ်တမ်း " + newest +
                " ကို တွေ့ထားပေမဲ့ ရှေ့အကြိမ်မှတ်တမ်း ပျောက်နေလို့ အသစ်/အဟောင်းကို အတည်မပြုနိုင်သေးပါ။"
            enLead = "The latest recorded result is " + newest +
                ", but an earlier slot is unknown, so its new/old class cannot be verified."
        }
        newest[0] == newest[1] && newDigits.isNotEmpty() -> {
            mmLead = "နောက်ဆုံး " + newest + " တွင် " + newest[0] +
                " တစ်လုံးဂဏန်းအသစ် တစ်မျိုးတည်းက နှစ်နေရာလုံးမှာ ပါဝင်ပါတယ်။"
            enLead = "In the latest " + newest + ", the same new digit " + newest[0] +
                " occupies both positions; it is one distinct new digit."
        }
        else -> {
            mmLead = "နောက်ဆုံး " + newest + " တွင် " + newest[0] + " က " +
                status(newest[0], false) + "၊ " + newest[1] + " က " +
                status(newest[1], false) + " ဖြစ်ပါတယ်။"
            enLead = "In the latest " + newest + ", " + newest[0] +
                " is " + status(newest[0], true) + " and " + newest[1] +
                " is " + status(newest[1], true) + "."
        }
    }
    val fullObserved = slots.size == 10 && slots.all { it != null }
    val remainingText = if (fullObserved) {
        words(
            if (remaining.isEmpty()) "ယခုအပတ်မှာ တစ်လုံးဂဏန်း 0–9 အားလုံး တွေ့ရှိထားပါတယ်။"
            else "ယခုအပတ် မတွေ့ရသေးသောဂဏန်းများမှာ " + joinedDigits(remaining) + " ဖြစ်ပါတယ်။",
            if (remaining.isEmpty()) "All 0–9 digits have been observed this week."
            else "Digits unseen this week: " + joinedDigitsEnglish(remaining) + ".")
    } else {
        words("ရရှိထားသောမှတ်တမ်းအရ မတွေ့ရသေးသောဂဏန်းများမှာ " +
            joinedDigits(remaining) + " ဖြစ်ပါတယ်။",
            "Not seen in the available records: " + joinedDigitsEnglish(remaining) + ".")
    }
    val history = observed.map { (index, draw) ->
        val prior = observed.filter { it.first < index }.map { it.second }
        val priorKnown = (0..index).all { it < slots.size && slots[it] != null }
        val oldSet = digitsSeen(prior)
        val labelMm = if (priorKnown) draw[0].toString() + " " +
            (if (draw[0] in oldSet) "အဟောင်း" else "အသစ်") + " + " +
            draw[1] + " " + (if (draw[1] in oldSet) "အဟောင်း" else "အသစ်")
            else "ရှေ့အကြိမ်မပြည့်၍ အမျိုးအစားအတည်မပြုနိုင်"
        val labelEn = if (priorKnown) draw[0].toString() + " " +
            (if (draw[0] in oldSet) "old" else "new") + " + " +
            draw[1] + " " + (if (draw[1] in oldSet) "old" else "new")
            else "earlier slot missing; unverified class"
        words(slotNamesMm[index] + " · " + draw + " — " + labelMm,
            slotNamesEn[index] + " · " + draw + " — " + labelEn)
    }
    return DemoFinding("05", words("အသစ်/အဟောင်း စီးဆင်းမှု", "New / old flow"),
        words(mmLead, enLead), listOf(
            words("စစ်ထားသောရလဒ် " + observed.size + "/10 ကြိမ်။" +
                if (s == DemoSituation.PARTIAL) " တစ်ကြိမ်မှတ်တမ်း မရရှိပါ။" else "",
                "Recorded " + observed.size + "/10 results." +
                if (s == DemoSituation.PARTIAL) " One slot is unknown." else ""),
            remainingText,
        ) + history)
}

private fun demoSameSlot(s: DemoSituation): DemoFinding {
    val title = words("နေ့တူ၊ အကြိမ်တူ အထွက်", "Same-day/session history")
    if (s == DemoSituation.PARTIAL) return DemoFinding("02", title,
        words("ရွေးထားတဲ့နေ့၊ အကြိမ်တူ သမိုင်းမှတ်တမ်း မရရှိသေးပါ။",
            "No same-weekday, same-session historical records are available."))
    val pair = when (s) {
        DemoSituation.TIES -> "21၊ 73 နဲ့ 46"
        DemoSituation.FULL -> "73"
        DemoSituation.EARLY -> "21"
        else -> "21 နဲ့ 73"
    }
    val pairEn = when (s) {
        DemoSituation.TIES -> "21, 73 and 46"
        DemoSituation.FULL -> "73"
        DemoSituation.EARLY -> "21"
        else -> "21 and 73"
    }
    val count = when (s) {
        DemoSituation.FULL -> 8
        DemoSituation.EARLY -> 2
        else -> 6
    }
    val details = listOf(
        words("တစ်လုံးဂဏန်း အများဆုံးမှာ 1 နဲ့ 7 ဖြစ်ပြီး 45 ကြိမ်စီ တွေ့ထားပါတယ်။",
            "Digits 1 and 7 lead with 45 appearances each."),
        words("ထိပ်အများဆုံး 2 (12 ကြိမ်)၊ ပိတ်အများဆုံး 1 (9 ကြိမ်) ဖြစ်ပါတယ်။",
            "Tens: 2 (12); ones: 1 (9)."),
        words("စုံ/မ အများဆုံးမှာ စုံမ (13 ကြိမ်) ဖြစ်ပါတယ်။",
            "Most observed parity: even/odd (13)."),
        words("အထူးအုပ်စုမှာ ညီအကို (43 ကြိမ်) အများဆုံး တွေ့ထားပါတယ်။",
            "Most observed special group: siblings (43)."),
        words("ရလဒ်များကို အကြိမ်တူ သမိုင်းမှတ်တမ်းအရ တွက်ထားခြင်းသာဖြစ်ပြီး ခန့်မှန်းချက်မဟုတ်ပါ။",
            "These are recorded same-slot frequencies, not a prediction."),
    )
    return DemoFinding("02", title,
        words("ရွေးထားတဲ့နေ့၊ အကြိမ်တူ သမိုင်းမှတ်တမ်းမှာ " + pair + " က " +
            count + " ကြိမ်စီနဲ့ အထွက်အများဆုံးဖြစ်ပါတယ်။",
            "In the same weekday/session history, " + pairEn +
            (if (s == DemoSituation.FULL || s == DemoSituation.EARLY) " leads" else " tie for the lead") +
            " at " + count + " appearances each."), details)
}

private fun demoPattern(s: DemoSituation): DemoFinding {
    val title = words("စုံ/မ အစဉ်ပုံစံတူ", "Parity-sequence matches")
    return when (s) {
        DemoSituation.EARLY -> DemoFinding("03", title,
            words("ယခုအပတ် ရှေ့အကြိမ် 4 ကြိမ် မပြည့်သေးလို့ စုံ/မ ပုံစံတူ မစစ်နိုင်သေးပါ။",
                "Four preceding weekly draws are required before checking parity sequences."))
        DemoSituation.PARTIAL -> DemoFinding("03", title,
            words("ယခုအပတ် ရှေ့အကြိမ်မှတ်တမ်း မပြည့်စုံသေးလို့ စုံ/မ ပုံစံတူကို အတည်မပြုနိုင်ပါ။",
                "The preceding weekly sequence has a missing result, so no verified match can be calculated."))
        DemoSituation.FULL -> DemoFinding("03", title,
            words("ယခုအပတ် စုံ/မ အစဉ်နဲ့ တိတိကျကျတူတဲ့ သမိုင်းအပတ် မတွေ့ခဲ့ပါ။",
                "No historical week matches the full aligned parity sequence."))
        DemoSituation.TIES -> DemoFinding("03", title,
            words("စုံ/မ အစဉ်တူ သမိုင်းအပတ် 2 ပတ်ရှိပြီး နောက်အကြိမ်မှာ စုံမနဲ့ မမ တစ်ကြိမ်စီ တွေ့ခဲ့ပါတယ်။",
                "Two historical weeks match; the following draw was even/odd once and odd/odd once."),
            listOf(words("အများဆုံးတူနေသဖြင့် အမျိုးအစားတစ်ခုတည်းကို မရွေးထားပါ။",
                "Both following parity classes are tied, so neither is singled out.")))
        DemoSituation.MIDDLE -> DemoFinding("03", title,
            words("ယခုအပတ် စုံ/မ အစဉ်နဲ့တူတဲ့ သမိုင်းအပတ် 3 ပတ်ရှိပြီး နောက်အကြိမ်မှာ စုံမ 2 ကြိမ် တွေ့ခဲ့ပါတယ်။",
                "Three historical weeks match this parity sequence; the following draw was even/odd twice."),
            listOf(words("ကျန် 1 ကြိမ်မှာ မမ ထွက်ခဲ့ပါတယ်။",
                "The remaining observed following draw was odd/odd.")))
    }
}

private fun demoStreaks(s: DemoSituation): DemoFinding {
    val title = words("ပျောက်ဂဏန်း / ပျောက်ဂဏန်းတွဲ", "Missing digits / pairs")
    val am = when (s) {
        DemoSituation.TIES -> words("မနက်အကြိမ်မှာ 3 နဲ့ 7 တို့ 16 ကြိမ်စီ၊ တွဲ 93 နဲ့ 62 တို့ 45 ကြိမ်စီ မတွေ့ရသေးပါ။",
            "AM: 3 and 7 are each absent for 16 draws; pairs 93 and 62 for 45 draws each.")
        else -> words("မနက်အကြိမ်မှာ ဂဏန်း 3 ကို 16 ကြိမ်ဆက်၊ တွဲ 93 ကို 45 ကြိမ်ဆက် မတွေ့ရသေးပါ။",
            "AM: digit 3 has been absent for 16 recorded draws; pair 93 for 45.")
    }
    val pm = words("ညနေအကြိမ်မှာ ဂဏန်း 1 ကို 8 ကြိမ်ဆက်၊ တွဲ 56 ကို 37 ကြိမ်ဆက် မတွေ့ရသေးပါ။",
        "PM: digit 1 has been absent for 8 recorded draws; pair 56 for 37.")
    val all = words("မနက်/ညနေ ပေါင်းမှတ်တမ်းမှာ ဂဏန်း 0 ကို 6 ကြိမ်၊ တွဲ 47 ကို 22 ကြိမ်ဆက် မတွေ့ရသေးပါ။",
        "Combined AM/PM: digit 0 has been absent for 6 recorded draws; pair 47 for 22.")
    if (s == DemoSituation.PARTIAL) return DemoFinding("06", title, am,
        listOf(words("ညနေအကြိမ်နှင့် နှစ်ကြိမ်ပေါင်းအတွက် အတည်ပြုနိုင်သော နမူနာမှတ်တမ်း မရရှိသေးပါ။",
            "No verified sample evidence is available for PM or combined streams.")))
    return DemoFinding("06", title, am, listOf(pm, all,
        words("မနက်၊ ညနေနဲ့ နှစ်ကြိမ်ပေါင်းကို သီးခြားစီတွက်ထားပါတယ်။",
            "AM, PM and combined are independent rankings.")))
}

private fun demoReferences(s: DemoSituation): DemoFinding {
    val title = words("ရှေ့ထွက်ဂဏန်း တိုက်စစ်", "Previous-results comparison")
    return when (s) {
        DemoSituation.EARLY -> DemoFinding("08", title,
            words("ရှေ့ဂဏန်း 21 တစ်ခုတည်းနဲ့ တူတဲ့ သမိုင်းမှာ နောက်အကြိမ် 12 ကို 2 ကြိမ် တွေ့ခဲ့ပါတယ်။",
                "With reference 21 alone, the following 12 was recorded twice."),
            listOf(words("ဒုတိယ ရှေ့ထွက်ဂဏန်း မရရှိသေးလို့ BOTH အခြေအနေကို မစစ်နိုင်သေးပါ။",
                "The second reference is unavailable; BOTH cannot be evaluated.")))
        DemoSituation.MIDDLE -> DemoFinding("08", title,
            words("ရှေ့ဂဏန်း 21 နဲ့ 66 နှစ်ခုလုံး တူခဲ့တဲ့ သမိုင်းမှာ နောက်အကြိမ် 35 ကို 1 ကြိမ် တွေ့ခဲ့ပါတယ်။",
                "When both references 21 and 66 matched, the following 35 appeared once."),
            listOf(words("21 တစ်ခုတည်းနဲ့ တူတဲ့ History မှာ 12 ကို 2 ကြိမ် တွေ့ထားပါတယ်။",
                "First-reference condition: 12 appeared twice."),
                words("66 တစ်ခုတည်းနဲ့ တူတဲ့ History မှာ 57 ကို 1 ကြိမ် တွေ့ထားပါတယ်။",
                    "Second-reference condition: 57 appeared once.")))
        DemoSituation.FULL -> DemoFinding("08", title,
            words("21 နဲ့ 66 နှစ်ခုလုံး တစ်ပြိုင်နက်တူတဲ့ သမိုင်းနောက်ထွက် မတွေ့ပါ။ တစ်ခုချင်းစီမှာတော့ အဖြေရှိပါတယ်။",
                "No recorded following outcome matches BOTH 21 and 66; individual conditions have results."),
            listOf(words("21 တစ်ခုတည်းနဲ့တူတဲ့ မှတ်တမ်းမှာ 12 ကို 2 ကြိမ် တွေ့ခဲ့ပါတယ်။",
                "First only: 12 appeared twice."),
                words("66 တစ်ခုတည်းနဲ့တူတဲ့ မှတ်တမ်းမှာ 57 ကို 1 ကြိမ် တွေ့ခဲ့ပါတယ်။",
                    "Second only: 57 appeared once.")))
        DemoSituation.TIES -> DemoFinding("08", title,
            words("21 နဲ့ 66 နှစ်ခုလုံးတူခဲ့တဲ့ သမိုင်းမှာ 35 နဲ့ 70 ကို 2 ကြိမ်စီ နောက်ထွက်အများဆုံး တွေ့ခဲ့ပါတယ်။",
                "With BOTH references 21 and 66 matched, 35 and 70 tie at two following results each."),
            listOf(words("First နဲ့ Second အခြေအနေတွေကို BOTH နဲ့ မပေါင်းဘဲ သီးခြားစစ်ထားပါတယ်။",
                "First-only and second-only searches remain independent of BOTH.")))
        DemoSituation.PARTIAL -> DemoFinding("08", title,
            words("ရှေ့ဂဏန်း 21 ကို သိထားပေမဲ့ ဒုတိယရှေ့ဂဏန်း မရှိသေးလို့ BOTH ကို မစစ်နိုင်ပါ။",
                "Reference 21 is available, but the second reference is unknown; BOTH cannot be checked."),
            listOf(words("21 တစ်ခုတည်းနဲ့ စစ်ထားသော နမူနာ History မှာ နောက်ထွက်အဖြေ မတွေ့ခဲ့ပါ။",
                "No recorded following outcome was found in the sample first-reference search.")))
    }
}

private fun demoSpecial(s: DemoSituation): DemoFinding {
    val known = weekSlots(s).filterNotNull()
    val groups = TwoDigitGroups.named.map { (name, numbers) ->
        name to known.count { it in numbers }
    }
    val repeatCount = known.groupingBy { it }.eachCount().values.sumOf {
        (it - 1).coerceAtLeast(0)
    }
    val all = groups + ("ပြန်ကျ" to repeatCount)
    fun myName(s: String) = if (s == "ညီကို") "ညီအကို" else s
    fun englishName(s: String) = when (s) {
        "အပူး" -> "doubles"
        "ညီကို" -> "siblings"
        "ပါဝါ" -> "power"
        "နက္ခတ်" -> "astrology"
        else -> "exact repeats"
    }
    val maximum = all.maxOfOrNull { it.second } ?: 0
    val leaders = all.filter { it.second == maximum && maximum > 0 }
    val lead = if (maximum == 0) {
        words("ယခုအပတ် ရရှိထားတဲ့မှတ်တမ်းမှာ အထူးအုပ်စုထွက်ရှိမှု မတွေ့ရသေးပါ။",
            "No special-group appearances were found in the recorded weekly sample.")
    } else {
        words("ယခုအပတ် မှတ်တမ်းမှာ " + leaders.joinToString(" နဲ့ ") { myName(it.first) } +
            " က " + maximum + " ကြိမ်စီနဲ့ အများဆုံး တွေ့ရှိထားပါတယ်။",
            "In this week's records, " + leaders.joinToString(" and ") { englishName(it.first) } +
                " lead at " + maximum + " appearances each.")
    }
    return DemoFinding("07", words("ယခုအပတ် အထူးအုပ်စု", "This week's special groups"),
        lead, all.map { (name, count) ->
            words(myName(name) + " — " + count + " ကြိမ်။",
                englishName(name) + " — " + count + " recorded appearances.")
        } + words("အုပ်စုတစ်ခုနဲ့တစ်ခု ဂဏန်းထပ်နိုင်လို့ Count တွေကို စုပေါင်း Total မလုပ်ထားပါ။",
            "Special groups can overlap; these counts are not an additive total."))
}

private fun demoPrevious(s: DemoSituation): DemoFinding {
    val results = previousWeek(s)
    val counted = results.flatMap { it.toList() }.groupingBy { it }.eachCount()
    val maximum = counted.values.maxOrNull() ?: 0
    val leaders = counted.filterValues { it == maximum }.keys.sorted()
    val missing = unseenDigits(results)
    val prefixMm = if (s == DemoSituation.PARTIAL) "ပြီးခဲ့တဲ့အပတ် ရရှိထားတဲ့မှတ်တမ်း " +
        results.size + " ကြိမ်မှာ " else "ပြီးခဲ့တဲ့အပတ် မှတ်တမ်းမှာ "
    val prefixEn = if (s == DemoSituation.PARTIAL) "In the " + results.size +
        " available previous-week records, " else "In last week's recorded results, "
    val parity = results.groupingBy { TwoDigitGroups.parity(it) }.eachCount()
    val parityMax = parity.values.maxOrNull() ?: 0
    val parityLead = parity.filterValues { it == parityMax }.keys.joinToString("၊ ")
    val specialCounts = TwoDigitGroups.counts(results)
    val specialMax = specialCounts.values.maxOrNull() ?: 0
    val specialTop = specialCounts.filterValues { it == specialMax }.keys.joinToString("၊ ") {
        if (it == "ညီကို") "ညီအကို" else it
    }
    val repeats = results.groupingBy { it }.eachCount().values.sumOf { (it - 1).coerceAtLeast(0) }
    return DemoFinding("04", words("ပြီးခဲ့တဲ့အပတ် အခြေအနေ", "Previous-week review"),
        words(prefixMm + joinedDigits(leaders) + " က " + maximum +
            " ကြိမ်စီနဲ့ အထွက်အများဆုံးဖြစ်ပြီး " + joinedDigits(missing) +
            " ကို မတွေ့ခဲ့ရပါ။",
            prefixEn + joinedDigitsEnglish(leaders) + " lead at " + maximum +
                " appearances each; " + joinedDigitsEnglish(missing) + " were not observed."),
        listOf(
            words("စုံ/မ အများဆုံးမှာ " + parityLead + " (" + parityMax + " ကြိမ်) ဖြစ်ပါတယ်။",
                "Most observed parity: " + parityLead + " (" + parityMax + ")."),
            words("အထူးအုပ်စု အများဆုံးမှာ " + specialTop + " (" + specialMax + " ကြိမ်) ဖြစ်ပါတယ်။",
                "Highest special-group count: " + specialMax + " (" + specialTop + ")."),
            words("တူညီတဲ့ 2D ဂဏန်း ထပ်ထွက်မှု " + repeats + " ကြိမ် တွေ့ထားပါတယ်။",
                "Exact 2D repeats: " + repeats + " additional appearances."),
            if (s == DemoSituation.PARTIAL) words(
                "မှတ်တမ်းမပြည့်စုံသဖြင့် မတွေ့ရသောဂဏန်းများကို အပတ်လုံး လုံးဝမထွက်ခဲ့ဟု မဆိုနိုင်ပါ။",
                "Records are incomplete; unseen-in-records does not mean absent for the entire week.")
            else words("ဒီအပတ်ရဲ့ အချက်အလက်တွေဟာ မှတ်တမ်းတွင် တွေ့ရှိထားသမျှကိုသာ ဖော်ပြခြင်းဖြစ်ပါတယ်။",
                "Findings describe observed records only."),
        ))
}

private fun demoReturns(s: DemoSituation): DemoFinding {
    val old = previousWeek(s)
    val missing = unseenDigits(old)
    val slots = weekSlots(s)
    val records = slots.mapIndexedNotNull { i, value -> value?.let { i to it } }
    val returned = missing.filter { digit -> records.any { digit in it.second } }
    val notRecorded = missing.filter { it !in returned }
    val firstReturned = returned.firstOrNull()?.let { digit ->
        digit to records.first { digit in it.second }
    }
    val prefix = if (s == DemoSituation.PARTIAL)
        "ပြီးခဲ့တဲ့အပတ် ရရှိထားတဲ့မှတ်တမ်းမှာ မတွေ့ခဲ့တဲ့ " else
        "ပြီးခဲ့တဲ့အပတ် မတွေ့ခဲ့တဲ့ "
    val prefixEn = if (s == DemoSituation.PARTIAL)
        "Among digits unseen in available previous-week records (" else
        "Of last week's unseen digits ("
    val messageMm = if (missing.isEmpty()) "ယခင်အပတ် ပျောက်ဂဏန်းမရှိပါ။"
        else if (returned.isEmpty()) prefix + joinedDigits(missing) +
            " တို့ကို ယခုအပတ် မှတ်တမ်းမှာလည်း မတွေ့ရသေးပါ။"
        else prefix + joinedDigits(missing) + " အနက် " + joinedDigits(returned) +
            " ကို ယခုအပတ်မှတ်တမ်းမှာ ပြန်တွေ့ထားပါတယ်။" +
            (if (notRecorded.isEmpty()) "" else " " + joinedDigits(notRecorded) + " ကို မတွေ့ရသေးပါ။")
    val messageEn = if (missing.isEmpty()) "No previous-week unseen digits were found."
        else prefixEn + joinedDigitsEnglish(missing) + "), " +
            (if (returned.isEmpty()) "none has been observed this week."
             else joinedDigitsEnglish(returned) + " have been observed again." +
                if (notRecorded.isEmpty()) "" else " Still unseen: " +
                    joinedDigitsEnglish(notRecorded) + ".")
    val details = mutableListOf<DemoWords>()
    if (firstReturned != null) {
        val (digit, first) = firstReturned
        val priorUncertain = (0..first.first).any { slots[it] == null }
        details += if (priorUncertain)
            words(digit + " ကို " + first.second + " မှာ တွေ့ထားပေမဲ့ ရှေ့မှတ်တမ်းမပြည့်စုံလို့ ပထမဆုံးပြန်ပေါ်မှုဟု မအတည်ပြုနိုင်ပါ။",
                "Digit " + digit + " appears in " + first.second +
                    ", but unknown earlier slots prevent a verified first-return claim.")
            else words(digit + " ရဲ့ ယခုအပတ် ပထမဆုံးတွေ့ရှိမှုမှာ " +
                first.second + " ဖြစ်ပါတယ်။", "First observed return of " + digit +
                " this week was in " + first.second + ".")
        details += words("သမိုင်းနမူနာမှာ " + digit +
            " ပြန်ပေါ်ချိန် တွဲထွက်ဂဏန်း 3 ကို 3 ကြိမ် တွေ့ခဲ့ပါတယ်။",
            "In the illustrative historical sample, companion digit 3 appeared three times when " +
                digit + " returned.")
    }
    if (s == DemoSituation.PARTIAL) details += words(
        "ပျောက်နေတဲ့မှတ်တမ်းတွေထဲမှာ အခြားဂဏန်းတွေ ပါနိုင်သဖြင့် စာရင်းကို ရရှိထားတဲ့ရလဒ်အဖြစ်သာ ဖတ်ပါ။",
        "Unknown records can contain other digits; treat this as observed-only evidence.")
    return DemoFinding("01", words("ပျောက်ဂဏန်း ပြန်ပေါ်မှု", "Return of unseen digits"),
        words(messageMm, messageEn), details)
}

private fun demoFindings(s: DemoSituation): List<DemoFinding> = listOf(
    demoSameSlot(s), demoPattern(s), demoStreaks(s), demoReferences(s),
    demoFlow(s), demoSpecial(s), demoPrevious(s), demoReturns(s),
)


/**
 * DEMO presentation contract: each numbered finding has a left-hand question,
 * a stable right-hand answer column, and its own plain-language conclusion.
 * Long explanations and extra result families live in the same method's
 * disclosure, not in decorative cards. Each scenario is an isolated fixture.
 */
private data class DemoReportLine(
    val question: DemoWords,
    val value: DemoWords,
    val ending: DemoWords,
)
private fun line(
    mmQuestion: String, enQuestion: String,
    mmValue: String, enValue: String,
    mmEnding: String, enEnding: String,
) = DemoReportLine(words(mmQuestion, enQuestion), words(mmValue, enValue),
    words(mmEnding, enEnding))

private data class DemoReportMethod(
    val id: String,
    val title: DemoWords,
    val lines: List<DemoReportLine>,
)

private fun mmNumbers(values: Collection<String>) =
    if (values.isEmpty()) "မရှိ" else values.joinToString("၊ ")
private fun enNumbers(values: Collection<String>) =
    if (values.isEmpty()) "none" else values.joinToString(", ")
private fun winners(values: List<String>): Pair<List<String>, Int> {
    val counts = values.groupingBy { it }.eachCount()
    val top = counts.values.maxOrNull() ?: 0
    return counts.filterValues { it == top && top > 0 }.keys.sorted() to top
}
private fun countDigits(values: List<String>): Pair<List<String>, Int> =
    winners(values.flatMap { pair -> pair.map { it.toString() } })

private fun sameSlotFixture(s: DemoSituation) = when (s) {
    DemoSituation.EARLY -> listOf("21", "73", "21", "73", "66", "12")
    DemoSituation.MIDDLE -> listOf(
        "21", "73", "21", "73", "66", "21", "73", "46",
        "46", "12", "35", "88", "73", "21")
    DemoSituation.FULL -> listOf(
        "21", "73", "21", "73", "66", "21", "73", "46",
        "46", "12", "35", "88", "73", "21", "73", "73",
        "45", "88", "35", "77")
    DemoSituation.TIES -> listOf(
        "21", "73", "46", "21", "73", "46", "21", "73",
        "46", "08", "55", "60")
    DemoSituation.PARTIAL -> emptyList()
}

private fun report02(s: DemoSituation): DemoReportMethod {
    val title = words("အထွက်အများဆုံး စစ်ဆေးချက်", "Most frequent results")
    val samples = sameSlotFixture(s)
    if (samples.isEmpty()) return DemoReportMethod("02", title, listOf(line(
        "နေ့တူ၊ အကြိမ်တူ သမိုင်းမှတ်တမ်းမှာ",
        "In the same-weekday/session history",
        "မရရှိသေး", "No records",
        "နှိုင်းယှဉ်နိုင်တဲ့ မှတ်တမ်း မရှိသေးပါ။",
        "No comparable historical samples are available.")))
    val (pairs, pairCount) = winners(samples)
    val (digits, digitCount) = countDigits(samples)
    val (tens, tensCount) = winners(samples.map { it.first().toString() })
    val (ones, onesCount) = winners(samples.map { it.last().toString() })
    val (parity, parityCount) = winners(samples.map { TwoDigitGroups.parity(it) })
    val specialCounts = TwoDigitGroups.named.mapValues { (_, group) ->
        samples.count { it in group }
    }
    val specialMax = specialCounts.values.maxOrNull() ?: 0
    val specials = specialCounts.filterValues { it == specialMax && specialMax > 0 }.keys
        .map { if (it == "ညီကို") "ညီအကို" else it }
    fun frequencyEnding(count: Int) = count.toString() + " ကြိမ်စီ တွေ့ခဲ့ပါတယ်။"
    fun frequencyEndingEn(count: Int) = count.toString() + " recorded appearances each."
    return DemoReportMethod("02", title, listOf(
        line("၁။ နေ့တူ၊ အကြိမ်တူ သမိုင်းမှာ အထွက်အများဆုံး 2D ဂဏန်းတွဲက",
            "1. The most frequent exact 2D pair in matching weekday/session history is",
            mmNumbers(pairs), enNumbers(pairs),
            frequencyEnding(pairCount), frequencyEndingEn(pairCount)),
        line("အထွက်အများဆုံး တစ်လုံးဂဏန်းက",
            "The most frequent single digit is",
            mmNumbers(digits), enNumbers(digits),
            frequencyEnding(digitCount), frequencyEndingEn(digitCount)),
        line("အများဆုံးတွေ့တဲ့ စုံ/မ အမျိုးအစားက",
            "The leading parity class is",
            mmNumbers(parity), enNumbers(parity),
            frequencyEnding(parityCount), frequencyEndingEn(parityCount)),
        line("အထူးဂဏန်းအုပ်စုထဲမှာ အများဆုံးက",
            "The most observed special group is",
            mmNumbers(specials), enNumbers(specials),
            if (specialMax==0) "အထူးအုပ်စု မတွေ့ပါ။" else frequencyEnding(specialMax),
            if (specialMax==0) "No group observed." else frequencyEndingEn(specialMax)),
        line("အများဆုံးတွေ့တဲ့ ထိပ်ဂဏန်းက",
            "The most frequent tens digit is",
            mmNumbers(tens), enNumbers(tens),
            frequencyEnding(tensCount), frequencyEndingEn(tensCount)),
        line("အများဆုံးတွေ့တဲ့ ပိတ်ဂဏန်းက",
            "The most frequent ones digit is",
            mmNumbers(ones), enNumbers(ones),
            frequencyEnding(onesCount), frequencyEndingEn(onesCount)),
    ))
}

private fun report03(s: DemoSituation): DemoReportMethod {
    val title=words("စုံ/မ ပုံစံတူ စစ်ဆေးချက်", "Matching parity sequence")
    val item=when(s) {
        DemoSituation.EARLY -> line(
            "၂။ ယခုအပတ် စုံ/မ အစဉ်ကို စစ်ဖို့",
            "2. The current week's parity sequence",
            "မပြည့်သေး", "Not ready",
            "ရှေ့အကြိမ် အနည်းဆုံး ၄ ကြိမ် လိုပါသေးတယ်။",
            "Needs at least four preceding results.")
        DemoSituation.PARTIAL -> line(
            "၂။ ယခုအပတ် စုံ/မ အစဉ်ကို",
            "2. The current week's parity sequence",
            "အတည်မပြုနိုင်", "Unverified",
            "ရှေ့အကြိမ်မှတ်တမ်း မပြည့်စုံသေးပါ။",
            "Earlier draw records have gaps.")
        DemoSituation.FULL -> line(
            "၂။ ယခုအပတ် စုံ/မ အစဉ်နဲ့ တူတဲ့ သမိုင်းအပတ်ကို",
            "2. Historical weeks with the same parity sequence",
            "မတွေ့ပါ", "No matches",
            "ရရှိတဲ့သမိုင်းမှတ်တမ်းမှာ မတွေ့ခဲ့ပါ။",
            "None matched in the sample history.")
        DemoSituation.TIES -> line(
            "၂။ ပုံစံတူ သမိုင်းအပတ်တွေရဲ့ နောက်အကြိမ်မှာ",
            "2. After historically matching parity sequences",
            "စုံမ၊ မမ", "Even/odd, odd/odd",
            "တစ်ကြိမ်စီ တူညီစွာတွေ့ခဲ့ပါတယ် (စုစုပေါင်း ၂ ပတ်)။",
            "Each occurred once across two matching weeks.")
        DemoSituation.MIDDLE -> line(
            "၂။ ယခုအပတ်ပုံစံနဲ့ တူတဲ့ သမိုင်းအပတ် ၃ ပတ်ရဲ့ နောက်အကြိမ်မှာ",
            "2. Following three historically matching weeks",
            "စုံမ", "Even/odd",
            "၂ ကြိမ်နဲ့ အများဆုံးတွေ့ခဲ့ပါတယ်။",
            "Led with two observed following outcomes.")
    }
    return DemoReportMethod("03",title,listOf(item))
}

private fun report06(s: DemoSituation): DemoReportMethod {
    val title=words("ပျောက်ဂဏန်း စစ်ဆေးချက်", "Longest observed absences")
    val tied=s==DemoSituation.TIES
    val items=mutableListOf(
        line("၃။ မနက်အကြိမ်မှာ အကြာဆုံးပျောက်နေတဲ့ တစ်လုံးဂဏန်းက",
            "3. The longest-absent digit in morning draws is",
            if(tied)"3၊ 7" else "3", if(tied)"3, 7" else "3",
            if(tied)"၁၆ ကြိမ်စီ မတွေ့ရသေးပါ။" else "၁၆ ကြိမ်ဆက် မတွေ့ရသေးပါ။",
            "Absent for 16 observed morning draws."),
        line("မနက်အကြိမ် ပျောက်နေတဲ့ 2D ဂဏန်းတွဲက",
            "The longest-absent AM 2D pair is",
            if(tied)"93၊ 62" else "93",if(tied)"93, 62" else "93",
            if(tied)"၄၅ ကြိမ်စီ မတွေ့ရသေးပါ။" else "၄၅ ကြိမ်ဆက် မတွေ့ရသေးပါ။",
            "Absent for 45 observed morning draws."),
    )
    if(s==DemoSituation.PARTIAL) {
        items += line("ညနေနဲ့ နှစ်ကြိမ်ပေါင်း မှတ်တမ်းတွေကို",
            "Evening and combined streams are",
            "မရရှိသေး", "Unavailable",
            "အတည်ပြုနိုင်တဲ့ နမူနာအချက်အလက် မရှိသေးပါ။",
            "No verified sample evidence is available.")
    } else {
        items += listOf(
            line("ညနေအကြိမ်မှာ အကြာဆုံးပျောက်တဲ့ တစ်လုံးဂဏန်းက",
                "The longest-absent PM single digit is",
                "1","1","၈ ကြိမ်ဆက် မတွေ့ရသေးပါ။","Absent for eight PM draws."),
            line("ညနေအကြိမ်မှာ အကြာဆုံးပျောက်တဲ့ 2D တွဲက",
                "The longest-absent PM 2D pair is",
                "56","56","၃၇ ကြိမ်ဆက် မတွေ့ရသေးပါ။","Absent for 37 PM draws."),
            line("မနက်/ညနေ ပေါင်းမှတ်တမ်းမှာ အကြာဆုံးပျောက်တဲ့ တစ်လုံးဂဏန်းက",
                "The longest-absent single digit across both sessions is",
                "0","0","၆ ကြိမ်ဆက် မတွေ့ရသေးပါ။","Absent for six combined draws."),
            line("မနက်/ညနေ ပေါင်းမှတ်တမ်းမှာ အကြာဆုံးပျောက်တဲ့ 2D တွဲက",
                "The longest-absent 2D pair across both sessions is",
                "47","47","၂၂ ကြိမ်ဆက် မတွေ့ရသေးပါ။","Absent for 22 combined draws."),
        )
    }
    return DemoReportMethod("06",title,items)
}

private fun report08(s: DemoSituation): DemoReportMethod {
    val title=words("ရှေ့ထွက်ဂဏန်း တိုက်စစ်", "Previous results comparison")
    val both=when(s) {
        DemoSituation.EARLY, DemoSituation.PARTIAL -> line(
            "၄။ ရှေ့ထွက်ဂဏန်းနှစ်ခုလုံး တိုက်စစ်ဖို့",
            "4. To compare both previous results",
            "မပြည့်စုံ", "Not available",
            "ဒုတိယရှေ့ဂဏန်း မသိရသေးပါ။",
            "The second reference is unknown.")
        DemoSituation.FULL -> line(
            "၄။ 21 နဲ့ 66 နှစ်ခုလုံးတူခဲ့တဲ့ သမိုင်းနောက်ထွက်ကို",
            "4. Following history matched by both 21 and 66",
            "မတွေ့ပါ", "No match",
            "တစ်ခုချင်းစီ စစ်ထားတဲ့အဖြေကတော့ သီးခြားရှိပါတယ်။",
            "Individual reference searches still have outcomes.")
        DemoSituation.TIES -> line(
            "၄။ ရှေ့ဂဏန်း 21 နဲ့ 66 နှစ်ခုလုံးတူတဲ့ သမိုင်းမှာ",
            "4. History matching both previous 21 and 66",
            "35၊ 70", "35, 70",
            "နောက်အကြိမ် ၂ ကြိမ်စီ အများဆုံးတွေ့ခဲ့ပါတယ်။",
            "Each led following outcomes twice.")
        DemoSituation.MIDDLE -> line(
            "၄။ ရှေ့ဂဏန်း 21 နဲ့ 66 နှစ်ခုလုံးတူတဲ့ သမိုင်းမှာ",
            "4. History matching both previous 21 and 66",
            "35", "35",
            "နောက်အကြိမ် ၁ ကြိမ် တွေ့ခဲ့ပါတယ်။",
            "One following appearance was found.")
    }
    val first=if(s==DemoSituation.PARTIAL)
        line("ပထမရှေ့ဂဏန်း 21 တစ်ခုတည်းနဲ့ တိုက်စစ်ရာမှာ",
            "With first reference 21 alone",
            "မတွေ့ပါ","No match",
            "သမိုင်းနောက်ထွက် မတွေ့ခဲ့ပါ။",
            "No following recorded sample matched.")
    else line("ပထမရှေ့ဂဏန်း 21 တစ်ခုတည်းနဲ့ တိုက်စစ်ရာမှာ",
        "With first reference 21 alone",
        "12","12","နောက်အကြိမ် ၂ ကြိမ် တွေ့ခဲ့ပါတယ်။",
        "Appeared twice afterward.")
    val second=if(s==DemoSituation.EARLY||s==DemoSituation.PARTIAL)
        line("ဒုတိယရှေ့ဂဏန်းအတွက်",
            "For the second reference",
            "မရရှိသေး", "Unknown",
            "ရှေ့မှတ်တမ်း မရှိသေးပါ။","Reference is unavailable.")
    else line("ဒုတိယရှေ့ဂဏန်း 66 တစ်ခုတည်းနဲ့ တိုက်စစ်ရာမှာ",
        "With second reference 66 alone",
        "57","57","နောက်အကြိမ် ၁ ကြိမ် တွေ့ခဲ့ပါတယ်။",
        "Appeared once afterward.")
    return DemoReportMethod("08",title,listOf(both,first,second))
}

private fun report05(s: DemoSituation): DemoReportMethod {
    val title=words("အသစ်/အဟောင်း ဂဏန်းစီးဆင်းမှု", "New / old digit flow")
    val slots=weekSlots(s)
    val events=slots.mapIndexedNotNull { i,pair->pair?.let { i to it } }
    if(events.isEmpty())return DemoReportMethod("05",title,listOf(line(
        "၅။ ယခုအပတ် အသစ်/အဟောင်းအခြေအနေက",
        "5. This week's new/old flow is",
        "မရရှိသေး", "No data", "အတည်ပြုထားသောရလဒ် မရှိသေးပါ။",
        "No recorded sample outcomes yet.")))
    val (idx, last)=events.last()
    val earlier=events.filter { it.first<idx }.map { it.second }
    val priorDigits=earlier.flatMap { it.toList() }.toSet()
    val known=(0..idx).all { it<slots.size && slots[it]!=null }
    val statement=when {
        !known -> words("ရှေ့အကြိမ်မှတ်တမ်း ပျောက်နေတဲ့အတွက် အသစ်/အဟောင်းကို အတည်မပြုနိုင်ပါ။",
            "Unknown earlier slots prevent a verified new/old classification.")
        last[0]==last[1]&&last[0] !in priorDigits ->
            words("တစ်လုံးဂဏန်းအသစ် တစ်မျိုးတည်းက နှစ်နေရာလုံးမှာ ပါဝင်ပါတယ်။",
                "One distinct new digit occupies both positions.")
        last[0]==last[1] -> words("တစ်လုံးဂဏန်းအဟောင်း တစ်မျိုးတည်းက နှစ်နေရာလုံးမှာ ပါဝင်ပါတယ်။",
            "One distinct old digit occupies both positions.")
        else -> words(last[0].toString() + " က " +
            (if(last[0] in priorDigits)"အဟောင်း" else "အသစ်") + "၊ " +
            last[1] + " က " + (if(last[1] in priorDigits)"အဟောင်း" else "အသစ်") +
            " ဖြစ်ပါတယ်။",
            last[0].toString() + " is " + (if(last[0] in priorDigits)"old" else "new") +
            "; " + last[1] + " is " + (if(last[1] in priorDigits)"old" else "new") + ".")
    }
    val unseen=unseenDigits(events.map { it.second })
    val evidence=if(known) {
        statement
    } else words("ရှေ့အကြိမ်မှတ်တမ်း ပျောက်နေလို့ အမျိုးအစား မသေချာပါ။",
        "Earlier slots are missing; classification is unverified.")
    val mmRemainder=joinedDigits(unseen)
    val enRemainder=joinedDigitsEnglish(unseen)
    return DemoReportMethod("05",title,listOf(
        line("၅။ ယခုအပတ် နောက်ဆုံးတွေ့ရှိတဲ့ 2D ရလဒ်က",
            "5. The latest recorded 2D this week is",
            last,last,evidence.mm,evidence.en),
        line("ယခုအပတ် ရရှိထားတဲ့မှတ်တမ်းမှာ မတွေ့ရသေးတဲ့ တစ်လုံးဂဏန်းတွေက",
            "The single digits not seen in available weekly records are",
            mmRemainder,enRemainder,
            "မှတ်တမ်း " + events.size + "/10 ကြိမ်အရ ဖြစ်ပါတယ်။",
            "Based on " + events.size + " of 10 possible weekly draws."),
    ))
}

private fun report07(s: DemoSituation): DemoReportMethod {
    val title=words("ယခုအပတ် အထူးအုပ်စု", "Special groups this week")
    val known=weekSlots(s).filterNotNull()
    val groups=TwoDigitGroups.named.map { (name, values) -> name to known.count { it in values } }
    val repeatCount=known.groupingBy { it }.eachCount().values.sumOf { (it-1).coerceAtLeast(0) }
    val all=groups+("ပြန်ကျ" to repeatCount)
    val maximum=all.maxOfOrNull { it.second }?:0
    fun friendly(k: String)=if(k=="ညီကို")"ညီအကို" else k
    val leaders=all.filter { it.second==maximum&&maximum>0 }.map { friendly(it.first) }
    val main=line("၆။ ယခုအပတ် ထွက်ရှိထားတဲ့ အထူးအုပ်စုတွေထဲမှာ အများဆုံးက",
        "6. The most recorded special group this week is",
        if(maximum==0)"မတွေ့သေး" else mmNumbers(leaders),
        if(maximum==0)"None" else enNumbers(leaders),
        if(maximum==0)"အထူးအုပ်စုထွက်ရှိမှု မတွေ့ရသေးပါ။"
        else maximum.toString()+" ကြိမ်စီ ဖြစ်ပါတယ်။",
        if(maximum==0)"No special-group occurrence yet."
        else maximum.toString()+" appearances each.")
    return DemoReportMethod("07",title,listOf(main)+all.map { (key,count) ->
        line(friendly(key)+" အုပ်စုရဲ့ ထွက်ရှိမှုက",
            "Observed count for "+friendly(key)+" is",
            count.toString(),count.toString(),
            "ကြိမ် တွေ့ထားပါတယ်။","recorded appearances.")
    })
}

private fun report04(s: DemoSituation): DemoReportMethod {
    val title=words("ပြီးခဲ့တဲ့အပတ် စာရင်းချုပ်", "Previous-week findings")
    val results=previousWeek(s)
    val (leaders,high)=countDigits(results)
    val missing=unseenDigits(results)
    val (parity,parityCount)=winners(results.map { TwoDigitGroups.parity(it) })
    val special=TwoDigitGroups.named.map { (name,values) ->
        (if(name=="ညီကို")"ညီအကို" else name) to results.count { it in values }
    }
    val spMax=special.maxOfOrNull { it.second }?:0
    val spTop=special.filter { it.second==spMax&&spMax>0 }.map { it.first }
    val repeats=results.groupingBy { it }.eachCount().values.sumOf { (it-1).coerceAtLeast(0) }
    return DemoReportMethod("04",title,listOf(
        line("၇။ ပြီးခဲ့တဲ့အပတ် တစ်လုံးဂဏန်းတွေထဲမှာ အထွက်အများဆုံးက",
            "7. The most frequent single digit last week was",
            mmNumbers(leaders),enNumbers(leaders),
            high.toString()+" ကြိမ်စီ တွေ့ထားပါတယ်။",
            high.toString()+" appearances each."),
        line("ပြီးခဲ့တဲ့အပတ် ရရှိထားတဲ့မှတ်တမ်းမှာ မတွေ့ခဲ့တဲ့ ဂဏန်းတွေက",
            "Digits not seen in available previous-week records are",
            joinedDigits(missing),joinedDigitsEnglish(missing),
            if(s==DemoSituation.PARTIAL)"မှတ်တမ်းအချို့ မပြည့်စုံသေးပါ။"
            else "မှတ်တမ်းအရ ဖြစ်ပါတယ်။",
            if(s==DemoSituation.PARTIAL)"Some draw records are missing."
            else "Based on the available historical record."),
        line("ပြီးခဲ့တဲ့အပတ် အများဆုံး စုံ/မ အမျိုးအစားက",
            "The most frequent parity class last week was",
            mmNumbers(parity),enNumbers(parity),
            parityCount.toString()+" ကြိမ်စီ တွေ့ထားပါတယ်။",
            parityCount.toString()+" occurrences each."),
        line("ပြီးခဲ့တဲ့အပတ် အများဆုံး အထူးအုပ်စုက",
            "The most observed special group last week was",
            mmNumbers(spTop),enNumbers(spTop),
            spMax.toString()+" ကြိမ်စီ တွေ့ထားပါတယ်။",
            spMax.toString()+" occurrences each."),
        line("ပြီးခဲ့တဲ့အပတ် တူညီတဲ့ 2D တွဲ ပြန်ကျမှုက",
            "Exact 2D repeat events last week total",
            repeats.toString(),repeats.toString(),
            "ကြိမ် ထပ်ထွက်ခဲ့ပါတယ်။",
            "additional appearances."),
    ))
}

private fun report01(s: DemoSituation): DemoReportMethod {
    val title=words("ယခင်အပတ် ပျောက်ဂဏန်း ပြန်ပေါ်မှု", "Previous-week digit returns")
    val missing=unseenDigits(previousWeek(s))
    val slots=weekSlots(s)
    val seen=slots.filterNotNull().flatMap { it.toList() }.toSet()
    val returned=missing.filter { it in seen }
    val still=missing.filter { it !in seen }
    val first=returned.firstOrNull()
    val firstSlot=first?.let { digit ->
        slots.indexOfFirst { it?.contains(digit)==true }
    }?:-1
    val certain=firstSlot>=0&&(0..firstSlot).all { it<slots.size&&slots[it]!=null }
    val start=line("၈။ ပြီးခဲ့တဲ့အပတ် မတွေ့ခဲ့တဲ့ဂဏန်းတွေထဲက ယခုအပတ် ပြန်တွေ့ရတာက",
        "8. Digits unseen last week but observed this week are",
        joinedDigits(returned),joinedDigitsEnglish(returned),
        if(returned.isEmpty())"ရရှိထားတဲ့ ယခုအပတ်မှတ်တမ်းမှာ မပေါ်သေးပါ။"
        else "ရရှိထားတဲ့ ယခုအပတ်မှတ်တမ်းမှာ ပြန်ပေါ်ခဲ့ပါတယ်။",
        if(returned.isEmpty())"None has returned in the sample records yet."
        else "These have appeared in available weekly samples.")
    val more=mutableListOf(
        line("ယခင်အပတ် မတွေ့ရသေးတဲ့ တစ်လုံးဂဏန်းတွေက",
            "Digits unseen in previous-week records were",
            joinedDigits(missing),joinedDigitsEnglish(missing),
            if(s==DemoSituation.PARTIAL)"ယခင်အပတ်မှတ်တမ်း မပြည့်စုံသေးပါ။"
            else "ရရှိထားတဲ့မှတ်တမ်းအရ ဖြစ်ပါတယ်။",
            if(s==DemoSituation.PARTIAL)"Previous-week records are incomplete."
            else "As observed in the previous-week records."),
        line("ယခုအပတ်မှာ မတွေ့ရသေးတဲ့ ယခင်ပျောက်ဂဏန်းတွေက",
            "Previously unseen digits still not recorded this week are",
            joinedDigits(still),joinedDigitsEnglish(still),
            "ရရှိထားတဲ့ ယခုအပတ်မှတ်တမ်းအရ ဖြစ်ပါတယ်။",
            "Based on available current-week results."),
    )
    if(first!=null) {
        more+=line("ပြန်ပေါ်ခဲ့တဲ့ "+first+" ရဲ့ ပထမဆုံးပြန်ပေါ်မှုကို",
            "The first verified return of "+first+" is",
            if(certain)slots[firstSlot].orEmpty() else "မအတည်ပြုနိုင်",
            if(certain)slots[firstSlot].orEmpty() else "Unverified",
            if(certain)"ယခုအပတ်မှတ်တမ်းမှာ ပထမတွေ့ခဲ့တာ ဖြစ်ပါတယ်။"
            else "ရှေ့အကြိမ်မှတ်တမ်း ပျောက်နေပါတယ်။",
            if(certain)"First observed return in the current week."
            else "Earlier draw slots are missing.")
        more+=line("သမိုင်းနမူနာမှာ "+first+" ပြန်ပေါ်စဉ် အများဆုံးတွဲထွက်တဲ့ဂဏန်းက",
            "The illustrative historical return companion to "+first+" is",
            "3","3","နမူနာသမိုင်းမှာ ၃ ကြိမ် တွေ့ခဲ့ပါတယ်။",
            "Seen three times in a sample historical fixture.")
    }
    return DemoReportMethod("01",title,listOf(start)+more)
}

private fun reportMethods(s: DemoSituation): List<DemoReportMethod> = listOf(
    report02(s),report03(s),report06(s),report08(s),
    report05(s),report07(s),report04(s),report01(s),
)

@Composable
internal fun TwoDSummaryDemoScreen(
    date: LocalDate,
    session: DrawSession,
    en: Boolean,
    modifier: Modifier = Modifier,
    onPickDate: () -> Unit,
    onChangeSession: (DrawSession) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(DemoSituation.MIDDLE.name) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf("") }
    var scenarioMenu by remember { mutableStateOf(false) }
    val situation=DemoSituation.entries.firstOrNull { it.name==selected }?:DemoSituation.MIDDLE
    val methods=remember(situation) { reportMethods(situation) }
    val tabs=listOf(
        words("လက်ရှိအကြိမ်", "Current draw"),
        words("ယခုအပတ်", "This week"),
        words("ယခင်အပတ်", "Last week"),
    )
    val sections=listOf(methods.take(4),methods.drop(4).take(2),methods.takeLast(2))
    Column(modifier.fillMaxSize().padding(horizontal=10.dp, vertical=5.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        DemoDateAndSession(date,session,en,onPickDate,onChangeSession)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            Text("DEMO",fontWeight=FontWeight.Bold,color=ReportWine,fontSize=11.sp)
            Text(words("နမူနာ Data · အစစ်မဟုတ်", "Illustrative samples only").display(en),
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize=10.sp,modifier=Modifier.weight(1f))
            Box {
                TextButton(onClick={scenarioMenu=true},
                    contentPadding=PaddingValues(horizontal=6.dp,vertical=0.dp)) {
                    Text(situation.label.display(en)+" ▾",fontSize=11.sp,color=ReportWine)
                }
                DropdownMenu(expanded=scenarioMenu,onDismissRequest={scenarioMenu=false}) {
                    DemoSituation.entries.forEach { situationOption ->
                        DropdownMenuItem(
                            text={Text(situationOption.label.display(en))},
                            onClick={
                                selected=situationOption.name
                                open=""
                                scenarioMenu=false
                            })
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            tabs.forEachIndexed { index,t ->
                val active=tab==index
                Surface(onClick={tab=index;open=""},
                    modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(8.dp),
                    color=if(active)ReportWine else ReportPale,
                    border=BorderStroke(1.dp,if(active)ReportWine else ReportLine)) {
                    Column(Modifier.fillMaxWidth().padding(vertical=9.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(1.dp)) {
                        Text(t.display(en),fontWeight=FontWeight.SemiBold,
                            fontSize=11.sp,color=if(active)Color.White else ReportWine,
                            maxLines=1)
                        Text(if(index==0)"4" else "2",
                            fontSize=10.sp,color=if(active)Color.White.copy(alpha=.84f)
                            else ReportWine.copy(alpha=.72f))
                    }
                }
            }
        }
        // Only one time-scope is visible. This was the original reason for 4/2/2 tags.
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(1.dp),
        ) {
            Text(words("ဘာကိုစစ်ထားသလဲ", "What was checked").display(en),
                fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.padding(start=30.dp,top=5.dp,bottom=3.dp))
            sections[tab].forEachIndexed { index,item ->
                DemoAlignedMethod(index+1,item,en,expanded=open==item.id,
                    onToggle={open=if(open==item.id)"" else item.id},
                    onRealDetail={onOpenDetail(item.id)})
            }
            Text(words(
                "အဖြေများသည် စမ်းသပ်ဒေတာသာဖြစ်ပြီး ခန့်မှန်းချက်မဟုတ်ပါ။ အချက်အလက်အပြည့်ကို မြင်ရန် သက်ဆိုင်ရာအချက်ကို နှိပ်ပါ။",
                "Only sample findings, never forecasts. Tap a finding for its full set of outputs.")
                .display(en),
                fontSize=10.sp,lineHeight=16.sp,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                modifier=Modifier.padding(vertical=8.dp))
        }
    }
}

@Composable
private fun DemoDateAndSession(
    date: LocalDate, session: DrawSession, en: Boolean,
    onPickDate: () -> Unit, onChangeSession: (DrawSession) -> Unit,
) {
    Surface(color=ReportWine,shape=RoundedCornerShape(10.dp),
        modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=4.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(date.toString(),fontSize=12.sp,fontWeight=FontWeight.SemiBold,
                color=Color.White,
                modifier=Modifier.weight(1f).clickable(onClick=onPickDate)
                    .padding(vertical=8.dp))
            listOf(
                DrawSession.MORNING to (if(en)"AM" else "မနက်"),
                DrawSession.EVENING to (if(en)"PM" else "ညနေ"),
            ).forEach { (s,label) ->
                Surface(onClick={onChangeSession(s)},
                    color=if(s==session)Color(0xFFFFE2EC) else Color.White.copy(alpha=.16f),
                    shape=RoundedCornerShape(7.dp)) {
                    Text(label,fontSize=11.sp,fontWeight=FontWeight.SemiBold,
                        color=if(s==session)ReportWine else Color.White,
                        modifier=Modifier.padding(horizontal=10.dp,vertical=7.dp))
                }
            }
        }
    }
}

@Composable
private fun DemoAlignedMethod(
    ordinal:Int,
    method:DemoReportMethod,
    en:Boolean,
    expanded:Boolean,
    onToggle:()->Unit,
    onRealDetail:()->Unit,
) {
    val primary=method.lines.firstOrNull() ?: return
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(1.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick=onToggle)
            .padding(top=7.dp,bottom=4.dp),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(ordinal.toString()+".",fontSize=12.sp,
                fontWeight=FontWeight.Bold,color=ReportWine,
                modifier=Modifier.width(20.dp))
            Text(method.title.display(en),fontSize=12.sp,
                fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface,
                modifier=Modifier.weight(1f))
            Text("A-"+method.id,fontSize=10.sp,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(expanded)"−" else "+",fontSize=17.sp,color=ReportWine)
        }
        DemoAlignedLine(primary,en,main=true)
        if(expanded) {
            Column(Modifier.padding(start=26.dp,bottom=5.dp),
                verticalArrangement=Arrangement.spacedBy(0.dp)) {
                method.lines.drop(1).forEach { item ->
                    HorizontalDivider(color=ReportLine.copy(alpha=.6f))
                    DemoAlignedLine(item,en,main=false)
                }
                TextButton(onClick=onRealDetail,
                    modifier=Modifier.align(Alignment.End).heightIn(min=42.dp)) {
                    Text(words("စစ်ဆေးမှုအသေးစိတ် ကြည့်ရန်",
                        "Open analysis details").display(en),
                        fontSize=11.sp,color=ReportWine)
                }
            }
        }
        HorizontalDivider(color=ReportLine,thickness=1.dp)
    }
}

/**
 * A fixed answer column stays aligned even if the explanation wraps in Burmese.
 * No card is placed around the number, and the closing clause stays attached
 * to that number (rather than becoming a disconnected third tiny column).
 */
@Composable
private fun DemoAlignedLine(item:DemoReportLine,en:Boolean,main:Boolean) {
    Row(Modifier.fillMaxWidth().padding(
        start=if(main)26.dp else 0.dp,
        top=if(main)2.dp else 6.dp,
        bottom=if(main)7.dp else 6.dp),
        horizontalArrangement=Arrangement.spacedBy(9.dp),
        verticalAlignment=Alignment.Top) {
        Text(item.question.display(en),fontSize=if(main)12.sp else 11.sp,
            lineHeight=if(main)18.sp else 17.sp,
            color=MaterialTheme.colorScheme.onSurface,
            modifier=Modifier.weight(.61f))
        Column(modifier=Modifier.weight(.39f),
            verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(item.value.display(en),
                fontSize=if(main)16.sp else 14.sp,
                lineHeight=if(main)21.sp else 19.sp,
                fontWeight=FontWeight.Bold,color=ReportWine)
            Text(item.ending.display(en),
                fontSize=if(main)11.sp else 10.sp,
                lineHeight=if(main)16.sp else 15.sp,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
