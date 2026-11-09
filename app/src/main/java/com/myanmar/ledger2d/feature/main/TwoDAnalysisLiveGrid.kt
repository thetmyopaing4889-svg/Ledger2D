package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.domain.TwoDAnalysisReport
import com.myanmar.ledger2d.core.domain.TwoDCount
import com.myanmar.ledger2d.core.domain.TwoDMissingDigitReturn
import com.myanmar.ledger2d.core.domain.TwoDStreak
import com.myanmar.ledger2d.core.domain.TwoDStreakStream

/**
 * Real verified results, rendered through the SAME approved card / glass
 * components as the isolated fake-data preview.
 *
 * No calculation, persistence or source precedence changes live here.
 * Empty history is NEVER represented with a placeholder dash or a false zero.
 */
internal object TwoDLiveSummary {
    fun unavailable(en: Boolean) = if (en) "Information not yet available"
        else "အချက်အလက် မရရှိနိုင်သေးပါ"

    data class Peak(val values: List<String>, val count: Int) {
        val present get() = count > 0 && values.isNotEmpty()
        fun display(en: Boolean): String = when {
            !present -> ""
            values.size == 1 -> values.first()
            values.size == 2 -> values.joinToString("·")
            else -> values.size.toString() + if (en) " tied" else " မျိုးတူ"
        }

        // A-01 must expose actual tied digits in the grid, not merely
        // "3 မျိုးတူ". Keep the fixed dashboard readable at phone widths.
        fun visibleDigits(limit: Int = 3): List<String> =
            values.distinct().sorted().take(limit)

        fun hiddenDigitCount(limit: Int = 3): Int =
            (values.distinct().size - limit).coerceAtLeast(0)
    }

    fun peak(data: List<TwoDCount>): Peak {
        val count = data.maxOfOrNull { it.count } ?: 0
        return Peak(data.filter { count > 0 && it.count == count }.map { it.digit }, count)
    }

    fun peak(data: Map<String, Int>): Peak {
        val count = data.values.maxOrNull() ?: 0
        return Peak(data.filterValues { count > 0 && it == count }.keys.toList(), count)
    }

    data class Missing(val top: Peak, val lowerBound: Boolean)

    fun missing(streaks: List<TwoDStreak>, unknown: Boolean): Missing {
        val max = streaks.maxOfOrNull { it.observedDrawsSince } ?: 0
        val ranked = streaks.filter { it.observedDrawsSince == max }
        // Zero-length streak is a verified "just seen" fact, not missing
        // history. Keep it as an observed value when stream has records.
        return Missing(
            Peak(ranked.map { it.value }, max),
            unknown || ranked.any { it.historyStartTruncated },
        )
    }

    fun streamAvailable(stream: TwoDStreakStream?): Boolean =
        stream != null && stream.observedDraws > 0
}

@Composable
internal fun TwoDAnalysisLiveGrid(
    group: String,
    report: TwoDAnalysisReport,
    en: Boolean,
    modifier: Modifier = Modifier,
    onOpenDetail: (String) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = group == "1" || maxHeight < 390.dp || maxWidth < 310.dp
        val gap = if (compact) 6.dp else 9.dp
        if (group == "1") {
            Column(Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap)) {
                    RealCard("02", report, en,
                        Modifier.weight(1f).fillMaxHeight(), compact,
                        true, onOpenDetail)
                    RealCard("06", report, en,
                        Modifier.weight(1f).fillMaxHeight(), compact,
                        true, onOpenDetail)
                }
                Row(Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap)) {
                    RealCard("03", report, en,
                        Modifier.weight(1f).fillMaxHeight(), compact,
                        true, onOpenDetail)
                    RealCard("08", report, en,
                        Modifier.weight(1f).fillMaxHeight(), compact,
                        true, onOpenDetail)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Top,
            ) {
                val ids = if (group == "2") listOf("05", "07")
                    else listOf("04", "01")
                ids.forEach { id ->
                    RealCard(id, report, en,
                        Modifier.weight(1f).fillMaxHeight(), compact,
                        false, onOpenDetail)
                }
            }
        }
    }
}

