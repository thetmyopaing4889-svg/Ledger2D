package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Visual sandbox ONLY. All values below are invented for layout stress tests.
 * This file does not read/write Room, call the network or modify the engine.
 * The experimental cards are deliberately not detail navigation controls.
 */
internal data class PreviewNumber(val value: String, val count: String)
internal data class PreviewAbsence(
    val session: String, val digit: PreviewNumber, val pair: PreviewNumber,
)

internal object TwoDFixedDemoSamples {
    val absent = listOf(
        PreviewAbsence("AM", PreviewNumber("3", "11+"),
            PreviewNumber("34", "423+")),
        PreviewAbsence("PM", PreviewNumber("6", "13+"),
            PreviewNumber("93", "417+")),
        PreviewAbsence("ALL", PreviewNumber("3", "14+"),
            PreviewNumber("93", "451+")),
    )
    val previous = listOf(
        "Both" to PreviewNumber("78", "3"),
        "First" to PreviewNumber("02·46", "1"),
        "Second" to PreviewNumber("31", "2"),
    )
    val special = listOf("အပူး" to "4", "ညီအကို" to "5",
        "ပါဝါ" to "3", "နက္ခတ်" to "2", "ပြန်ကျ" to "2")
    val missingDigits = listOf("1", "4", "6", "8")
    val firstSeen = listOf("0", "2", "5")
    val lastWeekMissing = listOf("2", "5", "9")
    val returns = listOf(
        "2" to PreviewNumber("7", "5"),
        "5" to PreviewNumber("3", "4"),
        "9" to PreviewNumber("8", "2"),
    )
}

/**
 * The parent gives this grid ONLY the space remaining after app bar, date,
 * timeline tabs, history selector and bottom navigation.
 * 4 results: 2×2 equal cells; 2 results: 1×2 equal full-height cells.
 * No height depends on data availability and no scroll is attached here.
 */
@Composable
internal fun TwoDFixedDemoGrid(
    group: String,
    en: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxHeight < 370.dp || maxWidth < 310.dp
        val gutter = if (compact) 6.dp else 9.dp
        if (group == "1") {
            Column(Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(gutter)) {
                Row(Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gutter)) {
                    PreviewTile(if (en) "Most seen" else "အထွက်အများဆုံး",
                        Modifier.weight(1f).fillMaxHeight(), compact) {
                        MostSeenPreview(en, compact)
                    }
                    PreviewTile(if (en) "Missing digit / pair"
                        else "ပျောက်ဂဏန်း / တွဲ",
                        Modifier.weight(1f).fillMaxHeight(), compact) {
                        MissingPreview(en, compact)
                    }
                }
                Row(Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gutter)) {
                    PreviewTile(if (en) "Parity pattern" else "စုံ/မ ပုံစံ",
                        Modifier.weight(1f).fillMaxHeight(), compact) {
                        PatternPreview(en, compact)
                    }
                    PreviewTile(if (en) "Previous comparison" else "ရှေ့ထွက်နှိုင်းယှဉ်",
                        Modifier.weight(1f).fillMaxHeight(), compact) {
                        PreviousPreview(en, compact)
                    }
                }
            }
        } else {
            val isWeek = group == "2"
            Row(Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(gutter)) {
                if (isWeek) {
                    PreviewTile(if (en) "This week" else "ယခုအပတ်",
                        Modifier.weight(1f).fillMaxHeight(), compact) {
                        WeeklyPreview(en, compact)
                    }
                    PreviewTile(if (en) "Special groups" else "အထူးအုပ်စု",
                        Modifier.weight(1f).fillMaxHeight(), compact) {
                        SpecialsPreview(en, compact)
                    }
                } else {
                    PreviewTile(if (en) "Previous week" else "ပြီးခဲ့တဲ့အပတ်",
                        Modifier.weight(1f).fillMaxHeight(), compact) {
                        LastWeekPreview(en, compact)
                    }
                    PreviewTile(if (en) "Missing-digit return" else "ပျောက်ဂဏန်းပြန်ပေါ်",
                        Modifier.weight(1f).fillMaxHeight(), compact) {
                        ReturnPreview(en, compact)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTile(
    title: String,
    modifier: Modifier,
    compact: Boolean,
    body: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = c.surface, shadowElevation = 5.dp, tonalElevation = 1.dp,
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .18f))) {
        Column(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(c.surface,
                    c.secondaryContainer.copy(alpha = .29f)))
            ).padding(if (compact) 7.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold, color = c.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            body()
        }
    }
}

