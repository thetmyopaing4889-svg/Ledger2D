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

/**
 * Home grid = visual summary, NEVER a compressed detail screen.
 * Each actual result is an inset object; no method title, sentence,
 * explanation, or old QuickFact preview is displayed in these tiles.
 */
@Composable
internal fun TwoDAnalysisGroupTag(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
        color = if (selected) c.secondaryContainer else c.surface,
        shadowElevation = if (selected) 3.dp else 1.dp,
        border = BorderStroke(
            1.dp, if (selected) c.secondary.copy(alpha = 0.55f)
            else c.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Box(Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) c.onSecondaryContainer else c.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 2,
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
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        (0..1).forEach { column ->
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                checks.filterIndexed { i, _ -> i % 2 == column }.forEach { id ->
                    ResultTile(id = id, report = report, en = en,
                        onClick = { onOpenDetail(id) })
                }
            }
        }
    }
}

private data class TopFinding(val value: String, val count: Int?, val atLeast: Boolean = false)

private fun topOf(values: List<TwoDCount>): TopFinding {
    val maximum = values.maxOfOrNull { it.count } ?: 0
    if (maximum <= 0) return TopFinding("—", null)
    val winners = values.filter { it.count == maximum }.map { it.digit }
    return topOfWinners(winners, maximum)
}

private fun topOfMap(values: Map<String, Int>, label: (String) -> String): TopFinding {
    val maximum = values.values.maxOrNull() ?: 0
    if (maximum <= 0) return TopFinding("—", null)
    return topOfWinners(values.filterValues { it == maximum }.keys.map(label), maximum)
}

private fun topOfWinners(winners: List<String>, count: Int): TopFinding =
    when (winners.size) {
        0 -> TopFinding("—", null)
        1 -> TopFinding(winners.single(), count)
        2 -> TopFinding(winners.joinToString("·"), count)
        else -> TopFinding(winners.size.toString() + " tied", count)
    }

private fun topAbsent(values: List<TwoDStreak>, incomplete: Boolean): TopFinding {
    val maximum = values.maxOfOrNull { it.observedDrawsSince } ?: 0
    val winners = values.filter { it.observedDrawsSince == maximum }
    if (winners.isEmpty()) return TopFinding("—", null)
    val data = topOfWinners(winners.map { it.value }, maximum)
    // A '+' warns that gaps/history edges may understate actual absence,
    // without presenting a false exact measurement.
    return data.copy(atLeast = incomplete || winners.any { it.historyStartTruncated })
}

@Composable
private fun ResultTile(
    id: String,
    report: TwoDAnalysisReport,
    en: Boolean,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(19.dp),
        color = c.surface,
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = 0.62f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when (id) {
                "02" -> SameSlotSummary(report, en)
                "03" -> PatternSummary(report, en)
                "06" -> MissingPairSummary(report, en)
                "08" -> PriorMatchSummary(report, en)
                "05" -> WeekFlowSummary(report, en)
                "07" -> SpecialSummary(report, en)
                "04" -> PreviousWeekSummary(report, en)
                "01" -> MissingReturnSummary(report, en)
            }
            Row(
                Modifier.fillMaxWidth().height(13.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ChevronRight, null,
                    modifier = Modifier.size(14.dp),
                    tint = c.onSurfaceVariant.copy(alpha = 0.65f))
            }
        }
    }
}

/**
 * A raised inset layer distinguishes results from all surrounding text.
 * Big values use theme secondary (deep wine in light theme) and count is
 * its own small, distinctly shaded capsule rather than inline prose.
 */
@Composable
private fun FindingPanel(
    label: String,
    finding: TopFinding,
    modifier: Modifier = Modifier,
    hero: Boolean = false,
    suffix: String = "×",
) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (hero) 14.dp else 12.dp),
        color = if (hero) c.secondaryContainer.copy(alpha = 0.70f)
            else c.surfaceVariant.copy(alpha = 0.72f),
        shadowElevation = if (hero) 2.dp else 0.dp,
        border = BorderStroke(1.dp, if (hero) c.secondary.copy(alpha = 0.15f)
            else c.outlineVariant.copy(alpha = 0.38f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(
                horizontal = if (hero) 8.dp else 6.dp,
                vertical = if (hero) 8.dp else 6.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = c.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                finding.value,
                style = if (hero && finding.value.length <= 3)
                    MaterialTheme.typography.headlineMedium
                else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (finding.value == "—") c.onSurfaceVariant.copy(alpha = 0.48f)
                    else c.secondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (finding.count != null) {
                CountCapsule(suffix + finding.count.toString())
            }
        }
    }
}

@Composable
private fun CountCapsule(text: String) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(9.dp)) {
        Text(text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = c.onSurface,
            maxLines = 1)
    }
}

