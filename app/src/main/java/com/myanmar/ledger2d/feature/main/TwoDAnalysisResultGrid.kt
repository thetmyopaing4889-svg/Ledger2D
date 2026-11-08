package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.domain.TwoDAnalysisReport
import com.myanmar.ledger2d.core.domain.TwoDCount
import com.myanmar.ledger2d.core.domain.TwoDStreak
import com.myanmar.ledger2d.core.model.DrawSession

/**
 * Each grid tile owns its information hierarchy. No reuse of the old generic
 * label/value/note method previews: they were readable in full-width cards
 * but cannot communicate a result clearly in a narrow two-column grid.
 */
@Composable
internal fun TwoDAnalysisGroupTag(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(11.dp),
        onClick = onClick,
        color = if (selected) scheme.primaryContainer else scheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(
            1.dp,
            if (selected) scheme.primary.copy(alpha = 0.45f)
            else scheme.outlineVariant.copy(alpha = 0.50f),
        ),
    ) {
        Box(Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun TwoDAnalysisResultGrid(
    group: String,
    report: TwoDAnalysisReport,
    en: Boolean,
    onOpenDetail: (String) -> Unit,
) {
    val checks = when (group) {
        "1" -> listOf("02", "03", "06", "08")
        "2" -> listOf("05", "07")
        else -> listOf("04", "01")
    }
    // Independent heights preserve space in each column while retaining
    // the user's approved 2x2 and 2x1 reading order.
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        (0..1).forEach { column ->
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                checks.filterIndexed { index, _ -> index % 2 == column }.forEach { id ->
                    TwoDGridFinding(
                        modifier = Modifier.fillMaxWidth(),
                        id = id,
                        report = report,
                        en = en,
                        onClick = { onOpenDetail(id) },
                    )
                }
            }
        }
    }
}

private fun leaderLabel(items: List<TwoDCount>, en: Boolean): String {
    val max = items.maxOfOrNull { it.count } ?: 0
    if (max == 0) return "—"
    val leaders = items.filter { it.count == max }
    return when (leaders.size) {
        0 -> "—"
        1 -> leaders.first().digit
        2 -> leaders.joinToString(" · ") { it.digit }
        else -> leaders.first().digit + " +" + (leaders.size - 1) +
            if (en) " tied" else " တူ"
    }
}

private fun mostAbsent(items: List<TwoDStreak>, en: Boolean): Pair<String, Int> {
    val maximum = items.maxOfOrNull { it.observedDrawsSince } ?: 0
    val leaders = items.filter { it.observedDrawsSince == maximum }
    val label = when (leaders.size) {
        0 -> "—"
        1 -> leaders.first().value
        2 -> leaders.joinToString(" · ") { it.value }
        else -> leaders.first().value + " +" + (leaders.size - 1) +
            if (en) " tied" else " တူ"
    }
    return label to maximum
}