@Composable
private fun RealCard(
    id: String,
    report: TwoDAnalysisReport,
    en: Boolean,
    modifier: Modifier,
    compact: Boolean,
    stretch: Boolean,
    onOpenDetail: (String) -> Unit,
) {
    val label = when (id) {
        "02" -> if (en) "Most seen" else "အထွက်အများဆုံး"
        "06" -> if (en) "Missing digit / pair" else "ပျောက်ဂဏန်း / တွဲ"
        "03" -> if (en) "Parity pattern" else "စုံ/မ ပုံစံ"
        "08" -> if (en) "Previous comparison" else "ရှေ့ထွက်နှိုင်းယှဉ်"
        "05" -> if (en) "This week" else "ယခုအပတ်"
        "07" -> if (en) "Special groups" else "အထူးအုပ်စု"
        "04" -> if (en) "Previous week" else "ပြီးခဲ့တဲ့အပတ်"
        else -> if (en) "Missing-digit return" else "ပျောက်ဂဏန်းပြန်ပေါ်"
    }
    PreviewTile(label, modifier, compact, stretch,
        onOpenDetail = { onOpenDetail(id) }) {
        when (id) {
            "02" -> RealMostSeen(report, en, compact)
            "03" -> RealParity(report, en, compact)
            "06" -> RealMissing(report, en, compact)
            "08" -> RealPrevious(report, en, compact)
            "05" -> RealWeekly(report, en, compact)
            "07" -> RealSpecials(report, en, compact)
            "04" -> RealLastWeek(report, en, compact)
            "01" -> RealReturns(report, en, compact)
        }
    }
}

@Composable
private fun LiveUnavailable(en: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .30f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = .13f)),
    ) {
        Text(TwoDLiveSummary.unavailable(en),
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = 10.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3)
    }
}

/**
 * Center a single short state in an otherwise equal-sized quarter-screen
 * card; do not fill the blank area with explanations or extra fake numbers.
 */
@Composable
private fun ColumnScope.CenterCardStatus(
    message: String,
) {
    val c = MaterialTheme.colorScheme
    Spacer(Modifier.weight(1f))
    Surface(
        color = c.secondaryContainer.copy(alpha = .28f),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .12f)),
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(message,
                style = MaterialTheme.typography.labelMedium,
                color = c.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 3)
        }
    }
    Spacer(Modifier.weight(1f))
}

@Composable
private fun LiveMiniValue(value: String, count: Int?, en: Boolean, featured: Boolean = false) {
    if (value.isBlank()) {
        LiveUnavailable(en)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PreviewBadge(value, primary = featured, small = true)
        if (count != null) {
            Text(count.toString() + if (en) " hits" else " ကြိမ်",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
        }
    }
}

@Composable
private fun LiveCountRow(label: String, peak: TwoDLiveSummary.Peak, en: Boolean,
    compact: Boolean) {
    if (!peak.present) {
        PreviewSectionTitle(label)
        LiveUnavailable(en)
    } else PreviewLine(label,
        peak.display(en) + " ×" + peak.count, compact)
}

@Composable
private fun ColumnScope.RealMostSeen(
    r: TwoDAnalysisReport, en: Boolean, compact: Boolean,
) {
    val a = r.a02
    if (a.samples == 0) {
        LiveUnavailable(en)
        return
    }
    val pair = TwoDLiveSummary.peak(a.numbers)
    val digit = TwoDLiveSummary.peak(a.singles)
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (pair.present) PreviewHero("2D",
            PreviewNumber(pair.display(en), pair.count.toString()),
            Modifier.weight(1f), en, compact)
        else Box(Modifier.weight(1f)) { LiveUnavailable(en) }
        if (digit.present) PreviewHero("0–9",
            PreviewNumber(digit.display(en), digit.count.toString()),
            Modifier.weight(1f), en, compact)
        else Box(Modifier.weight(1f)) { LiveUnavailable(en) }
    }
    HorizontalDivider(thickness = .5.dp)
    LiveCountRow(if (en) "Parity" else "စုံ/မ",
        TwoDLiveSummary.peak(a.parity.mapKeys {
            twoDParityLabel(it.key, en)
        }), en, compact)
    val groups = a.groups.mapKeys { twoDGroupLabel(it.key, en) }
    LiveCountRow(if (en) "Group" else "အထူး",
        TwoDLiveSummary.peak(groups), en, compact)
    val topTens = TwoDLiveSummary.peak(a.tens)
    val topOnes = TwoDLiveSummary.peak(a.ones)
    if (topTens.present && topOnes.present) {
        PreviewLine(if (en) "10s / 1s" else "ထိပ် / ပိတ်",
            topTens.display(en) + " ×" + topTens.count + " · " +
                topOnes.display(en) + " ×" + topOnes.count, compact)
    } else LiveUnavailable(en)
}

