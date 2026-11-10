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
    var openCheck by rememberSaveable { mutableStateOf("") }
    val situation = DemoSituation.entries.firstOrNull { it.name == selected }
        ?: DemoSituation.MIDDLE
    val entries = remember(situation) { demoFindings(situation) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        DemoDateAndSession(date, session, en, onPickDate, onChangeSession)
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(color = ReportPale, shape = RoundedCornerShape(5.dp)) {
                Text("DEMO", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ReportWine)
            }
            Text(
                words("နမူနာဒေတာသာ · ရက်စွဲပြောင်းလည်း အဖြေမပြောင်းပါ",
                    "Examples only · date/session do not alter sample findings").display(en),
                fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DemoSituation.entries.forEach { option ->
                val active = option == situation
                Surface(
                    onClick = { selected = option.name; openCheck = "" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (active) ReportWine else ReportPale,
                    border = BorderStroke(1.dp, if (active) ReportWine else ReportLine),
                ) {
                    Text(
                        option.label.display(en),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (active) Color.White else ReportWine,
                    )
                }
            }
        }

        DemoSectionHeading(words("လက်ရှိအကြိမ်", "Current draw").display(en))
        entries.take(4).forEach { item ->
            DemoSentenceRow(item, en, openCheck == item.id,
                onToggle = { openCheck = if (openCheck == item.id) "" else item.id },
                onRealDetail = { onOpenDetail(item.id) })
        }
        DemoSectionHeading(words("ယခုအပတ်", "This week").display(en))
        entries.drop(4).take(2).forEach { item ->
            DemoSentenceRow(item, en, openCheck == item.id,
                onToggle = { openCheck = if (openCheck == item.id) "" else item.id },
                onRealDetail = { onOpenDetail(item.id) })
        }
        DemoSectionHeading(words("ပြီးခဲ့တဲ့အပတ်", "Previous week").display(en))
        entries.takeLast(2).forEach { item ->
            DemoSentenceRow(item, en, openCheck == item.id,
                onToggle = { openCheck = if (openCheck == item.id) "" else item.id },
                onRealDetail = { onOpenDetail(item.id) })
        }
        Text(
            words("ဤစာသားများသည် စမ်းသပ်ရန် နမူနာမှတ်တမ်းများသာဖြစ်ပြီး တကယ့်ရလဒ် သို့မဟုတ် ခန့်မှန်းချက်မဟုတ်ပါ။",
                "Illustrative records only; not actual results or forecasts.").display(en),
            fontSize = 10.sp, lineHeight = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 5.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun DemoDateAndSession(
    date: LocalDate, session: DrawSession, en: Boolean,
    onPickDate: () -> Unit, onChangeSession: (DrawSession) -> Unit,
) {
    Surface(color = ReportWine, shape = RoundedCornerShape(11.dp),
        modifier = Modifier.fillMaxWidth(), shadowElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(date.toString(),
                modifier = Modifier.weight(1f).clickable(onClick = onPickDate)
                    .padding(vertical = 6.dp),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            listOf(
                DrawSession.MORNING to (if (en) "AM" else "မနက်"),
                DrawSession.EVENING to (if (en) "PM" else "ညနေ"),
            ).forEach { (key, label) ->
                Surface(onClick = { onChangeSession(key) },
                    color = if (key == session) Color(0xFFFFE1EA) else Color.White.copy(alpha = .13f),
                    shape = RoundedCornerShape(8.dp)) {
                    Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (key == session) ReportWine else Color.White)
                }
            }
        }
    }
}

@Composable
private fun DemoSectionHeading(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = ReportWine, fontWeight = FontWeight.Bold)
        HorizontalDivider(modifier = Modifier.weight(1f), color = ReportLine)
    }
}

/** Lead sentences stay readable. Tap to reveal examples of other independent outputs. */
@Composable
private fun DemoSentenceRow(
    finding: DemoFinding, en: Boolean, expanded: Boolean,
    onToggle: () -> Unit, onRealDetail: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().clickable(onClick = onToggle)
                .padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = RoundedCornerShape(5.dp), color = ReportPale) {
                    Text("A-" + finding.id,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        color = ReportWine, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text(finding.name.display(en), modifier = Modifier.weight(1f),
                    fontSize = 12.sp, lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(if (expanded) "−" else "+", fontSize = 18.sp, color = ReportWine)
            }
            Text(finding.lead.display(en),
                fontSize = 13.sp, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 3.dp))
        }
        if (expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                color = ReportPale,
                shape = RoundedCornerShape(9.dp),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (finding.details.isEmpty()) {
                        Text(words("ဤအခြေအနေအတွက် ထပ်မံအတည်ပြုထားသော နမူနာအဖြေ မရှိသေးပါ။",
                            "No further verified sample finding is available for this situation.")
                            .display(en), fontSize = 12.sp, lineHeight = 18.sp)
                    } else {
                        finding.details.forEach {
                            Text("•  " + it.display(en), fontSize = 12.sp,
                                lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    HorizontalDivider(color = ReportLine)
                    TextButton(
                        onClick = onRealDetail,
                        modifier = Modifier.align(Alignment.End).heightIn(min = 40.dp),
                    ) {
                        Text(words("အစစ်အမှန် စစ်ဆေးမှုအသေးစိတ် ကြည့်ရန်",
                            "Open REAL analysis detail").display(en),
                            fontSize = 12.sp, color = ReportWine)
                    }
                }
            }
        }
        HorizontalDivider(color = ReportLine.copy(alpha = .70f), thickness = .5.dp)
    }
}