@Composable
private fun GridEyebrow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun GridResult(
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        value,
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = if (highlighted) scheme.primary else scheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun GridSecondary(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun GridSubResult(label: String, value: String, emphasis: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (emphasis) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GridSeparator() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        thickness = 0.7.dp,
    )
}

@Composable
private fun TwoDGridFinding(
    modifier: Modifier,
    id: String,
    report: TwoDAnalysisReport,
    en: Boolean,
    onClick: () -> Unit,
) {
    fun tr(mm: String, english: String) = if (en) english else mm
    val scheme = MaterialTheme.colorScheme
    val title = when (id) {
        "02" -> tr("နေ့တူ၊ အကြိမ်တူ", "Same draw history")
        "03" -> tr("အပတ်ပုံစံတူ", "Weekly pattern")
        "06" -> tr("မတွေ့တာကြာ", "Longest unseen")
        "08" -> tr("ရှေ့အကြိမ် နှိုင်းယှဉ်", "Previous-draw match")
        "05" -> tr("ဒီအပတ် ဂဏန်းအခြေအနေ", "This week's digits")
        "07" -> tr("အထူးအုပ်စု / ပြန်ကျ", "Specials & repeats")
        "04" -> tr("ပြီးခဲ့တဲ့အပတ်", "Previous week")
        else -> tr("ပျောက်ဂဏန်း ပြန်ပေါ်", "Missing-digit returns")
    }
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.65f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Default.ChevronRight, null,
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.68f),
                    modifier = Modifier.size(16.dp))
            }

            when (id) {
                "02" -> {
                    val a = report.a02
                    if (a.samples == 0) {
                        GridSecondary(tr("နေ့တူ၊ အကြိမ်တူ မှတ်တမ်းမရှိသေး",
                            "No earlier results for this weekday and draw"))
                    } else {
                        val pairMax = a.numbers.maxOfOrNull { it.count } ?: 0
                        val digitMax = a.singles.maxOfOrNull { it.count } ?: 0
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                GridEyebrow(tr("ဂဏန်းတွဲ", "2D pair"))
                                GridResult(leaderLabel(a.numbers, en))
                                GridSecondary(pairMax.toString() + tr(" ကြိမ်", " times"))
                            }
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                GridEyebrow(tr("ဂဏန်း", "Digit"))
                                GridResult(leaderLabel(a.singles, en))
                                GridSecondary(digitMax.toString() + tr(" ကြိမ်", " times"))
                            }
                        }
                        GridSeparator()
                        val low = a.singles.filter { it.count > 0 }.minOfOrNull { it.count }
                        val lowDigits = if (low == null) emptyList()
                            else a.singles.filter { it.count == low }
                        GridSubResult(
                            tr("အနည်းဆုံး", "Least digit"),
                            (if (lowDigits.size <= 2) lowDigits.joinToString(" · ") { it.digit }
                             else lowDigits.first().digit + " +" + (lowDigits.size - 1)) +
                                (if (low != null) " · " + low + tr(" ကြိမ်", "x") else ""),
                        )
                        GridEyebrow(tr("နှိုင်းယှဉ်မှတ်တမ်း ", "Verified draws: ") + a.samples)
                    }
                }

                "03" -> {
                    val a = report.a03
                    when {
                        !a.eligible -> {
                            GridSecondary(tr("စစ်ဆေးရန် အချက်အလက်မပြည့်သေး",
                                "Not enough verified draws to compare"))
                            GridEyebrow(tr("အပတ်ပုံစံတူရန် ရှေ့အကြိမ်/ရက် မှတ်တမ်းလိုသည်",
                                "Needs aligned earlier draws and open dates"))
                        }
                        a.matched.isEmpty() -> {
                            GridSecondary(tr("ပုံစံတူအပတ် မတွေ့သေး",
                                "No matching historical week"))
                            GridEyebrow(tr("စစ်ဆေးချက် ပြည့်စုံ", "Check completed"))
                        }
                        else -> {
                            GridEyebrow(tr("တူညီသောအပတ်", "Matching weeks"))
                            GridResult(a.matched.size.toString())
                            GridSeparator()
                            val maximum = a.nextParity.values.maxOrNull() ?: 0
                            val classes = a.nextParity.filterValues { it == maximum && maximum > 0 }
                                .keys.joinToString(" · ") { twoDParityLabel(it, en) }
                            GridEyebrow(tr("နောက်ဆက်လာသည့်အမျိုးအစား",
                                "Following parity class"))
                            Text(classes.ifBlank { "—" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            GridEyebrow(maximum.toString() + tr(" ကြိမ်တွေ့", " historical hits"))
                        }
                    }
                }

                "06" -> {
                    val key = if (report.target.session == DrawSession.MORNING)
                        "morning" else "evening"
                    val a = report.a06[key]
                    if (a == null || a.observedDraws == 0) {
                        GridSecondary(tr("စစ်ဆေးရန် မှတ်တမ်းမရှိသေး",
                            "No verified draws for this stream"))
                    } else {
                        val (pair, pairGap) = mostAbsent(a.fullNumbers, en)
                        val (digit, digitGap) = mostAbsent(a.singleDigits, en)
                        GridEyebrow(tr("မှတ်တမ်းတွင် မတွေ့သောဂဏန်းတွဲ", "Longest unseen 2D pair"))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            GridResult(pair, modifier = Modifier.weight(1f))
                            GridSecondary(pairGap.toString() + tr(" ကြိမ်", " draws"))
                        }
                        GridSeparator()
                        GridEyebrow(tr("မတွေ့သောဂဏန်း", "Longest unseen digit"))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            GridResult(digit, modifier = Modifier.weight(1f))
                            GridSecondary(digitGap.toString() + tr(" ကြိမ်", " draws"))
                        }
                        if (a.hasUnknownOpenSlots ||
                            a.fullNumbers.any { it.historyStartTruncated }) {
                            GridEyebrow(tr("အတည်ပြုမှတ်တမ်းအရသာ", "Verified records only"))
                        }
                    }
                }

                "08" -> {
                    val a = report.a08
                    if (a.firstReference == null || a.secondReference == null) {
                        GridSecondary(tr("ရှေ့အကြိမ် ၂ ခု မပြည့်သေး",
                            "Two earlier verified results needed"))
                    } else {
                        GridEyebrow(tr("ရှေ့ရလဒ်", "Prior results"))
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(a.firstReference.digit,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = scheme.onSurface)
                            Text("·", style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant)
                            Text(a.secondReference.digit,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = scheme.onSurface)
                        }
                        GridSeparator()
                        val count = a.both.matches.size
                        if (count == 0) {
                            GridSecondary(tr("နှစ်ခုလုံးတူသည့် မှတ်တမ်းမတွေ့",
                                "No verified match for both"))
                        } else {
                            GridSubResult(tr("နှစ်ခုလုံးတူ", "Both matched"),
                                count.toString() + tr(" ကြိမ်", " times"), emphasis = true)
                        }
                        GridSubResult(
                            tr("တစ်ခုချင်းတူ", "Single matches"),
                            a.firstOnly.matches.size.toString() + " / " +
                                a.secondOnly.matches.size.toString(),
                        )
                        GridEyebrow(tr("ရှေ့ ၁ / ရှေ့ ၂", "First / second"))
                        if (a.both.missingTargetCount > 0) {
                            GridEyebrow(tr("နောက်ရလဒ်မရှိသေးသည့် မှတ်တမ်း " +
                                a.both.missingTargetCount, "Missing follow-up results: " +
                                a.both.missingTargetCount))
                        }
                    }
                }

                "05" -> {
                    val a = report.a05
                    if (a.transitions.isEmpty()) {
                        GridSecondary(tr("ဒီအပတ် ထွက်ဂဏန်း မရှိသေး",
                            "No confirmed draw this week"))
                    } else {
                        GridEyebrow(tr("ဒီအပတ် မတွေ့သေးသောဂဏန်း", "Unseen digits this week"))
                        GridResult(a.remainingDigits.joinToString(" · ").ifEmpty {
                            tr("မကျန်", "None")
                        })
                        GridSeparator()
                        val latest = a.transitions.last()
                        GridEyebrow(tr("နောက်ဆုံးထွက်သည့် အသစ်ဂဏန်း", "Latest new digits"))
                        Text(if (!latest.classificationVerified)
                            tr("အတည်မပြုနိုင်သေး", "Unverified")
                            else latest.newlySeenDigits.joinToString(" · ").ifEmpty {
                                tr("မရှိ", "None")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold, color = scheme.onSurface,
                            maxLines = 3)
                        if (!a.completeThroughTarget) {
                            GridEyebrow(tr("အချို့အကြိမ် မှတ်တမ်းမရှိ", "Partial weekly history"))
                        }
                    }
                }

                "07" -> {
                    val a = report.a07
                    if (a.observedDraws == 0) {
                        GridSecondary(tr("ဒီအပတ် ထွက်ဂဏန်း မရှိသေး",
                            "No confirmed draw this week"))
                    } else {
                        GridEyebrow(tr("ဂဏန်းတွဲ ပြန်ကျ", "Exact 2D repeats"))
                        GridResult(a.repeatedNumbers.sumOf { it.count }.toString())
                        GridSeparator()
                        a.groups.entries.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { (name, count) ->
                                    Column(Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        GridEyebrow(twoDGroupLabel(name, en))
                                        Text(count.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (count > 0) scheme.onSurface
                                                else scheme.onSurfaceVariant)
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        if (!a.completeThroughTarget) {
                            GridEyebrow(tr("အချို့အကြိမ် မှတ်တမ်းမရှိ", "Partial weekly history"))
                        }
                    }
                }

                "04" -> {
                    val a = report.a04
                    if (a.observedDraws == 0) {
                        GridSecondary(tr("အရင်အပတ် မှတ်တမ်းမရှိသေး",
                            "No verified draws last week"))
                    } else {
                        GridEyebrow(if (a.complete) tr("ပျောက်ဂဏန်း", "Missing digits")
                            else tr("မှတ်တမ်းအရ မတွေ့သေး", "Unseen in available draws"))
                        GridResult(a.remainingDigits.joinToString(" · ").ifEmpty {
                            tr("မကျန်", "None")
                        })
                        GridSeparator()
                        GridSubResult(tr("ဂဏန်းတွဲ ပြန်ကျ", "Exact repeats"),
                            a.repeatedNumbers.sumOf { it.count }.toString() +
                                tr(" ကြိမ်", " times"))
                        if (!a.complete) {
                            GridEyebrow(tr("အပတ်မှတ်တမ်း မပြည့်", "Partial week"))
                        }
                    }
                }

                "01" -> {
                    val a = report.a01
                    if (report.a04.observedDraws == 0) {
                        GridSecondary(tr("အရင်အပတ် မှတ်တမ်းမရှိသေး",
                            "Previous week has no verified draws"))
                    } else {
                        val returned = a.results.filter { it.thisWeekFirstSeen != null }
                        GridEyebrow(tr("ဒီအပတ် ပြန်တွေ့သောဂဏန်း", "Digits returned this week"))
                        if (returned.isEmpty()) {
                            GridSecondary(tr("ပြန်တွေ့တာ မရှိသေး", "None observed yet"))
                        } else {
                            GridResult(returned.joinToString(" · ") { it.digit.toString() })
                        }
                        GridSeparator()
                        GridSubResult(
                            tr("အရင်အပတ် မတွေ့", "Unseen last week"),
                            a.missingDigits.joinToString(" · ").ifEmpty { tr("မရှိ", "None") },
                        )
                        if (!a.previousWeekComplete ||
                            returned.any { !it.firstSeenVerified }) {
                            GridEyebrow(tr("မှတ်တမ်းအချို့ မပြည့်", "Some records incomplete"))
                        }
                    }
                }
            }
        }
    }
}