@Composable
private fun ColumnScope.RealParity(
    r: TwoDAnalysisReport, en: Boolean, compact: Boolean,
) {
    val a = r.a03
    if (!a.eligible) {
        CenterCardStatus(TwoDLiveSummary.unavailable(en))
        return
    }
    if (a.matched.isEmpty()) {
        CenterCardStatus(
            if (en) "No verified matching pattern" else "တူညီသောမှတ်တမ်း မတွေ့ပါ"
        )
        return
    }
    PreviewSectionTitle(if (en) "Most common next class" else "အများဆုံး ဆက်ထွက်")
    val p = TwoDLiveSummary.peak(a.nextParity.mapKeys {
        twoDParityLabel(it.key, en)
    })
    if (p.present) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .48f),
            border = BorderStroke(1.dp,
                MaterialTheme.colorScheme.secondary.copy(alpha = .14f)),
            shape = RoundedCornerShape(10.dp)) {
            Box(Modifier.fillMaxWidth().padding(8.dp),
                contentAlignment = Alignment.CenterEnd) {
                LiveMiniValue(p.display(en), p.count, en, featured = true)
            }
        }
    } else LiveUnavailable(en)
    PreviewLine(if (en) "Matching weeks" else "ပုံစံတူအပတ်",
        a.matched.size.toString(), compact)
}

@Composable
private fun ColumnScope.RealMissing(
    r: TwoDAnalysisReport, en: Boolean, compact: Boolean,
) {
    listOf("AM" to "morning", "PM" to "evening", "ALL" to "combined")
        .forEachIndexed { i, (label, key) ->
            if (i != 0) HorizontalDivider(thickness = .5.dp)
            val stream = r.a06[key]
            if (!TwoDLiveSummary.streamAvailable(stream)) {
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Box(Modifier.weight(1f)) { LiveUnavailable(en) }
                }
            } else {
                val data = requireNotNull(stream)
                val digit = TwoDLiveSummary.missing(
                    data.singleDigits, data.hasUnknownOpenSlots)
                val pair = TwoDLiveSummary.missing(
                    data.fullNumbers, data.hasUnknownOpenSlots)
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(label, modifier = Modifier.width(if (compact) 23.dp else 26.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    if (digit.top.values.isNotEmpty()) {
                        TinyNumberWell("0–9",
                            PreviewNumber(digit.top.display(en),
                                digit.top.count.toString() +
                                if (digit.lowerBound) "+" else ""),
                            primary = true, en = en,
                            modifier = Modifier.weight(1.07f), compact = compact)
                    } else Box(Modifier.weight(1.07f)) { LiveUnavailable(en) }
                    if (pair.top.values.isNotEmpty()) {
                        TinyNumberWell("2D",
                            PreviewNumber(pair.top.display(en),
                                pair.top.count.toString() +
                                if (pair.lowerBound) "+" else ""),
                            primary = false, en = en,
                            modifier = Modifier.weight(1f), compact = compact)
                    } else Box(Modifier.weight(1f)) { LiveUnavailable(en) }
                }
            }
        }
}

