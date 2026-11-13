package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.core.domain.TwoDigitGroups
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.TwoDAnalysisReport
import com.myanmar.ledger2d.core.domain.TwoDCount
import com.myanmar.ledger2d.core.domain.TwoDWeekFlow
import com.myanmar.ledger2d.core.domain.TwoDStreakStream
import com.myanmar.ledger2d.core.domain.TwoDConditional
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * Human-readable 2D Summary DEMO only. All data is illustrative and isolated:
 * no network, Room, ledger tables, real historical results or future predictions.
 *
 * Seven illustrative situations, never connected to real draw results.
 * Current date/session only affect demo reference results and their explanations.
 */
private val ReportWine = AppColors.Primary
private val ReportPale = Color(0xFFFFEDF2)
private val ReportLine = Color(0xFFE8CED8)
private val ReportDataInk = Color(0xFF164F6C) // High-contrast answer ink

/** One consistent value size in every numbered finding and the weekly pattern. */
private val DemoResultSize = 15.sp

private data class DemoWords(val mm: String, val en: String) {
    fun display(english: Boolean) = if (english) en else mm
}
private fun words(mm: String, en: String) = DemoWords(mm, en)
private enum class DemoSituation(val label: DemoWords) {
    EARLY(words("အပတ်အစ", "Early")),
    MIDDLE(words("အပတ်လယ်", "Midweek")),
    FULL(words("အပတ်ဆုံး", "Full week")),
    TIES(words("အဖြေတူ", "Ties")),
    PARTIAL(words("မှတ်တမ်းပျောက်", "Gaps")),
    CLOSED(words("အတည်ပြုပိတ်ရက်", "Closed day")),
    MISSING_REF(words("ရှေ့ Result မရှိ", "Missing reference")),
}

private fun weekSlots(s: DemoSituation): List<String?> = when (s) {
    DemoSituation.EARLY -> listOf("12")
    DemoSituation.MIDDLE -> listOf("12", "03", "02", "35")
    DemoSituation.FULL -> listOf("12", "03", "02", "35", "74", "11", "21", "90", "57", "66")
    DemoSituation.TIES -> listOf("12", "03", "02", "35", "74", "11", "21", "90")
    DemoSituation.PARTIAL -> listOf("12", null, "02", "35")
    DemoSituation.CLOSED, DemoSituation.MISSING_REF -> listOf("12", "03", "02", "35")
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
    DemoSituation.CLOSED, DemoSituation.MISSING_REF -> listOf(
        "21", "73", "21", "73", "66", "21", "73", "46",
        "46", "12", "35", "88", "73", "21")
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
        line("နေ့တူ၊ အကြိမ်တူ သမိုင်းမှာ အထွက်အများဆုံး 2D ဂဏန်းတွဲက",
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
            "ယခုအပတ် စုံ/မ အစဉ်ကို စစ်ဖို့",
            "2. The current week's parity sequence",
            "မပြည့်သေး", "Not ready",
            "ရှေ့အကြိမ် အနည်းဆုံး ၄ ကြိမ် လိုပါသေးတယ်။",
            "Needs at least four preceding results.")
        DemoSituation.PARTIAL -> line(
            "ယခုအပတ် စုံ/မ အစဉ်ကို",
            "2. The current week's parity sequence",
            "အတည်မပြုနိုင်", "Unverified",
            "ရှေ့အကြိမ်မှတ်တမ်း မပြည့်စုံသေးပါ။",
            "Earlier draw records have gaps.")
        DemoSituation.FULL -> line(
            "ယခုအပတ် စုံ/မ အစဉ်နဲ့ တူတဲ့ သမိုင်းအပတ်ကို",
            "2. Historical weeks with the same parity sequence",
            "မတွေ့ပါ", "No matches",
            "ရရှိတဲ့သမိုင်းမှတ်တမ်းမှာ မတွေ့ခဲ့ပါ။",
            "None matched in the sample history.")
        DemoSituation.TIES -> line(
            "စုံ/မ အစဉ်ပုံစံတူ သမိုင်းအပတ်တွေရဲ့ နောက်အကြိမ်မှာ",
            "2. After historically matching parity sequences",
            "စုံမ၊ မမ", "Even/odd, odd/odd",
            "တစ်ကြိမ်စီ တူညီစွာတွေ့ခဲ့ပါတယ် (စုစုပေါင်း ၂ ပတ်)။",
            "Each occurred once across two matching weeks.")
        DemoSituation.MIDDLE, DemoSituation.CLOSED, DemoSituation.MISSING_REF -> line(
            "ယခုအပတ် စုံ/မ အစဉ်ပုံစံနဲ့ တူတဲ့ သမိုင်းအပတ် ၃ ပတ်ရဲ့ နောက်အကြိမ်မှာ",
            "2. Following three historically matching weeks",
            "စုံမ", "Even/odd",
            "၂ ကြိမ်နဲ့ အများဆုံးတွေ့ခဲ့ပါတယ်။",
            "Led with two observed following outcomes.")
    }
    return DemoReportMethod("03",title,listOf(item))
}

private fun report06(s: DemoSituation): DemoReportMethod {
    val title = words("ပျောက်ဂဏန်း စစ်ဆေးချက်", "Longest observed absences")
    fun streak(
        mm: String, english: String, digit: String, englishDigit: String,
        digitCount: String, pair: String, englishPair: String, pairCount: String,
    ) = line(
        mm, english,
        digit + " · " + digitCount + " ကြိမ်\n" + pair + " · " + pairCount + " ကြိမ်",
        englishDigit + " · " + digitCount + " draws\n" +
            englishPair + " · " + pairCount + " draws",
        "တစ်လုံးဂဏန်း / 2D တွဲ အစဉ်လိုက်ဖြစ်ပါတယ်။",
        "Single digit / exact 2D pair respectively.",
    )
    val morning = streak(
        "မနက်အကြိမ်မှာ အကြာဆုံးမတွေ့ရတာက",
        "The longest ongoing absence for morning draws is",
        if(s==DemoSituation.TIES) "3၊ 7" else "3",
        if(s==DemoSituation.TIES) "3, 7" else "3",
        "16", if(s==DemoSituation.TIES) "93၊ 62" else "93",
        if(s==DemoSituation.TIES) "93, 62" else "93", "45")
    if(s==DemoSituation.PARTIAL) return DemoReportMethod("06", title,
        listOf(morning, line("ညနေနဲ့ နှစ်ကြိမ်ပေါင်းအတွက်",
            "Evening and combined streams", "မရရှိသေး", "Unavailable",
            "အတည်ပြုနိုင်တဲ့ နမူနာမှတ်တမ်း မရှိသေးပါ။",
            "Verified samples are unavailable.")))
    return DemoReportMethod("06", title, listOf(
        morning,
        streak("ညနေအကြိမ်မှာ အကြာဆုံးမတွေ့ရတာက",
            "The longest ongoing absence for evening draws is",
            "1", "1", "8", "56", "56", "37"),
        streak("မနက်/ညနေ ပေါင်းမှတ်တမ်းမှာ အကြာဆုံးမတွေ့ရတာက",
            "The longest ongoing absence in combined draws is",
            "0", "0", "6", "47", "47", "22"),
    ))
}

private data class DemoPriorReference(
    val date: LocalDate,
    val session: DrawSession,
    val result: String?,
)

private data class DemoFollowingEpisode(
    val date: LocalDate,
    val first: String,
    val second: String,
    val result: String?,
)

private fun demoEarlierTradingDay(
    date: LocalDate, closedDates: Set<LocalDate>,
): LocalDate {
    var candidate = date.minusDays(1)
    while (!DrawSchedule.isWorkingDate(candidate, closedDates))
        candidate = candidate.minusDays(1)
    return candidate
}

/** Stable, artificial values indexed by date/session: never read verified app results. */
private fun demoSyntheticResult(date: LocalDate, session: DrawSession): String {
    val seed = date.toEpochDay() * 37L +
        if (session == DrawSession.MORNING) 19L else 63L
    return Math.floorMod(seed, 100L).toString().padStart(2, '0')
}

private fun demoRefLabel(ref: DemoPriorReference, en: Boolean): String {
    val sessionLabel = if (en) {
        if (ref.session == DrawSession.MORNING) "AM" else "PM"
    } else {
        if (ref.session == DrawSession.MORNING) "မနက်" else "ညနေ"
    }
    return ref.date.toString() + " · " + sessionLabel
}

