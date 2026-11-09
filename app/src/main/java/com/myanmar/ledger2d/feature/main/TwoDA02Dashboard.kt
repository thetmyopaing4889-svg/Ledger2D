
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    var position by rememberSaveable { mutableStateOf("both") }
    var browserOpen by rememberSaveable { mutableStateOf(false) }
    var missingOnly by rememberSaveable { mutableStateOf(false) }
    var initialPair by rememberSaveable { mutableStateOf<String?>(null) }
    var explanationOpen by rememberSaveable { mutableStateOf(false) }
    var selectedDigitHistory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDigitPosition by rememberSaveable { mutableStateOf("both") }
    var selectedGroupHistory by rememberSaveable { mutableStateOf<String?>(null) }
    var topPairChoiceOpen by rememberSaveable { mutableStateOf(false) }
    var topDigitChoiceOpen by rememberSaveable { mutableStateOf(false) }

    val maxPairs = TwoDDetailSelection.leaders(data.numbers)
    val maxDigits = TwoDDetailSelection.leaders(data.singles)
    val leastDigit = data.singles.filter { it.count > 0 }.minOfOrNull { it.count }
    val leastDigits = data.singles.filter { leastDigit != null && it.count == leastDigit }
    val maxPairCount = maxPairs.firstOrNull()?.count ?: 0
    val maxDigitCount = maxDigits.firstOrNull()?.count ?: 0
    fun condensed(items: List<TwoDCount>) = items.take(3).joinToString(" · ") { it.digit } +
        if (items.size > 3) " +" + (items.size - 3) else ""
    fun digitsHistoryLabel(key: String) = key + " · " + tr("ဂဏန်းထွက်ရှိမှု", "Digit history")

    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("စစ်ထားသောအကြိမ် ", "Verified draws: ") + data.samples,
                style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = { explanationOpen = true }) {
                Icon(Icons.Default.Info, contentDescription =
                    tr("စစ်ဆေးချက်ရှင်းလင်းချက်", "About this check"), tint = scheme.primary)
            }
        }

        if (data.samples == 0) {
            A02Panel {
                Text(tr("အချက်အလက် မရရှိနိုင်သေးပါ", "Information not yet available"),
                    style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang))
            }
        } else {
            // A-06 frame family: one compact hero for each independent frequency family.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (maxPairs.isNotEmpty()) TwoDResultFrame(
                    tr("အထွက်အများဆုံး ဂဏန်းတွဲ", "Top 2D pairs"),
                    condensed(maxPairs), maxPairCount.toString() + tr(" ကြိမ်စီ", " times each"),
                    en, Modifier.weight(1f),
                    onClick = { topPairChoiceOpen = true })
                if (maxDigits.isNotEmpty()) TwoDResultFrame(
                    tr("အထွက်အများဆုံးဂဏန်း", "Top digits"),
                    condensed(maxDigits), maxDigitCount.toString() + tr(" ကြိမ်စီ", " times each"),
                    en, Modifier.weight(1f),
                    onClick = { topDigitChoiceOpen = true })
            }

            // Explicitly means the least frequent among digits that appeared, NOT missing digits.
            if (leastDigits.isNotEmpty()) A02Panel {
                Text(tr("ထွက်ဖူးသောဂဏန်းများထဲမှ အနည်းဆုံး", "Least frequent of observed digits"),
                    style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                    color = scheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TwoDNumberFocus(leastDigits.joinToString(" · ") { it.digit },
                        en, modifier = Modifier.weight(1f), compact = true)
                    Text(leastDigit.toString() + tr(" ကြိမ်စီ", " times each"),
                        style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang),
                        color = scheme.onSurface)
                }
            }

            // Detail is visible on first entry; history and the 00-99 browser are optional actions.
            A02Panel {
                A02PanelTitle(tr("ဂဏန်းတွဲများ (00–99)", "2D pairs (00–99)"), "", en)
                data.numbers.filter { it.count > 0 }.take(5).forEach { item ->
                    Surface(onClick = { initialPair = item.digit
                        missingOnly = false; browserOpen = true },
                        color = scheme.surfaceVariant.copy(alpha = .50f),
                        shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TwoDListNumberBadge(item.digit, en)
                            Spacer(Modifier.weight(1f))
                            Text(item.count.toString() + tr(" ကြိမ်", " times"),
                                style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang))
                            Icon(Icons.Default.ChevronRight, null,
                                tint = scheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                if (maxPairs.size > 1) Text(
                    tr("အများဆုံးတူညီသောဂဏန်းတွဲ", "All tied top pairs") +
                        ": " + maxPairs.joinToString(" · ") { it.digit },
                    style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                    color = scheme.onSurfaceVariant)
                Button(onClick = { initialPair = null; missingOnly = false; browserOpen = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                    Text(tr("00–99 အားလုံး ကြည့်ရန်", "View all 00–99 pairs"),
                        style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang))
                }
                if (data.unseenNumbers.isNotEmpty()) TextButton(
                    onClick = { initialPair = null; missingOnly = true; browserOpen = true }) {
                    Text(tr("မထွက်ဖူးသော ဂဏန်းတွဲများ", "Unseen pairs") +
                        " · " + data.unseenNumbers.size,
                        style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang))
                }
            }

            A02Panel {
                A02PanelTitle(tr("ဂဏန်းများ (0–9)", "Digits (0–9)"), "", en)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf("both" to tr("ပေါင်း", "Both"),
                        "tens" to tr("ထိပ်", "Tens"),
                        "ones" to tr("ပိတ်", "Ones")).forEach { (key, label) ->
                        FilterChip(
                            modifier = Modifier.weight(1f),
                            selected = position == key, onClick = { position = key },
                            label = { Text(label,
                                style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang)) })
                    }
                }
                val list = when (position) {
                    "tens" -> data.tens
                    "ones" -> data.ones
                    else -> data.singles
                }
                val byDigit = list.associateBy { it.digit }
                (0..9).toList().chunked(5).forEach { group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        group.forEach { digit ->
                            val key = digit.toString()
                            val hit = byDigit[key]?.count ?: 0
                            Surface(onClick = {
                                selectedDigitPosition = position
                                selectedDigitHistory = key
                            },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (hit == list.maxOfOrNull { it.count } && hit > 0)
                                    scheme.secondaryContainer else scheme.surfaceVariant.copy(alpha = .54f)) {
                                Column(Modifier.padding(vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(key, style = LedgerTypographyStandard.style(
                                        LedgerTextRole.CARD_TITLE, lang), color = scheme.onSurface)
                                    Text(hit.toString(), style = LedgerTypographyStandard.style(
                                        LedgerTextRole.TABLE, lang), color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Text(tr("ဂဏန်းကိုနှိပ်ပြီး ထွက်ခဲ့သောရက်များ ကြည့်နိုင်သည်။",
                    "Tap any digit for recorded occurrence dates."),
                    style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                    color = scheme.onSurfaceVariant)
            }

            // Categories never belong inside the pair list.
            A02Panel {
                A02PanelTitle(tr("စုံ/မ ထွက်ရှိမှု", "Parity counts"), "", en)
                data.parity.forEach { (key, amount) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(twoDParityLabel(key, en),
                            style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang),
                            modifier = Modifier.weight(1f))
                        Text(amount.toString() + tr(" ကြိမ်", " times"),
                            style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang))
                        if (amount > 0) TextButton(
                            onClick = { selectedGroupHistory = "parity:" + key }) {
                            Text(tr("မှတ်တမ်း", "History"),
                                style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang))
                        }
                    }
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .25f))
                }
            }
            A02Panel {
                A02PanelTitle(tr("အထူးဂဏန်းအုပ်စုများ", "Special groups"), "", en)
                data.groups.forEach { (key, amount) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(twoDGroupLabel(key, en),
                            style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang),
                            modifier = Modifier.weight(1f))
                        Text(amount.toString() + tr(" ကြိမ်", " times"),
                            style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang))
                        if (amount > 0) TextButton(onClick = {
                            selectedGroupHistory = "group:" + key
                        }) { Text(tr("မှတ်တမ်း", "History"),
                            style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang)) }
                    }
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .25f))
                }
            }
        }
    }


    if (topPairChoiceOpen) AlertDialog(
        onDismissRequest = { topPairChoiceOpen = false },
        title = { Text(tr("အထွက်အများဆုံး ဂဏန်းတွဲများ", "Top 2D pair results"),
            style = LedgerTypographyStandard.style(LedgerTextRole.CARD_TITLE, lang)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                maxPairs.forEach { item ->
                    Surface(onClick = {
                        topPairChoiceOpen = false
                        initialPair = item.digit; missingOnly = false; browserOpen = true
                    }, shape = RoundedCornerShape(10.dp),
                        color = scheme.surfaceVariant.copy(alpha = .55f)) {
                        Row(Modifier.fillMaxWidth().padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TwoDListNumberBadge(item.digit, en)
                            Spacer(Modifier.weight(1f))
                            Text(item.count.toString() + tr(" ကြိမ်", " times"),
                                style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang))
                            Icon(Icons.Default.ChevronRight, null,
                                tint = scheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { topPairChoiceOpen = false }) {
            Text(tr("ပိတ်မည်", "Close"))
        } },
    )
    if (topDigitChoiceOpen) AlertDialog(
        onDismissRequest = { topDigitChoiceOpen = false },
        title = { Text(tr("အထွက်အများဆုံး ဂဏန်းများ", "Top digit results"),
            style = LedgerTypographyStandard.style(LedgerTextRole.CARD_TITLE, lang)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                maxDigits.forEach { item ->
                    Surface(onClick = {
                        topDigitChoiceOpen = false
                        selectedDigitPosition = "both"
                        selectedDigitHistory = item.digit
                    }, shape = RoundedCornerShape(10.dp),
                        color = scheme.surfaceVariant.copy(alpha = .55f)) {
                        Row(Modifier.fillMaxWidth().padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TwoDListNumberBadge(item.digit, en)
                            Spacer(Modifier.weight(1f))
                            Text(item.count.toString() + tr(" ကြိမ်", " times"),
                                style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang))
                            Icon(Icons.Default.ChevronRight, null,
                                tint = scheme.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { topDigitChoiceOpen = false }) {
            Text(tr("ပိတ်မည်", "Close"))
        } },
    )

    if (explanationOpen) AlertDialog(
        onDismissRequest = { explanationOpen = false },
        title = { Text(TwoDAnalysisText.title("02", en)) },
        text = { Text(TwoDAnalysisText.description("02", en)) },
        confirmButton = { TextButton(onClick = { explanationOpen = false }) {
            Text(tr("ပိတ်မည်", "Close"))
        } })

    selectedDigitHistory?.let { digit ->
        val occurrences = TwoDDetailSelection.positionEvidence(
            data.digitOccurrences[digit].orEmpty(), digit, selectedDigitPosition)
        AlertDialog(
            onDismissRequest = { selectedDigitHistory = null },
            title = { Text(
                (when (selectedDigitPosition) {
                    "tens" -> tr("ထိပ်", "Tens") + " "
                    "ones" -> tr("ပိတ်", "Ones") + " "
                    else -> ""
                }) + digitsHistoryLabel(digit),
                style = LedgerTypographyStandard.style(LedgerTextRole.CARD_TITLE, lang)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(occurrences.size) { index ->
                        val record = occurrences[index]
                        Column(Modifier.padding(vertical = 7.dp)) {
                            Text(record.date.toString() + " · " +
                                (if (record.session ==
                                    com.myanmar.ledger2d.core.model.DrawSession.MORNING)
                                    tr("မနက်အကြိမ်", "Morning")
                                else tr("ညနေအကြိမ်", "Evening")),
                                style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang))
                            Text(record.digit + " · " + digit + " ×" +
                                TwoDDetailSelection.positionCount(
                                    record.digit, digit, selectedDigitPosition),
                                style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang))
                        }
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .25f))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedDigitHistory = null }) {
                Text(tr("ပိတ်မည်", "Close"))
            } })
    }
    selectedGroupHistory?.let { selected ->
        val type = selected.substringBefore(":")
        val key = selected.substringAfter(":")
        val records = data.recordedResults.filter { row ->
            if (type == "parity")
                com.myanmar.ledger2d.core.domain.TwoDigitGroups.parity(row.digit) == key
            else row.digit in
                com.myanmar.ledger2d.core.domain.TwoDigitGroups.named[key].orEmpty()
        }
        AlertDialog(
            onDismissRequest = { selectedGroupHistory = null },
            title = { Text(if (type == "parity") twoDParityLabel(key, en)
                else twoDGroupLabel(key, en),
                style = LedgerTypographyStandard.style(LedgerTextRole.CARD_TITLE, lang)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(records.size) { index ->
                        val row = records[index]
                        Text(row.date.toString() + " · " +
                            (if (row.session ==
                                com.myanmar.ledger2d.core.model.DrawSession.MORNING)
                                tr("မနက်အကြိမ်", "Morning")
                            else tr("ညနေအကြိမ်", "Evening")) + " · " + row.digit,
                            modifier = Modifier.padding(vertical = 8.dp),
                            style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang))
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .25f))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedGroupHistory = null }) {
                Text(tr("ပိတ်မည်", "Close"))
            } })
    }
    if (browserOpen) A02NumberBrowser(
        data, en, initiallyMissing = missingOnly,
        initialPair = initialPair,
        onDismiss = { browserOpen = false; initialPair = null },
    )
}