@Composable
private fun ColumnScope.RealPrevious(
    r: TwoDAnalysisReport, en: Boolean, compact: Boolean,
) {
    val a = r.a08
    if (a.firstReference == null || a.secondReference == null) {
        CenterCardStatus(TwoDLiveSummary.unavailable(en))
        return
    }
    PreviewLine(if (en) "Prior" else "ရှေ့ထွက်",
        a.firstReference.digit + " · " + a.secondReference.digit, compact)
    HorizontalDivider(thickness = .5.dp)

    val categories = listOf(
        (if (en) "Both" else "နှစ်ခု") to a.both,
        (if (en) "First" else "ပထမ") to a.firstOnly,
        (if (en) "Second" else "ဒုတိယ") to a.secondOnly,
    ).map { (label, check) ->
        label to TwoDLiveSummary.peak(check.frequencies)
    }
    val available = categories.filter { it.second.present }
    val unavailable = categories.filterNot { it.second.present }

    // Give the one verified leader its own result, and put ALL the
    // unavailable conditions into ONE quiet status area rather than
    // repeating the same long Burmese sentence two or three times.
    available.forEachIndexed { i, (label, peak) ->
        if (i != 0) HorizontalDivider(thickness = .5.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (compact) 34.dp else 42.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, modifier = Modifier.width(if (compact) 31.dp else 39.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
            Spacer(Modifier.weight(1f))
            // Two leaders such as 02·46 should remain numbers (not prose).
            peak.visibleDigits(limit = 2).forEach {
                PreviewBadge(it, small = true)
            }
            if (peak.hiddenDigitCount(limit = 2) > 0) {
                Text("+" + peak.hiddenDigitCount(limit = 2),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "×" + peak.count,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
    if (unavailable.isNotEmpty()) {
        if (available.isNotEmpty()) HorizontalDivider(thickness = .5.dp)
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .27f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = .10f)),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(unavailable.joinToString(" / ") { it.first },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium)
                Text(TwoDLiveSummary.unavailable(en),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2)
            }
        }
    }
}

@Composable
private fun LiveDigitTokens(digits: List<String>, featured: Boolean = false) {
    // A real week can contain up to ten distinct 0–9 digits. Unlike the
    // four-value DEMO fixture, they must wrap without squeezing to 10 columns.
    digits.chunked(4).forEach { chunk ->
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            chunk.forEach { d ->
                PreviewBadge(d, modifier = Modifier.weight(1f),
                    primary = featured)
            }
            repeat(4 - chunk.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ColumnScope.RealWeekly(
    r: TwoDAnalysisReport, en: Boolean, compact: Boolean,
) {
    val a = r.a05
    if (a.transitions.isEmpty()) {
        LiveUnavailable(en)
        return
    }
    PreviewSectionTitle(if (en) "Unseen digits (0–9)" else "ပျောက်ဂဏန်း (0–9)")
    if (a.remainingDigits.isNotEmpty()) {
        LiveDigitTokens(a.remainingDigits.map { it.toString() }, featured = true)
    } else {
        Text(if (en) "No missing digits" else "ပျောက်ဂဏန်းမရှိ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
    HorizontalDivider(thickness = .5.dp)
    PreviewSectionTitle(if (en) "New this week" else "ဒီအပတ် အသစ်ထွက်")
    val latest = a.transitions.lastOrNull()
    if (latest == null || !latest.classificationVerified) {
        LiveUnavailable(en)
    } else if (latest.newlySeenDigits.isEmpty()) {
        Text(if (en) "None in last draw" else "နောက်ဆုံးအကြိမ် အသစ်မရှိ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else LiveDigitTokens(latest.newlySeenDigits.map { it.toString() }.sorted())
    Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
    PreviewLine(if (en) "Recorded" else "မှတ်တမ်း",
        a.transitions.size.toString() + if (a.completeThroughTarget) "" else "+", compact)
}

@Composable
private fun ColumnScope.RealSpecials(
    r: TwoDAnalysisReport, en: Boolean, compact: Boolean,
) {
    val a = r.a07
    if (a.observedDraws == 0) {
        LiveUnavailable(en)
        return
    }
    val items = listOf(
        "အပူး" to (a.groups["အပူး"] ?: 0),
        "ညီအကို" to (a.groups["ညီကို"] ?: a.groups["ညီအကို"] ?: 0),
        "ပါဝါ" to (a.groups["ပါဝါ"] ?: 0),
        "နက္ခတ်" to (a.groups["နက္ခတ်"] ?: 0),
        "ပြန်ကျ" to a.repeatedNumbers.sumOf { it.count },
    )
    val top = items.maxOf { it.second }
    val c = MaterialTheme.colorScheme
    Surface(color = c.surface.copy(alpha = .96f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .12f)),
        shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp)) {
            items.forEachIndexed { i, (label, count) ->
                if (i > 0) HorizontalDivider(thickness = .5.dp)
                Row(Modifier.fillMaxWidth().heightIn(
                        min = if (compact) 27.dp else 37.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (en) when(label) {
                        "အပူး" -> "Double"
                        "ညီအကို" -> "Sibling"
                        "ပါဝါ" -> "Power"
                        "နက္ခတ်" -> "Star"
                        else -> "Repeat"
                    } else label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    PreviewBadge(count.toString(), primary = top > 0 && count == top)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RealLastWeek(
    r: TwoDAnalysisReport, en: Boolean, compact: Boolean,
) {
    val a = r.a04
    if (a.observedDraws == 0) {
        LiveUnavailable(en)
        return
    }
    PreviewSectionTitle(if (en) "Most observed digit" else "အထွက်အများဆုံးဂဏန်း")
    val p = TwoDLiveSummary.peak(a.singleDigitFrequency)
    if (p.present) {
        val c = MaterialTheme.colorScheme
        // Both the winning digit and its count belong to the SAME result
        // plate; the old disconnected "အကြိမ်ရေ" label was misleading.
        Surface(
            color = c.secondaryContainer.copy(alpha = .48f),
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, c.secondary.copy(alpha = .18f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    modifier = Modifier.width(65.dp).height(82.dp),
                    color = c.secondary,
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = .20f)),
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(7.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("0–9", style = MaterialTheme.typography.labelSmall,
                            color = c.onSecondary.copy(alpha = .88f))
                        Text(p.values.first(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = c.onSecondary)
                    }
                }
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(if (en) "Seen" else "ထွက်ရှိမှု",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onSurfaceVariant)
                    Text(p.count.toString() + (if (en) " times" else " ကြိမ်"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = c.secondary,
                        maxLines = 1)
                    if (p.values.size > 1) {
                        Text(
                            (if (en) "Tied: " else "တူညီ: ") +
                                p.values.drop(1).take(2).joinToString("·") +
                                (if (p.values.size > 3) " +" + (p.values.size - 3) else ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.onSurfaceVariant,
                            maxLines = 2)
                    }
                }
            }
        }
    } else LiveUnavailable(en)
    Spacer(Modifier.height(if (compact) 6.dp else 13.dp))
    HorizontalDivider(thickness = .5.dp)
    PreviewSectionTitle(if (en) "Missing last week" else "ပြီးခဲ့တဲ့အပတ် ပျောက်")
    if (!a.complete) LiveUnavailable(en)
    else if (a.remainingDigits.isNotEmpty())
        LiveDigitTokens(a.remainingDigits.map { it.toString() })
    else Text(if (en) "No missing digits" else "ပျောက်ဂဏန်းမရှိ",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ColumnScope.RealReturns(
    r: TwoDAnalysisReport, en: Boolean, compact: Boolean,
) {
    val a = r.a01
    if (!a.previousWeekComplete) {
        LiveUnavailable(en)
        return
    }
    // A verified complete previous week with no missing digits is
    // a known empty set, NOT unavailable data.
    if (a.missingDigits.isEmpty()) {
        Text(if (en) "No missing digits" else "ပျောက်ဂဏန်းမရှိ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val items = a.results.filter { it.historyFirstReturnEpisodes > 0 }
        .sortedWith(
            compareByDescending<TwoDMissingDigitReturn> {
                it.thisWeekFirstSeen != null
            }.thenByDescending { it.historyFirstReturnEpisodes }
                .thenBy { it.digit })
        .take(3)
    if (items.isEmpty()) {
        LiveUnavailable(en)
        return
    }
    PreviewSectionTitle(if (en) "Missing → Companion" else "ပျောက် → တွဲထွက်")
    val c = MaterialTheme.colorScheme
    Surface(color = c.surface.copy(alpha = .96f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .12f)),
        shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp)) {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(thickness = .5.dp)
                val max = item.commonCompanions.maxOfOrNull { it.count } ?: 0
                val peak = TwoDLiveSummary.Peak(
                    item.commonCompanions.filter { max > 0 && it.count == max }
                        .map { it.companion.toString() }, max)
                Column(
                    Modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // First line is context + occurrence count; next line
                    // shows real companion digits, including tied winners.
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        PreviewBadge(item.digit.toString(), small = true)
                        Text("→", style = MaterialTheme.typography.labelLarge,
                            color = c.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        if (peak.present) Text(
                            peak.count.toString() +
                                (if (en) " hits" else " ကြိမ်"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = c.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (peak.present) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            peak.visibleDigits().forEach { digit ->
                                PreviewBadge(digit, primary = true, small = true)
                            }
                            if (peak.hiddenDigitCount() > 0) {
                                Text(
                                    "+" + peak.hiddenDigitCount(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = c.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Text(
                            TwoDLiveSummary.unavailable(en),
                            style = MaterialTheme.typography.labelSmall,
                            color = c.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
    }
}