@Composable
private fun EmptyFinding(status: String) {
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(14.dp),
        color = c.surfaceVariant.copy(alpha = 0.48f)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("—", style = MaterialTheme.typography.headlineMedium,
                color = c.onSurfaceVariant.copy(alpha = 0.45f),
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(status, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = c.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FindingRow(label: String, finding: TopFinding, suffix: String = "×") {
    val c = MaterialTheme.colorScheme
    Surface(
        color = c.surfaceVariant.copy(alpha = 0.62f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = 0.32f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = c.onSurfaceVariant,
                maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(finding.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold, color = c.secondary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (finding.count != null) {
                CountCapsule(suffix + finding.count + if (finding.atLeast) "+" else "")
            }
        }
    }
}

@Composable
private fun DigitTokens(label: String, digits: Collection<Char>) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.surfaceVariant.copy(alpha = 0.65f),
        shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium, color = c.onSurfaceVariant,
                maxLines = 1)
            if (digits.isEmpty()) {
                Text("—", style = MaterialTheme.typography.titleMedium,
                    color = c.onSurfaceVariant.copy(alpha = 0.50f))
            } else {
                digits.toList().chunked(5).forEach { rowDigits ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        rowDigits.forEach { digit ->
                            Surface(Modifier.weight(1f),
                                color = c.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)) {
                                Box(Modifier.heightIn(min = 26.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text(digit.toString(),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold, color = c.onSecondaryContainer)
                                }
                            }
                        }
                        repeat(5 - rowDigits.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SameSlotSummary(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a02
    if (a.samples == 0) { EmptyFinding("No data"); return }
    val parity = topOfMap(a.parity) { it }
    val groups = topOfMap(a.groups) { if (it == "ညီကို") "ညီအကို" else it }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FindingPanel("2D", topOf(a.numbers), Modifier.weight(1f), hero = true)
        FindingPanel("0–9", topOf(a.singles), Modifier.weight(1f), hero = true)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FindingPanel("စုံ/မ", parity, Modifier.weight(1f))
        FindingPanel("Group", groups, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FindingPanel(if (en) "Tens" else "ထိပ်", topOf(a.tens), Modifier.weight(1f))
        FindingPanel(if (en) "Ones" else "ပိတ်", topOf(a.ones), Modifier.weight(1f))
    }
}

@Composable
private fun PatternSummary(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a03
    if (!a.eligible) { EmptyFinding("4+ draws"); return }
    if (a.matched.isEmpty()) { EmptyFinding("No match"); return }
    FindingPanel(if (en) "Next parity" else "စုံ/မ",
        topOfMap(a.nextParity) { it }, hero = true)
    FindingRow("Weeks", TopFinding(a.matched.size.toString(), null))
}

@Composable
private fun MissingPairSummary(report: TwoDAnalysisReport, en: Boolean) {
    // Independent AM, PM and combined streams; never reuse the target
    // session stream as an answer for all three.
    listOf(
        "AM" to "morning", "PM" to "evening", "ALL" to "combined",
    ).forEach { (label, key) ->
        val a = report.a06[key]
        val data = if (a == null || a.observedDraws == 0)
            TopFinding("—", null)
        else topAbsent(a.fullNumbers, a.hasUnknownOpenSlots)
        FindingRow(label, data, suffix = "Gap ")
    }
}

@Composable
private fun PriorMatchSummary(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a08
    if (a.firstReference == null || a.secondReference == null) {
        EmptyFinding("No data")
        return
    }
    // Each of the three independent historical searches has its OWN next
    // target 2D frequency distribution. Display the most observed follow-on.
    listOf(
        "BOTH" to a.both.frequencies,
        "1st" to a.firstOnly.frequencies,
        "2nd" to a.secondOnly.frequencies,
    ).forEach { (label, values) ->
        FindingRow(label, topOf(values))
    }
}

@Composable
private fun WeekFlowSummary(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a05
    if (a.transitions.isEmpty()) { EmptyFinding("No data"); return }
    DigitTokens("LEFT", a.remainingDigits)
    val last = a.transitions.last()
    if (last.classificationVerified) DigitTokens("NEW", last.newlySeenDigits)
}

@Composable
private fun SpecialSummary(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a07
    if (a.observedDraws == 0) { EmptyFinding("No data"); return }
    val named = listOf("အပူး", "ညီကို", "ပါဝါ", "နက္ခတ်")
    named.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pair.forEach { key ->
                FindingPanel(
                    if (key == "ညီကို") "ညီအကို" else key,
                    TopFinding((a.groups[key] ?: 0).toString(), null),
                    Modifier.weight(1f),
                )
            }
        }
    }
    FindingRow(if (en) "Repeat" else "ပြန်ကျ",
        TopFinding(a.repeatedNumbers.sumOf { it.count }.toString(), null))
}

@Composable
private fun PreviousWeekSummary(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a04
    if (a.observedDraws == 0) { EmptyFinding("No data"); return }
    FindingPanel("TOP 0–9", topOf(a.singleDigitFrequency), hero = true)
    DigitTokens("MISSING", a.remainingDigits)
}

@Composable
private fun MissingReturnSummary(report: TwoDAnalysisReport, en: Boolean) {
    val a = report.a01
    if (report.a04.observedDraws == 0) { EmptyFinding("No data"); return }
    if (a.missingDigits.isEmpty()) { EmptyFinding("None"); return }
    // Compact first-two previews; each source missing digit retains its own
    // independently counted FIRST historical return companion data.
    val ordered = a.results.sortedWith(
        compareByDescending<com.myanmar.ledger2d.core.domain.TwoDMissingDigitReturn> {
            it.thisWeekFirstSeen != null
        }.thenByDescending { it.historyFirstReturnEpisodes }.thenBy { it.digit }
    )
    ordered.take(2).forEach { item ->
        val highest = item.commonCompanions.maxOfOrNull { it.count } ?: 0
        val partners = item.commonCompanions.filter { it.count == highest && highest > 0 }
        val top = if (partners.isEmpty()) TopFinding("—", null)
        else topOfWinners(partners.map { it.companion.toString() }, highest)
        FindingRow(item.digit.toString() + " →", top)
    }
    if (ordered.size > 2) {
        val c = MaterialTheme.colorScheme
        Text("+" + (ordered.size - 2),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End, color = c.onSurfaceVariant)
    }
}