private fun demoSampleEpisodes(
    date: LocalDate,
    first: String,
    second: String,
    situation: DemoSituation,
): List<DemoFollowingEpisode> {
    // The target and every fixture episode share weekday/session by using
    // the target date minus whole weeks. A matching reference never implies
    // that the historical NEXT draw must be known.
    val otherFirst = ((first.toInt() + 1) % 100).toString().padStart(2, '0')
    val otherSecond = ((second.toInt() + 1) % 100).toString().padStart(2, '0')
    val paired = ((first.toInt() + second.toInt() + 13) % 100).toString().padStart(2, '0')
    val tie = ((paired.toInt() + 17) % 100).toString().padStart(2, '0')
    val soloFirst = ((first.toInt() + 29) % 100).toString().padStart(2, '0')
    val soloSecond = ((second.toInt() + 47) % 100).toString().padStart(2, '0')
    val candidates = when (situation) {
        DemoSituation.FULL -> listOf(
            Triple(first, otherSecond, soloFirst),
            Triple(first, otherSecond, soloFirst),
            Triple(otherFirst, second, soloSecond),
            Triple(first, second, null), // matching sources, result not recorded
        )
        DemoSituation.TIES -> listOf(
            Triple(first, second, paired),
            Triple(first, second, tie),
            Triple(first, second, paired),
            Triple(first, second, tie),
            Triple(first, otherSecond, soloFirst),
            Triple(otherFirst, second, soloSecond),
        )
        else -> listOf(
            Triple(first, second, paired),
            Triple(first, otherSecond, soloFirst),
            Triple(first, otherSecond, soloFirst),
            Triple(otherFirst, second, soloSecond),
            Triple(first, second, null),
        )
    }
    return candidates.mapIndexed { idx, it ->
        DemoFollowingEpisode(date.minusWeeks((idx + 1).toLong()),
            it.first, it.second, it.third)
    }
}

private fun report08(
    s: DemoSituation, date: LocalDate, session: DrawSession,
): DemoReportMethod {
    val title = words("ရှေ့ထွက်ဂဏန်း တိုက်စစ်", "Historical references")
    if (!DrawSchedule.isWorkingDate(date, emptySet())) return DemoReportMethod(
        "08", title, listOf(line(
            "ရွေးထားတဲ့နေ့ရဲ့ ရှေ့ထွက်ဂဏန်း တိုက်စစ်မှုက",
            "Historical prior-result comparison for this date",
            "အကြိမ်ပိတ်", "Closed date",
            "စနေ/တနင်္ဂနွေဖြစ်လို့ ဒီရက်စွဲမှာ 2D အကြိမ် မရှိပါ။",
            "This date is a weekend; no trading draw is scheduled.")))

    val usualPrior = demoEarlierTradingDay(date, emptySet())
    // The closed-day situation supplies VERIFIED demo closure evidence.
    // A missing *record* never enters this closure set.
    val closedDates = if (s == DemoSituation.CLOSED) setOf(usualPrior)
        else emptySet()
    val prior = demoEarlierTradingDay(date, closedDates)
    val firstSlot = if (session == DrawSession.MORNING)
        DemoPriorReference(prior, DrawSession.MORNING, null)
        else DemoPriorReference(prior, DrawSession.EVENING, null)
    val secondSlot = if (session == DrawSession.MORNING)
        DemoPriorReference(prior, DrawSession.EVENING, null)
        else DemoPriorReference(date, DrawSession.MORNING, null)
    val first = firstSlot.copy(result = if (s == DemoSituation.MISSING_REF)
        null else demoSyntheticResult(firstSlot.date, firstSlot.session))
    val second = secondSlot.copy(result = if (s == DemoSituation.PARTIAL)
        null else demoSyntheticResult(secondSlot.date, secondSlot.session))

    val firstValue = first.result
    val secondValue = second.result
    val artificialFirst = firstValue ?: demoSyntheticResult(first.date, first.session)
    val artificialSecond = secondValue ?: demoSyntheticResult(second.date, second.session)
    val episodes = demoSampleEpisodes(date, artificialFirst, artificialSecond, s)
    fun matchesFirst(x: DemoFollowingEpisode) =
        firstValue != null && x.first == firstValue
    fun matchesSecond(x: DemoFollowingEpisode) =
        secondValue != null && x.second == secondValue

    val bothRows = episodes.filter { matchesFirst(it) && matchesSecond(it) }
    val firstRows = episodes.filter { matchesFirst(it) }
    val secondRows = episodes.filter { matchesSecond(it) }
    val resultBoth = bothRows.mapNotNull { it.result }
    val resultFirst = firstRows.mapNotNull { it.result }
    val resultSecond = secondRows.mapNotNull { it.result }

    fun conditionLine(
        mmQuestion: String, enQuestion: String, eligible: Boolean,
        all: List<DemoFollowingEpisode>, known: List<String>,
    ): DemoReportLine {
        if (!eligible) return line(mmQuestion, enQuestion,
            "Result မတွေ့", "Reference unavailable",
            "လိုအပ်တဲ့ ရှေ့အကြိမ်ရလဒ် မရှိသေးလို့ ဒီအခြေအနေကို မစစ်နိုင်ပါ။",
            "The source draw is unknown; this comparison cannot run.")
        if (all.isEmpty()) return line(mmQuestion, enQuestion,
            "မတွေ့ပါ", "No match",
            "နှိုင်းယှဉ်ခဲ့တဲ့ သမိုင်းအခြေအနေတူ မတွေ့ရှိပါ။",
            "No historical source sequence matched.")
        if (known.isEmpty()) return line(mmQuestion, enQuestion,
            "နောက်ထွက်မသိ", "Outcome unknown",
            "ရှေ့ဂဏန်းတူတဲ့ မှတ်တမ်းရှိပေမဲ့ နောက်ထွက် Result မရရှိပါ။",
            "Reference matches exist, but following draw results are missing.")
        val (leaders, count) = winners(known)
        val gaps = all.size - known.size
        val mmEnding = count.toString() + " ကြိမ်စီ နောက်ထွက်တွေ့ခဲ့ပါတယ်။" +
            if (gaps > 0) " နောက်ထွက်မရှိသေးတဲ့ မှတ်တမ်း " + gaps + " ခုကို မတွက်ထားပါ။" else ""
        val enEnding = count.toString() + " following appearances each." +
            if (gaps > 0) " Excluded " + gaps + " missing historical outcomes." else ""
        return line(mmQuestion, enQuestion,
            mmNumbers(leaders), enNumbers(leaders), mmEnding, enEnding)
    }

    val firstDesc = firstValue?.let { "ပထမရှေ့ဂဏန်း " + it } ?: "ပထမရှေ့အကြိမ်"
    val firstDescEn = firstValue?.let { "first reference " + it } ?: "first reference"
    val secondDesc = secondValue?.let { "ဒုတိယရှေ့ဂဏန်း " + it } ?: "ဒုတိယရှေ့အကြိမ်"
    val secondDescEn = secondValue?.let { "second reference " + it } ?: "second reference"
    val bothDesc = if (firstValue != null && secondValue != null)
        "ရှေ့ထွက် " + firstValue + " နဲ့ " + secondValue +
            " နှစ်ခုလုံးတူခဲ့တဲ့ သမိုင်းနောက်ထွက်အများဆုံးက"
        else "ရှေ့ထွက်ဂဏန်းနှစ်ခုလုံးကို သမိုင်းနဲ့ တိုက်စစ်ရာမှာ"
    val bothDescEn = if (firstValue != null && secondValue != null)
        "The leading historical next result after both " + firstValue +
            " and " + secondValue + " matched is"
        else "Comparing both preceding results with history"

    val output = mutableListOf(
        conditionLine(bothDesc, bothDescEn,
            firstValue != null && secondValue != null, bothRows, resultBoth),
        conditionLine(firstDesc + " နဲ့ သီးခြားတိုက်စစ်ရာမှာ",
            "When independently matching " + firstDescEn,
            firstValue != null, firstRows, resultFirst),
        conditionLine(secondDesc + " နဲ့ သီးခြားတိုက်စစ်ရာမှာ",
            "When independently matching " + secondDescEn,
            secondValue != null, secondRows, resultSecond),
    )
    output += listOf(
        line("ပထမရည်ညွှန်းအကြိမ် (" + demoRefLabel(first, false) + ") ရလဒ်က",
            "First source draw (" + demoRefLabel(first, true) + ") is",
            firstValue ?: "Result မတွေ့",
            firstValue ?: "Not recorded",
            if (firstValue == null)
                "ဖွင့်ရက်မှတ်တမ်း မရရှိသေးတာဖြစ်ပြီး ပိတ်ရက်လို့ မယူဆပါ။"
            else "ဒီရလဒ်ကို အခြေခံပြီး စစ်ထားပါတယ်။",
            if (firstValue == null) "Unknown draw; not treated as a closure."
            else "This verified sample is the first reference."),
        line("ဒုတိယရည်ညွှန်းအကြိမ် (" + demoRefLabel(second, false) + ") ရလဒ်က",
            "Second source draw (" + demoRefLabel(second, true) + ") is",
            secondValue ?: "Result မတွေ့",
            secondValue ?: "Not recorded",
            if (secondValue == null)
                "ဖွင့်ရက်မှတ်တမ်း မရရှိသေးတာဖြစ်ပြီး ပိတ်ရက်လို့ မယူဆပါ။"
            else "ဒီရလဒ်ကို အခြေခံပြီး စစ်ထားပါတယ်။",
            if (secondValue == null) "Unknown draw; not treated as a closure."
            else "This verified sample is the second reference."),
    )
    if (closedDates.isNotEmpty()) output += line(
        "ရှေ့ဆုံးရက် " + usualPrior + " ကို",
        "The immediately preceding weekday " + usualPrior + " was",
        "ပိတ်ရက်", "Verified closed",
        "နမူနာပိတ်ရက်အဖြစ် အတည်ပြုထားလို့ ကျော်ပြီး " + prior +
            " ရဲ့ ရလဒ်တွေကို ရည်ညွှန်းထားပါတယ်။",
        "A verified sample closure, so references move to " + prior + ".")
    return DemoReportMethod("08", title, output)
}

