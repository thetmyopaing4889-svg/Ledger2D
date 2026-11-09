
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
    val haptic = LocalHapticFeedback.current
    var position by rememberSaveable { mutableStateOf("both") }
    var browserOpen by rememberSaveable { mutableStateOf(false) }
    var missingOnly by rememberSaveable { mutableStateOf(false) }
    var explanationOpen by rememberSaveable { mutableStateOf(false) }
    var pairExpanded by rememberSaveable { mutableStateOf(false) }
    var digitExpanded by rememberSaveable { mutableStateOf(false) }
    var leadersDialog by rememberSaveable { mutableStateOf("") }
    var selectedDigitHistory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGroupHistory by rememberSaveable { mutableStateOf<String?>(null) }
    val maxCount = data.numbers.maxOfOrNull { it.count } ?: 0
    val maxPairs = data.numbers.filter { it.count == maxCount && maxCount > 0 }
    val leastCount = data.numbers.filter { it.count > 0 }.minOfOrNull { it.count }
    val leastPairs = data.numbers.filter { it.count == leastCount }

    val maxDigit = data.singles.maxOfOrNull { it.count } ?: 0
    val topDigits = data.singles.filter { maxDigit > 0 && it.count == maxDigit }
    val leastDigitCount = data.singles.filter { it.count > 0 }.minOfOrNull { it.count }
    val leastDigits = data.singles.filter { leastDigitCount != null && it.count == leastDigitCount }
    fun showCandidates(values: List<TwoDCount>): String = when {
        values.isEmpty() -> "—"
        values.size <= 2 -> values.joinToString(" · ") { it.digit }
        else -> values.size.toString() + tr(" မျိုးတူ", " tied")
    }
    fun positionLeaders(values: List<TwoDCount>): String {
        val max = values.maxOfOrNull { it.count } ?: 0
        if (max <= 0) return "—"
        return values.filter { it.count == max }.joinToString(" · ") { it.digit } +
            " · " + max + tr(" ကြိမ်", " times")
    }
    fun categoryLeaders(values: Map<String, Int>, label: (String) -> String): String {
        val max = values.values.maxOrNull() ?: 0
        if (max <= 0) return tr("မရှိ", "None")
        return values.filterValues { it == max }.keys.joinToString(" · ") { label(it) } +
            " · " + max + tr(" ကြိမ်", " times")
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("စစ်ထားသောအကြိမ် ", "Verified draws: ") + data.samples,
                style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = { explanationOpen = true }) {
                Icon(Icons.Default.Info, contentDescription =
                    tr("စစ်ဆေးချက်ရှင်းလင်းချက်", "About this check"),
                    tint = scheme.primary)
            }
        }
        if (data.samples == 0) {
            A02Panel {
                Text(tr("အချက်အလက် မရရှိနိုင်သေးပါ",
                    "Information not yet available"),
                    style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang),
                    color = scheme.onSurfaceVariant)
            }
        } else {
            // Equal visual priority for full 2D pairs and single digits.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                A02ComparePane(
                    modifier = Modifier.weight(1f),
                    label = tr("ဂဏန်းတွဲ 00–99", "2D pairs 00–99"),
                    value = showCandidates(maxPairs),
                    detail = maxCount.toString() + tr(" ကြိမ်စီ", " times each"),
                    onClick = { leadersDialog = "pairs" },
                )
                A02ComparePane(
                    modifier = Modifier.weight(1f),
                    label = tr("ဂဏန်း 0–9", "Digits 0–9"),
                    value = showCandidates(topDigits),
                    detail = maxDigit.toString() + tr(" ကြိမ်စီ", " times each"),
                    onClick = { leadersDialog = "digits" },
                )
            }
            Surface(onClick = { leadersDialog = "least" },
                shape = RoundedCornerShape(13.dp),
                color = scheme.surfaceVariant.copy(alpha = 0.7f)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("ဂဏန်း အထွက်အနည်းဆုံး (ပေါ်ဖူး)",
                        "Least observed digit (>0)"),
                        style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                        modifier = Modifier.weight(1f))
                    Text(showCandidates(leastDigits) + " ×" + (leastDigitCount ?: 0) +
                        tr(" ကြိမ်", " times"),
                        style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang),
                        fontWeight = FontWeight.SemiBold, color = scheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ChevronRight, null, tint = scheme.primary,
                        modifier = Modifier.size(18.dp))
                }
            }
            A02DisclosureLine(
                title = tr("ဂဏန်းတွဲ အသေးစိတ်", "2D pair details"),
                expanded = pairExpanded, onClick = { pairExpanded = !pairExpanded },
            )
            if (pairExpanded) {
                A02Panel {
                    A02PanelTitle(tr("ဂဏန်းတွဲ အထွက်အကြိမ်ရေ", "Most observed pairs"), "00–99", en)
                    data.numbers.take(5).forEach { item ->
                        A02FrequencyBar(item, maxCount, en)
                    }
                    if (maxPairs.size > 1) {
                        Text(tr("အများဆုံးတူညီမှု", "Tied at maximum") + " · " +
                            maxPairs.joinToString(" · ") { it.digit },
                            style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                            color = scheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("အထွက်အနည်းဆုံး (ပေါ်ဖူး)", "Least observed pairs (>0)"),
                            style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                            modifier = Modifier.weight(1f))
                        Text(showCandidates(leastPairs) + " · " + (leastCount ?: 0) +
                            tr(" ကြိမ်", " times"),
                            style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang))
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = { browserOpen = true; missingOnly = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    ) { Text(tr("00–99 အားလုံး ကြည့်ရန်", "Explore all 00–99")) }
                    HorizontalDivider(color = scheme.outlineVariant)
                    A02PanelTitle(tr("စုံ/မ ထွက်ရှိမှု", "Parity distribution"), "", en)
                    Text(categoryLeaders(data.parity) { twoDParityLabel(it, en) },
                        style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang),
                        fontWeight = FontWeight.SemiBold, color = scheme.primary)
                    A02CompactCounts(data.parity.entries.map {
                        twoDParityLabel(it.key, en) to it.value
                    }, en)
                    data.parity.filterValues { it > 0 }.forEach { (key, _) ->
                        TextButton(onClick = { selectedGroupHistory = "parity:" + key }) {
                            Text(twoDParityLabel(key, en) + " · " +
                                tr("ထွက်ခဲ့သောရက်များ", "View dates"),
                                style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang))
                        }
                    }
                    A02PanelTitle(tr("အထူးဂဏန်းအုပ်စုများ", "Special groups"), "", en)
                    Text(categoryLeaders(data.groups) { twoDGroupLabel(it, en) },
                        style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang),
                        fontWeight = FontWeight.SemiBold, color = scheme.primary)
                    A02CompactCounts(data.groups.entries.map {
                        twoDGroupLabel(it.key, en) to it.value
                    }, en)
                    data.groups.filterValues { it > 0 }.forEach { (key, _) ->
                        TextButton(onClick = { selectedGroupHistory = "group:" + key }) {
                            Text(twoDGroupLabel(key, en) + " · " +
                                tr("ထွက်ခဲ့သောရက်များ", "View dates"),
                                style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang))
                        }
                    }
                    if (data.unseenNumbers.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { browserOpen = true; missingOnly = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text(tr("မထွက်ဖူးသော ဂဏန်းတွဲများ", "Unseen pairs") +
                                " · " + data.unseenNumbers.size)
                        }
                    }
                }
            }
            A02DisclosureLine(
                title = tr("ဂဏန်း 0–9 အသေးစိတ်", "Digit 0–9 details"),
                expanded = digitExpanded, onClick = { digitExpanded = !digitExpanded },
            )
            if (digitExpanded) {
                A02Panel {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("ထိပ် အများဆုံး", "Top tens digit"),
                            style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                            modifier = Modifier.weight(1f))
                        Text(positionLeaders(data.tens),
                            style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("ပိတ် အများဆုံး", "Top ones digit"),
                            style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                            modifier = Modifier.weight(1f))
                        Text(positionLeaders(data.ones),
                            style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(
                            "both" to tr("ပေါင်း", "Both"),
                            "tens" to tr("ထိပ်", "Tens"),
                            "ones" to tr("ပိတ်", "Ones"),
                        ).forEach { (key, label) ->
                            Surface(
                                onClick = { position = key
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                                modifier = Modifier.weight(1f),
                                color = if (position == key) scheme.primaryContainer else scheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Box(Modifier.heightIn(min = 44.dp),
                                    contentAlignment = Alignment.Center) {
                                    Text(label, style =
                                        LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang),
                                        fontWeight = if (position == key) FontWeight.Bold else FontWeight.Medium)
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
                                val leader = hit == maxDigitCount && hit > 0
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (leader) scheme.primaryContainer
                                        else scheme.surfaceVariant.copy(alpha = 0.75f),
                                ) {
                                    Column(Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(digit.toString(),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold)
                                        Text(hit.toString(),
                                            style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang))
                                    }
                                }
                            }
                        }
                    }
                    Text(tr("အပေါ်က ဂဏန်း၊ အောက်က ထွက်ရှိမှုအကြိမ်ရေ ဖြစ်ပါတယ်။",
                        "Digit above, observed frequency below."),
                        style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang),
                        color = scheme.onSurfaceVariant)
                }
            }
        }
    }

    if (leadersDialog.isNotEmpty()) {
        val leaders = when (leadersDialog) {
            "pairs" -> maxPairs
            "digits" -> topDigits
            else -> leastDigits
        }
        val heading = when (leadersDialog) {
            "pairs" -> tr("အထွက်အများဆုံး ဂဏန်းတွဲ", "Most observed 2D pairs")
            "digits" -> tr("အထွက်အများဆုံး ဂဏန်း", "Most observed digits")
            else -> tr("အထွက်အနည်းဆုံး ဂဏန်း", "Least observed digits (>0)")
        }
        AlertDialog(
            onDismissRequest = { leadersDialog = "" },
            title = { Text(heading) },
            text = {
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    leaders.forEach { candidate ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(candidate.digit, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            Text(candidate.count.toString() + tr(" ကြိမ်", " times"),
                                style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang))
                            if (leadersDialog != "pairs") {
                                TextButton(onClick = {
                                    selectedDigitHistory = candidate.digit
                                    leadersDialog = ""
                                }) {
                                    Text(tr("မှတ်တမ်း", "History"),
                                        style = LedgerTypographyStandard.style(LedgerTextRole.BUTTON, lang))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { leadersDialog = "" }) {
                    Text(tr("ပိတ်မည်", "Close"))
                }
            },
        )
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

    selectedDigitHistory?.let { digit ->
        val records = data.digitOccurrences[digit].orEmpty()
        AlertDialog(
            onDismissRequest = { selectedDigitHistory = null },
            title = { Text(digit + " · " + tr("ဂဏန်းထွက်ရှိမှု", "Digit appearances"),
                style = LedgerTypographyStandard.style(LedgerTextRole.CARD_TITLE, lang)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(records.size) { index ->
                        val row = records[index]
                        Column(Modifier.padding(vertical = 7.dp)) {
                            Text(row.date.toString() + " · " +
                                (if (row.session ==
                                    com.myanmar.ledger2d.core.model.DrawSession.MORNING)
                                    tr("မနက်အကြိမ်", "Morning")
                                else tr("ညနေအကြိမ်", "Evening")),
                                style = LedgerTypographyStandard.style(LedgerTextRole.HELPER, lang))
                            Text(row.digit + " · " + digit + " ×" +
                                row.digit.count { c -> c == digit[0] },
                                style = LedgerTypographyStandard.style(LedgerTextRole.BODY, lang))
                        }
                        HorizontalDivider(color = scheme.outlineVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedDigitHistory = null }) {
                Text(tr("ပိတ်မည်", "Close"))
            } },
        )
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
                        HorizontalDivider(color = scheme.outlineVariant)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedGroupHistory = null }) {
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
                        Text(number, style = MaterialTheme.typography.headlineSmall,
                            color = scheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold)
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
                                    Text(pair, style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
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
