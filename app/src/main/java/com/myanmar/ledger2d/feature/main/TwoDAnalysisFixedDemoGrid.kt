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
import androidx.compose.ui.graphics.Color
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
 * 4 results: 2×2 equal cells using the available screen height.
 * 2 results: equal SIDE-BY-SIDE cells measured from the taller one's
 * actual content (including Burmese line height), never full-height.
 * The preview remains non-scrollable; the real Analysis path is untouched.
 */
@Composable
internal fun TwoDFixedDemoGrid(
    group: String,
    en: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = group == "1" || maxHeight < 390.dp || maxWidth < 310.dp
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
            // Intrinsic measurement takes the TALLER card's actual
            // Burmese content height. The shorter card stretches only to
            // that common height. Avoid hard-coded row-count estimates:
            // they clipped A-01's third row on the user's phone.
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(gutter),
                verticalAlignment = Alignment.Top,
            ) {
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
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = c.surface,
        shadowElevation = 7.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .15f)),
    ) {
        Box(
            Modifier.fillMaxWidth().background(
                Brush.verticalGradient(
                    listOf(
                        c.surface,
                        c.secondaryContainer.copy(alpha = .32f),
                        c.secondaryContainer.copy(alpha = .53f),
                    ),
                ),
                shape = shape,
            ),
        ) {
            // A restrained specular rim is a glass-like highlight, not
            // an expensive runtime blur or opaque inner grey frame.
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                c.surface.copy(alpha = .16f),
                                Color.White.copy(alpha = .80f),
                                c.surface.copy(alpha = .16f),
                            ),
                        ),
                    ),
            )
            Column(
                Modifier.fillMaxWidth()
                    .padding(if (compact) 9.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.width(3.dp).height(17.dp)
                            .background(c.secondary, RoundedCornerShape(3.dp)),
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = c.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                body()
            }
        }
    }
}

@Composable
private fun PreviewHero(
    label: String, number: PreviewNumber, modifier: Modifier,
    en: Boolean, compact: Boolean,
) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Surface(
        modifier = modifier.height(if (compact) 88.dp else 106.dp),
        shape = shape,
        color = c.secondary,
        tonalElevation = 0.dp,
        shadowElevation = 5.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .24f)),
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        c.secondary.copy(alpha = .90f),
                        c.secondary,
                        c.secondary.copy(alpha = .97f),
                    ),
                ),
                shape,
            ),
        ) {
            Box(
                Modifier.fillMaxWidth().height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent,
                                Color.White.copy(alpha = .46f),
                                Color.Transparent),
                        ),
                    ),
            )
            Column(
                Modifier.fillMaxSize()
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp),
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = c.onSecondary.copy(alpha = .84f), maxLines = 1)
                Text(number.value,
                    style = if (compact) MaterialTheme.typography.titleLarge
                        else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold, color = c.onSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(number.count + if (en) " hits" else " ကြိမ်",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSecondary.copy(alpha = .95f),
                    fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PreviewLine(
    label: String, value: String, compact: Boolean,
) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 22.dp else 30.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = c.onSurfaceVariant, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = c.secondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A-02: the 2D and 0–9 results are the heroes. Its three other
 * supporting lines are kept directly underneath instead of spreading
 * to the bottom of an equal-sized 2x2 outer cell.
 */
@Composable
private fun ColumnScope.MostSeenPreview(en: Boolean, compact: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PreviewHero("2D", PreviewNumber("52", "6"),
            Modifier.weight(1f), en, compact)
        PreviewHero("0–9", PreviewNumber("1", "41"),
            Modifier.weight(1f), en, compact)
    }
    HorizontalDivider(thickness = .5.dp)
    PreviewLine(if (en) "Parity" else "စုံ/မ",
        (if (en) "EE" else "စုံစုံ") + " ×45", compact)
    PreviewLine(if (en) "Group" else "အထူး",
        (if (en) "Sibling" else "ညီအကို") + " ×33", compact)
    PreviewLine(if (en) "10s / 1s" else "ထိပ် / ပိတ်",
        "4 ×21 · 2 ×22", compact)
}

/**
 * Unlike the previous three-line / height-weighted well, the count is
 * always measured to its content. It can wrap to two lines on a small
 * phone instead of being cut at the bottom of a clipped fixed-height row.
 */
/**
 * A-06: ONE clean layer per result instead of a stacked heavy grey
 * frame around the pair. Labels, number and count each have their own
 * measured text line. This keeps the numeric hierarchy without clipping.
 */
@Composable
private fun TinyNumberWell(
    title: String, number: PreviewNumber, primary: Boolean,
    en: Boolean, modifier: Modifier,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = if (primary) c.secondaryContainer.copy(alpha = .73f)
            else c.surface.copy(alpha = .96f),
        border = BorderStroke(
            1.dp, if (primary) c.secondary.copy(alpha = .27f)
                else c.outlineVariant.copy(alpha = .80f),
        ),
        shadowElevation = 0.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Two-line compact hierarchy: label + primary number on
            // the first line, count on the second. All three A-06 rows
            // must fit one fixed quarter-screen card.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = c.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    number.value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (primary) c.secondary else c.onSurface,
                    maxLines = 1,
                )
            }
            Text(
                number.count + (if (en) " draws" else " ကြိမ်"),
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant,
                maxLines = 2,
                softWrap = true,
            )
        }
    }
}

