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
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        shadowElevation = 7.dp, tonalElevation = 3.dp, color = c.primaryContainer) {
        Column(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(
            c.primaryContainer, c.secondaryContainer.copy(alpha = .85f))))
            .padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            DT(title, en, LedgerTextRole.HELPER, color = c.onPrimaryContainer)
            DT(answer, en, LedgerTextRole.MONEY, color = c.onPrimaryContainer, maxLines = 3)
            if (!detail.isNullOrEmpty()) DT(detail, en, LedgerTextRole.HELPER,
                color = c.onPrimaryContainer.copy(alpha = .80f))
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
            else if (a.matched.isEmpty()) DE(en,
                tx(en, "တူညီသောမှတ်တမ်း မတွေ့ပါ", "No verified historical match"))
            else {
                val most = a.nextParity.values.maxOrNull() ?: 0
                DH(tx(en, "အများဆုံး ဆက်ထွက်", "Most observed next class"),
                    a.nextParity.filterValues { it == most }.keys.joinToString(" · ") {
                        twoDParityLabel(it, en)
                    }, en, count(en, most))
                DP(tx(en, "ယခုအပတ် စုံ/မ အစဉ်", "This week's parity sequence"), en) {
                    DT(a.checkedPrefix.joinToString(" → ") { twoDParityLabel(it, en) }, en)
                }
                DGroups(tx(en, "နောက်ထွက် စုံ/မ", "Following parity counts"),
                    a.nextParity.filterValues { it > 0 }, en, parity = true)
                DP(tx(en, "တူညီသောမှတ်တမ်း", "Matching records"), en) {
                    DL(tx(en, "တူညီသောအပတ်", "Matching weeks"),
                        count(en, a.matched.size), en)
                    a.matched.take(5).forEach {
                        DL(tx(en, "အပြီးထွက်", "Next result"), evidence(en, it), en)
                    }
                    if (a.matched.size > 5) DB(tx(en, "အားလုံးကြည့်ရန်", "View all"), en) {
                        show(tx(en, "တူညီသောမှတ်တမ်း", "Matching records"),
                            a.matched.map { evidence(en, it) })
                    }
                }
            }
        }
        "06" -> {
            val streams = TwoDDetailSelection.streams(report.a06)
            if (streams.isEmpty()) DE(en)
            else {
                var local by remember(report.target) {
                    mutableStateOf(if (report.target.session == DrawSession.MORNING)
                        "morning" else "evening")
                }
                val selected = selectedStreakStream ?: local
                val stream = if (selected in streams) selected else streams.first()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    streams.forEach { key ->
                        val label = when (key) {
                            "morning" -> tx(en, "မနက်အကြိမ်", "Morning")
                            "evening" -> tx(en, "ညနေအကြိမ်", "Evening")
                            else -> tx(en, "နှစ်ကြိမ်ပေါင်း", "Combined")
                        }
                        FilterChip(selected = stream == key,
                            onClick = {
                                if (onStreakStreamChange == null) local = key
                                else onStreakStreamChange(key)
                            }, label = { DT(label, en, LedgerTextRole.HELPER) })
                    }
                }
                val a = requireNotNull(report.a06[stream])
                val single = TwoDDetailSelection.longest(a.singleDigits)
                val pairs = TwoDDetailSelection.longest(a.fullNumbers)
                fun topDisplay(values: List<TwoDStreak>): String =
                    values.take(4).joinToString(" · ") { it.value } +
                        if (values.size > 4)
                            " (" + values.size + tx(en, " မျိုးတူ", " tied") + ")"
                        else ""
                if (single.isNotEmpty()) DH(tx(en, "ပျောက်ဂဏန်း (0–9)", "Missing digits (0–9)"),
                    topDisplay(single), en, count(en, single.first().observedDrawsSince))
                if (pairs.isNotEmpty()) DH(tx(en, "ပျောက်ဂဏန်းတွဲ (00–99)", "Missing pairs (00–99)"),
                    topDisplay(pairs), en, count(en, pairs.first().observedDrawsSince))
                DP(tx(en, "ပျောက်ဂဏန်းများ", "Single-digit absence"), en) {
                    a.singleDigits.forEach {
                        DL(it.value, count(en, it.observedDrawsSince) +
                            (it.lastSeen?.let { seen -> " · " + evidence(en, seen) } ?: ""), en)
                    }
                }
                DP(tx(en, "ပျောက်ဂဏန်းတွဲများ", "Two-digit absence"), en) {
                    a.fullNumbers.take(8).forEach {
                        DL(it.value, count(en, it.observedDrawsSince) +
                            (it.lastSeen?.let { seen -> " · " + evidence(en, seen) } ?: ""), en)
                    }
                    DB(tx(en, "ဂဏန်းတွဲအားလုံး ကြည့်ရန်", "View all pairs"), en) {
                        show(tx(en, "ပျောက်ဂဏန်းတွဲများ", "Two-digit absence"),
                            a.fullNumbers.map {
                                it.value + " · " + count(en, it.observedDrawsSince) +
                                    (it.lastSeen?.let { seen -> " · " + evidence(en, seen) } ?: "")
                            })
                    }
                }
            }
        }
        "08" -> {
            val a = report.a08
            val matches = TwoDDetailSelection.conditions(a)
            if (a.firstReference == null && a.secondReference == null && matches.isEmpty()) DE(en)
            else {
                if (a.firstReference != null || a.secondReference != null)
                    DP(tx(en, "ရှေ့ထွက်ဂဏန်းများ", "Previous results"), en) {
                        a.firstReference?.let {
                            DL(tx(en, "ပထမ", "First"), evidence(en, it), en)
                        }
                        a.secondReference?.let {
                            DL(tx(en, "ဒုတိယ", "Second"), evidence(en, it), en)
                        }
                    }
                matches.forEach { (key, result) ->
                    val label = when (key) {
                        "both" -> tx(en, "နှစ်ခုတွဲ တိုက်စစ်", "Both references")
                        "first" -> tx(en, "ပထမတစ်ခု တိုက်စစ်", "First reference")
                        else -> tx(en, "ဒုတိယတစ်ခု တိုက်စစ်", "Second reference")
                    }
                    val top = TwoDDetailSelection.leaders(result.frequencies)
                    if (top.isNotEmpty()) {
                        DH(label, top.take(4).joinToString(" · ") { it.digit } +
                            (if (top.size > 4) " (" + top.size +
                                tx(en, " မျိုးတူ", " tied") + ")" else ""),
                            en, count(en, top.first().count))
                        DP(tx(en, "ဆက်ထွက်ခဲ့သော ဂဏန်းတွဲများ",
                            "Observed following pairs"), en) {
                            DL(tx(en, "တူညီသောမှတ်တမ်း", "Matching records"),
                                count(en, result.matches.size), en)
                            result.frequencies.take(6).forEach {
                                DL(it.digit, count(en, it.count), en)
                            }
                            DB(tx(en, "ထွက်ခဲ့သောရက်များ", "View occurrence dates"), en) {
                                show(label, result.matches.map { evidence(en, it) })
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
                fun flow(it: TwoDTransition): String =
                    if (it.result.digit[0] == it.result.digit[1]) {
                        it.result.digit[0].toString() + " " +
                            if (it.newlySeenDigits.isNotEmpty()) tx(en, "အသစ်", "New")
                            else tx(en, "အဟောင်း", "Old")
                    } else {
                        tx(en, if (it.newTens) "အသစ်" else "အဟောင်း",
                            if (it.newTens) "New" else "Old") + " + " +
                        tx(en, if (it.newOnes) "အသစ်" else "အဟောင်း",
                            if (it.newOnes) "New" else "Old")
                    }
                DH(tx(en, "ကျန်ဂဏန်း (0–9)", "Unseen digits (0–9)"),
                    a.remainingDigits.joinToString(" ").ifEmpty {
                        tx(en, "ပျောက်ဂဏန်းမရှိ", "No missing digits")
                    }, en)
                DP(tx(en, "နောက်ဆုံးအကြိမ်", "Latest draw"), en) {
                    DL(evidence(en, a.transitions.last().result),
                        flow(a.transitions.last()), en)
                }
                DP(tx(en, "အသစ်/အဟောင်း ဂဏန်းစီးဆင်းမှု", "New / old digit flow"), en) {
                    a.transitions.forEach {
                        DL(evidence(en, it.result), flow(it), en)
                    }
                }
            }
        }
        "07" -> {
            val a = report.a07
            if (a.observedDraws == 0) DE(en)
            else {
                DH(tx(en, "ဒီအပတ် ထွက်ဂဏန်း", "This week's draws"),
                    count(en, a.observedDraws), en)
                DGroups(tx(en, "အထူးအုပ်စုများ", "Special groups"),
                    a.groups + ("ပြန်ကျ" to a.repeatedNumbers.sumOf { it.count }), en,
                    open = { key ->
                        val history = if (key == "ပြန်ကျ")
                            a.repeatedNumbers.map { it.digit + " · " + count(en, it.count) }
                        else a.groupOccurrences[key].orEmpty().map { evidence(en, it) }
                        show(twoDGroupLabel(key, en), history)
                    })
            }
        }
        "04" -> {
            val a = report.a04
            if (a.observedDraws == 0) DE(en)
            else {
                val top = TwoDDetailSelection.leaders(a.singleDigitFrequency)
                if (top.isNotEmpty()) DH(tx(en, "အထွက်အများဆုံးဂဏန်း",
                    "Most observed digit"), top.joinToString(" · ") { it.digit },
                    en, count(en, top.first().count))
                DP(tx(en, "ပြီးခဲ့တဲ့အပတ်", "Previous week"), en) {
                    DL(tx(en, "အပတ်စတင်ရက်", "Week started"), a.weekStart.toString(), en)
                    DL(tx(en, "ထွက်ဂဏန်းမှတ်တမ်း", "Recorded draws"),
                        count(en, a.observedDraws), en)
                    DL(tx(en, "ပျောက်ဂဏန်း", "Unseen digits"),
                        a.remainingDigits.joinToString(" ").ifEmpty {
                            tx(en, "ပျောက်ဂဏန်းမရှိ", "No missing digits")
                        }, en)
                }
                DFrequency(tx(en, "ဂဏန်း 0–9 အကြိမ်ရေ", "Digit frequency (0–9)"),
                    a.singleDigitFrequency, en)
                DGroups(tx(en, "စုံ/မ ထွက်ရှိမှု", "Parity counts"), a.parity, en, parity = true)
                DGroups(tx(en, "အထူးအုပ်စုများ", "Special groups"), a.groups, en)
                if (a.repeatedNumbers.isNotEmpty()) DP(tx(en, "ပြန်ကျ", "Exact repeats"), en) {
                    a.repeatedNumbers.forEach { DL(it.digit, count(en, it.count), en) }
                }
            }
        }
        "01" -> {
            val a = report.a01
            if (a.missingDigits.isEmpty()) {
                if (a.previousWeekComplete) DE(en,
                    tx(en, "ပျောက်ဂဏန်းမရှိ", "No missing digits")) else DE(en)
            } else {
                DH(tx(en, "ပြီးခဲ့တဲ့အပတ် ပျောက်ဂဏန်း", "Previous week's missing digits"),
                    a.missingDigits.joinToString(" "), en)
                a.results.forEach { item ->
                    DP(tx(en, "ပျောက်ဂဏန်း", "Missing digit") + " " + item.digit, en) {
                        item.thisWeekFirstSeen?.let {
                            DL(tx(en, "ဒီအပတ် ပြန်ဆင်းခဲ့သောဂဏန်းတွဲ",
                                "Observed return this week"), evidence(en, it), en)
                        }
                        if (item.historicalMissingWeeks > 0) DL(
                            tx(en, "ယခင်က ဂဏန်းပျောက်ခဲ့သောအပတ်", "Historical missing weeks"),
                            item.historicalMissingWeeks.toString() + tx(en, " ပတ်", " weeks"), en)
                        if (item.commonCompanions.isNotEmpty()) DL(
                            tx(en, "ပြန်ဆင်းချိန် အများဆုံးတွဲခဲ့သောဂဏန်း", "Most common companion"),
                            item.commonCompanions.joinToString(" · ") {
                                it.companion.toString() + " (" + it.count + ")"
                            }, en)
                        if (item.historicalFirstReturns.isNotEmpty()) DB(
                            tx(en, "ယခင်ပြန်ဆင်းခဲ့သောရက်များ", "View return dates"), en) {
                            show(item.digit.toString() + " · " +
                                tx(en, "ယခင်ပြန်ဆင်းခဲ့သောမှတ်တမ်း", "Observed return history"),
                                item.historicalFirstReturns.map { evidence(en, it) })
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
