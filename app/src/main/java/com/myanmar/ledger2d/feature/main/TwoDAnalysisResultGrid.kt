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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.domain.TwoDAnalysisReport

/**
 * Navigation and content have separate priorities: the three compact
 * timeline tabs take one row, and each of the eight checks gives its real
 * result directly within the selected two-column grid.
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
        border = BorderStroke(1.dp,
            if (selected) scheme.primary.copy(alpha = 0.45f)
            else scheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Box(Modifier.padding(horizontal = 3.dp, vertical = 7.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        checks.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { id ->
                    TwoDGridFinding(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        id = id, report = report, en = en,
                        onClick = { onOpenDetail(id) },
                    )
                }
            }
        }
    }
}

private fun hasData(id: String, report: TwoDAnalysisReport): Boolean = when (id) {
    "02" -> report.a02.samples > 0
    "03" -> report.a03.eligible
    "06" -> report.a06.values.any { it.observedDraws > 0 }
    "08" -> report.a08.firstReference != null && report.a08.secondReference != null
    "05" -> report.a05.transitions.isNotEmpty()
    "07" -> report.a07.observedDraws > 0
    "04" -> report.a04.observedDraws > 0
    "01" -> report.a04.observedDraws > 0
    else -> false
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
    val facts = twoDQuickFacts(id, report, en)
    val ready = hasData(id, report)
    val title = when (id) {
        "02" -> tr("နေ့တူ၊ အကြိမ်တူ", "Same weekday/draw")
        "03" -> tr("အပတ်ပုံစံတူ", "Weekly pattern")
        "06" -> tr("မထွက်တာကြာ", "Absence streaks")
        "08" -> tr("ရှေ့အကြိမ်နှိုင်းယှဉ်", "Prior-draw matches")
        "05" -> tr("ဂဏန်း အသစ်/အဟောင်း", "New/old digits")
        "07" -> tr("အထူးအုပ်စု / ပြန်ကျ", "Specials & repeats")
        "04" -> tr("အရင်အပတ် အခြေအနေ", "Previous-week review")
        else -> tr("ပျောက်ဂဏန်း ပြန်ပေါ်", "Missing-digit returns")
    }
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(16.dp),
                    tint = scheme.primary)
            }

            if (!ready) {
                Text(tr("မှတ်တမ်းမပြည့်သေး", "Not enough records"),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant, maxLines = 2)
            } else if (id == "02" && facts.size >= 3) {
                // Pair and single-digit evidence are related but use different denominators.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    facts.take(2).forEachIndexed { index, fact ->
                        Column(Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (index == 0) tr("ဂဏန်းတွဲ", "Pair") else tr("ဂဏန်း", "Digit"),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant, maxLines = 1)
                            Text(fact.value, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold, color = scheme.onSurface,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(fact.note, style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant, maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("အနည်းဆုံး", "Least digit"),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant, maxLines = 1)
                    Text(facts[2].value + " · " + facts[2].note,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(tr("မှတ်တမ်း ", "Sample ") + report.a02.samples,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant)
            } else if (id == "07" && facts.isNotEmpty()) {
                // Real status for all four special groups, not just the largest.
                Text(facts[0].label, style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant, maxLines = 2)
                Text(facts[0].value, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, maxLines = 2)
                HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                report.a07.groups.entries.chunked(2).forEach { line ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        line.forEach { (name, count) ->
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(twoDGroupLabel(name, en),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(count.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (line.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (!report.a07.completeThroughTarget) {
                    Text(tr("မှတ်တမ်းမပြည့်", "Partial records"),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant)
                }
            } else if (facts.isNotEmpty()) {
                // A-01 starts with actual returned digits, avoiding A-04's repeated headline.
                val primary = if (id == "01" && facts.size > 1) facts[1] else facts[0]
                Text(primary.label, style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant, maxLines = 2)
                Text(primary.value, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = if (id == "04" || id == "05") 3 else 2,
                    overflow = TextOverflow.Ellipsis)
                if (primary.note.isNotBlank()) {
                    Text(primary.note, style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                val secondary = if (id == "01") facts[0] else facts.getOrNull(1)
                if (secondary != null) {
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    Text(secondary.label, style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant, maxLines = 2)
                    Text(secondary.value, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    if (secondary.note.isNotBlank() && id != "01") {
                        Text(secondary.note, style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