/**
 * A-06: exactly three compact content-measured AM / PM / ALL rows.
 * Do not weight each section to the outer card's height: that led to
 * 11+ / 423+ etc being clipped in the on-device screenshot.
 */
@Composable
private fun ColumnScope.MissingPreview(en: Boolean, compact: Boolean) {
    TwoDFixedDemoSamples.absent.forEachIndexed { index, slot ->
        if (index != 0) HorizontalDivider(thickness = .5.dp)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(slot.session,
                modifier = Modifier.width(if (compact) 23.dp else 26.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1)
            TinyNumberWell("0–9", slot.digit, primary = true, en = en,
                modifier = Modifier.weight(1.07f))
            TinyNumberWell("2D", slot.pair, primary = false, en = en,
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewBadge(
    value: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    small: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(9.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (primary) c.secondary else c.secondaryContainer,
        shadowElevation = if (primary) 4.dp else 1.dp,
        tonalElevation = 0.dp,
        border = BorderStroke(
            1.dp,
            if (primary) Color.White.copy(alpha = .19f)
                else c.secondary.copy(alpha = .12f),
        ),
    ) {
        Box(
            Modifier.background(
                Brush.verticalGradient(
                    if (primary) listOf(c.secondary.copy(alpha = .94f), c.secondary)
                    else listOf(c.surface.copy(alpha = .70f),
                        c.secondaryContainer.copy(alpha = .75f)),
                ),
                shape,
            ).padding(
                horizontal = if (small) 6.dp else 8.dp,
                vertical = if (small) 3.dp else 6.dp,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                value,
                style = if (small) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (primary) c.onSecondary else c.secondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ColumnScope.PatternPreview(en: Boolean, compact: Boolean) {
    val c = MaterialTheme.colorScheme
    Text(if (en) "Most common next class" else "အများဆုံး ဆက်ထွက်",
        style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = c.secondaryContainer.copy(alpha = .52f),
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .15f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(if (en) "Class" else "စုံ/မ",
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            PreviewBadge(if (en) "Odd–Even" else "မစုံ", primary = true)
        }
    }
    PreviewLine(if (en) "Frequency" else "အကြိမ်ရေ", "6", compact)
    PreviewLine(if (en) "Matching weeks" else "ပုံစံတူအပတ်", "8", compact)
}

@Composable
private fun ColumnScope.PreviousPreview(en: Boolean, compact: Boolean) {
    val c = MaterialTheme.colorScheme
    PreviewLine(if (en) "Prior" else "ရှေ့ထွက်", "51 · 05", compact)
    HorizontalDivider(thickness = .5.dp)
    TwoDFixedDemoSamples.previous.forEachIndexed { index, (label, result) ->
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (compact) 29.dp else 38.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                if (en) label else when (index) {
                    0 -> "နှစ်ခု"; 1 -> "ပထမ"; else -> "ဒုတိယ"
                },
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant,
                modifier = Modifier.width(if (compact) 31.dp else 39.dp),
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            result.value.split("·").forEach { number ->
                PreviewBadge(number, small = true)
            }
            Text(
                "×" + result.count,
                style = MaterialTheme.typography.labelSmall,
                color = c.onSurfaceVariant, maxLines = 1,
            )
        }
        if (index < TwoDFixedDemoSamples.previous.lastIndex)
            HorizontalDivider(thickness = .5.dp)
    }
}

@Composable
private fun TokenStrip(digits: List<String>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        digits.forEach { digit ->
            PreviewBadge(digit, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ColumnScope.WeeklyPreview(en: Boolean, compact: Boolean) {
    PreviewSectionTitle(if (en) "Unseen digits (0–9)" else "ပျောက်ဂဏန်း (0–9)")
    TokenStrip(TwoDFixedDemoSamples.missingDigits)
    Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
    HorizontalDivider(thickness = .5.dp)
    PreviewSectionTitle(if (en) "New this week" else "ဒီအပတ် အသစ်ထွက်")
    TokenStrip(TwoDFixedDemoSamples.firstSeen)
    Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
    PreviewLine(if (en) "Recorded" else "မှတ်တမ်း", "7/8", compact)
}

@Composable
private fun ColumnScope.SpecialsPreview(en: Boolean, compact: Boolean) {
    val c = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp), color = c.surface.copy(alpha = .96f),
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .12f)),
        shadowElevation = 1.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp),
        ) {
            TwoDFixedDemoSamples.special.forEachIndexed { i, (label, value) ->
                if (i != 0) HorizontalDivider(thickness = .5.dp)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = if (compact) 27.dp else 37.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        if (en) when (label) {
                            "အပူး" -> "Double"; "ညီအကို" -> "Sibling"
                            "ပါဝါ" -> "Power"; "နက္ခတ်" -> "Star"
                            else -> "Repeat"
                        } else label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    PreviewBadge(value)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.LastWeekPreview(en: Boolean, compact: Boolean) {
    val c = MaterialTheme.colorScheme
    PreviewSectionTitle(if (en) "Most observed digit" else "အထွက်အများဆုံးဂဏန်း")
    Surface(
        color = c.secondaryContainer.copy(alpha = .43f),
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .16f)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PreviewBadge("8", primary = true)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (en) "Frequency" else "အကြိမ်ရေ",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.onSurfaceVariant)
                Text("7" + if (en) " times" else " ကြိမ်",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = c.onSurface)
            }
        }
    }
    Spacer(Modifier.height(if (compact) 6.dp else 13.dp))
    HorizontalDivider(thickness = .5.dp)
    PreviewSectionTitle(if (en) "Missing last week" else "ပြီးခဲ့တဲ့အပတ် ပျောက်")
    TokenStrip(TwoDFixedDemoSamples.lastWeekMissing)
}

@Composable
private fun ColumnScope.ReturnPreview(en: Boolean, compact: Boolean) {
    val c = MaterialTheme.colorScheme
    PreviewSectionTitle(if (en) "Missing → Companion" else "ပျောက် → တွဲထွက်")
    Surface(
        color = c.surface.copy(alpha = .96f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, c.secondary.copy(alpha = .12f)),
        shadowElevation = 1.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 9.dp),
        ) {
            TwoDFixedDemoSamples.returns.forEachIndexed { i, (missing, companion) ->
                if (i != 0) HorizontalDivider(thickness = .5.dp)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = if (compact) 37.dp else 49.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PreviewBadge(missing, small = true)
                    Text("→", style = MaterialTheme.typography.labelLarge,
                        color = c.onSurfaceVariant)
                    PreviewBadge(companion.value, primary = true, small = true)
                    Spacer(Modifier.weight(1f))
                    Text(companion.count + if (en) " hits" else " ကြိမ်",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}
