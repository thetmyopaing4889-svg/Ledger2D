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
import com.myanmar.ledger2d.core.domain.TwoDMissingDigitReturn

/**
 * Only the main Analysis dashboard. Method names, prose, histories and
 * calculations belong to independent details; this is a compact result
 * canvas with inset values and micro-labels, not six cards inside a card.
 */
@Composable
internal fun TwoDAnalysisGroupTag(
    text: String, selected: Boolean, modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(13.dp), onClick = onClick,
        color = if (selected) c.secondaryContainer else c.surface,
        shadowElevation = if (selected) 2.dp else 0.dp,
        border = BorderStroke(1.dp, if (selected) c.secondary.copy(alpha = .45f)
            else c.outlineVariant.copy(alpha = .75f)),
    ) {
        Box(Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelMedium,
                color = if (selected) c.onSecondaryContainer else c.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun TwoDAnalysisResultGrid(
    group: String, report: TwoDAnalysisReport, en: Boolean,
    onOpenDetail: (String) -> Unit,
) {
    val checks = when (group) {
        // Balance the bento columns: the six-category 02 is naturally tall;
        // the 03 ineligible indicator is deliberately very short.
        "1" -> listOf("02", "06", "03", "08")
        "2" -> listOf("05", "07")
        else -> listOf("04", "01")
    }
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top) {
        (0..1).forEach { column ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                checks.filterIndexed { i, _ -> i % 2 == column }.forEach { id ->
                    ResultTile(id, report, en) { onOpenDetail(id) }
                }
            }
        }
    }
}

private data class Leader(
    val value: String,
    val count: Int? = null,
    val minOnly: Boolean = false,
    val tiedValues: List<String> = emptyList(),
)

private fun top(values: List<TwoDCount>): Leader {
    val max = values.maxOfOrNull { it.count } ?: 0
    return if (max == 0) Leader("—") else winners(
        values.filter { it.count == max }.map { it.digit }, max)
}
private fun top(values: Map<String, Int>, display: (String) -> String = { it }): Leader {
    val max = values.values.maxOrNull() ?: 0
    return if (max == 0) Leader("—") else winners(
        values.filterValues { it == max }.keys.map(display), max)
}
private fun winners(values: List<String>, count: Int): Leader = when (values.size) {
    0 -> Leader("—")
    1 -> Leader(values[0], count)
    2 -> Leader("2 tied", count, tiedValues = values)
    else -> Leader(values.size.toString() + " tied", count, tiedValues = values)
}
private fun hasTwoNumericTies(result: Leader): Boolean =
    result.tiedValues.size == 2 &&
        result.tiedValues.all { value ->
            value.length <= 2 && value.all { char -> char.isDigit() }
        }

private fun absentTop(stream: List<TwoDStreak>, incomplete: Boolean): Leader {
    if (stream.isEmpty()) return Leader("—")
    val max = stream.maxOf { it.observedDrawsSince }
    val tied = stream.filter { it.observedDrawsSince == max }
    return winners(tied.map { it.value }, max).copy(
        minOnly = incomplete || tied.any { it.historyStartTruncated })
}

@Composable
private fun ResultTile(id: String, report: TwoDAnalysisReport, en: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val unavailable = when (id) {
        "02" -> report.a02.samples == 0
        "03" -> !report.a03.eligible || report.a03.matched.isEmpty()
        "06" -> report.a06.values.none { it.observedDraws > 0 }
        "08" -> report.a08.firstReference == null || report.a08.secondReference == null
        "05" -> report.a05.transitions.isEmpty()
        "07" -> report.a07.observedDraws == 0
        "04" -> report.a04.observedDraws == 0
        "01" -> !report.a01.previousWeekComplete || report.a01.missingDigits.isEmpty()
        else -> true
    }
    Surface(
        modifier = Modifier.fillMaxWidth(), onClick = onClick,
        shape = RoundedCornerShape(18.dp), color = c.surface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = .62f)),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp,
                vertical = if (unavailable) 7.dp else 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (unavailable) {
                val status = when {
                    id == "03" && !report.a03.eligible -> "N/A"
                    id == "03" -> "No match"
                    id == "01" && !report.a01.previousWeekComplete -> "Unverified"
                    id == "01" && report.a04.observedDraws > 0 -> "None"
                    else -> "No data"
                }
                EmptyCompact(status, if (id == "03") {
                    if (en) "Parity" else "စုံ/မ"
                } else null)
            } else when (id) {
                "02" -> SameSlot(report, en)
                "03" -> WeeklyPattern(report)
                "06" -> Absence(report)
                "08" -> PreviousResults(report)
                "05" -> WeeklyDigits(report)
                "07" -> Specials(report, en)
                "04" -> PriorWeek(report)
                "01" -> MissingReturns(report)
            }
            }
            // Overlay the detail affordance; it no longer makes every tile
            // unnecessarily taller (especially the empty A-03 tile).
            Icon(Icons.Default.ChevronRight, null,
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 3.dp).size(11.dp),
                tint = c.onSurfaceVariant.copy(alpha = .45f))
        }
    }
}