@Composable
private fun A02ComparePane(
    modifier: Modifier, label: String, value: String, detail: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(15.dp),
        color = scheme.primaryContainer.copy(alpha = 0.65f),
        tonalElevation = 2.dp, shadowElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = scheme.onPrimaryContainer, maxLines = 2)
            Text(value, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(detail, style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimaryContainer, maxLines = 2,
                    modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = scheme.onPrimaryContainer,
                    modifier = Modifier.size(15.dp))
            }
        }
    }
}

@Composable
private fun A02DisclosureLine(title: String, expanded: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(13.dp),
        color = scheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                null, tint = scheme.primary)
        }
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
    initialPair: String? = null,
    onDismiss: () -> Unit,
) {
    fun tr(mm: String, english: String) = if (en) english else mm
    val lang = if (en) "en" else "my"
    val scheme = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var text by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(if (initiallyMissing) "unseen" else "all") }
    var selected by rememberSaveable(initialPair) { mutableStateOf(initialPair) }
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
            if (selected != null) {
                val number = selected.orEmpty()
                val records = data.numberOccurrences[number].orEmpty().asReversed()
                TextButton(onClick = { selected = null }) {
                    Icon(Icons.Default.ArrowBack, null,
                        modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(tr("ဂဏန်းတွဲအားလုံး", "All pairs"))
                }
                Surface(shape = RoundedCornerShape(14.dp),
                    color = scheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TwoDNumberFocus(number, en, compact = true)
                        Text((counts[number]?.count ?: 0).toString() +
                            tr(" ကြိမ် ထွက်ခဲ့သည်", " recorded draws"),
                            style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang),
                            color = scheme.onPrimaryContainer)
                    }
                }
                if (records.isEmpty()) {
                    Text(tr("ရွေးထားသောကာလအတွင်း ထွက်ဖူးသည့်မှတ်တမ်းမရှိပါ။",
                        "No verified occurrence in the selected period."),
                        style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang),
                        color = scheme.onSurfaceVariant)
                } else {
                    Text(tr("ထွက်ခဲ့သောနေ့နှင့် အကြိမ်များ", "Verified occurrence dates and draws"),
                        style = LedgerTypographyStandard.style(LedgerTextRole.CARD_TITLE, lang))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(records, key = { it.date.toString() + "_" + it.session.name }) { record ->
                            Surface(shape = RoundedCornerShape(10.dp),
                                color = scheme.surfaceVariant.copy(alpha = 0.65f)) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(record.date.toString(),
                                        style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang))
                                    Text(if (record.session == com.myanmar.ledger2d.core.model.DrawSession.MORNING)
                                        tr("မနက်အကြိမ်", "Morning")
                                    else tr("ညနေအကြိမ်", "Evening"),
                                        style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang),
                                        color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            } else {
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
                if (displayed.isEmpty()) {
                    Text(tr("ကိုက်ညီသော ဂဏန်းတွဲမရှိပါ", "No matching pairs"),
                        style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxWidth().height(340.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(displayed, key = { it }) { pair ->
                            val count = counts[pair]?.count ?: 0
                            Surface(
                                onClick = { selected = pair
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                shape = RoundedCornerShape(13.dp),
                                color = if (count == 0) scheme.surfaceVariant.copy(alpha = 0.55f)
                                    else scheme.secondaryContainer.copy(alpha = 0.55f),
                            ) {
                                Column(Modifier.heightIn(min = 62.dp)
                                    .padding(horizontal = 3.dp, vertical = 7.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(pair, style = LedgerTypographyStandard.style(
                                            LedgerTextRole.CARD_TITLE, lang),
                                        color = scheme.onSurface)
                                    Text(count.toString(),
                                        style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang),
                                        color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                Text(tr("အပေါ်က ဂဏန်းတွဲ၊ အောက်က ထွက်ရှိမှုအကြိမ်ရေ ဖြစ်ပါတယ်။",
                    "Pair above, observed count below."),
                    style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                    color = scheme.onSurfaceVariant)
            }
        }
    }
}
