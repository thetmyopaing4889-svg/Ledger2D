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

@Composable
internal fun TwoDAnalysisDetails(id: String, report: TwoDAnalysisReport, en: Boolean) {
    fun tr(mm: String, english: String) = if (en) english else mm
    fun n(count: Int) = count.toString() + tr(" ကြိမ်", " draws")
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
                AnalysisDetailNote(tr("Result မတွေ့", "No matching records"))
            } else {
                val top = a.numbers.takeWhile { it.count == (a.numbers.firstOrNull()?.count ?: 0) }
                AnalysisDetailLine(tr("အထွက်အများဆုံး", "Most observed"),
                    top.joinToString(" · ") { "${it.digit} (${it.count})" })
                val positive = a.numbers.filter { it.count > 0 }
                val minimum = positive.minOfOrNull { it.count }
                AnalysisDetailLine(tr("အထွက်အနည်းဆုံး (ပေါ်ဖူး)", "Least observed (>0)"),
                    positive.filter { it.count == minimum }.joinToString(" · ") { "${it.digit} (${it.count})" })
                AnalysisDetailLine(tr("မပေါ်ဖူးသော 2D", "Never observed 2D"),
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
                a.parity.forEach { (name, count) -> AnalysisDetailLine(name, n(count)) }
                a.groups.forEach { (name, count) -> AnalysisDetailLine(name, n(count)) }
            }
        }
        "03" -> {
            val a = report.a03
            if (!a.eligible) {
                AnalysisDetailNote(tr(
                    "အနည်းဆုံး ရလဒ် ၄ ကြိမ် မပြည့်သေးခြင်း၊ Result မတွေ့ခြင်း၊ သို့မဟုတ် ပိတ်ရက်ရှိခြင်းကြောင့် တိတိကျကျ မစစ်နိုင်သေးပါ။",
                    "Requires at least 4 verified aligned results and an open week without unknown slots.",
                ))
            } else {
                AnalysisDetailLine(tr("စစ်ထားသော စုံ/မ အစဉ်", "Parity sequence"),
                    a.checkedPrefix.joinToString(" → "))
                AnalysisDetailLine(tr("တူညီသောအပတ်", "Matching weeks"), n(a.matched.size))
                if (a.matched.isEmpty()) AnalysisDetailNote(tr(
                    "တူညီသော History မတွေ့", "No verified historical match",
                ))
                a.nextParity.filterValues { it > 0 }.forEach { (name, count) ->
                    AnalysisDetailLine(name, n(count))
                }
                a.matched.take(6).forEach { AnalysisDetailLine(tr("အပြီးထွက်", "Next result"), match(it)) }
                if (a.matched.size > 6) TextButton(onClick = {
                    show(tr("တူညီသောအပတ်များ", "Matching weeks"), a.matched.map(::match))
                }) { Text(tr("အားလုံးကြည့်ရန်", "See all matches")) }
            }
        }
        "06" -> {
            var stream by remember(report.target) { mutableStateOf(
                if (report.target.session == DrawSession.MORNING) "morning" else "evening",
            ) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "morning" to tr("မနက်", "AM"),
                    "evening" to tr("ညနေ", "PM"),
                    "combined" to tr("ပေါင်း", "Both"),
                ).forEach { (key, name) ->
                    FilterChip(selected = stream == key, onClick = { stream = key },
                        label = { Text(name, style = MaterialTheme.typography.labelSmall) })
                }
            }
            val a = report.a06.getValue(stream)
            AnalysisDetailLine(tr("စစ်ဆေးထားသော ရလဒ်", "Observed draws"), n(a.observedDraws))
            if (a.hasUnknownOpenSlots) AnalysisDetailNote(tr(
                "ကြားထဲမှာ Result မတွေ့သောအကြိမ်ရှိသည်။ ပျောက်နေသည့်အကြိမ်ရေမှာ ပြည့်စုံမည်မဟုတ်ပါ။",
                "Unknown open draw slots exist. Streak counts may be incomplete.",
            ))
            if (a.observedDraws == 0) AnalysisDetailNote(tr("Result မတွေ့", "No verified results"))
            else {
                AnalysisDetailNote(tr("2D အတွဲ (00–99)", "Two-digit numbers (00–99)"))
                a.fullNumbers.take(8).forEach {
                    AnalysisDetailLine(it.value,
                        n(it.observedDrawsSince) +
                            if (it.historyStartTruncated) tr(" · အစမသိ", " · start unknown") else "")
                }
                TextButton(onClick = {
                    show(tr("ပျောက်နေသော 2D အားလုံး", "All 2D absence streaks"),
                        a.fullNumbers.map {
                            it.value + " · " + n(it.observedDrawsSince) +
                                if (it.historyStartTruncated) tr(" (အစမသိ)", " (start unknown)") else ""
                        })
                }) { Text(tr("2D အားလုံးကြည့်ရန်", "View all 2D numbers")) }
                AnalysisDetailNote(tr("တစ်လုံးဂဏန်း (0–9)", "Single digits (0–9)"))
                a.singleDigits.forEach {
                    AnalysisDetailLine(it.value, n(it.observedDrawsSince) +
                        if (it.historyStartTruncated) tr(" · အစမသိ", " · start unknown") else "")
                }
            }
        }
        "08" -> {
            val a = report.a08
            AnalysisDetailLine(tr("ရှေ့ရလဒ် (၁)", "Previous result 1"),
                a.firstReference?.let(::match) ?: tr("Result မတွေ့", "Not found"))
            AnalysisDetailLine(tr("ရှေ့ရလဒ် (၂)", "Previous result 2"),
                a.secondReference?.let(::match) ?: tr("Result မတွေ့", "Not found"))
            val parts = listOf(
                tr("နှစ်ခုတွဲ တိုက်စစ်", "Both references") to a.both,
                tr("ပထမတစ်ခု တိုက်စစ်", "First only") to a.firstOnly,
                tr("ဒုတိယတစ်ခု တိုက်စစ်", "Second only") to a.secondOnly,
            )
            parts.forEach { (title, result) ->
                HorizontalDivider()
                AnalysisDetailLine(title,
                    if (result.sourceDigit == null) tr("Result မတွေ့", "Reference missing")
                    else n(result.matches.size))
                if (result.sourceDigit != null && result.matches.isEmpty())
                    AnalysisDetailNote(tr("တူညီမှု မတွေ့", "No verified matches"))
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
                "ဒီအပတ် ရလဒ် မရှိသေးပါ။ ပထမဆုံးရလဒ်အပြီးမှ စစ်ဆေးမည်။",
                "No confirmed result this week. Activates after the first draw.",
            )) else {
                AnalysisDetailLine(tr("ဒီအပတ် စစ်ထားသောအကြိမ်", "This week's draws"),
                    n(a.transitions.size))
                if (!a.completeThroughTarget) AnalysisDetailNote(tr(
                    "ဒီအပတ် အချို့အကြိမ် Result မတွေ့ပါ။ ကျန်ဂဏန်းစာရင်းမှာ ရရှိထားသောမှတ်တမ်းအပေါ်သာ အခြေခံသည်။",
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
                    AnalysisDetailLine(match(t.result), wording)
                }
                AnalysisDetailLine(
                    if (a.completeThroughTarget) tr("မပေါ်သေးတဲ့ တစ်လုံးဂဏန်း", "Unseen single digits")
                    else tr("ရရှိသောမှတ်တမ်း၌ မပေါ်", "Unseen in available records"),
                    a.remainingDigits.joinToString(" ").ifEmpty { tr("မရှိ", "None") })
            }
        }
        "07" -> {
            val a = report.a07
            if (a.observedDraws == 0) AnalysisDetailNote(tr(
                "ဒီအပတ် ရလဒ် မရှိသေးပါ။", "No confirmed result this week.",
            )) else {
                AnalysisDetailLine(tr("ဒီအပတ် ရလဒ်", "This week's draws"), n(a.observedDraws))
                a.groups.forEach { (name, count) -> AnalysisDetailLine(name, n(count)) }
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
            if (!a.complete) AnalysisDetailNote(tr(
                "Result မတွေ့သော အကြိမ်များရှိသည်။ ကျန်ဂဏန်းကို အတည်မပြုနိုင်သေးပါ။",
                "Some open draw results are missing; absence cannot be confirmed.",
            ))
            AnalysisDetailLine(
                if (a.complete) tr("ကျန်ဂဏန်း", "Absent digits")
                    else tr("ရှိသောမှတ်တမ်းတွင် မပေါ်", "Unseen in available records"),
                a.remainingDigits.joinToString(" ").ifEmpty { tr("မရှိ", "None") },
            )
            AnalysisDetailNote(tr("တစ်လုံးဂဏန်း 0–9 အကြိမ်ရေ", "Single digit frequency"))
            AnalysisDetailLine("", a.singleDigitFrequency.joinToString("  ") { "${it.digit}:${it.count}" })
            a.parity.forEach { (name, count) -> AnalysisDetailLine(name, n(count)) }
            a.groups.forEach { (name, count) -> AnalysisDetailLine(name, n(count)) }
            AnalysisDetailLine(tr("ပြန်ကျ", "Exact repeats"),
                a.repeatedNumbers.joinToString(" · ") { "${it.digit} (${it.count})" }
                    .ifEmpty { tr("မရှိ", "None") })
        }
        "01" -> {
            val a = report.a01
            if (!a.previousWeekComplete) AnalysisDetailNote(tr(
                "ပြီးခဲ့တဲ့အပတ် မှတ်တမ်းမပြည့်စုံ၍ ပျောက်ဂဏန်းကို အတည်မပြုနိုင်သေးပါ။",
                "Last week's results are incomplete; missing digits are not verified.",
            ))
            AnalysisDetailLine(
                if (a.previousWeekComplete) tr("ပြီးခဲ့တဲ့အပတ် ကျန်ဂဏန်း", "Last week's missing digits")
                    else tr("ရှိသောမှတ်တမ်းအရ ကျန်", "Unseen in available records"),
                a.missingDigits.joinToString(" ").ifEmpty { tr("မရှိ", "None") })
            a.results.forEach { item ->
                HorizontalDivider()
                AnalysisDetailLine(tr("ဂဏန်း", "Digit"), item.digit.toString())
                AnalysisDetailLine(tr("ဒီအပတ် ပထမပြန်ပေါ်", "First return this week"),
                    item.thisWeekFirstSeen?.let(::match) ?: tr("မတွေ့သေး", "Not seen yet"))
                if (item.thisWeekFirstSeen != null && !item.firstSeenVerified)
                    AnalysisDetailNote(tr("အရင် Result မတွေ့၍ ပထမပြန်ပေါ်မှု မသေချာပါ။",
                        "Earlier slots missing: first return cannot be verified."))
                AnalysisDetailLine(tr("History ပြန်ပေါ်ဖြစ်စဉ်", "Historical first-return episodes"),
                    n(item.historyFirstReturnEpisodes))
                AnalysisDetailLine(tr("အတွေ့များဆုံး တွဲဖက်", "Most common companion"),
                    item.commonCompanions.joinToString(" · ") {
                        "${it.companion} (${it.count})"
                    }.ifEmpty { tr("Result မတွေ့", "No verified episodes") })
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top) {
        if (label.isNotEmpty()) {
            Text(label, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.9f))
        }
        Text(value, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(if (label.isEmpty()) 1f else 1.1f))
    }
}

@Composable
private fun AnalysisDetailNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