@Composable
private fun PreviewHero(
    label: String, number: PreviewNumber, modifier: Modifier,
    en: Boolean, compact: Boolean,
) {
    val c = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(10.dp),
        color = c.secondary, shadowElevation = 3.dp,
        border = BorderStroke(1.dp, c.onSecondary.copy(alpha = .17f))) {
        Column(
            Modifier.fillMaxSize().padding(if (compact) 6.dp else 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = c.onSecondary.copy(alpha = .83f), maxLines = 1)
            Text(number.value, style = if (compact)
                MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold, color = c.onSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(number.count + (if (en) " hits" else " ကြိမ်"),
                style = MaterialTheme.typography.labelSmall,
                color = c.onSecondary, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PreviewLine(
    label: String, value: String, compact: Boolean,
) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = if (compact) 20.dp else 23.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = c.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = c.secondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MostSeenPreview(en: Boolean, compact: Boolean) {
    Row(Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        PreviewHero("2D", PreviewNumber("52", "6"),
            Modifier.weight(1f).fillMaxHeight(), en, compact)
        PreviewHero("0–9", PreviewNumber("1", "41"),
            Modifier.weight(1f).fillMaxHeight(), en, compact)
    }
    HorizontalDivider(thickness = .5.dp)
    PreviewLine(if (en) "Parity" else "စုံ/မ",
        (if (en) "EE" else "စုံစုံ") + " ×45", compact)
    PreviewLine(if (en) "Group" else "အထူး",
        (if (en) "Sibling" else "ညီအကို") + " ×33", compact)
    PreviewLine(if (en) "10s / 1s" else "ထိပ် / ပိတ်",
        "4 ×21 · 2 ×22", compact)
}

@Composable
private fun TinyNumberWell(
    title: String, number: PreviewNumber, primary: Boolean,
    en: Boolean, modifier: Modifier,
) {
    val c = MaterialTheme.colorScheme
    Surface(modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (primary) c.secondaryContainer else c.tertiary.copy(alpha = .12f),
        border = BorderStroke(1.dp, if (primary) c.secondary.copy(alpha = .18f)
            else c.tertiary.copy(alpha = .22f))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant, maxLines = 1)
            Text(number.value, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (primary) c.secondary else c.onSurface,
                maxLines = 1)
            Text(number.count + (if (en) " draws" else " ကြိမ်"),
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MissingPreview(en: Boolean, compact: Boolean) {
    TwoDFixedDemoSamples.absent.forEachIndexed { index, slot ->
        if (index != 0) HorizontalDivider(thickness = .5.dp)
        Row(Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(slot.session, modifier = Modifier.width(if (compact) 21.dp else 25.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            TinyNumberWell(if (en) "Digit" else "ဂဏန်း",
                slot.digit, primary = true, en = en,
                modifier = Modifier.weight(1.05f).fillMaxHeight())
            TinyNumberWell(if (en) "Pair" else "2D တွဲ",
                slot.pair, primary = false, en = en,
                modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun PreviewBadge(
    value: String, modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp),
        color = if (primary) c.secondary else c.secondaryContainer,
        shadowElevation = if (primary) 3.dp else 0.dp) {
        Box(Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center) {
            Text(value, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (primary) c.onSecondary else c.secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PatternPreview(en: Boolean, compact: Boolean) {
    val c = MaterialTheme.colorScheme
    Text(if (en) "Most common next class" else "အများဆုံး ဆက်ထွက်",
        style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    Spacer(Modifier.weight(1f))
    PreviewBadge(if (en) "Odd–Even" else "မစုံ", primary = true)
    PreviewLine(if (en) "Frequency" else "အကြိမ်ရေ", "6", compact)
    PreviewLine(if (en) "Matching weeks" else "ပုံစံတူအပတ်", "8", compact)
    Spacer(Modifier.weight(1f))
}

@Composable
private fun PreviousPreview(en: Boolean, compact: Boolean) {
    val c = MaterialTheme.colorScheme
    PreviewLine(if (en) "Prior" else "ရှေ့ထွက်", "51 · 05", compact)
    HorizontalDivider(thickness = .5.dp)
    TwoDFixedDemoSamples.previous.forEachIndexed { index, (label, result) ->
        Row(Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (en) label else when (index) {
                0 -> "နှစ်ခု"; 1 -> "ပထမ"; else -> "ဒုတိယ"
            }, style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant, modifier = Modifier.width(33.dp), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(result.value, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = c.secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("×" + result.count, style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun TokenStrip(digits: List<String>) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        digits.forEach { digit ->
            PreviewBadge(digit, Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeeklyPreview(en: Boolean, compact: Boolean) {
    val c = MaterialTheme.colorScheme
    Text(if (en) "Unseen digits (0–9)" else "ပျောက်ဂဏန်း (0–9)",
        style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    Spacer(Modifier.weight(1f))
    TokenStrip(TwoDFixedDemoSamples.missingDigits)
    Spacer(Modifier.weight(1f))
    HorizontalDivider(thickness = .5.dp)
    Text(if (en) "New this week" else "ဒီအပတ် အသစ်ထွက်",
        style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    TokenStrip(TwoDFixedDemoSamples.firstSeen)
    Spacer(Modifier.weight(1f))
    PreviewLine(if (en) "Recorded" else "မှတ်တမ်း", "7/8", compact)
}

@Composable
private fun SpecialsPreview(en: Boolean, compact: Boolean) {
    TwoDFixedDemoSamples.special.forEachIndexed { i, (label, value) ->
        if (i != 0) HorizontalDivider(thickness = .5.dp)
        Row(Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (en) when (label) {
                "အပူး" -> "Double"; "ညီအကို" -> "Sibling";
                "ပါဝါ" -> "Power"; "နက္ခတ်" -> "Star"; else -> "Repeat"
            } else label, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            PreviewBadge(value)
        }
    }
}

@Composable
private fun LastWeekPreview(en: Boolean, compact: Boolean) {
    val c = MaterialTheme.colorScheme
    Text(if (en) "Most observed digit" else "အထွက်အများဆုံးဂဏန်း",
        style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    Spacer(Modifier.weight(1f))
    PreviewBadge("8", Modifier.fillMaxWidth(), primary = true)
    PreviewLine(if (en) "Seen" else "ထွက်ရှိမှု", "7 ကြိမ်", compact)
    Spacer(Modifier.weight(1f))
    HorizontalDivider(thickness = .5.dp)
    Text(if (en) "Missing last week" else "ပြီးခဲ့တဲ့အပတ် ပျောက်",
        style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    TokenStrip(TwoDFixedDemoSamples.lastWeekMissing)
    Spacer(Modifier.weight(1f))
}

@Composable
private fun ReturnPreview(en: Boolean, compact: Boolean) {
    val c = MaterialTheme.colorScheme
    Text(if (en) "Missing → Companion" else "ပျောက် → တွဲထွက်",
        style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    Spacer(Modifier.weight(1f))
    TwoDFixedDemoSamples.returns.forEachIndexed { i, (missing, companion) ->
        if (i != 0) HorizontalDivider(thickness = .5.dp)
        Row(Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            PreviewBadge(missing)
            Text("→", style = MaterialTheme.typography.titleMedium,
                color = c.onSurfaceVariant)
            PreviewBadge(companion.value, primary = true)
            Spacer(Modifier.weight(1f))
            Text(companion.count + (if (en) " hits" else " ကြိမ်"),
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant, maxLines = 1)
        }
    }
    Spacer(Modifier.weight(1f))
}