private data class DemoFlowSlot(val result: String?, val pattern: DemoWords)

private fun demoFlowSlots(situation: DemoSituation): List<DemoFlowSlot> {
    val seen = mutableSetOf<Char>()
    var gapBefore = false
    return weekSlots(situation).take(10).map { result ->
        when {
            result == null -> {
                gapBefore = true
                DemoFlowSlot(null, words("Result မတွေ့", "Missing"))
            }
            gapBefore -> DemoFlowSlot(result, words("မသေချာ", "Unverified"))
            else -> {
                val firstOld = result[0] in seen
                val secondOld = result[1] in seen
                val label = when {
                    firstOld && secondOld -> words("ဟောင်း+ဟောင်း", "Old+Old")
                    !firstOld && !secondOld -> words("သစ်+သစ်", "New+New")
                    !firstOld && secondOld -> words("သစ်+ဟောင်း", "New+Old")
                    else -> words("ဟောင်း+သစ်", "Old+New")
                }
                seen.addAll(result.toList()) // 11 contains just one distinct digit
                DemoFlowSlot(result, label)
            }
        }
    }
}

@Composable
private fun DemoWeekFlow(situation: DemoSituation, en: Boolean) {
    val slots = remember(situation) { demoFlowSlots(situation) }
    val raw = weekSlots(situation)
    val observed = raw.filterNotNull()
    val remaining = unseenDigits(observed)
    val missing = raw.any { it == null }
    Column(
        Modifier.fillMaxWidth().padding(start = 28.dp, end = 3.dp, bottom = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            words("ယခုအပတ် ထွက်ရှိထားတဲ့ 2D ဂဏန်းတွေနဲ့ အသစ်/အဟောင်း ပုံစံတွေက—",
                "This week's recorded 2D results and their new/old patterns:")
                .display(en),
            fontSize = 12.sp, lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Five days across (columns); AM and PM stay vertically paired.
        // Row 0: draws 1,3,5,7,9. Row 1: draws 2,4,6,8,10.
        // Future slots are blank guides, never "Result missing".
        repeat(2) { row ->
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) { col ->
                    val item = slots.getOrNull(col * 2 + row)
                    Column(
                        Modifier.weight(1f).heightIn(min = 54.dp)
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (item != null) {
                            Text(
                                item.result ?: "—",
                                fontSize = DemoResultSize, lineHeight = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.result == null)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else ReportDataInk,
                            )
                            Text(
                                item.pattern.display(en),
                                fontSize = 9.sp, lineHeight = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // Not-yet-recorded future positions; intentionally not
                            // marked as a historical closure or missing result.
                            Text("··", fontSize = DemoResultSize,
                                lineHeight = 22.sp,
                                color = ReportWine.copy(alpha = .22f))
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = ReportLine)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Top) {
            Text(words("ယခုအပတ် မတွေ့ရသေးတဲ့ ကျန်ဂဏန်းတွေက",
                "Digits not yet seen this week are").display(en),
                modifier = Modifier.weight(.56f), fontSize = 12.sp,
                lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(.44f)) {
                Text(if(en) joinedDigitsEnglish(remaining) else joinedDigits(remaining),
                    fontSize = DemoResultSize, fontWeight = FontWeight.Bold, color = ReportDataInk)
                Text(
                    if(missing)
                        words("ရရှိထားတဲ့မှတ်တမ်းအရသာ။",
                            "Based on available records only.").display(en)
                    else
                        words("စစ်ပြီးသား " + observed.size + " ကြိမ်အရ။",
                            "Based on " + observed.size + " results.").display(en),
                    fontSize = 10.sp, lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if(missing) Text(
            words("Result မတွေ့တဲ့အကြိမ်နောက်ပိုင်း အသစ်/အဟောင်းကို အတည်မပြုနိုင်ပါ။",
                "New/old labels after an unknown draw are unverified.").display(en),
            fontSize = 11.sp, lineHeight = 16.sp, color = ReportWine)
    }
}

private fun report05(s: DemoSituation): DemoReportMethod {
    val title=words("အသစ်/အဟောင်း ဂဏန်းစီးဆင်းမှု", "New / old digit flow")
    val slots=weekSlots(s)
    val events=slots.mapIndexedNotNull { i,pair->pair?.let { i to it } }
    if(events.isEmpty())return DemoReportMethod("05",title,listOf(line(
        "ယခုအပတ် အသစ်/အဟောင်းအခြေအနေက",
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
        line("ယခုအပတ် နောက်ဆုံးတွေ့ရှိတဲ့ 2D ရလဒ်က",
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
    val maximum=groups.maxOfOrNull { it.second }?:0
    fun friendly(k: String)=if(k=="ညီကို")"ညီအကို" else k
    val leaders=groups.filter { it.second==maximum&&maximum>0 }.map { friendly(it.first) }
    val main=line("ယခုအပတ် ထွက်ရှိထားတဲ့ အထူးအုပ်စုတွေထဲမှာ အများဆုံးက",
        "6. The most recorded special group this week is",
        if(maximum==0)"မတွေ့သေး" else mmNumbers(leaders),
        if(maximum==0)"None" else enNumbers(leaders),
        if(maximum==0)"အထူးအုပ်စုထွက်ရှိမှု မတွေ့ရသေးပါ။"
        else maximum.toString()+" ကြိမ်စီ ဖြစ်ပါတယ်။",
        if(maximum==0)"No special-group occurrence yet."
        else maximum.toString()+" appearances each.")
    return DemoReportMethod("07",title,listOf(main)+all.map { (key,count) ->
        line((if(key=="ပြန်ကျ") "တူညီတဲ့ 2D ဂဏန်း ပြန်ကျမှုက" else friendly(key)+" အုပ်စုထွက်ရှိမှုက"),
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
        line("ပြီးခဲ့တဲ့အပတ် တစ်လုံးဂဏန်းတွေထဲမှာ အထွက်အများဆုံးက",
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
    val start=line("ပြီးခဲ့တဲ့အပတ် မတွေ့ခဲ့တဲ့ဂဏန်းတွေထဲက ယခုအပတ် ပြန်တွေ့ရတာက",
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


/** Render the Engine's verified data, never synthetic DEMO results. */
private fun realShown(items:List<String>, en:Boolean, limit:Int=4):String {
    val values=items.distinct().sorted()
    if(values.isEmpty())return if(en)"None" else "မရှိ"
    val join=if(en)", " else "၊ "
    return values.take(limit).joinToString(join) +
        if(values.size>limit)" +"+(values.size-limit) else ""
}
private fun realEmpty(id:String, mm:String, en:String):DemoReportMethod =
    DemoReportMethod(id,words(mm,en),listOf(line(
        mm,en,"မရရှိသေး","Unavailable",
        "အတည်ပြုထားတဲ့ နှိုင်းယှဉ်မှတ်တမ်း မလုံလောက်သေးပါ။",
        "Insufficient verified recorded results.")))
private fun realCountLine(
    qmm:String,qen:String,data:List<TwoDCount>,
    renameEn:(String)->String={it},renameMm:(String)->String={it},
):DemoReportLine {
    val p=TwoDLiveSummary.peak(data)
    return if(!p.present)line(qmm,qen,
        "မရရှိသေး","Unavailable",
        "နှိုင်းယှဉ်နိုင်တဲ့ မှတ်တမ်း မရှိသေးပါ။",
        "No verified observations are available.")
    else line(qmm,qen,
        realShown(p.values.map(renameMm),false),
        realShown(p.values.map(renameEn),true),
        p.count.toString()+" ကြိမ်စီ တွေ့ထားပါတယ်။",
        p.count.toString()+" observations each.")
}
private fun realCountLine(
    qmm:String,qen:String,data:Map<String,Int>,
    renameEn:(String)->String={it},renameMm:(String)->String={it},
):DemoReportLine=realCountLine(qmm,qen,
    data.map{TwoDCount(it.key,it.value)},renameEn,renameMm)

private fun real02(r:TwoDAnalysisReport):DemoReportMethod {
    val d=r.a02
    if(d.samples==0)return realEmpty("02",
        "နေ့တူ၊ အကြိမ်တူ သမိုင်းအထွက်အများဆုံးက",
        "Most frequent results for the matching weekday and session")
    return DemoReportMethod("02",words("အထွက်အများဆုံး","Most frequent"),listOf(
        realCountLine("နေ့တူ၊ အကြိမ်တူ သမိုင်းမှာ အထွက်အများဆုံး 2D ဂဏန်းတွဲက",
            "The leading exact 2D pair for this weekday/session is",d.numbers),
        realCountLine("တစ်လုံးဂဏန်း အများဆုံးက","The leading single digit is",d.singles),
        realCountLine("အများဆုံး စုံ/မ အမျိုးအစားက","The leading parity class is",
            d.parity,renameEn={twoDParityLabel(it,true)}),
        // Real observations exist, but zero special groups is a genuine
        // "none observed" finding, not an unavailable-data error.
        if(d.groups.values.all { it == 0 })
            line("အများဆုံး အထူးအုပ်စုက","The leading special group is",
                "မတွေ့ပါ","None",
                "ရရှိထားတဲ့ Result တွေထဲမှာ အထူးအုပ်စုမတွေ့ခဲ့ပါ။",
                "No special-group occurrence in recorded results.")
        else realCountLine("အများဆုံး အထူးအုပ်စုက","The leading special group is",
            d.groups,renameEn={twoDGroupLabel(it,true)},
            renameMm={twoDGroupLabel(it,false)}),
        realCountLine("အများဆုံး ထိပ်ဂဏန်းက","The leading tens digit is",d.tens),
        realCountLine("အများဆုံး ပိတ်ဂဏန်းက","The leading ones digit is",d.ones)
    ))
}
private fun real03(r:TwoDAnalysisReport):DemoReportMethod {
    val d=r.a03
    val mm="ယခုအပတ် စုံ/မ အစဉ်ပုံစံတူ သမိုင်းနောက်ထွက်မှာ"
    val en="After matching historical weekly parity sequences"
    val result=when {
        !d.eligible -> line(mm,en,"မပြည့်စုံ","Insufficient",
            "ရှေ့အကြိမ် ၄–၉ ကြိမ်ရဲ့ အစဉ်ကို အတည်မပြုနိုင်သေးပါ။",
            "A verified preceding 4–9 draw prefix is not available.")
        d.matched.isEmpty() && d.missingFollowingCount>0 ->
            line(mm,en,"နောက်ထွက်မသိ","Following result unknown",
                "Pattern တူတဲ့ သမိုင်းအပတ် "+d.missingFollowingCount+
                    " ပတ်မှာ နောက်အကြိမ် Result မရှိသေးပါ။",
                d.missingFollowingCount.toString()+
                    " historically matching prefixes have missing following results.")
        d.matched.isEmpty() -> line(mm,en,"မတွေ့ပါ","No match",
            "ရရှိထားတဲ့သမိုင်းမှာ ပုံစံတူမတွေ့ခဲ့ပါ။",
            "No verified matching history was found.")
        else -> {
            val observed=realCountLine(mm,en,d.nextParity,
                renameEn={twoDParityLabel(it,true)})
            if(d.missingFollowingCount>0) observed.copy(ending=words(
                observed.ending.mm+" နောက်ထွက်မသိတဲ့ Pattern တူအပတ် "+
                    d.missingFollowingCount+" ပတ်ကို မတွက်ထားပါ။",
                observed.ending.en+" Excludes "+
                    d.missingFollowingCount+" unknown following results."))
            else observed
        }
    }
    return DemoReportMethod("03",words("စုံ/မ အစဉ်","Parity pattern"),listOf(result))
}
private fun realStreak(
    stream:TwoDStreakStream?, qmm:String, qen:String,
):DemoReportLine {
    if(stream==null||stream.observedDraws==0)return line(qmm,qen,
        "မရရှိသေး","Unavailable",
        "အတည်ပြုထားတဲ့ အကြိမ်မှတ်တမ်း မရှိသေးပါ။",
        "No verified draws are available in this stream.")
    val digit=TwoDLiveSummary.missing(stream.singleDigits,stream.hasUnknownOpenSlots)
    val pair=TwoDLiveSummary.missing(stream.fullNumbers,stream.hasUnknownOpenSlots)
    val dmm=realShown(digit.top.values,false,2)
    val pmm=realShown(pair.top.values,false,2)
    val den=realShown(digit.top.values,true,2)
    val pen=realShown(pair.top.values,true,2)
    val lowerBound=digit.lowerBound||pair.lowerBound
    return line(qmm,qen,
        "0–9: "+dmm+" · "+digit.top.count+" ကြိမ်\n"+
            "2D: "+pmm+" · "+pair.top.count+" ကြိမ်",
        "0–9: "+den+" · "+digit.top.count+" draws\n"+
            "2D: "+pen+" · "+pair.top.count+" draws",
        if(lowerBound)"မှတ်တမ်းအကန့်အသတ်ရှိ၍ အနည်းဆုံးအကြိမ်ရေ ဖြစ်ပါတယ်။"
            else "အတည်ပြုမှတ်တမ်းအရ ဖြစ်ပါတယ်။",
        if(lowerBound)"Lower bounds; some open slots or earlier history are unknown."
            else "Measured across verified draws.")
}
private fun real06(r:TwoDAnalysisReport):DemoReportMethod {
    val keys=if(r.target.session==DrawSession.MORNING)
        listOf("morning","evening","combined") else
        listOf("evening","morning","combined")
    val captions=mapOf(
        "morning" to words("မနက်အကြိမ် အကြာဆုံးပျောက်တဲ့ တစ်လုံး/2D တွဲက",
            "Longest missing digit / pair in AM draws"),
        "evening" to words("ညနေအကြိမ် အကြာဆုံးပျောက်တဲ့ တစ်လုံး/2D တွဲက",
            "Longest missing digit / pair in PM draws"),
        "combined" to words("မနက်/ညနေ ပေါင်းအကြိမ်မှာ အကြာဆုံးပျောက်တဲ့ တစ်လုံး/2D တွဲက",
            "Longest missing digit / pair across both sessions"),
    )
    return DemoReportMethod("06",words("ပျောက်ဂဏန်း","Missing streaks"),
        keys.map{key->
            val q=requireNotNull(captions[key])
            realStreak(r.a06[key],q.mm,q.en)
        })
}
private fun realCondition(qmm:String,qen:String,
    known:Boolean,source:TwoDConditional):DemoReportLine {
    if(!known)return line(qmm,qen,"Result မရှိ","Unknown reference",
        "လိုအပ်တဲ့ ရှေ့ထွက် Result ကို အတည်မပြုနိုင်သေးပါ။",
        "The required preceding draw is not recorded.")
    val p=TwoDLiveSummary.peak(source.frequencies)
    if(!p.present){
        return if(source.missingTargetCount>0)line(qmm,qen,
            "နောက်ထွက်မသိ","Unknown following result",
            "ရှေ့ပုံစံတူပြီး နောက်ထွက်မှတ်တမ်း "+
                source.missingTargetCount+" ခု မရှိသေးပါ။",
            source.missingTargetCount.toString()+
                " matching sources have unknown following results.")
        else line(qmm,qen,"မတွေ့ပါ","No match",
            "ရရှိတဲ့သမိုင်းမှာ တူညီတဲ့အခြေအနေ မတွေ့ခဲ့ပါ။",
            "No comparable recorded historical outcome was found.")
    }
    val gaps=source.missingTargetCount
    return line(qmm,qen,realShown(p.values,false),realShown(p.values,true),
        p.count.toString()+" ကြိမ်စီ တွေ့ခဲ့ပါတယ်။"+
            if(gaps>0)" နောက်ထွက်မရှိတဲ့မှတ်တမ်း "+gaps+" ခု မတွက်ထားပါ။" else "",
        p.count.toString()+" following appearances each."+
            if(gaps>0)" Excludes "+gaps+" unknown outcomes." else "")
}
private fun real08(r:TwoDAnalysisReport):DemoReportMethod {
    val data=r.a08
    val first=data.firstReference
    val second=data.secondReference
    val mm=if(first!=null&&second!=null)
        "ရှေ့ဂဏန်း "+first.digit+" နဲ့ "+second.digit+" နှစ်ခုလုံးတူခဲ့တဲ့ သမိုင်းမှာ"
        else "ရှေ့ထွက်ဂဏန်းနှစ်ခုလုံးကို သမိုင်းနဲ့ တိုက်စစ်ရာမှာ"
    val en=if(first!=null&&second!=null)
        "After historical references "+first.digit+" and "+second.digit+" both matched"
        else "Historical comparison requiring both previous results"
    val bothRow=realCondition(mm,en,first!=null&&second!=null,data.both)
    val firstRow=realCondition("ပထမရှေ့ဂဏန်း "+
            (first?.digit?:"မရှိ")+" တစ်ခုတည်းနဲ့တိုက်စစ်ရာမှာ",
            "With the first reference "+(first?.digit?:"unknown")+" alone",
            first!=null,data.firstOnly)
    val secondRow=realCondition("ဒုတိယရှေ့ဂဏန်း "+
            (second?.digit?:"မရှိ")+" တစ်ခုတည်းနဲ့တိုက်စစ်ရာမှာ",
            "With the second reference "+(second?.digit?:"unknown")+" alone",
            second!=null,data.secondOnly)
    val rows=when {
        first!=null && second!=null -> mutableListOf(bothRow,firstRow,secondRow)
        first!=null -> mutableListOf(firstRow,bothRow,secondRow)
        second!=null -> mutableListOf(secondRow,bothRow,firstRow)
        else -> mutableListOf(bothRow,firstRow,secondRow)
    }
    fun refLine(qmm:String,qen:String,
        ref:com.myanmar.ledger2d.core.domain.TwoDMatch?):DemoReportLine =
        line(qmm,qen,ref?.digit?:"Result မရှိ",
            ref?.digit?:"Unknown",
            if(ref!=null)ref.date.toString()+" · "+
                (if(ref.session==DrawSession.MORNING)"မနက်" else "ညနေ")+
                " အကြိမ်ရလဒ် ဖြစ်ပါတယ်။"
            else "Result မရှိတာကို ပိတ်ရက်လို့ မယူဆပါ။",
            if(ref!=null)ref.date.toString()+" · "+
                (if(ref.session==DrawSession.MORNING)"AM" else "PM")+
                " verified result."
            else "An unrecorded draw is not assumed to be a closed day.")
    rows+=refLine("ပထမရှေ့ထွက်ရလဒ်က","The first preceding result is",first)
    rows+=refLine("ဒုတိယရှေ့ထွက်ရလဒ်က","The second preceding result is",second)
    return DemoReportMethod("08",words("ရှေ့ထွက်","Prior results"),rows)
}
private fun real05(r:TwoDAnalysisReport):DemoReportMethod =
    DemoReportMethod("05",words("အသစ်/အဟောင်း","New/old"),listOf(line(
        "ယခုအပတ် အသစ်/အဟောင်း အစဉ်က",
        "This week's chronological new/old pattern is",
        r.a05.transitions.size.toString()+" ကြိမ်",
        r.a05.transitions.size.toString()+" draws",
        if(r.a05.completeThroughTarget)"ရရှိထားတဲ့အကြိမ်အထိ အတည်ပြုထားပါတယ်။"
        else "ဖွင့်ရက် Result မရရှိတဲ့အကြိမ် ရှိနိုင်ပါတယ်။",
        if(r.a05.completeThroughTarget)"Verified through the selected target."
        else "Some preceding open results are unknown.")))
private fun real07(r:TwoDAnalysisReport):DemoReportMethod {
    val a=r.a07
    if(a.observedDraws==0)return realEmpty("07",
        "ယခုအပတ် အထူးအုပ်စု ထွက်ရှိမှုက",
        "This week's special-group occurrences")
    val counts=linkedMapOf(
        "အပူး" to (a.groups["အပူး"]?:0),
        "ညီကို" to (a.groups["ညီကို"]?:a.groups["ညီအကို"]?:0),
        "ပါဝါ" to (a.groups["ပါဝါ"]?:0),
        "နက္ခတ်" to (a.groups["နက္ခတ်"]?:0),
    )
    val p=TwoDLiveSummary.peak(counts)
    val lead=if(p.present)realCountLine(
        "ယခုအပတ် အများဆုံးတွေ့တဲ့ အထူးအုပ်စုက",
        "The most frequent weekly special group is",counts,
        renameEn={twoDGroupLabel(it,true)},
        renameMm={twoDGroupLabel(it,false)})
    else line("ယခုအပတ် အထူးအုပ်စုထွက်ရှိမှုက",
        "Special groups in recorded weekly draws",
        "မတွေ့ပါ","None","ရရှိထားတဲ့အကြိမ်တွေမှာ မတွေ့ခဲ့ပါ။",
        "None observed in recorded results.")
    val note=if(a.completeThroughTarget)"" else " ရရှိထားတဲ့မှတ်တမ်းအရသာ။"
    val noteEn=if(a.completeThroughTarget)"" else " Recorded results only."
    val rest=counts.map{(key,count)->line(
        twoDGroupLabel(key,false)+" အုပ်စုထွက်ရှိမှုက",
        twoDGroupLabel(key,true)+" occurrences",
        count.toString(),count.toString(),
        "ကြိမ် ဖြစ်ပါတယ်။"+note,"occurrences."+noteEn)
    }+listOf(line("ယခုအပတ် တူညီတဲ့ 2D တွဲ ပြန်ကျမှုက",
        "Additional appearances of exact repeated 2D are",
        a.repeatedNumbers.sumOf{it.count}.toString(),
        a.repeatedNumbers.sumOf{it.count}.toString(),
        "ကြိမ် ဖြစ်ပါတယ်။"+note,"additional appearances."+noteEn))
    return DemoReportMethod("07",words("အထူး","Special groups"),listOf(lead)+rest)
}
private fun real04(r:TwoDAnalysisReport):DemoReportMethod {
    val a=r.a04
    if(a.observedDraws==0)return realEmpty("04",
        "ပြီးခဲ့တဲ့အပတ် မှတ်တမ်းမှာ","In the previous week's recorded draws")
    val mm=if(a.complete)"ပြီးခဲ့တဲ့အပတ်" else "ပြီးခဲ့တဲ့အပတ် ရရှိထားတဲ့မှတ်တမ်းမှာ"
    val en=if(a.complete)"Last week" else "In available previous-week draws"
    val coverage=if(a.complete)"ပြီးခဲ့တဲ့အပတ် မှတ်တမ်းအပြည့်အရ ဖြစ်ပါတယ်။"
        else "ရရှိထားတဲ့ "+a.observedDraws+"/"+a.expectedOpenDraws+
            " ကြိမ်အရသာ ဖြစ်ပါတယ်။"
    val coverageEn=if(a.complete)"Based on the verified completed week."
        else "Based only on "+a.observedDraws+"/"+a.expectedOpenDraws+" open draws."
    return DemoReportMethod("04",words("ယခင်အပတ်","Previous week"),listOf(
        realCountLine(mm+" အထွက်အများဆုံး တစ်လုံးဂဏန်းက",
            en+" the leading single digit is",a.singleDigitFrequency),
        line(mm+" မတွေ့ခဲ့တဲ့ တစ်လုံးဂဏန်းတွေက",
            en+" the digits not observed were",
            realShown(a.remainingDigits.map{it.toString()},false,10),
            realShown(a.remainingDigits.map{it.toString()},true,10),
            coverage,coverageEn),
        realCountLine(mm+" စုံ/မ အများဆုံးအမျိုးအစားက",
            en+" the most frequent parity class is",a.parity,
            renameEn={twoDParityLabel(it,true)}),
        realCountLine(mm+" အထူးအုပ်စု အများဆုံးက",
            en+" the most frequent special group is",a.groups,
            renameEn={twoDGroupLabel(it,true)},
            renameMm={twoDGroupLabel(it,false)}),
        line(mm+" တူညီတဲ့ 2D တွဲ ပြန်ကျမှုက",
            en+" the additional exact repeated 2D total",
            a.repeatedNumbers.sumOf{it.count}.toString(),
            a.repeatedNumbers.sumOf{it.count}.toString(),
            "ကြိမ် ဖြစ်ပါတယ်။"+if(a.complete)"" else " မှတ်တမ်းမပြည့်စုံသေးပါ။",
            "additional appearances."+if(a.complete)"" else " Records are incomplete."),
    ))
}
private fun real01(r:TwoDAnalysisReport):DemoReportMethod {
    val a=r.a01
    if(a.missingDigits.isEmpty()){
        if(!a.previousWeekComplete)return realEmpty("01",
            "ပြီးခဲ့တဲ့အပတ် ပျောက်ဂဏန်း ပြန်ပေါ်မှုက",
            "Previous-week missing digit returns")
        return DemoReportMethod("01",words("ပြန်ပေါ်မှု","Returns"),listOf(
            line("ပြီးခဲ့တဲ့အပတ် မတွေ့ခဲ့တဲ့ဂဏန်းတွေက",
                "Digits not seen last week are",
                "မရှိ","None","အတည်ပြုထားတဲ့ အပတ်မှတ်တမ်းအရ ဖြစ်ပါတယ်။",
                "The previous week is complete.")))
    }
    val returned=a.results.filter{it.thisWeekFirstSeen!=null}
    val notReturned=a.results.filter{it.thisWeekFirstSeen==null}
    val qualifier=if(a.previousWeekComplete)"" else "ရရှိထားတဲ့မှတ်တမ်းအရ "
    val rows=mutableListOf(
        line(qualifier+"ပြီးခဲ့တဲ့အပတ် မတွေ့ခဲ့တဲ့ဂဏန်းတွေထဲက ယခုအပတ် ပြန်တွေ့တာက",
            "Previously unseen digits observed this week are",
            realShown(returned.map{it.digit.toString()},false,10),
            realShown(returned.map{it.digit.toString()},true,10),
            if(returned.isEmpty())"ရရှိထားတဲ့ ယခုအပတ်မှတ်တမ်းမှာ မတွေ့ရသေးပါ။"
                else "ရရှိထားတဲ့ ယခုအပတ်မှတ်တမ်းမှာ တွေ့ထားပါတယ်။",
            if(returned.isEmpty())"None recorded yet this week."
                else "Observed in available current-week records."),
        line("ယခင်အပတ် မတွေ့ခဲ့တဲ့ တစ်လုံးဂဏန်းတွေက",
            "Digits unseen in previous-week draws are",
            realShown(a.missingDigits.map{it.toString()},false,10),
            realShown(a.missingDigits.map{it.toString()},true,10),
            if(a.previousWeekComplete)"အတည်ပြုထားတဲ့အပတ်အရ ဖြစ်ပါတယ်။"
                else "ယခင်အပတ်မှတ်တမ်း မပြည့်စုံသေးပါ။",
            if(a.previousWeekComplete)"Verified completed-week record."
                else "Previous-week records have gaps."),
        line("ယခုအပတ် ပြန်မတွေ့ရသေးတဲ့ ယခင်ပျောက်ဂဏန်းတွေက",
            "Previously unseen digits still unrecorded this week are",
            realShown(notReturned.map{it.digit.toString()},false,10),
            realShown(notReturned.map{it.digit.toString()},true,10),
            "ရရှိထားတဲ့အကြိမ်တွေ အရသာ ဖြစ်ပါတယ်။",
            "Based only on current-week recorded results."),
    )
    returned.forEach{item->
        val first=item.thisWeekFirstSeen
        if(first!=null)rows+=line(
            "ယခုအပတ် ဂဏန်း "+item.digit+" ရဲ့ ပထမပြန်တွေ့မှုက",
            "The first observed return of "+item.digit+" this week is",
            first.digit,first.digit,
            if(item.firstSeenVerified)first.date.toString()+" မှာ အတည်ပြုထားပါတယ်။"
                else "ရှေ့အကြိမ်ပျောက်နေလို့ ပထမဆုံးဟု မအတည်ပြုနိုင်သေးပါ။",
            if(item.firstSeenVerified)"Verified on "+first.date+"."
                else "Earlier gaps prevent verifying the first return.")
        val c=item.commonCompanions.maxOfOrNull{it.count}?:0
        if(c>0)rows+=line(
            "ဂဏန်း "+item.digit+" ပြန်ပေါ်ရာ သမိုင်းတွဲထွက်အများဆုံးက",
            "The top historical companion when "+item.digit+" returned is",
            realShown(item.commonCompanions.filter{it.count==c}
                .map{it.companion.toString()},false),
            realShown(item.commonCompanions.filter{it.count==c}
                .map{it.companion.toString()},true),
            c.toString()+" ကြိမ်စီ တွေ့ခဲ့ပါတယ်။",
            c.toString()+" recorded historical occurrences each.")
    }
    return DemoReportMethod("01",words("ပြန်ပေါ်မှု","Returns"),rows)
}
private fun realReportMethods(r:TwoDAnalysisReport):List<DemoReportMethod> =
    listOf(real02(r),real03(r),real06(r),real08(r),
        real05(r),real07(r),real04(r),real01(r))

/** Fixed Mon–Fri columns and AM/PM rows; never fill future slots with mock data. */
@Composable
private fun RealWeekFlow(
    flow:TwoDWeekFlow,target:DrawIdentity,closedDates:Set<LocalDate>,en:Boolean,
){
    val monday=target.date.minusDays((target.date.dayOfWeek.value-1).toLong())
    val bySlot=flow.transitions.associateBy{it.result.date to it.result.session}
    if(flow.transitions.isEmpty()){
        Text(
            words("ယခုအပတ် ပထမဆုံးအတည်ပြု Result မထွက်သေးလို့ အသစ်/အဟောင်း စစ်ဆေးမှု မစတင်ရသေးပါ။",
                "New/old analysis is inactive until the first verified result this week.")
                .display(en),
            modifier=Modifier.fillMaxWidth().padding(start=28.dp,end=21.dp,bottom=7.dp),
            fontSize=12.sp,lineHeight=18.sp,
            color=MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(
        Modifier.fillMaxWidth().padding(start=28.dp,end=21.dp,bottom=7.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp),
    ){
        Text(words("ယခုအပတ် 2D ထွက်ရှိမှုနဲ့ အသစ်/အဟောင်းပုံစံက—",
            "This week's 2D results and new/old patterns:").display(en),
            fontSize=12.sp,lineHeight=18.sp,
            color=MaterialTheme.colorScheme.onSurface)
        repeat(2){row->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)){
                repeat(5){col->
                    val d=monday.plusDays(col.toLong())
                    val ses=if(row==0)DrawSession.MORNING else DrawSession.EVENING
                    val closed=!DrawSchedule.isWorkingDate(d,closedDates)
                    val earlier=d.isBefore(target.date)||
                        (d==target.date&&ses.ordinal<target.session.ordinal)
                    val found=bySlot[d to ses]
                    Column(Modifier.weight(1f).heightIn(min=54.dp)
                        .padding(vertical=3.dp),
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(2.dp)){
                        when{
                            closed -> {
                                Text("·",fontSize=DemoResultSize,
                                    color=ReportWine.copy(alpha=.32f))
                                Text(if(en)"Closed" else "ပိတ်",fontSize=9.sp,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            !earlier -> Text("··",fontSize=DemoResultSize,
                                color=ReportWine.copy(alpha=.22f))
                            found==null -> {
                                Text("—",fontSize=DemoResultSize,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if(en)"Missing" else "မရရှိသေး",fontSize=9.sp,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else -> {
                                Text(found.result.digit,fontSize=DemoResultSize,
                                    fontWeight=FontWeight.Bold,lineHeight=22.sp,
                                    color=ReportDataInk)
                                val p=if(!found.classificationVerified)
                                    if(en)"Unverified" else "မသေချာ"
                                else when{
                                    found.newTens&&found.newOnes ->
                                        if(en)"New+New" else "သစ်+သစ်"
                                    !found.newTens&&!found.newOnes ->
                                        if(en)"Old+Old" else "ဟောင်း+ဟောင်း"
                                    found.newTens ->
                                        if(en)"New+Old" else "သစ်+ဟောင်း"
                                    else -> if(en)"Old+New" else "ဟောင်း+သစ်"
                                }
                                Text(p,fontSize=9.sp,lineHeight=12.sp,
                                    maxLines=2,textAlign=TextAlign.Center,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color=ReportLine)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp),
            verticalAlignment=Alignment.Top){
            Text(words("ယခုအပတ် မတွေ့ရသေးတဲ့ ကျန်ဂဏန်းတွေက",
                "Digits not yet seen in the week's recorded results are").display(en),
                modifier=Modifier.weight(.56f),
                fontSize=12.sp,lineHeight=18.sp,
                color=MaterialTheme.colorScheme.onSurface)
            Column(Modifier.weight(.44f)){
                Text(realShown(flow.remainingDigits.map{it.toString()},en,10),
                    fontSize=DemoResultSize,fontWeight=FontWeight.Bold,
                    color=ReportDataInk)
                Text(if(flow.completeThroughTarget)
                    words("ရွေးထားတဲ့အကြိမ်မတိုင်ခင်အထိ အတည်ပြုထားပါတယ်။",
                        "Verified through the selected target.").display(en)
                else words("ဖွင့်ရက် Result ပျောက်နေလို့ ရရှိတဲ့မှတ်တမ်းအရသာ။",
                    "Preceding open slots are missing; recorded results only.").display(en),
                    fontSize=10.sp,lineHeight=15.sp,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun reportMethods(s: DemoSituation, date: LocalDate, session: DrawSession): List<DemoReportMethod> = listOf(
    report02(s),report03(s),report06(s),report08(s, date, session),
    report05(s),report07(s),report04(s),report01(s),
)

@Composable
internal fun TwoDSummaryScreen(
    date:LocalDate,
    session:DrawSession,
    en:Boolean,
    report:TwoDAnalysisReport?,
    closedDates:Set<LocalDate>,
    isLoading:Boolean,
    validTarget:Boolean,
    validHistoryRange:Boolean,
    modifier:Modifier=Modifier,
    onPickDate:()->Unit,
    onChangeSession:(DrawSession)->Unit,
    onOpenDetail:(String)->Unit,
){
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf("") }
    val methods=remember(report){report?.let(::realReportMethods).orEmpty()}
    val tabs=listOf(
        words("လက်ရှိအကြိမ်","Current draw"),
        words("ယခုအပတ်","This week"),
        words("ယခင်အပတ်","Last week"),
    )
    val sections=listOf(methods.take(4),methods.drop(4).take(2),methods.takeLast(2))
    Column(modifier.fillMaxSize().padding(horizontal=10.dp,vertical=5.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)){
        CherryMysteryHeader(date,session,en,onPickDate,onChangeSession)
        if(report==null){
            Box(Modifier.fillMaxWidth().weight(1f),
                contentAlignment=Alignment.Center){
                if(isLoading){
                    CircularProgressIndicator(modifier=Modifier.size(28.dp),
                        color=ReportWine,strokeWidth=2.dp)
                }else{
                    Text(when{
                        !validTarget -> words("ရွေးထားတဲ့ရက်စွဲမှာ 2D အကြိမ်ပိတ်ထားပါတယ်။",
                            "No trading draw is scheduled on this date.").display(en)
                        !validHistoryRange -> words("History ရက်စွဲအပိုင်းအခြားကို ပြန်စစ်ပါ။",
                            "Review the selected history date range.").display(en)
                        else -> words("အတည်ပြုထားတဲ့ Result မှတ်တမ်း မရရှိနိုင်သေးပါ။",
                            "Verified result data is not available yet.").display(en)
                    },fontSize=13.sp,lineHeight=20.sp,
                        color=MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign=TextAlign.Center)
                }
            }
        }else{
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

        // Render all four/two/two MAIN findings first. Only measured spare room
        // can add another line. Opened sections show their full detail on scroll.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val viewportPx = with(LocalDensity.current) { maxHeight.roundToPx() }
            val reservePx = with(LocalDensity.current) { 8.dp.roundToPx() }
            val plans: List<Map<String, Int>> = when (tab) {
                0 -> listOf(
                    emptyMap(),
                    mapOf("02" to 2),
                    mapOf("02" to 2, "08" to 2),
                    mapOf("02" to 2, "08" to 2, "06" to 2),
                    mapOf("02" to 3, "08" to 2, "06" to 2),
                )
                1 -> listOf(emptyMap(), mapOf("07" to 2), mapOf("07" to 3))
                else -> listOf(
                    emptyMap(),
                    mapOf("04" to 2),
                    mapOf("04" to 2, "01" to 2),
                    mapOf("04" to 3, "01" to 2),
                    mapOf("04" to 3, "01" to 3),
                )
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
                    val choices = if (open.isNotEmpty()) listOf(emptyMap()) else plans
                    val measured = choices.mapIndexed { candidate, plan ->
                        val measurable = subcompose("summary-$tab-$candidate") {
                            Column(Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                sections[tab].forEachIndexed { index, item ->
                                    DemoAlignedMethod(
                                        ordinal = index + 1 + when (tab) {
                                            0 -> 0
                                            1 -> 4
                                            else -> 6
                                        },
                                        method = item,
                                        weekFlow = report.a05,
                                        target = report.target,
                                        closedDates = closedDates,
                                        en = en,
                                        expanded = open == item.id,
                                        visibleCount = if (item.id == "05") 0
                                            else plan[item.id] ?: 1,
                                        onToggle = {
                                            open = if (open == item.id) "" else item.id
                                        },
                                        onRealDetail = { onOpenDetail(item.id) },
                                    )
                                }
                                Text(
                                    words("သမိုင်းမှတ်တမ်းအရ စစ်ဆေးထားခြင်းဖြစ်ပြီး ခန့်မှန်းချက်မဟုတ်ပါ။",
                                        "Historical observations, not a prediction.")
                                        .display(en),
                                    fontSize = 10.sp, lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 7.dp),
                                )
                            }
                        }.single()
                        measurable.measure(
                            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                        )
                    }
                    val selectedIndex = measured.indexOfLast {
                        it.height + reservePx <= viewportPx
                    }.coerceAtLeast(0)
                    val selected = measured[selectedIndex]
                    layout(constraints.maxWidth, selected.height) {
                        selected.placeRelative(0, 0)
                    }
                }
            }
        }
        }
    }
}

/**
 * Decorative two-digit mystery: fragmented, blurred numeral silhouettes.
 * They are deliberately NOT decoded digits, predictions or historical results.
 */
@Composable
private fun CherryMysteryHeader(
    date: LocalDate, session: DrawSession, en: Boolean,
    onPickDate: () -> Unit, onChangeSession: (DrawSession) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = AppColors.PrimaryDeep,
        shadowElevation = 5.dp,
    ) {
        Box(
            Modifier.fillMaxWidth().height(117.dp).background(
                Brush.horizontalGradient(listOf(
                    Color(0xFF89102F), AppColors.Primary,
                    AppColors.PrimaryDeep,
                ))
            )
        ) {
            Canvas(Modifier.matchParentSize()) {
                val glow = Color(0xFFF5A4C1)
                drawCircle(
                    glow.copy(alpha = .12f), radius = size.height * .84f,
                    center = Offset(size.width * .47f, size.height * .33f),
                )
                drawCircle(
                    Color.White.copy(alpha = .075f),
                    radius = size.height * .53f,
                    center = Offset(size.width * .94f, size.height * -.22f),
                )
                listOf(
                    .11f to .25f, .22f to .70f, .80f to .16f,
                    .91f to .62f, .70f to .78f,
                ).forEachIndexed { index, p ->
                    drawCircle(
                        color = Color(0xFFFFD7D9),
                        radius = if (index % 2 == 0) 1.5.dp.toPx()
                            else 1.dp.toPx(),
                        center = Offset(size.width * p.first,
                            size.height * p.second),
                        alpha = .23f,
                    )
                }
            }
            Row(
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CherryFrostedDigit(variant = 0)
                CherryFrostedDigit(variant = 1)
            }
            // Keep the real navigation affordance, not a Mystery caption.
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
                shape = RoundedCornerShape(30.dp),
                color = Color(0xFF710D2E).copy(alpha = .90f),
            ) {
                Row(
                    Modifier.padding(start = 11.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        date.toString() + " · " + when (session) {
                            DrawSession.MORNING ->
                                if (en) "Morning" else "မနက်အကြိမ်"
                            DrawSession.EVENING ->
                                if (en) "Evening" else "ညနေအကြိမ်"
                        },
                        fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = .95f),
                        modifier = Modifier.clickable(onClick = onPickDate)
                            .padding(vertical = 5.dp),
                    )
                    IconButton(
                        modifier = Modifier.size(25.dp),
                        onClick = {
                            onChangeSession(
                                if (session == DrawSession.MORNING)
                                    DrawSession.EVENING else DrawSession.MORNING
                            )
                        },
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = if (en) "Change session"
                                else "အကြိမ်ပြောင်းရန်",
                            modifier = Modifier.size(17.dp),
                            tint = Color.White.copy(alpha = .9f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Two frosted panes imply a hidden pair, but their soft, disjointed shapes
 * cannot resolve into any particular two-digit number.
 */
@Composable
private fun CherryFrostedDigit(variant: Int) {
    Surface(
        modifier = Modifier.size(width = 72.dp, height = 68.dp),
        shape = RoundedCornerShape(15.dp),
        color = Color.White.copy(alpha = .055f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .20f)),
        shadowElevation = 1.dp,
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(
                    Color.White.copy(alpha = .075f),
                    Color(0xFFFF99B2).copy(alpha = .07f),
                    AppColors.PrimaryDeep.copy(alpha = .17f),
                ))
            )
        ) {
            // Only ambiguous remnants of two possible numeral shapes:
            // short incomplete curves never join into a readable 0-9 glyph.
            Canvas(
                Modifier.fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 9.dp)
                    .blur(11.dp),
            ) {
                val w = size.width
                val h = size.height
                val pale = Color(0xFFFFD9E5)
                val curveBounds = Offset(w * .13f, h * .08f)
                val curveSize = Size(w * .74f, h * .82f)
                if (variant == 0) {
                    drawArc(
                        color = pale.copy(alpha = .34f),
                        startAngle = 191f, sweepAngle = 55f,
                        useCenter = false,
                        topLeft = curveBounds, size = curveSize,
                        style = Stroke(width = 8.dp.toPx(),
                            cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = pale.copy(alpha = .28f),
                        startAngle = 17f, sweepAngle = 60f,
                        useCenter = false,
                        topLeft = curveBounds, size = curveSize,
                        style = Stroke(width = 7.dp.toPx(),
                            cap = StrokeCap.Round),
                    )
                    drawLine(
                        color = pale.copy(alpha = .19f),
                        start = Offset(w * .39f, h * .42f),
                        end = Offset(w * .56f, h * .56f),
                        strokeWidth = 7.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                } else {
                    drawArc(
                        color = pale.copy(alpha = .32f),
                        startAngle = 278f, sweepAngle = 57f,
                        useCenter = false,
                        topLeft = curveBounds, size = curveSize,
                        style = Stroke(width = 8.dp.toPx(),
                            cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = pale.copy(alpha = .27f),
                        startAngle = 105f, sweepAngle = 48f,
                        useCenter = false,
                        topLeft = curveBounds, size = curveSize,
                        style = Stroke(width = 8.dp.toPx(),
                            cap = StrokeCap.Round),
                    )
                    drawLine(
                        color = pale.copy(alpha = .16f),
                        start = Offset(w * .55f, h * .33f),
                        end = Offset(w * .39f, h * .59f),
                        strokeWidth = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(
                    color = Color(0xFFFFB7CF).copy(alpha = .20f),
                    radius = w * .30f,
                    center = Offset(
                        w * if (variant == 0) .38f else .62f,
                        h * .47f,
                    ),
                )
            }
            // Frost and non-uniform shadow mask the underlying fragments.
            Box(
                Modifier.matchParentSize().background(
                    Brush.horizontalGradient(listOf(
                        Color(0xFF8B153B).copy(alpha = .43f),
                        Color.White.copy(alpha = .12f),
                        Color(0xFF9F1239).copy(alpha = .43f),
                    ))
                )
            )
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(
                        Color.White.copy(alpha = .07f),
                        AppColors.Primary.copy(alpha = .18f),
                        AppColors.PrimaryDeep.copy(alpha = .24f),
                    ))
                )
            )
            Text(
                "?",
                modifier = Modifier.align(Alignment.Center)
                    .padding(bottom = 3.dp),
                color = Color(0xFFFFE0E9).copy(alpha = .66f),
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DemoAlignedMethod(
    ordinal: Int, method: DemoReportMethod,
    weekFlow: TwoDWeekFlow, target: DrawIdentity,
    closedDates: Set<LocalDate>,
    en: Boolean, expanded: Boolean, visibleCount: Int,
    onToggle: () -> Unit, onRealDetail: () -> Unit,
) {
    val main = method.lines.firstOrNull() ?: return
    val visible = visibleCount.coerceIn(0, method.lines.size)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick=onToggle)
                .padding(top=7.dp,bottom=3.dp),
            verticalAlignment=Alignment.Top,
            horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(ordinal.toString()+".", fontSize=13.sp,
                fontWeight=FontWeight.Bold,color=ReportWine,
                modifier=Modifier.width(22.dp))
            if(method.id=="05") {
                Text(words("ယခုအပတ် အသစ်/အဟောင်း အခြေအနေ",
                    "This week's new / old patterns").display(en),
                    fontSize=12.sp,lineHeight=18.sp,
                    color=MaterialTheme.colorScheme.onSurface,
                    modifier=Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f)) {
                    DemoAlignedLine(main,en,true)
                }
            }
            Text(if(expanded)"−" else "+",fontSize=16.sp,
                color=ReportWine,modifier=Modifier.width(15.dp))
        }
        if(method.id=="05") {
            RealWeekFlow(weekFlow,target,closedDates,en)
        } else {
            Column(Modifier.fillMaxWidth().padding(start=28.dp,end=21.dp,bottom=5.dp),
                verticalArrangement=Arrangement.spacedBy(1.dp)) {
                method.lines.drop(1).take((visible-1).coerceAtLeast(0)).forEach { item ->
                    HorizontalDivider(color=ReportLine.copy(alpha=.50f),
                        modifier=Modifier.padding(vertical=3.dp))
                    DemoAlignedLine(item,en,false)
                }
            }
        }
        if(expanded) {
            Column(Modifier.fillMaxWidth().padding(start=28.dp,end=21.dp,bottom=5.dp),
                verticalArrangement=Arrangement.spacedBy(1.dp)) {
                method.lines.drop(if(method.id=="05")method.lines.size else visible)
                    .forEach { item ->
                        HorizontalDivider(color=ReportLine.copy(alpha=.65f))
                        DemoAlignedLine(item,en,false)
                    }
                if(method.id=="05") {
                    Text(words("ဒီ Pattern မှာ ယခုအပတ်အတွင်း အရင်အကြိမ်တွေမှာ ပေါ်ခဲ့တဲ့ တစ်လုံးဂဏန်းကို အဟောင်းလို့ သတ်မှတ်ထားပါတယ်။",
                        "Old means the digit appeared in an earlier draw of this same week.")
                        .display(en),fontSize=11.sp,lineHeight=16.sp,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick=onRealDetail,
                    modifier=Modifier.align(Alignment.End).heightIn(min=40.dp)) {
                    Text(words("စစ်ဆေးမှုအသေးစိတ် ကြည့်ရန်",
                        "Open analysis details").display(en),
                        fontSize=11.sp,color=ReportWine)
                }
            }
        }
        HorizontalDivider(color=ReportLine)
    }
}

/** Text stays on the left; answer and frequency form ONE visual reading unit. */
@Composable
private fun DemoAlignedLine(item: DemoReportLine, en: Boolean, main: Boolean) {
    Row(Modifier.fillMaxWidth().padding(
            top=if(main)0.dp else 4.dp,bottom=if(main)5.dp else 3.dp),
        horizontalArrangement=Arrangement.spacedBy(8.dp),
        verticalAlignment=Alignment.Top) {
        Text(item.question.display(en),
            fontSize=if(main)12.sp else 11.sp,
            lineHeight=if(main)18.sp else 17.sp,
            color=MaterialTheme.colorScheme.onSurface,
            modifier=Modifier.weight(.57f))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight=FontWeight.Bold,
                    color=ReportDataInk,fontSize=DemoResultSize)) {
                    append(item.value.display(en))
                }
                append("  ")
                withStyle(SpanStyle(fontWeight=FontWeight.SemiBold,
                    color=MaterialTheme.colorScheme.onSurface,
                    fontSize=11.sp)) {
                    append(item.ending.display(en))
                }
            },
            modifier=Modifier.weight(.43f),
            textAlign=TextAlign.Start,
            lineHeight=20.sp
        )
    }
}