@Composable
private fun EmptyCompact(status: String, meaning: String? = null) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (meaning == null) Arrangement.Center else Arrangement.SpaceBetween,
    ) {
        if (meaning != null) {
            Text(meaning, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium, color = c.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("—", style = MaterialTheme.typography.titleMedium,
                color = c.onSurfaceVariant.copy(alpha = .45f))
            Text(status, style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A single inset well only for the two genuinely dominant figures. */
@Composable
private fun Hero(label: String, result: Leader, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(13.dp),
        color = c.secondary, shadowElevation = 3.dp,
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .65f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = c.onSecondary.copy(alpha = .82f),
                fontWeight = FontWeight.Medium, maxLines = 1)
            if (hasTwoNumericTies(result)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    result.tiedValues.forEach { number ->
                        Surface(shape = RoundedCornerShape(7.dp),
                            color = c.onSecondary.copy(alpha = .15f)) {
                            Text(number, modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold, color = c.onSecondary,
                                maxLines = 1)
                        }
                    }
                }
            } else {
                Text(result.value,
                    style = if (result.value.length <= 3) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.titleMedium,
                    color = c.onSecondary, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (result.count != null) {
                Surface(shape = RoundedCornerShape(8.dp),
                    color = c.onSecondary.copy(alpha = .16f)) {
                    Text("×" + result.count,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = c.onSecondary)
                }
            }
        }
    }
}

@Composable
private fun ResultChip(value: String, small: Boolean = false, accent: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(8.dp),
        color = if (accent) c.secondaryContainer else c.surfaceVariant.copy(alpha = .85f)) {
        Text(value, modifier = Modifier.padding(
            horizontal = if (small) 4.dp else 6.dp,
            vertical = if (small) 2.dp else 3.dp),
            style = if (small || value.length > 4) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (value == "—") c.onSurfaceVariant
                else if (accent) c.secondary else c.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RankedChips(result: Leader, accent: Boolean = false) {
    if (hasTwoNumericTies(result)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            result.tiedValues.forEach { ResultChip(it, small = true, accent = accent) }
        }
    } else {
        ResultChip(result.value, small = result.value.length > 4, accent = accent)
    }
}

@Composable
private fun GapChip(result: Leader) {
    val count = result.count ?: return
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(8.dp),
        color = c.tertiaryContainer.copy(alpha = .90f)) {
        Row(Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Gap", style = MaterialTheme.typography.labelSmall,
                color = c.onTertiaryContainer.copy(alpha = .76f))
            Text(count.toString() + if (result.minOnly) "+" else "",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = c.onTertiaryContainer)
        }
    }
}

/** A flat label / inset value / count, without a nested bordered card. */
@Composable
private fun CompactRow(label: String, result: Leader, modifier: Modifier = Modifier,
    countPrefix: String = "×", labelWidth: Int = 0,
    accent: Boolean = false, showGapChip: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    Row(modifier.heightIn(min = 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, modifier = if (labelWidth > 0) Modifier.width(labelWidth.dp)
            else Modifier,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = c.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        RankedChips(result, accent = accent)
        if (showGapChip) {
            GapChip(result)
        } else if (result.count != null) {
            Text(countPrefix + result.count + if (result.minOnly) "+" else "",
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurface, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun DividerHairline() {
    HorizontalDivider(thickness = .5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .48f))
}

@Composable
private fun ShortPosition(label: String, result: Leader, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = c.onSurfaceVariant, maxLines = 1)
        ResultChip(result.value, small = true)
        if (result.count != null) {
            Text("×" + result.count, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1)
        }
    }
}

@Composable
private fun SameSlot(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a02
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Hero("2D", top(a.numbers), Modifier.weight(1f))
        Hero("0–9", top(a.singles), Modifier.weight(1f))
    }
    DividerHairline()
    CompactRow("စုံ/မ", top(a.parity), Modifier.fillMaxWidth())
    CompactRow("Group", top(a.groups) { twoDGroupLabel(it, en) }, Modifier.fillMaxWidth())
    DividerHairline()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        ShortPosition(if (en) "10s" else "ထိပ်", top(a.tens), Modifier.weight(1f))
        ShortPosition(if (en) "1s" else "ပိတ်", top(a.ones), Modifier.weight(1f))
    }
}

@Composable
private fun WeeklyPattern(report: TwoDAnalysisReport) {
    val a = report.a03
    Hero("စုံ/မ", top(a.nextParity))
    CompactRow("Weeks", Leader(a.matched.size.toString()), Modifier.fillMaxWidth())
}

@Composable
private fun Absence(report: TwoDAnalysisReport) {
    listOf("AM" to "morning", "PM" to "evening", "ALL" to "combined")
        .forEachIndexed { i, (label, key) ->
            if (i > 0) DividerHairline()
            val stream = report.a06[key]
            val result = if (stream == null || stream.observedDraws == 0) Leader("—")
                else absentTop(stream.fullNumbers, stream.hasUnknownOpenSlots)
            CompactRow(label, result, Modifier.fillMaxWidth(), labelWidth = 25,
                accent = true, showGapChip = true)
        }
}

@Composable
private fun PreviousResults(report: TwoDAnalysisReport) {
    val a = report.a08
    listOf("BOTH" to a.both.frequencies, "1st" to a.firstOnly.frequencies,
        "2nd" to a.secondOnly.frequencies).forEachIndexed { i, (label, matches) ->
        if (i > 0) DividerHairline()
        CompactRow(label, top(matches), Modifier.fillMaxWidth(), "×", 34,
            accent = true)
    }
}

@Composable
private fun DigitStrip(label: String, digits: Collection<Char>) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = c.onSurfaceVariant, maxLines = 1)
        if (digits.isEmpty()) {
            ResultChip("0")
        } else digits.toList().chunked(5).forEach { chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                chunk.forEach { digit ->
                    Surface(modifier = Modifier.weight(1f), color = c.secondaryContainer,
                        shape = RoundedCornerShape(7.dp)) {
                        Box(Modifier.defaultMinSize(minHeight = 26.dp),
                            contentAlignment = Alignment.Center) {
                            Text(digit.toString(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, color = c.onSecondaryContainer)
                        }
                    }
                }
                repeat(5 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WeeklyDigits(report: TwoDAnalysisReport) {
    val a = report.a05
    DigitStrip(if (a.completeThroughTarget) "LEFT" else "UNSEEN", a.remainingDigits)
    val latest = a.transitions.lastOrNull()
    if (latest != null && latest.classificationVerified) {
        DividerHairline()
        DigitStrip("NEW", latest.newlySeenDigits)
    }
}

@Composable
private fun Specials(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a07
    listOf("အပူး", "ညီကို", "ပါဝါ", "နက္ခတ်").forEachIndexed { i, key ->
        if (i > 0) DividerHairline()
        CompactRow(twoDGroupLabel(key, en), Leader((a.groups[key] ?: 0).toString()),
            Modifier.fillMaxWidth(), labelWidth = 0)
    }
    DividerHairline()
    CompactRow(if (en) "Repeat" else "ပြန်ကျ",
        Leader(a.repeatedNumbers.sumOf { it.count }.toString()), Modifier.fillMaxWidth())
}

@Composable
private fun PriorWeek(report: TwoDAnalysisReport) {
    val a = report.a04
    Hero(if (a.complete) "TOP 0–9" else "SEEN TOP",
        top(a.singleDigitFrequency))
    DividerHairline()
    DigitStrip(if (a.complete) "MISSING" else "UNSEEN", a.remainingDigits)
}

@Composable
private fun MissingReturns(report: TwoDAnalysisReport) {
    val a = report.a01
    val order = a.results.sortedWith(
        compareByDescending<TwoDMissingDigitReturn> { it.thisWeekFirstSeen != null }
            .thenByDescending { it.historyFirstReturnEpisodes }.thenBy { it.digit }
    )
    order.take(2).forEachIndexed { i, item ->
        if (i > 0) DividerHairline()
        val max = item.commonCompanions.maxOfOrNull { it.count } ?: 0
        val tied = item.commonCompanions.filter { max > 0 && it.count == max }
        val result = if (tied.isEmpty()) Leader("—")
            else winners(tied.map { it.companion.toString() }, max)
        CompactRow(item.digit.toString() + " →", result, Modifier.fillMaxWidth())
    }
    if (order.size > 2) {
        Text("+" + (order.size - 2), modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
