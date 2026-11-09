package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession

/** Reuse the approved wording; keep the engine's internal "ညီကို" group key. */
internal fun twoDGroupLabel(name: String, en: Boolean): String = if (!en) {
    if (name == "ညီကို") "ညီအကို" else name
} else when (name) {
    "အပူး" -> "Doubles"
    "ညီကို" -> "Sibling pairs"
    "ပါဝါ" -> "Power"
    "နက္ခတ်" -> "Astrology"
    "ပြန်ကျ" -> "Exact repeats"
    else -> name
}

internal fun twoDParityLabel(name: String, en: Boolean): String = if (!en) name else when (name) {
    "စုံစုံ" -> "Even–Even"
    "မမ" -> "Odd–Odd"
    "စုံမ" -> "Even–Odd"
    "မစုံ" -> "Odd–Even"
    else -> name
}

/** Data-only selection rules, independently testable without Compose. */
internal object TwoDDetailSelection {
    fun leaders(counts: List<TwoDCount>): List<TwoDCount> {
        val max = counts.maxOfOrNull { it.count } ?: 0
        return if (max == 0) emptyList()
        else counts.filter { it.count == max }.sortedBy { it.digit }
    }
    fun streams(data: Map<String, TwoDStreakStream>): List<String> =
        listOf("morning", "evening", "combined").filter {
            (data[it]?.observedDraws ?: 0) > 0
        }
    fun conditions(data: TwoDPreviousResults): List<Pair<String, TwoDConditional>> =
        listOf("both" to data.both, "first" to data.firstOnly,
            "second" to data.secondOnly).filter { it.second.matches.isNotEmpty() }
    fun longest(data: List<TwoDStreak>): List<TwoDStreak> {
        val max = data.maxOfOrNull { it.observedDrawsSince } ?: 0
        return data.filter { it.observedDrawsSince == max }.sortedBy { it.value }
    }

    /** Stable per-row remaining digits; no mutable state is captured by a composable lambda. */
    fun remainingAfter(transitions: List<TwoDTransition>): List<String> {
        val seen = mutableSetOf<Char>()
        return transitions.map { row ->
            seen.addAll(row.result.digit.toList())
            ('0'..'9').filterNot { it in seen }.joinToString(" ")
        }
    }
}
private fun tx(en: Boolean, mm: String, eng: String) = if (en) eng else mm
private fun count(en: Boolean, amount: Int) = amount.toString() + tx(en, " ကြိမ်", " times")
private fun slot(en: Boolean, session: DrawSession) =
    if (session == DrawSession.MORNING) tx(en, "မနက်အကြိမ်", "Morning")
    else tx(en, "ညနေအကြိမ်", "Evening")
private fun evidence(en: Boolean, item: TwoDMatch) =
    item.date.toString() + " · " + slot(en, item.session) + " · " + item.digit

@Composable
private fun DT(value: String, en: Boolean, role: LedgerTextRole = LedgerTextRole.BODY,
    modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = Int.MAX_VALUE) {
    Text(value, modifier, color = color, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = LedgerTypographyStandard.style(role, if (en) "en" else "my"))
}

@Composable
private fun DP(title: String, en: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = c.surface, tonalElevation = 1.dp, shadowElevation = 3.dp,
        border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = .28f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            DT(title, en, LedgerTextRole.CARD_TITLE)
            content()
        }
    }
}

@Composable
private fun DH(title: String, answer: String, en: Boolean, detail: String? = null) {
    val c = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp),
        shadowElevation = 4.dp, tonalElevation = 1.dp, color = c.surface,
        border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = .26f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            DT(title, en, LedgerTextRole.HELPER, Modifier.fillMaxWidth(),
                c.onSurfaceVariant)
            TwoDNumberFocus(answer, en, Modifier.fillMaxWidth())
            if (!detail.isNullOrEmpty()) DT(detail, en, LedgerTextRole.TABLE,
                color = c.onSurfaceVariant)
        }
    }
}

