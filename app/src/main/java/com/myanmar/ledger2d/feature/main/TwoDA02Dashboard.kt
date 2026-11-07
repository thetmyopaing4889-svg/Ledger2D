
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard
import com.myanmar.ledger2d.core.domain.TwoDCount
import com.myanmar.ledger2d.core.domain.TwoDSameSlot

/**
 * Data-first A-02 pilot. It deliberately consumes the existing engine output,
 * leaving draw selection, frequency counting and historical scope untouched.
 */
@Composable
internal fun TwoDA02Dashboard(data: TwoDSameSlot, en: Boolean) {
    fun tr(mm: String, english: String) = if (en) english else mm
    val lang = if (en) "en" else "my"
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var position by rememberSaveable { mutableStateOf("both") }
    var browserOpen by rememberSaveable { mutableStateOf(false) }
    var missingOnly by rememberSaveable { mutableStateOf(false) }
    var explanationOpen by rememberSaveable { mutableStateOf(false) }
    val maxCount = data.numbers.maxOfOrNull { it.count } ?: 0
    val maxPairs = data.numbers.filter { it.count == maxCount && maxCount > 0 }
    val leastCount = data.numbers.filter { it.count > 0 }.minOfOrNull { it.count }
    val leastPairs = data.numbers.filter { it.count == leastCount }
    val leading = maxPairs.firstOrNull()?.digit ?: "—"
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(tr("မှတ်တမ်းအနှစ်ချုပ်", "History summary"),
                style = LedgerTypographyStandard.style(LedgerTextRole.SECTION_TITLE, lang),
                modifier = Modifier.weight(1f))
            IconButton(onClick = { explanationOpen = true }) {
                Icon(Icons.Default.Info, contentDescription =
                    tr("စစ်ဆေးချက်ရှင်းလင်းချက်", "About this check"),
                    tint = scheme.primary)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            A02MetricTile(
                modifier = Modifier.weight(1f),
                label = tr("စစ်ထားသောအကြိမ်", "Draws checked"),
                value = data.samples.toString(), en = en,
                prominent = false,
            )
            A02MetricTile(
                modifier = Modifier.weight(1f),
                label = tr("အထွက်အများဆုံး", "Most frequent"),
                value = leading, en = en,
                foot = if (maxCount == 0) "—" else maxCount.toString() + tr(" ကြိမ်", " times"),
                prominent = true,
            )
            A02MetricTile(
                modifier = Modifier.weight(1f),
                label = tr("မထွက်ခဲ့သောတွဲ", "Unseen pairs"),
                value = if (data.samples == 0) "—" else data.unseenNumbers.size.toString(),
                en = en, prominent = false,
            )
        }
        if (data.samples == 0) {
            A02Panel {
                Text(tr("နေ့တူ အကြိမ်တူ ထွက်ဂဏန်းမှတ်တမ်း မရှိသေးပါ။",
                    "No recorded results for the same weekday and draw yet."),
                    style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang),
                    color = scheme.onSurfaceVariant)
            }
        } else {
            A02Panel {
                A02PanelTitle(
                    tr("ဂဏန်းတွဲ အထွက်အကြိမ်ရေ", "Most frequent pairs"),
                    tr("00–99", "00–99"), en)
                data.numbers.take(8).forEach { item ->
                    A02FrequencyBar(item, maxCount, en)
                }
                if (maxPairs.size > 1) {
                    Text(tr("အများဆုံးတူညီမှု", "Tied at the maximum") + " · " +
                        maxPairs.joinToString("  ") { it.digit },
                        style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                        color = scheme.onSurfaceVariant)
                }
                HorizontalDivider(color = scheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("အထွက်အနည်းဆုံး (ပေါ်ဖူး)", "Least frequent (observed)"),
                            style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                            color = scheme.onSurfaceVariant)
                        Text(leastPairs.take(6).joinToString(" · ") { it.digit } +
                            if (leastPairs.size > 6) "  +" + (leastPairs.size - 6) else "",
                            style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang),
                            fontWeight = FontWeight.SemiBold)
                    }
                    Text((leastCount ?: 0).toString() + tr(" ကြိမ်", " times"),
                        style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang),
                        color = scheme.primary)
                }
                Button(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    onClick = { browserOpen = true; missingOnly = false
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    shape = RoundedCornerShape(15.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Icon(Icons.Default.GridView, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(tr("00–99 အားလုံး ကြည့်ရန်", "Explore all 00–99"),
                        style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang))
                }
            }
            A02Panel {
                A02PanelTitle(tr("ဂဏန်း 0–9 အကြိမ်ရေ", "Digit frequency (0–9)"),
                    tr("ထိပ်နှင့်ပိတ်", "Tens and ones"), en)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(
                        "both" to tr("ပေါင်း", "Both"),
                        "tens" to tr("ထိပ်", "Tens"),
                        "ones" to tr("ပိတ်", "Ones"),
                    ).forEach { (key, text) ->
                        val active = position == key
                        val tone by animateColorAsState(
                            if (active) scheme.primaryContainer else scheme.surfaceVariant,
                            label = "digit-position",
                        )
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = tone,
                            shape = RoundedCornerShape(13.dp),
                            shadowElevation = if (active) 3.dp else 0.dp,
                            onClick = { position = key
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        ) {
                            Box(Modifier.heightIn(min = 45.dp),
                                contentAlignment = Alignment.Center) {
                                Text(text,
                                    style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang),
                                    color = if (active) scheme.onPrimaryContainer
                                        else scheme.onSurfaceVariant,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
                val counts = when (position) {
                    "tens" -> data.tens
                    "ones" -> data.ones
                    else -> data.singles
                }
                val byDigit = counts.associateBy { it.digit }
                val maxDigitCount = counts.maxOfOrNull { it.count } ?: 0
                (0..9).toList().chunked(5).forEach { row ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { digit ->
                            val hit = byDigit[digit.toString()]?.count ?: 0
                            val leadingDigit = hit == maxDigitCount && hit > 0
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                color = if (leadingDigit) scheme.primaryContainer
                                    else scheme.surfaceVariant.copy(alpha = 0.75f),
                                tonalElevation = if (leadingDigit) 2.dp else 0.dp,
                            ) {
                                Column(Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(digit.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (leadingDigit) scheme.onPrimaryContainer
                                            else scheme.onSurface)
                                    Text(hit.toString(),
                                        style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang),
                                        color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Text(tr("အပေါ်က အကြီးဂဏန်းက 0–9၊ အောက်ကိန်းက ထွက်ရှိမှုအကြိမ်ရေ ဖြစ်ပါတယ်။",
                    "Top: digit (0–9). Bottom: observed frequency."),
                    style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                    color = scheme.onSurfaceVariant)
            }
            A02Panel {
                A02PanelTitle(tr("စုံ/မ ထွက်ရှိမှု", "Parity distribution"), "", en)
                A02CompactCounts(data.parity.entries.map {
                    twoDParityLabel(it.key, en) to it.value
                }, en)
                HorizontalDivider(color = scheme.outlineVariant)
                A02PanelTitle(tr("အထူးဂဏန်းအုပ်စုများ", "Special groups"), "", en)
                A02CompactCounts(data.groups.entries.map {
                    twoDGroupLabel(it.key, en) to it.value
                }, en)
            }
            if (data.unseenNumbers.isNotEmpty()) {
                OutlinedButton(
                    onClick = { browserOpen = true; missingOnly = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Default.VisibilityOff, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("ယခင်မထွက်ခဲ့သော ဂဏန်းတွဲများ ကြည့်ရန်", "View unseen pairs"),
                        style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang))
                }
            }
        }
    }
    if (explanationOpen) {
        AlertDialog(
            onDismissRequest = { explanationOpen = false },
            title = { Text(TwoDAnalysisText.title("02", en)) },
            text = { Text(TwoDAnalysisText.description("02", en)) },
            confirmButton = { TextButton(onClick = { explanationOpen = false }) {
                Text(tr("ပိတ်မည်", "Close"))
            } },
        )
    }
    if (browserOpen) {
        A02NumberBrowser(
            data = data, en = en, initiallyMissing = missingOnly,
            onDismiss = { browserOpen = false },
        )
    }
}

@Composable
private fun A02MetricTile(
    modifier: Modifier,
    label: String,
    value: String,
    en: Boolean,
    prominent: Boolean,
    foot: String = "",
) {
    val scheme = MaterialTheme.colorScheme
    val lang = if (en) "en" else "my"
    val background = if (prominent) scheme.primaryContainer else scheme.surface
    Surface(
        modifier = modifier.heightIn(min = 106.dp),
        color = background,
        shape = RoundedCornerShape(19.dp),
        shadowElevation = if (prominent) 7.dp else 3.dp,
        tonalElevation = if (prominent) 3.dp else 1.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                color = if (prominent) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (prominent) scheme.onPrimaryContainer else scheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (foot.isNotBlank()) {
                Text(foot, style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang),
                    color = if (prominent) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    maxLines = 1)
            }
        }
    }
}

@Composable
private fun A02Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(21.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}

@Composable
private fun A02PanelTitle(title: String, aside: String, en: Boolean) {
    val lang = if (en) "en" else "my"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = LedgerTypographyStandard.style(LedgerTextRole.SECTION_TITLE, lang),
            modifier = Modifier.weight(1f))
        if (aside.isNotBlank()) {
            Text(aside, style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun A02FrequencyBar(item: TwoDCount, maximum: Int, en: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val lang = if (en) "en" else "my"
    val fraction = if (maximum > 0) item.count.toFloat() / maximum else 0f
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "a02-frequency",
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(item.digit, modifier = Modifier.width(33.dp),
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f).height(10.dp)
            .background(scheme.surfaceVariant, RoundedCornerShape(6.dp))) {
            Box(Modifier.fillMaxWidth(animated.coerceIn(0f, 1f)).fillMaxHeight()
                .background(
                    Brush.horizontalGradient(listOf(scheme.primary, scheme.secondary)),
                    RoundedCornerShape(6.dp)))
        }
        Text(item.count.toString(),
            style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang),
            modifier = Modifier.width(27.dp),
            color = scheme.onSurface, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun A02CompactCounts(values: List<Pair<String, Int>>, en: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val lang = if (en) "en" else "my"
    values.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()) {
            row.forEach { (name, count) ->
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = scheme.surfaceVariant.copy(alpha = 0.75f),
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(name, style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang),
                            modifier = Modifier.weight(1f), maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(count.toString(), style = MaterialTheme.typography.titleSmall,
                            color = scheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun A02NumberBrowser(
    data: TwoDSameSlot,
    en: Boolean,
    initiallyMissing: Boolean,
    onDismiss: () -> Unit,
) {
    fun tr(mm: String, english: String) = if (en) english else mm
    val lang = if (en) "en" else "my"
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var text by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(if (initiallyMissing) "unseen" else "all") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val counts = data.numbers.associateBy { it.digit }
    val unseen = data.unseenNumbers.toSet()
    val displayed = (0..99).map { it.toString().padStart(2, '0') }.filter { pair ->
        (text.isBlank() || pair.contains(text.trim())) &&
            (mode == "all" || (mode == "seen" && pair !in unseen) ||
                (mode == "unseen" && pair in unseen))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(
        topStart = 26.dp, topEnd = 26.dp,
    )) {
        Column(Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("ဂဏန်းတွဲ 00–99", "2D pairs 00–99"),
                    style = LedgerTypographyStandard.style(LedgerTextRole.SECTION_TITLE, lang),
                    modifier = Modifier.weight(1f))
                Text(displayed.size.toString() + tr(" ကွက်", " pairs"),
                    style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                    color = scheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c in '0'..'9' }.take(2) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text(tr("ဂဏန်းတွဲရှာရန်", "Find a pair")) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    "all" to tr("အားလုံး", "All"),
                    "seen" to tr("ထွက်ဖူး", "Observed"),
                    "unseen" to tr("မထွက်ဖူး", "Unseen"),
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = mode == key,
                        onClick = { mode = key; selected = null
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        label = { Text(label,
                            style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (selected != null) {
                val number = selected.orEmpty()
                Surface(shape = RoundedCornerShape(14.dp),
                    color = scheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(number, style = MaterialTheme.typography.headlineSmall,
                            color = scheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold)
                        Text((counts[number]?.count ?: 0).toString() +
                            tr(" ကြိမ် ထွက်ခဲ့သည်", " recorded draws"),
                            style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang),
                            color = scheme.onPrimaryContainer)
                    }
                }
            }
            if (displayed.isEmpty()) {
                Text(tr("ကိုက်ညီသော ဂဏန်းတွဲမရှိပါ", "No matching pairs"),
                    style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxWidth().height(380.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(displayed, key = { it }) { pair ->
                        val count = counts[pair]?.count ?: 0
                        val active = selected == pair
                        Surface(
                            onClick = { selected = pair
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                            shape = RoundedCornerShape(13.dp),
                            color = when {
                                active -> scheme.primaryContainer
                                count == 0 -> scheme.surfaceVariant.copy(alpha = 0.55f)
                                else -> scheme.secondaryContainer.copy(alpha = 0.55f)
                            },
                            shadowElevation = if (active) 3.dp else 0.dp,
                        ) {
                            Column(Modifier.heightIn(min = 62.dp)
                                .padding(horizontal = 3.dp, vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(pair, style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) scheme.onPrimaryContainer else scheme.onSurface)
                                Text(count.toString(),
                                    style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang),
                                    color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Text(tr("အပေါ်ကဂဏန်းတွဲ၊ အောက်က ထွက်ခဲ့သောအကြိမ်ရေ ဖြစ်ပါတယ်။",
                "Pair above, observed count below."),
                style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                color = scheme.onSurfaceVariant)
        }
    }
}
