package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession

internal fun twoDGroupLabel(name: String, en: Boolean): String = if (!en) name else when (name) {
    "အပူး" -> "Doubles"
    "ညီကို" -> "Sibling pairs"
    "ပါဝါ" -> "Power"
    "နက္ခတ်" -> "Astrology"
    else -> name
}

internal fun twoDParityLabel(name: String, en: Boolean): String = if (!en) name else when (name) {
    "စုံစုံ" -> "Even–Even"
    "မမ" -> "Odd–Odd"
    "စုံမ" -> "Even–Odd"
    "မစုံ" -> "Odd–Even"
    else -> name
}

@Composable
internal fun TwoDAnalysisDetails(
    id: String,
    report: TwoDAnalysisReport,
    en: Boolean,
    selectedStreakStream: String? = null,
    onStreakStreamChange: ((String) -> Unit)? = null,
) {
    fun tr(mm: String, english: String) = if (en) english else mm
    fun n(count: Int) = count.toString() + tr(" ကြိမ်", " times")
    fun slot(s: DrawSession) = if (s == DrawSession.MORNING) tr("မနက်", "AM") else tr("ညနေ", "PM")
    fun match(m: TwoDMatch) = m.date.toString() + " · " + slot(m.session) + " · " + m.digit
    var dialogTitle by remember(id) { mutableStateOf<String?>(null) }
    var dialogRows by remember(id) { mutableStateOf(emptyList<String>()) }
    fun show(title: String, rows: List<String>) { dialogTitle = title; dialogRows = rows }
    val note = MaterialTheme.colorScheme.onSurfaceVariant

    when (id) {
        "02" -> {
            val a = report.a02
            AnalysisDetailLine(tr("စစ်ထားသော အကြိမ်", "Verified draws"), n(a.samples))
            if (a.samples == 0) {
                AnalysisDetailNote(tr("ထွက်ဂဏန်းမတွေ့", "No matching records"))
            } else {
                val top = a.numbers.takeWhile { it.count == (a.numbers.firstOrNull()?.count ?: 0) }
                AnalysisDetailLine(tr("အထွက်အများဆုံး", "Most observed"),
                    top.joinToString(" · ") { "${it.digit} (${it.count})" })
                val positive = a.numbers.filter { it.count > 0 }
                val minimum = positive.minOfOrNull { it.count }
                AnalysisDetailLine(tr("အထွက်အနည်းဆုံး (ပေါ်ဖူး)", "Least observed (>0)"),
                    positive.filter { it.count == minimum }.joinToString(" · ") { "${it.digit} (${it.count})" })
                AnalysisDetailLine(tr("ယခင်မထွက်ခဲ့သော ဂဏန်းတွဲများ", "Never observed 2D"),
                    a.unseenNumbers.size.toString() + tr(" လုံး", " numbers"))
                TextButton(
                    onClick = { show(tr("00–99 အကြိမ်ရေ", "00–99 frequencies"),
                        (0..99).map { value ->
                            val digit = value.toString().padStart(2, '0')
                            "$digit · ${a.numbers.firstOrNull { it.digit == digit }?.count ?: 0}"
                        }) },
                ) { Text(tr("ဂဏန်းအားလုံး ကြည့်ရန်", "See all numbers")) }
                AnalysisDetailNote(tr("တစ်လုံးဂဏန်း 0–9 (ထိပ် + ပိတ်)", "Single digits 0–9 (tens + ones)"))
                AnalysisDetailLine("", a.singles.joinToString("  ") { "${it.digit}:${it.count}" })
                AnalysisDetailNote(tr("ထိပ် (0–9)", "Tens position (0–9)"))
                AnalysisDetailLine("", a.tens.joinToString("  ") { "${it.digit}:${it.count}" })
                AnalysisDetailNote(tr("ပိတ် (0–9)", "Ones position (0–9)"))
                AnalysisDetailLine("", a.ones.joinToString("  ") { "${it.digit}:${it.count}" })
                a.parity.forEach { (name, count) ->
                    AnalysisDetailLine(twoDParityLabel(name, en), n(count))
                }
                a.groups.forEach { (name, count) ->
                    AnalysisDetailLine(twoDGroupLabel(name, en), n(count))
                }
            }
        }
        "03" -> {
            val a = report.a03
            if (!a.eligible) {
                AnalysisDetailNote(tr(
                    "အနည်းဆုံး ထွက်ဂဏန်း ၄ ကြိမ် မပြည့်သေးခြင်း၊ ထွက်ဂဏန်းမှတ်တမ်း မတွေ့ခြင်း၊ သို့မဟုတ် ပိတ်ရက်ရှိခြင်းကြောင့် မစစ်နိုင်သေးပါ။",
                    "Requires at least 4 verified aligned results and an open week without unknown slots.",
                ))
            } else {
                AnalysisDetailLine(tr("စစ်ထားသော စုံ/မ အစဉ်", "Parity sequence"),
                    a.checkedPrefix.joinToString(" → ") { twoDParityLabel(it, en) })
                AnalysisDetailLine(tr("တူညီသောအပတ်", "Matching weeks"), n(a.matched.size))
                if (a.matched.isEmpty()) AnalysisDetailNote(tr(
                    "တူညီသောမှတ်တမ်း မတွေ့ပါ", "No verified historical match",
                ))
                a.nextParity.filterValues { it > 0 }.forEach { (name, count) ->
                    AnalysisDetailLine(twoDParityLabel(name, en), n(count))
                }
                a.matched.take(6).forEach { AnalysisDetailLine(tr("အပြီးထွက်", "Next result"), match(it)) }
                if (a.matched.size > 6) TextButton(onClick = {
                    show(tr("တူညီသောအပတ်များ", "Matching weeks"), a.matched.map(::match))
                }) { Text(tr("အားလုံးကြည့်ရန်", "See all matches")) }
            }
        }
        "06" -> {
            var localStream by remember(report.target) { mutableStateOf(
                if (report.target.session == DrawSession.MORNING) "morning" else "evening",
            ) }
            val stream = selectedStreakStream ?: localStream
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "morning" to tr("မနက်", "AM"),
                    "evening" to tr("ညနေ", "PM"),
                    "combined" to tr("ပေါင်း", "Both"),
                ).forEach { (key, name) ->
                    FilterChip(selected = stream == key, onClick = {
                        if (onStreakStreamChange != null) onStreakStreamChange(key)
                        else localStream = key
                    },
                        label = { Text(name, style = MaterialTheme.typography.labelSmall) })
                }
            }
            val a = report.a06.getValue(stream)
            AnalysisDetailLine(tr("စစ်ထားသော ထွက်ဂဏန်းအကြိမ်", "Observed draws"), n(a.observedDraws))
            if (a.hasUnknownOpenSlots) AnalysisDetailNote(tr(
                "ကြားထဲမှာ ထွက်ဂဏန်းမှတ်တမ်း မရှိတဲ့အကြိမ်တွေ ရှိပါတယ်။ ပျောက်နေတဲ့အကြိမ်ရေကို ရှိတဲ့မှတ်တမ်းအရပဲ တွက်ထားပါတယ်။",
                "Unknown open draw slots exist. Streak counts may be incomplete.",
            ))
            if (a.observedDraws == 0) AnalysisDetailNote(tr("ထွက်ဂဏန်းမတွေ့", "No verified results"))
            else {
                AnalysisDetailNote(tr("ပျောက်ဂဏန်းတွဲများ", "Two-digit numbers (00–99)"))
                a.fullNumbers.take(8).forEach {
                    AnalysisDetailLine(it.value,
                        n(it.observedDrawsSince) +
                            if (it.historyStartTruncated) tr(" · နောက်ဆုံးထွက်ခဲ့တဲ့အကြိမ် မသိ", " · start unknown")
                            else it.lastSeen?.let { previous -> " · " + match(previous) }.orEmpty())
                }
                TextButton(onClick = {
                    show(tr("ပျောက်ဂဏန်းနှင့် ဂဏန်းတွဲများ", "Missing digits and pairs"),
                        listOf(tr("ပျောက်ဂဏန်းများ (0–9)", "Single digits (0–9)")) +
                            a.singleDigits.map {
                                it.value + " · " + n(it.observedDrawsSince) +
                                    if (it.historyStartTruncated) tr(" (နောက်ဆုံးထွက်ခဲ့တဲ့အကြိမ် မသိ)", " (start unknown)")
                                    else it.lastSeen?.let { previous -> " · " + match(previous) }.orEmpty()
                            } +
                            listOf(tr("ပျောက်ဂဏန်းတွဲများ (00–99)", "Pairs (00–99)")) +
                            a.fullNumbers.map {
                                it.value + " · " + n(it.observedDrawsSince) +
                                    if (it.historyStartTruncated) tr(" (နောက်ဆုံးထွက်ခဲ့တဲ့အကြိမ် မသိ)", " (start unknown)")
                                    else it.lastSeen?.let { previous -> " · " + match(previous) }.orEmpty()
                            })
                }) { Text(tr("အားလုံးကြည့်ရန်", "View all 2D numbers")) }
                AnalysisDetailNote(tr("ပျောက်ဂဏန်းများ", "Single digits (0–9)"))
                a.singleDigits.forEach {
                    AnalysisDetailLine(it.value, n(it.observedDrawsSince) +
                        if (it.historyStartTruncated) tr(" · နောက်ဆုံးထွက်ခဲ့တဲ့အကြိမ် မသိ", " · start unknown") else "")
                }
            }
        }
        "08" -> {
            val a = report.a08
            AnalysisDetailLine(tr("ရှေ့အကြိမ်ထွက်ဂဏန်း (၁)", "Previous result 1"),
                a.firstReference?.let(::match) ?: tr("ထွက်ဂဏန်းမတွေ့", "Not found"))
            AnalysisDetailLine(tr("ရှေ့အကြိမ်ထွက်ဂဏန်း (၂)", "Previous result 2"),
                a.secondReference?.let(::match) ?: tr("ထွက်ဂဏန်းမတွေ့", "Not found"))
            val parts = listOf(
                tr("နှစ်ခုတွဲ တိုက်စစ်", "Both references") to a.both,
                tr("ပထမတစ်ခု တိုက်စစ်", "First only") to a.firstOnly,
                tr("ဒုတိယတစ်ခု တိုက်စစ်", "Second only") to a.secondOnly,
            )
            parts.forEach { (title, result) ->
                HorizontalDivider()
                AnalysisDetailLine(title,
                    if (result.sourceDigit == null) tr("ထွက်ဂဏန်းမတွေ့", "Reference missing")
                    else n(result.matches.size))
                if (result.missingTargetCount > 0)
                    AnalysisDetailLine(
                        tr("နောက်တစ်ကြိမ်ထွက်ဂဏန်း မှတ်တမ်းမတွေ့ပါ", "Source matched, next result missing"),
                        n(result.missingTargetCount),
                    )
                if (result.sourceDigit != null && result.matches.isEmpty() &&
                    result.missingTargetCount == 0) {
                    AnalysisDetailNote(tr("အတည်ပြုတူညီမှု မတွေ့", "No verified matches"))
                }
                result.frequencies.take(8).forEach {
                    AnalysisDetailLine(it.digit, n(it.count))
                }
                if (result.matches.isNotEmpty()) TextButton(onClick = {
                    show(title, result.matches.map(::match))
                }) { Text(tr("တူညီသောရက်များ ကြည့်ရန်", "View matched dates")) }
            }
        }
        "05" -> {
            val a = report.a05
            if (a.transitions.isEmpty()) AnalysisDetailNote(tr(
                "ဒီအပတ် ထွက်ဂဏန်းမရှိသေးပါ။ ပထမဆုံးထွက်ဂဏန်း ရရှိမှ စစ်ဆေးပါမယ်။",
                "No confirmed result this week. Activates after the first draw.",
            )) else {
                AnalysisDetailLine(tr("ဒီအပတ် စစ်ထားသောအကြိမ်", "This week's draws"),
                    n(a.transitions.size))
                if (!a.completeThroughTarget) AnalysisDetailNote(tr(
                    "ဒီအပတ်မှာ ထွက်ဂဏန်းမှတ်တမ်း မရှိတဲ့အကြိမ်တွေ ရှိပါတယ်။ ပျောက်နေသော ဂဏန်းတွေကို ရှိတဲ့မှတ်တမ်းပေါ်မှာပဲ တွက်ထားပါတယ်။",
                    "Some open draw slots are unknown. Unseen digits refer only to available records.",
                ))
                a.transitions.take(10).forEach { t ->
                    val wording = if (t.result.digit[0] == t.result.digit[1]) {
                        if (t.newlySeenDigits.isNotEmpty()) tr("ဂဏန်းအသစ် ၁ လုံး", "1 new digit")
                        else tr("ဂဏန်းအဟောင်း ၁ လုံး", "1 old digit")
                    } else {
                        (if (t.newTens) tr("အသစ်", "New") else tr("အဟောင်း", "Old")) + " + " +
                            (if (t.newOnes) tr("အသစ်", "New") else tr("အဟောင်း", "Old"))
                    }
                    AnalysisDetailLine(
                        match(t.result),
                        if (t.classificationVerified) wording
                        else wording + tr(" (ရရှိသောမှတ်တမ်းအရ)", " (available records only)"),
                    )
                }
                AnalysisDetailLine(
                    if (a.completeThroughTarget) tr("ပျောက်နေသော ဂဏန်း", "Unseen single digits")
                    else tr("မှတ်တမ်းအရှိ", "Unseen in available records"),
                    a.remainingDigits.joinToString(" ").ifEmpty { tr("မရှိ", "None") })
            }
        }
        "07" -> {
            val a = report.a07
            if (a.observedDraws == 0) AnalysisDetailNote(tr(
                "ဒီအပတ် ထွက်ဂဏန်းမရှိသေးပါ။", "No confirmed result this week.",
            )) else {
                AnalysisDetailLine(tr("ဒီအပတ် ထွက်ဂဏန်း", "This week's draws"), n(a.observedDraws))
                if (!a.completeThroughTarget) AnalysisDetailNote(tr(
                    "ထွက်ဂဏန်းမှတ်တမ်း မရှိတဲ့အကြိမ်တွေ ရှိပါတယ်။ အုပ်စုအရေအတွက်ကို ရှိတဲ့မှတ်တမ်းတွေပေါ်မှာပဲ တွက်ထားပါတယ်။",
                    "Some open draw slots are unknown; group counts cover observed results only.",
                ))
                a.groups.forEach { (name, count) ->
                    AnalysisDetailLine(twoDGroupLabel(name, en), n(count))
                    val occurrences = a.groupOccurrences[name].orEmpty()
                    if (occurrences.isNotEmpty()) {
                        TextButton(onClick = {
                            show(twoDGroupLabel(name, en), occurrences.map(::match))
                        }) { Text(tr("ထွက်ခဲ့သောရက်များ", "View occurrence dates")) }
                    }
                }
                AnalysisDetailLine(tr("ပြန်ကျ စုစုပေါင်း", "Exact repeats total"),
                    n(a.repeatedNumbers.sumOf { it.count }))
                a.repeatedNumbers.forEach { AnalysisDetailLine(it.digit, n(it.count)) }
            }
        }
        "04" -> {
            val a = report.a04
            AnalysisDetailLine(tr("ပြီးခဲ့တဲ့အပတ်", "Previous week"), a.weekStart.toString())
            AnalysisDetailLine(tr("မှတ်တမ်းအကြိမ်", "Verified draws"),
                "${a.observedDraws} / ${a.expectedOpenDraws}")
            if (a.observedDraws == 0) AnalysisDetailNote(tr(
                "ပြီးခဲ့တဲ့အပတ် ထွက်ဂဏန်းမှတ်တမ်း မရှိသေးပါ။ ပျောက်ဂဏန်းကို စစ်ဆေးဖို့ အချက်အလက်မရှိပါ။",
                "No verified draw last week; cannot establish missing digits.",
            ))
            if (!a.complete && a.observedDraws > 0) AnalysisDetailNote(tr(
                "ပြီးခဲ့တဲ့အပတ်မှာ ထွက်ဂဏန်းမှတ်တမ်း မရှိတဲ့အကြိမ်တွေ ရှိပါတယ်။ ပျောက်ဂဏန်းကို ရှိတဲ့မှတ်တမ်းတွေပေါ်မှာပဲ တွက်ထားပါတယ်။",
                "Some draw results are missing; absent digits are calculated from available records.",
            ))
            AnalysisDetailLine(
                if (a.complete) tr("ပျောက်ဂဏန်း", "Absent digits")
                    else tr("ပျောက်ဂဏန်း", "Unseen in available records"),
                (if (a.observedDraws == 0) tr("ထွက်ဂဏန်းမတွေ့", "No verified draws")
                else a.remainingDigits.joinToString(" ").ifEmpty { tr("မရှိ", "None") }),
            )
            AnalysisDetailNote(tr("တစ်လုံးဂဏန်း 0–9 အကြိမ်ရေ", "Single digit frequency"))
            AnalysisDetailLine("", a.singleDigitFrequency.joinToString("  ") { "${it.digit}:${it.count}" })
            a.parity.forEach { (name, count) ->
                AnalysisDetailLine(twoDParityLabel(name, en), n(count))
            }
            a.groups.forEach { (name, count) ->
                AnalysisDetailLine(twoDGroupLabel(name, en), n(count))
            }
            AnalysisDetailLine(tr("ပြန်ကျ", "Exact repeats"),
                a.repeatedNumbers.joinToString(" · ") { "${it.digit} (${it.count})" }
                    .ifEmpty { tr("မရှိ", "None") })
        }
        "01" -> {
            val a = report.a01
            if (!a.previousWeekComplete) AnalysisDetailNote(tr(
                "ပြီးခဲ့တဲ့အပတ် ထွက်ဂဏန်းမှတ်တမ်း မပြည့်ပါ။ ပျောက်ဂဏန်းစာရင်းကို ရှိတဲ့မှတ်တမ်းပေါ်မှာပဲ တွက်ထားပါတယ်။",
                "Last week's records are incomplete; absent digits are based on available draws.",
            ))
            AnalysisDetailLine(
                if (a.previousWeekComplete) tr("ပြီးခဲ့တဲ့အပတ် ပျောက်ဂဏန်း", "Last week's missing digits")
                    else tr("ရှိသောမှတ်တမ်းအရ ပျောက်ဂဏန်း", "Unseen in available records"),
                a.missingDigits.joinToString(" ").ifEmpty { tr("မရှိ", "None") })
            a.results.forEach { item ->
                HorizontalDivider()
                AnalysisDetailLine(tr("ဂဏန်း", "Digit"), item.digit.toString())
                AnalysisDetailLine(
                    if (item.firstSeenVerified) tr("ပျောက်ဂဏန်းပြန်ဆင်း", "Verified first return this week")
                    else tr("ဒီအပတ် ပြန်ဆင်းခဲ့သောဂဏန်းတွဲ", "Observed result this week"),
                    item.thisWeekFirstSeen?.let(::match) ?: tr("မတွေ့သေး", "Not seen yet"))
                if (item.thisWeekFirstSeen != null && !item.firstSeenVerified)
                    AnalysisDetailNote(tr("ဒီအပတ် ရှေ့အကြိမ်အချို့ရဲ့ ထွက်ဂဏန်းမှတ်တမ်း မရှိလို့ ပထမဆုံးပြန်ဆင်းတာ ဟုတ်မဟုတ် မသိနိုင်ပါ။",
                        "Earlier slots missing: first return cannot be verified."))
                AnalysisDetailLine(tr("ယခင်က ဂဏန်းပျောက်ခဲ့သောအပတ်", "Historical missing weeks"),
                    item.historicalMissingWeeks.toString() + tr(" ပတ်", " weeks"))
                AnalysisDetailLine(tr("နောက်အပတ် မှတ်တမ်းအရ ပြန်ဆင်းခဲ့", "Observed in following week"),
                    item.historyFirstReturnEpisodes.toString() + tr(" ပတ်", " weeks"))
                AnalysisDetailLine(tr("နောက်အပတ် မှတ်တမ်းအရ မဆင်းခဲ့", "Not observed in following week"),
                    item.historicalNoReturnWeeks.toString() + tr(" ပတ်", " weeks"))
                if (item.historicalUnknownNextWeeks > 0)
                    AnalysisDetailLine(tr("နောက်အပတ် ထွက်ဂဏန်းမှတ်တမ်း မရှိ", "No following-week records"),
                        item.historicalUnknownNextWeeks.toString() + tr(" ပတ်", " weeks"))
                AnalysisDetailLine(tr("ပြန်ဆင်းချိန် အများဆုံးတွဲခဲ့သောဂဏန်း", "Most common companion"),
                    item.commonCompanions.joinToString(" · ") {
                        "${it.companion} (${it.count})"
                    }.ifEmpty { tr("ထွက်ဂဏန်းမတွေ့", "No verified episodes") })
                if (item.historicalFirstReturns.isNotEmpty()) {
                    TextButton(onClick = {
                        show(item.digit.toString() + " · " + tr("ယခင်ပြန်ဆင်းခဲ့သောမှတ်တမ်း", "Observed return history"),
                            item.historicalFirstReturns.map(::match))
                    }) { Text(tr("ယခင်ပြန်ဆင်းခဲ့သောရက်များ", "View historical evidence")) }
                }
            }
        }
    }

    if (dialogTitle != null) {
        AlertDialog(
            onDismissRequest = { dialogTitle = null },
            title = { Text(dialogTitle.orEmpty()) },
            text = {
                LazyColumn(Modifier.heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(dialogRows.size) { index ->
                        Text(dialogRows[index], style = MaterialTheme.typography.bodyMedium)
                        if (index != dialogRows.lastIndex) HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialogTitle = null }) {
                Text(tr("ပိတ်မည်", "Close"))
            } },
        )
    }
}

@Composable
private fun AnalysisDetailLine(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    val stacked = label.isEmpty() || label.length > 22 || value.length > 34
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (stacked) {
            if (label.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, color = scheme.onSurface,
                    modifier = Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.42f))
    }
}

@Composable
private fun AnalysisDetailNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp))
}