@Composable
private fun DL(title: String, answer: String, en: Boolean) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        DT(title, en, LedgerTextRole.HELPER, Modifier.weight(1f), c.onSurfaceVariant)
        DT(answer, en, LedgerTextRole.BODY, Modifier.weight(1.2f))
    }
    HorizontalDivider(color = c.outlineVariant.copy(alpha = .28f))
}

@Composable
private fun DB(label: String, en: Boolean, action: () -> Unit) {
    TextButton(onClick = action, contentPadding = PaddingValues(horizontal = 4.dp)) {
        DT(label, en, LedgerTextRole.BUTTON, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DE(en: Boolean, special: String? = null) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .42f)) {
        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            DT(special ?: tx(en, "အချက်အလက် မရရှိနိုင်သေးပါ",
                "Information not yet available"), en,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DFrequency(title: String, data: List<TwoDCount>, en: Boolean) {
    val counts = data.associate { it.digit to it.count }
    DP(title, en) {
        (0..9).map { it.toString() }.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { digit ->
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .40f)) {
                        Column(Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            DT(digit, en, LedgerTextRole.CARD_TITLE)
                            DT((counts[digit] ?: 0).toString(), en, LedgerTextRole.TABLE,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DGroups(title: String, data: Map<String, Int>, en: Boolean,
    parity: Boolean = false, open: ((String) -> Unit)? = null) {
    if (data.isEmpty()) return
    DP(title, en) {
        data.forEach { (key, amount) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                DT(if (parity) twoDParityLabel(key, en) else twoDGroupLabel(key, en),
                    en, modifier = Modifier.weight(1f))
                DT(count(en, amount), en, LedgerTextRole.TABLE,
                    color = MaterialTheme.colorScheme.primary)
                if (amount > 0 && open != null) DB(tx(en, "ကြည့်ရန်", "View"), en) {
                    open(key)
                }
            }
        }
    }
}


@Composable
private fun DCompactHighlight(
    title: String, value: String, foot: String, en: Boolean, modifier: Modifier = Modifier,
) {
    TwoDResultFrame(title, value, foot, en, modifier)
}

/** Each row in a history dialog names the source reference and the actual next result. */
private fun chainText(en: Boolean, row: TwoDReferenceChain): String {
    val references = listOfNotNull(
        row.precedingFirst?.let {
            tx(en, "ပထမ", "First") + " " + evidence(en, it)
        },
        row.precedingSecond?.let {
            tx(en, "ဒုတိယ", "Second") + " " + evidence(en, it)
        },
    )
    return references.joinToString("\n") + "\n" +
        tx(en, "ဆက်ထွက် → ", "Following → ") + evidence(en, row.following)
}


@Composable
private fun DNumberLine(number: String, countText: String, en: Boolean,
    action: (() -> Unit)? = null,
) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        TwoDNumberFocus(number, en, compact = true)
        Spacer(Modifier.weight(1f))
        DT(countText, en, LedgerTextRole.TABLE)
        if (action != null) DB(tx(en, "မှတ်တမ်း", "History"), en, action)
    }
    HorizontalDivider(color = c.outlineVariant.copy(alpha = .25f))
}

@Composable
private fun DTimelineLine(
    context: String, number: String, description: String, en: Boolean,
) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TwoDNumberFocus(number, en, compact = true)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            DT(context, en, LedgerTextRole.HELPER, color = c.onSurfaceVariant)
            if (description.isNotBlank()) DT(description, en, LedgerTextRole.BODY)
        }
    }
    HorizontalDivider(color = c.outlineVariant.copy(alpha = .23f))
}

@Composable
internal fun TwoDAnalysisDetails(
    id: String, report: TwoDAnalysisReport, en: Boolean,
    selectedStreakStream: String? = null,
    onStreakStreamChange: ((String) -> Unit)? = null,
) {
    var dialog by remember(id, report.target) { mutableStateOf<String?>(null) }
    var rows by remember(id, report.target) { mutableStateOf(emptyList<String>()) }
    fun show(title: String, evidenceRows: List<String>) {
        if (evidenceRows.isNotEmpty()) {
            dialog = title
            rows = evidenceRows
        }
    }

    when (id) {
        "03" -> {
            val a = report.a03
            if (!a.eligible) DE(en)
            else {
                if (a.matched.isNotEmpty()) {
                    val max = a.nextParity.values.maxOrNull() ?: 0
                    DH(tx(en, "အများဆုံး ဆက်ထွက်", "Most observed following parity"),
                        a.nextParity.filterValues { it == max }.keys.joinToString(" · ") {
                            twoDParityLabel(it, en)
                        }, en, count(en, max))
                }
                DP(tx(en, "ယခုအပတ် စုံ/မ အစဉ်", "This week's parity sequence"), en) {
                    a.currentPreceding.forEach { row ->
                        DL(evidence(en, row),
                            twoDParityLabel(TwoDigitGroups.parity(row.digit), en), en)
                    }
                }
                if (a.matched.isEmpty()) DE(en,
                    tx(en, "တူညီသောမှတ်တမ်း မတွေ့ပါ", "No verified historical match"))
                else {
                    DGroups(tx(en, "နောက်ထွက် စုံ/မ", "Following parity"),
                        a.nextParity.filterValues { it > 0 }, en, parity = true)
                    DP(tx(en, "ပုံစံတူ အပတ်များ", "Matching historical weeks"), en) {
                        a.matchingWeeks.take(5).forEach { week ->
                            DL(week.weekStart.toString(),
                                week.following.digit + " · " +
                                    twoDParityLabel(TwoDigitGroups.parity(week.following.digit), en),
                                en)
                        }
                        DB(tx(en, "ပုံစံတူအပတ်များကို ယှဉ်ကြည့်ရန်",
                            "Compare complete weekly patterns"), en) {
                            show(tx(en, "ပုံစံတူအပတ်များ", "Matching patterns"),
                                a.matchingWeeks.flatMap { week ->
                                    listOf(tx(en, "အပတ်စတင်", "Week of") + " " + week.weekStart) +
                                    week.preceding.map {
                                        evidence(en, it) + " · " +
                                            twoDParityLabel(TwoDigitGroups.parity(it.digit), en)
                                    } + listOf(tx(en, "နောက်ထွက် → ", "Following → ") +
                                        evidence(en, week.following))
                                })
                        }
                    }
                }
            }
        }
        "06" -> {
            val available = TwoDDetailSelection.streams(report.a06)
            if (available.isEmpty()) DE(en)
            else {
                var local by remember(report.target) {
                    mutableStateOf(if (report.target.session == DrawSession.MORNING)
                        "morning" else "evening")
                }
                val chosen = selectedStreakStream ?: local
                val stream = if (chosen in available) chosen else available.first()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    available.forEach { key ->
                        FilterChip(modifier = Modifier.weight(1f),
                            selected = stream == key,
                            onClick = {
                                if (onStreakStreamChange == null) local = key
                                else onStreakStreamChange(key)
                            }, label = { DT(when (key) {
                                "morning" -> "AM"
                                "evening" -> "PM"
                                else -> "ALL"
                            }, en, LedgerTextRole.BUTTON) })
                    }
                }
                val a = requireNotNull(report.a06[stream])
                val single = TwoDDetailSelection.longest(a.singleDigits)
                val pairs = TwoDDetailSelection.longest(a.fullNumbers)
                DP(when (stream) {
                    "morning" -> tx(en, "မနက်အကြိမ်", "Morning")
                    "evening" -> tx(en, "ညနေအကြိမ်", "Evening")
                    else -> tx(en, "မနက်/ညနေ ပေါင်း", "Combined AM/PM")
                }, en) {
                    DL(tx(en, "မှတ်တမ်းရှိသောအကြိမ်", "Recorded draws"),
                        count(en, a.observedDraws), en)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (single.isNotEmpty()) DCompactHighlight(
                        tx(en, "ပျောက်ဂဏန်း", "Missing digit"), single.first().value,
                        count(en, single.first().observedDrawsSince) +
                            (if (single.size > 1) " · " + single.size +
                                tx(en, " မျိုးတူ", " tied") else ""),
                        en, Modifier.weight(1f))
                    if (pairs.isNotEmpty()) DCompactHighlight(
                        tx(en, "ပျောက်ဂဏန်းတွဲ", "Missing pair"), pairs.first().value,
                        count(en, pairs.first().observedDrawsSince) +
                            (if (pairs.size > 1) " · " + pairs.size +
                                tx(en, " မျိုးတူ", " tied") else ""),
                        en, Modifier.weight(1f))
                }
                DP(tx(en, "ပျောက်ဂဏန်းများ (0–9)", "Missing digits (0–9)"), en) {
                    a.singleDigits.take(5).forEach {
                        DNumberLine(it.value, count(en, it.observedDrawsSince), en)
                    }
                    DB(tx(en, "ဂဏန်းအားလုံးနှင့် နောက်ဆုံးထွက်ရက်",
                        "All digits and last seen dates"), en) {
                        show(tx(en, "ပျောက်ဂဏန်းများ", "Digit absence"),
                            a.singleDigits.map { item ->
                                item.value + " · " + count(en, item.observedDrawsSince) + "\n" +
                                    (item.lastSeen?.let { evidence(en, it) } ?: tx(en,
                                        "ရွေးထားသောမှတ်တမ်းတွင် မတွေ့", "Unseen in selected records"))
                            })
                    }
                }
                DP(tx(en, "ပျောက်ဂဏန်းတွဲများ (00–99)", "Missing pairs (00–99)"), en) {
                    a.fullNumbers.take(5).forEach {
                        DNumberLine(it.value, count(en, it.observedDrawsSince), en)
                    }
                    DB(tx(en, "ဂဏန်းတွဲအားလုံးနှင့် နောက်ဆုံးထွက်ရက်",
                        "All pairs and last seen dates"), en) {
                        show(tx(en, "ပျောက်ဂဏန်းတွဲများ", "Pair absence"),
                            a.fullNumbers.map { item ->
                                item.value + " · " + count(en, item.observedDrawsSince) + "\n" +
                                    (item.lastSeen?.let { evidence(en, it) } ?: tx(en,
                                        "ရွေးထားသောမှတ်တမ်းတွင် မတွေ့", "Unseen in selected records"))
                            })
                    }
                }
            }
        }
        "08" -> {
            val a = report.a08
            val parts = TwoDDetailSelection.conditions(a)
            if (a.firstReference == null && a.secondReference == null && parts.isEmpty()) DE(en)
            else {
                DP(tx(en, "ရှေ့ထွက်ဂဏန်းများ", "Previous results"), en) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        a.firstReference?.let { row ->
                            TwoDResultFrame(tx(en, "ပထမ", "First"), row.digit,
                                row.date.toString() + " · " + slot(en, row.session),
                                en, Modifier.weight(1f))
                        }
                        a.secondReference?.let { row ->
                            TwoDResultFrame(tx(en, "ဒုတိယ", "Second"), row.digit,
                                row.date.toString() + " · " + slot(en, row.session),
                                en, Modifier.weight(1f))
                        }
                    }
                }
                parts.forEach { (key, result) ->
                    val refs = when (key) {
                        "both" -> listOfNotNull(
                            a.firstReference?.digit, a.secondReference?.digit).joinToString(" + ")
                        "first" -> a.firstReference?.digit.orEmpty()
                        else -> a.secondReference?.digit.orEmpty()
                    }
                    val label = when (key) {
                        "both" -> tx(en, "နှစ်ခုတွဲ", "Both references")
                        "first" -> tx(en, "ပထမတစ်ခု", "First reference")
                        else -> tx(en, "ဒုတိယတစ်ခု", "Second reference")
                    }
                    val top = TwoDDetailSelection.leaders(result.frequencies)
                    if (top.isNotEmpty()) {
                        DP(label + " · " + refs, en) {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TwoDResultFrame(tx(en, "အများဆုံး ဆက်ထွက်", "Top following 2D"),
                                    top.take(3).joinToString(" · ") { it.digit },
                                    count(en, top.first().count), en, Modifier.weight(1f))
                                TwoDResultFrame(tx(en, "တွေ့ရှိသောမှတ်တမ်း", "Matching records"),
                                    result.matches.size.toString(),
                                    tx(en, "သမိုင်းမှတ်တမ်း", "Historical outcomes"),
                                    en, Modifier.weight(1f))
                            }
                            DT(tx(en, "ဆက်ထွက်ခဲ့သော ဂဏန်းတွဲများ",
                                "Following 2D results"), en, LedgerTextRole.CARD_TITLE)
                            result.frequencies.forEach { item ->
                                DNumberLine(item.digit, count(en, item.count), en) {
                                    show(refs + " → " + item.digit,
                                        result.referenceChains.filter {
                                            it.following.digit == item.digit
                                        }.map { chainText(en, it) })
                                }
                            }
                            DB(tx(en, "History အားလုံး ကြည့်ရန်",
                                "View full source-to-result history"), en) {
                                show(label + " · " + refs,
                                    result.referenceChains.map { chainText(en, it) })
                            }
                        }
                    }
                }
            }
        }
        "05" -> {
            val a = report.a05
            if (a.transitions.isEmpty()) DE(en)
            else {
                DH(tx(en, "ကျန်ဂဏန်း (0–9)", "Unseen digits (0–9)"),
                    a.remainingDigits.joinToString(" ").ifEmpty {
                        tx(en, "ပျောက်ဂဏန်းမရှိ", "No missing digits")
                    }, en)
                val remainingRows = TwoDDetailSelection.remainingAfter(a.transitions)
                DP(tx(en, "အသစ်/အဟောင်း ဂဏန်းစီးဆင်းမှု",
                    "New / old digit flow"), en) {
                    a.transitions.forEachIndexed { index, item ->
                        val newly = item.newlySeenDigits.sorted().joinToString(" ")
                        val older = (item.result.digit.toSet() - item.newlySeenDigits)
                            .sorted().joinToString(" ")
                        val words = listOf(
                            if (newly.isNotBlank()) tx(en, "အသစ်", "New") + ": " + newly else "",
                            if (older.isNotBlank()) tx(en, "အဟောင်း", "Old") + ": " + older else "",
                        ).filter { it.isNotEmpty() }.joinToString(" · ")
                        DT(evidence(en, item.result), en, LedgerTextRole.HELPER)
                        DT(words, en, LedgerTextRole.BODY)
                        DT(tx(en, "ကျန်ဂဏန်း", "Remaining") + ": " +
                            remainingRows[index].ifEmpty {
                                tx(en, "မရှိ", "None")
                            } + (if (!item.classificationVerified)
                                " · " + tx(en, "ရရှိသောမှတ်တမ်းအရ", "Available records only")
                                else ""), en, LedgerTextRole.TABLE,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider()
                    }
                }
            }
        }
        "07" -> {
            val a = report.a07
            if (a.observedDraws == 0) DE(en)
            else {
                val counts = a.groups.toList() +
                    listOf("ပြန်ကျ" to a.repeatedNumbers.sumOf { it.count })
                DP(tx(en, "အထူးဂဏန်းအုပ်စုများ", "Special groups"), en) {
                    counts.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            pair.forEach { (key, total) ->
                                TwoDResultFrame(twoDGroupLabel(key, en),
                                    total.toString(), tx(en, "အကြိမ်ရေ", "Occurrences"),
                                    en, Modifier.weight(1f),
                                    onClick = if (total == 0) null else ({
                                        val results = if (key == "ပြန်ကျ")
                                            a.repeatedOccurrences.flatMap { (number, matches) ->
                                                matches.mapIndexed { index, row ->
                                                    number + " · " + evidence(en, row) + " · " +
                                                        (if (index == 0)
                                                            tx(en, "ပထမထွက်", "First")
                                                         else tx(en, "ထပ်ထွက်", "Repeated"))
                                                }
                                            }
                                        else a.groupOccurrences[key].orEmpty().map {
                                            evidence(en, it)
                                        }
                                        show(twoDGroupLabel(key, en), results)
                                    }))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    DL(tx(en, "စစ်ထားသောအကြိမ်", "Recorded draws"),
                        count(en, a.observedDraws), en)
                }
                DP(tx(en, "အုပ်စုအလိုက် ထွက်ဂဏန်းတွဲ", "Observed pairs by group"), en) {
                    counts.filter { it.second > 0 }.forEach { (key, total) ->
                        val source = if (key == "ပြန်ကျ")
                            a.repeatedOccurrences.values.flatten()
                        else a.groupOccurrences[key].orEmpty()
                        val numbers = source.groupingBy { it.digit }.eachCount().toList()
                            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }
                                .thenBy { it.first })
                        DT(twoDGroupLabel(key, en) + " · " + count(en, total), en,
                            LedgerTextRole.CARD_TITLE)
                        numbers.take(5).forEach { (number, occurrences) ->
                            DNumberLine(number, count(en,
                                if (key == "ပြန်ကျ") (occurrences - 1).coerceAtLeast(0)
                                else occurrences), en) {
                                val history = if (key == "ပြန်ကျ")
                                    a.repeatedOccurrences[number].orEmpty()
                                else a.groupOccurrences[key].orEmpty().filter {
                                    it.digit == number
                                }
                                show(twoDGroupLabel(key, en) + " · " + number,
                                    history.mapIndexed { index, it ->
                                        evidence(en, it) + (if (key == "ပြန်ကျ")
                                            " · " + if (index == 0)
                                                tx(en, "ပထမထွက်", "First") else
                                                tx(en, "ထပ်ထွက်", "Repeated")
                                        else "")
                                    })
                            }
                        }
                        if (numbers.size > 5) DB(tx(en, "အားလုံးကြည့်ရန်", "View all"), en) {
                            show(twoDGroupLabel(key, en),
                                source.map { evidence(en, it) })
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
        "04" -> {
            val a = report.a04
            if (a.observedDraws == 0) DE(en)
            else {
                DP(tx(en, "ပြီးခဲ့တဲ့အပတ်", "Previous week"), en) {
                    DT(a.weekStart.toString() + " – " + a.weekStart.plusDays(4), en,
                        LedgerTextRole.BODY)
                    DL(tx(en, "စစ်ထားသောအကြိမ်", "Recorded draws"),
                        count(en, a.observedDraws), en)
                }
                val top = TwoDDetailSelection.leaders(a.singleDigitFrequency)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (top.isNotEmpty()) DCompactHighlight(
                        tx(en, "အထွက်အများဆုံး", "Top digits"),
                        top.take(4).joinToString(" · ") { it.digit },
                        count(en, top.first().count), en, Modifier.weight(1f))
                    DCompactHighlight(tx(en, "ပျောက်ဂဏန်း", "Unseen digits"),
                        a.remainingDigits.joinToString(" ").ifEmpty { tx(en, "မရှိ", "None") },
                        tx(en, "မှတ်တမ်းအရ", "Recorded results"), en, Modifier.weight(1f))
                }
                DFrequency(tx(en, "ဂဏန်း 0–9 အကြိမ်ရေ", "Digit frequency (0–9)"),
                    a.singleDigitFrequency, en)
                DP(tx(en, "ဂဏန်းတစ်လုံးချင်း History", "Digit history"), en) {
                    a.singleDigitFrequency.filter { it.count > 0 }.forEach { item ->
                        DNumberLine(item.digit, count(en, item.count), en) {
                            show(item.digit + " · " + tx(en, "ဂဏန်းထွက်ရှိမှု", "Digit history"),
                                a.recordedResults.filter { item.digit[0] in it.digit }
                                    .map { evidence(en, it) + " · " + item.digit + " ×" +
                                        it.digit.count { d -> d == item.digit[0] } })
                        }
                    }
                }
                DGroups(tx(en, "စုံ/မ ထွက်ရှိမှု", "Parity counts"),
                    a.parity, en, parity = true, open = { key ->
                        show(twoDParityLabel(key, en), a.recordedResults.filter {
                            TwoDigitGroups.parity(it.digit) == key
                        }.map { evidence(en, it) })
                    })
                DGroups(tx(en, "အထူးဂဏန်းအုပ်စုများ", "Special groups"),
                    a.groups, en, open = { key ->
                        show(twoDGroupLabel(key, en), a.recordedResults.filter {
                            it.digit in TwoDigitGroups.named[key].orEmpty()
                        }.map { evidence(en, it) })
                    })
                if (a.repeatedNumbers.isNotEmpty())
                    DP(tx(en, "ပြန်ကျ", "Exact repeats"), en) {
                        a.repeatedNumbers.forEach { item ->
                            DNumberLine(item.digit, count(en, item.count), en) {
                                show(tx(en, "ပြန်ကျ", "Exact repeats") + " · " + item.digit,
                                    a.recordedResults.filter { it.digit == item.digit }
                                        .map { evidence(en, it) })
                            }
                        }
                    }
            }
        }
        "01" -> {
            val a = report.a01
            if (a.missingDigits.isEmpty()) {
                DE(en, if (report.a04.observedDraws > 0)
                    tx(en, "ပျောက်ဂဏန်းမရှိ", "No unseen digits") else null)
            } else {
                DH(tx(en, "ပြီးခဲ့တဲ့အပတ် ပျောက်ဂဏန်း",
                    "Last week's unseen digits"), a.missingDigits.joinToString(" "), en)
                a.results.filter { item ->
                    item.thisWeekFirstSeen != null || item.commonCompanions.isNotEmpty() ||
                        item.historicalEpisodes.isNotEmpty()
                }.forEach { item ->
                    DP(tx(en, "ပျောက်ဂဏန်း", "Missing digit") + " " + item.digit, en) {
                        if (item.commonCompanions.isNotEmpty())
                            DL(tx(en, "အများဆုံးတွဲထွက်", "Most common companion"),
                                item.commonCompanions.joinToString(" · ") {
                                    it.companion.toString() + " ×" + it.count
                                }, en)
                        item.thisWeekFirstSeen?.let {
                            DL(tx(en, "ဒီအပတ် ပြန်ဆင်း", "Observed return this week"),
                                evidence(en, it), en)
                        }
                        if (item.historyFirstReturnEpisodes > 0)
                            DL(tx(en, "ယခင် ပြန်ဆင်းခဲ့သောအပတ်", "Historical return weeks"),
                                item.historyFirstReturnEpisodes.toString(), en)
                        if (item.historicalEpisodes.isNotEmpty())
                            DB(tx(en, "ပျောက်ခဲ့သောအပတ်နှင့် ပြန်ဆင်းမှု",
                                "Missing-week to return history"), en) {
                                show(item.digit.toString() + " · " +
                                    tx(en, "ပြန်ဆင်းမှု", "Return history"),
                                    item.historicalEpisodes.map { episode ->
                                        tx(en, "ပျောက်ခဲ့သောအပတ်", "Missing week") +
                                            " · " + episode.missingWeekStart + "\n" +
                                        tx(en, "နောက်အပတ် ပြန်ဆင်း → ", "Following-week return → ") +
                                            evidence(en, episode.followingWeekFirstReturn)
                                    })
                            }
                    }
                }
            }
        }
    }
    if (dialog != null) AlertDialog(
        onDismissRequest = { dialog = null },
        title = { DT(dialog.orEmpty(), en, LedgerTextRole.CARD_TITLE) },
        text = {
            LazyColumn(Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows.size) { index ->
                    DT(rows[index], en)
                    if (index < rows.lastIndex) HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .28f))
                }
            }
        },
        confirmButton = { TextButton(onClick = { dialog = null }) {
            DT(tx(en, "ပိတ်မည်", "Close"), en, LedgerTextRole.BUTTON)
        } },
    )
}
