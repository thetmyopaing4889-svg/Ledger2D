package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
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
        shadowElevation = if (selected) 5.dp else 2.dp,
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
    // One coherent grid, not two independent masonry columns. Current
    // draw: two aligned lead tiles, followed by two FULL-WIDTH rows.
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (group) {
            "1" -> {
                val bothPopulated = report.a02.samples > 0 &&
                    report.a06.values.any { it.observedDraws > 0 }
                Row(
                    Modifier.fillMaxWidth().then(
                        if (bothPopulated) Modifier.height(IntrinsicSize.Max) else Modifier
                    ),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    ResultTile(
                        "02", report, en,
                        onClick = { onOpenDetail("02") },
                        modifier = Modifier.weight(1f).then(
                            if (bothPopulated) Modifier.fillMaxHeight() else Modifier
                        ),
                        expand = bothPopulated,
                    )
                    ResultTile(
                        "06", report, en,
                        onClick = { onOpenDetail("06") },
                        modifier = Modifier.weight(1f).then(
                            if (bothPopulated) Modifier.fillMaxHeight() else Modifier
                        ),
                        expand = bothPopulated,
                    )
                }
                ResultTile("03", report, en,
                    onClick = { onOpenDetail("03") }, modifier = Modifier.fillMaxWidth())
                ResultTile("08", report, en,
                    onClick = { onOpenDetail("08") }, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                val checks = if (group == "2") listOf("05", "07")
                    else listOf("04", "01")
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    checks.forEach { id ->
                        ResultTile(id, report, en,
                            onClick = { onOpenDetail(id) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            expand = true)
                    }
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
private fun ResultTile(
    id: String,
    report: TwoDAnalysisReport,
    en: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expand: Boolean = false,
) {
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
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = shape,
        color = c.surface,
        tonalElevation = 1.dp,
        shadowElevation = 7.dp,
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .13f)),
    ) {
        Box(
            Modifier.fillMaxWidth().then(
                if (expand) Modifier.fillMaxHeight() else Modifier
            ).background(
                Brush.verticalGradient(
                    listOf(c.surface.copy(alpha = .99f),
                        c.secondaryContainer.copy(alpha = .32f))
                ),
                shape,
            ),
        ) {
            Column(
                Modifier.fillMaxWidth().then(
                    if (expand) Modifier.fillMaxHeight() else Modifier
                ).padding(horizontal = 12.dp, vertical = if (unavailable) 12.dp else 14.dp),
                verticalArrangement = if (expand) Arrangement.SpaceBetween
                    else Arrangement.spacedBy(11.dp),
            ) {
                TileHeading(id, en)
                if (unavailable) {
                    val status = when {
                        id == "03" && !report.a03.eligible ->
                            if (en) "Need more history" else "မှတ်တမ်းမလုံလောက်"
                        id == "03" ->
                            if (en) "No matching pattern" else "ပုံစံတူမတွေ့"
                        id == "01" && !report.a01.previousWeekComplete ->
                            if (en) "Not verified" else "မစစ်ဆေးနိုင်သေး"
                        id == "01" && report.a04.observedDraws > 0 ->
                            if (en) "None missing" else "ပျောက်ဂဏန်းမရှိ"
                        else -> if (en) "No data" else "မှတ်တမ်းမရှိ"
                    }
                    EmptyCompact(status)
                } else {
                    when (id) {
                        "02" -> SameSlot(report, en)
                        "03" -> WeeklyPattern(report, en)
                        "06" -> Absence(report, en)
                        "08" -> PreviousResults(report, en)
                        "05" -> WeeklyDigits(report)
                        "07" -> Specials(report, en)
                        "04" -> PriorWeek(report, en)
                        "01" -> MissingReturns(report)
                    }
                }
            }
            Icon(
                Icons.Default.ChevronRight, null,
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 4.dp).size(12.dp),
                tint = c.onSurfaceVariant.copy(alpha = .60f),
            )
        }
    }
}

@Composable
private fun TileHeading(id: String, en: Boolean) {
    val c = MaterialTheme.colorScheme
    val label = when (id) {
        "02" -> if (en) "Most seen" else "အထွက်အများဆုံး"
        "06" -> if (en) "Missing digits / pairs" else "ပျောက်ဂဏန်း / ပျောက်ဂဏန်းတွဲ"
        "03" -> if (en) "Parity pattern" else "စုံ/မ"
        "08" -> if (en) "Previous-result comparison" else "ရှေ့ထွက်နှိုင်းယှဉ်"
        "05" -> if (en) "This week" else "ယခုအပတ်"
        "07" -> if (en) "Special groups" else "အထူးအုပ်စု"
        "04" -> if (en) "Last week" else "ပြီးခဲ့တဲ့အပတ်"
        else -> if (en) "Missing digit return" else "ပျောက်ဂဏန်းပြန်ပေါ်"
    }
    Row(
        Modifier.fillMaxWidth().padding(end = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(3.dp), color = c.secondary,
            modifier = Modifier.width(4.dp).height(20.dp),
        ) {}
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = c.onSurface, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyCompact(status: String, meaning: String? = null) {
    val c = MaterialTheme.colorScheme
    if (meaning != null) {
        // Ineligible is not the same as a zero result. This short two-line
        // status retains the method's meaning without a large empty tile.
        Column(Modifier.fillMaxWidth().heightIn(min = 65.dp)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(meaning, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant)
            Text(status, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium, color = c.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Row(Modifier.fillMaxWidth().heightIn(min = 38.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Text("—", style = MaterialTheme.typography.titleMedium,
                color = c.onSurfaceVariant.copy(alpha = .45f))
            Spacer(Modifier.width(7.dp))
            Text(status, style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** A single inset well only for the two genuinely dominant figures. */
@Composable
private fun Hero(label: String, result: Leader, modifier: Modifier = Modifier,
    en: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    Surface(modifier = modifier.heightIn(min = 138.dp),
        shape = RoundedCornerShape(12.dp),
        color = c.secondary, shadowElevation = 6.dp,
        border = BorderStroke(1.dp, c.onSecondary.copy(alpha = .23f))) {
        Column(
            Modifier.fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(c.secondary.copy(alpha = .91f), c.secondary)
                    )
                )
                .padding(horizontal = 11.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
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
                    Text(result.count.toString() + if (en) " hits" else " ကြိမ်",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
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

/** A flat label / inset value / count, without a nested bordered card. */
@Composable
private fun CompactRow(label: String, result: Leader, modifier: Modifier = Modifier,
    countPrefix: String = "×", labelWidth: Int = 0,
    accent: Boolean = false,
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
        if (result.count != null) {
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
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Hero("2D", top(a.numbers), Modifier.weight(1f), en)
        Hero("0–9", top(a.singles), Modifier.weight(1f), en)
    }
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = c.surface.copy(alpha = .86f),
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .12f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactRow(if (en) "Parity" else "စုံ/မ",
                top(a.parity), Modifier.fillMaxWidth())
            DividerHairline()
            CompactRow(if (en) "Group" else "အထူး",
                top(a.groups) { twoDGroupLabel(it, en) }, Modifier.fillMaxWidth())
        }
    }
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = c.surface.copy(alpha = .86f),
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .12f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            ShortPosition(if (en) "10s" else "ထိပ်",
                top(a.tens), Modifier.weight(1f))
            ShortPosition(if (en) "1s" else "ပိတ်",
                top(a.ones), Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeeklyPattern(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a03
    Hero(if (en) "Parity" else "စုံ/မ", top(a.nextParity), en = en)
    CompactRow("Weeks", Leader(a.matched.size.toString()), Modifier.fillMaxWidth())
}

/**
 * Main-grid A-06: the missing SINGLE DIGIT is the hero. The missing
 * exact 2D pair is always shown next to it, never omitted. Each stream
 * uses its own domain data; the small counts are secondary evidence.
 */
@Composable
private fun MissingNumberWell(
    label: String,
    result: Leader,
    primary: Boolean,
    en: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    val shownValue = when {
        result.tiedValues.size in 3..4 -> result.tiedValues.joinToString("·")
        result.tiedValues.size > 4 ->
            result.tiedValues.take(3).joinToString("·") +
                " +" + (result.tiedValues.size - 3)
        else -> result.value
    }
    Surface(
        modifier = modifier.heightIn(min = 98.dp),
        shape = RoundedCornerShape(11.dp),
        color = if (primary) c.secondaryContainer else c.tertiary.copy(alpha = .10f),
        border = BorderStroke(
            1.dp, if (primary) c.secondary.copy(alpha = .24f)
                else c.tertiary.copy(alpha = .30f),
        ),
        shadowElevation = if (primary) 5.dp else 3.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                label, style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (hasTwoNumericTies(result)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    result.tiedValues.forEach { number ->
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = if (primary) c.surface.copy(alpha = .70f)
                                else c.secondaryContainer,
                        ) {
                            Text(
                                number,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (primary) c.secondary else c.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else {
                Text(
                    shownValue,
                    style = when {
                        shownValue.length > 5 -> MaterialTheme.typography.labelLarge
                        primary -> MaterialTheme.typography.headlineMedium
                        else -> MaterialTheme.typography.titleLarge
                    },
                    fontWeight = FontWeight.ExtraBold,
                    color = if (shownValue == "—") c.onSurfaceVariant
                        else if (primary) c.secondary else c.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // Keep count clearly subordinate and NEVER truncate a
            // three-digit streak inside a half-column well.
            if (result.count == null) {
                Text("—", style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        result.count.toString() +
                            (if (result.minOnly) "+" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium, color = c.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        if (en) "draws" else "ကြိမ်",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Normal, color = c.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AbsenceRow(
    label: String,
    digit: Leader,
    pair: Leader,
    en: Boolean,
) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = c.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (en) "Not seen" else "မထွက်သေး",
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MissingNumberWell(
                if (en) "Digit" else "ဂဏန်း",
                digit, primary = true, en = en, modifier = Modifier.weight(1.12f),
            )
            MissingNumberWell(
                if (en) "2D Pair" else "2D တွဲ",
                pair, primary = false, en = en, modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Absence(report: TwoDAnalysisReport, en: Boolean) {
    listOf(
        "AM" to "morning",
        "PM" to "evening",
        "ALL" to "combined",
    ).forEachIndexed { index, (label, key) ->
        if (index > 0) DividerHairline()
        val stream = report.a06[key]
        val digit = if (stream == null || stream.observedDraws == 0) Leader("—")
            else absentTop(stream.singleDigits, stream.hasUnknownOpenSlots)
        val pair = if (stream == null || stream.observedDraws == 0) Leader("—")
            else absentTop(stream.fullNumbers, stream.hasUnknownOpenSlots)
        AbsenceRow(label = label, digit = digit, pair = pair, en = en)
    }
}

/** Full-width A-08 is a comparison dashboard rather than a tall thin list. */
@Composable
private fun ComparisonPanel(
    label: String,
    result: Leader,
    en: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.heightIn(min = 94.dp),
        color = c.surface.copy(alpha = .93f),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .15f)),
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                label, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant,
                maxLines = 1,
            )
            RankedChips(result, accent = true)
            Text(
                if (result.count == null) "—" else
                    result.count.toString() +
                    (if (en) " hits" else " ကြိမ်"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium, color = c.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviousResults(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a08
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            if (en) "Reference" else "ရှေ့ထွက်ဂဏန်း",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium, color = c.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        ResultChip(a.firstReference?.digit ?: "—", accent = true)
        ResultChip(a.secondReference?.digit ?: "—", accent = true)
    }
    DividerHairline()
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        listOf(
            (if (en) "Both" else "နှစ်ခု") to a.both.frequencies,
            (if (en) "1st" else "ပထမ") to a.firstOnly.frequencies,
            (if (en) "2nd" else "ဒုတိယ") to a.secondOnly.frequencies,
        ).forEach { (label, frequencies) ->
            ComparisonPanel(
                label, top(frequencies), en, modifier = Modifier.weight(1f),
            )
        }
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
private fun PriorWeek(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a04
    Hero(if (a.complete) "TOP 0–9" else "SEEN TOP",
        top(a.singleDigitFrequency), en = en)
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
