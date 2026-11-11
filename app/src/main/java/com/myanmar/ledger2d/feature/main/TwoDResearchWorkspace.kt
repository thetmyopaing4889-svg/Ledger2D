@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun rt(en: Boolean, mm: String, english: String) = if (en) english else mm

@Composable
private fun ResearchText(
    label: String, en: Boolean, role: LedgerTextRole = LedgerTextRole.BODY,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Text(label, modifier = modifier, color = color,
        style = LedgerTypographyStandard.style(role, if (en) "en" else "my"))
}

@Composable
private fun ResearchPanel(
    en: Boolean, title: String, content: @Composable ColumnScope.() -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(19.dp),
        color = c.surface, tonalElevation = 1.dp, shadowElevation = 2.dp,
        border = BorderStroke(1.dp, c.outlineVariant.copy(alpha = .35f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ResearchText(title, en, LedgerTextRole.CARD_TITLE)
            content()
        }
    }
}

@Composable
private fun ResearchStat(
    title: String, number: Int, foot: String, en: Boolean, modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .36f)) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ResearchText(title, en, LedgerTextRole.HELPER,
                MaterialTheme.colorScheme.onSurfaceVariant)
            ResearchText(number.toString(), en, LedgerTextRole.CARD_TITLE)
            Text(foot, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun sessionName(session: DrawSession, en: Boolean) =
    if (session == DrawSession.MORNING) rt(en, "မနက်", "AM")
    else rt(en, "ညနေ", "PM")

private fun researchStatusText(status: TwoDResearchStatus, en: Boolean) = when (status) {
    TwoDResearchStatus.OBSERVED -> rt(en, "ရလဒ်ရှိ", "Observed")
    TwoDResearchStatus.MATCH -> rt(en, "ကိုက်ညီ", "Matched")
    TwoDResearchStatus.OTHER -> rt(en, "မကိုက်ညီ", "Other outcome")
    TwoDResearchStatus.MISSING -> rt(en, "Result မတွေ့", "Result not found")
    TwoDResearchStatus.PENDING -> rt(en, "မထွက်သေး", "Not yet due")
    TwoDResearchStatus.OUT_OF_RANGE -> rt(en, "ကာလအပြင်", "Outside range")
}

@Composable
private fun ResearchEvidenceLine(row: TwoDResearchEvidence, en: Boolean) {
    val c = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
        color = c.surfaceVariant.copy(alpha = .38f)) {
        Column(Modifier.fillMaxWidth().padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(researchStatusText(row.status, en),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                    color = if (row.status == TwoDResearchStatus.MATCH) c.primary else c.onSurfaceVariant)
                ResearchText(row.source.date.toString(), en, LedgerTextRole.HELPER,
                    c.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ResearchText(sessionName(row.source.session, en) + " · " + row.source.digit,
                        en, LedgerTextRole.BODY)
                    Text(rt(en, "အခြေခံရလဒ်", "Source"),
                        style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                }
                Icon(Icons.Default.ArrowForward, contentDescription = null,
                    tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ResearchText(sessionName(row.expected.session, en) + " · " +
                        (row.following?.digit ?: "—"), en, LedgerTextRole.BODY)
                    Text(row.expected.date.toString(),
                        style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Research has its own query and range controls. It reads the exact same
 * reconciled/verified stream as Analysis; it never writes or repairs history.
 */
@Composable
internal fun TwoDResearchWorkspace(
    draws: List<HistoricDraw>,
    closedDates: Set<LocalDate>,
    cutoff: DrawIdentity,
    en: Boolean,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    var source by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var exact by rememberSaveable { mutableStateOf(false) }
    var linkKey by rememberSaveable { mutableStateOf("same") }
    var scopeKey by rememberSaveable { mutableStateOf("all") }
    var rangeKey by rememberSaveable { mutableStateOf("all") }
    var customStart by rememberSaveable { mutableStateOf(cutoff.date.minusMonths(1).toString()) }
    var customEnd by rememberSaveable { mutableStateOf(cutoff.date.toString()) }
    var picking by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf<TwoDResearchQuery?>(null) }
    var filter by rememberSaveable { mutableStateOf("all") }
    var visible by rememberSaveable { mutableIntStateOf(20) }
    var showAllFrequencies by rememberSaveable { mutableStateOf(false) }
    var rangeMenu by remember { mutableStateOf(false) }

    val start = runCatching { LocalDate.parse(customStart) }.getOrNull()
    val end = runCatching { LocalDate.parse(customEnd) }.getOrNull()
    val rangeValid = rangeKey != "custom" || (start != null && end != null &&
        !start.isAfter(end))
    val scope = if (linkKey == "same") TwoDResearchScope.AM else when (scopeKey) {
        "am" -> TwoDResearchScope.AM
        "pm" -> TwoDResearchScope.PM
        else -> TwoDResearchScope.ALL
    }
    val relationship = if (linkKey == "same")
        TwoDResearchRelationship.SAME_DAY_PM else TwoDResearchRelationship.NEXT_DRAW
    val researchRange = when (rangeKey) {
        "week" -> TwoDHistoryRange(cutoff.date.minusWeeks(1), cutoff.date)
        "month" -> TwoDHistoryRange(cutoff.date.minusMonths(1), cutoff.date)
        "custom" -> TwoDHistoryRange(start, end)
        else -> TwoDHistoryRange()
    }
    val validSource = source.length == 2
    val validTarget = !exact || target.length == 2
    val readyToSearch = ready && rangeValid && validSource && validTarget

    val report by produceState<TwoDResearchReport?>(
        initialValue = null, submitted, draws, closedDates, cutoff, ready,
    ) {
        value = null
        val query = submitted
        if (ready && query != null) {
            value = withContext(Dispatchers.Default) {
                TwoDResearchEngine.search(draws, query, cutoff, closedDates)
            }
        }
    }
    val current = report?.takeIf { it.query == submitted }
    val allEvidence = current?.evidence.orEmpty().asReversed()
    val filtered = allEvidence.filter { row -> when (filter) {
        "recorded" -> row.following != null
        "matched" -> row.status == TwoDResearchStatus.MATCH
        "other" -> row.status == TwoDResearchStatus.OTHER
        "unknown" -> row.following == null
        else -> true
    } }
    val displayed = filtered.take(visible)

    LazyColumn(modifier = modifier.padding(horizontal = 12.dp),
        contentPadding = PaddingValues(top = 9.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)) {

        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ResearchText(rt(en, "ကိုယ်ပိုင် 2D သမိုင်းလေ့လာမှု", "Your historical research"),
                    en, LedgerTextRole.CARD_TITLE)
                ResearchText(rt(en,
                    "စိတ်ဝင်စားသည့်အခြေအနေကို ရွေးပြီး အတည်ပြုထားသော Result များကို စစ်ဆေးပါ။",
                    "Build a question and check actual confirmed historical results."),
                    en, LedgerTextRole.HELPER, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            ResearchPanel(en, rt(en, "၁။ ဘယ်လိုဆက်စပ်မှုကို လေ့လာမလဲ", "1. Choose a relationship")) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = linkKey == "same",
                        onClick = { linkKey = "same" },
                        modifier = Modifier.weight(1f),
                        label = { Text(rt(en, "မနက် → ညနေ", "AM → PM"), maxLines = 1) })
                    FilterChip(selected = linkKey == "next",
                        onClick = { linkKey = "next" },
                        modifier = Modifier.weight(1f),
                        label = { Text(rt(en, "နောက်တစ်ကြိမ်", "Next draw"), maxLines = 1) })
                }
                if (linkKey == "next") {
                    ResearchText(rt(en, "အခြေခံအကြိမ်", "Source occasions"),
                        en, LedgerTextRole.HELPER)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.fillMaxWidth()) {
                        listOf("am" to "AM", "pm" to "PM", "all" to "ALL").forEach { (key, label) ->
                            FilterChip(selected = scopeKey == key,
                                onClick = { scopeKey = key }, modifier = Modifier.weight(1f),
                                label = { Text(label) })
                        }
                    }
                } else {
                    ResearchText(rt(en,
                        "နေ့တစ်နေ့၏ မနက်အကြိမ်မှ အဲဒီနေ့ညနေအကြိမ်သို့သာ စစ်ဆေးမည်။",
                        "Compare morning and evening on the same date."),
                        en, LedgerTextRole.HELPER,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            ResearchPanel(en, rt(en, "၂။ ဂဏန်းရွေးချယ်ရန်", "2. Choose numbers")) {
                OutlinedTextField(value = source,
                    onValueChange = { next ->
                        if (next.length <= 2 && next.all { it in '0'..'9' }) source = next
                    },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(rt(en, "အခြေခံဂဏန်း (00–99)", "Source pair (00–99)")) },
                    placeholder = { Text(rt(en, "ဥပမာ 12", "e.g. 12")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !exact, onClick = { exact = false },
                        modifier = Modifier.weight(1f),
                        label = { Text(rt(en, "ဘာဆက်ထွက်ခဲ့လဲ", "Find outcomes")) })
                    FilterChip(selected = exact, onClick = { exact = true },
                        modifier = Modifier.weight(1f),
                        label = { Text(rt(en, "ဂဏန်းတိုက်စစ်", "Exact match")) })
                }
                if (exact) {
                    OutlinedTextField(value = target,
                        onValueChange = { next ->
                            if (next.length <= 2 && next.all { it in '0'..'9' }) target = next
                        },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(rt(en, "တိုက်စစ်မည့်ဂဏန်း (00–99)", "Target pair (00–99)")) },
                        placeholder = { Text("21") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    TextButton(onClick = { if (source.length == 2) target = source.reversed() },
                        enabled = source.length == 2) {
                        Icon(Icons.Default.SwapHoriz, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(rt(en, "ပြောင်းပြန်ဂဏန်း သတ်မှတ်မည်", "Use reversed source pair"))
                    }
                }
            }
        }

        item {
            ResearchPanel(en, rt(en, "၃။ သမိုင်းမှတ်တမ်းကာလ", "3. Historical period")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    ResearchText(rt(en, "စစ်ဆေးမည့်ကာလ", "Evidence window"),
                        en, LedgerTextRole.HELPER)
                    Box {
                        TextButton(onClick = { rangeMenu = true }) {
                            Text(when (rangeKey) {
                                "week" -> rt(en, "၁ ပတ်", "1 week")
                                "month" -> rt(en, "၁ လ", "1 month")
                                "custom" -> rt(en, "စိတ်ကြိုက်", "Custom")
                                else -> rt(en, "ရှိသမျှမှတ်တမ်း", "All history")
                            })
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = rangeMenu, onDismissRequest = { rangeMenu = false }) {
                            listOf(
                                "all" to rt(en, "ရှိသမျှမှတ်တမ်း", "All history"),
                                "week" to rt(en, "၁ ပတ်", "1 week"),
                                "month" to rt(en, "၁ လ", "1 month"),
                                "custom" to rt(en, "စိတ်ကြိုက်", "Custom"),
                            ).forEach { (key, label) ->
                                DropdownMenuItem(text = { Text(label) },
                                    onClick = { rangeKey = key; rangeMenu = false })
                            }
                        }
                    }
                }
                if (rangeKey == "custom") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picking = "start" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(customStart, maxLines = 1,
                                style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = { picking = "end" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp)) {
                            Text(customEnd, maxLines = 1,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (!rangeValid) Text(rt(en, "အစနှင့်အဆုံးရက်စွဲ မမှန်ပါ။",
                        "Start date must not be after end date."),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                ResearchText(rt(en,
                    "Source နှင့် ဆက်စပ်ရလဒ် နှစ်ခုလုံး ရွေးထားသည့်ကာလအတွင်းရှိမှ စစ်မည်။",
                    "Both source and following occasion must fall inside the selected range."),
                    en, LedgerTextRole.HELPER, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            Button(
                onClick = {
                    submitted = TwoDResearchQuery(source, scope, relationship,
                        if (exact) target else null, researchRange)
                    filter = "all"
                    visible = 20
                    showAllFrequencies = false
                },
                enabled = readyToSearch,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(8.dp))
                Text(rt(en, "သမိုင်းစစ်ဆေးရန်", "Search history"))
            }
        }

        if (!ready) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else if (submitted != null && current == null) {
            item {
                Box(Modifier.fillMaxWidth().padding(20.dp),
                    contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else if (current != null) {
            item {
                ResearchPanel(en, rt(en, "သုတေသနရလဒ်", "Research results")) {
                    val q = current.query
                    ResearchText(
                        (if (q.relationship == TwoDResearchRelationship.SAME_DAY_PM)
                            "AM" else q.scope.name) + " " + q.sourcePair + " → " +
                            (q.targetPair ?: "?"),
                        en, LedgerTextRole.CARD_TITLE)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        ResearchStat(rt(en, "Source", "Sources"), current.sourceCount,
                            rt(en, "တွေ့ရှိ", "found"), en, Modifier.weight(1f))
                        ResearchStat(rt(en, "စစ်နိုင်", "Observed"), current.observedCount,
                            rt(en, "ရလဒ်ရှိ", "results"), en, Modifier.weight(1f))
                        ResearchStat(
                            if (q.targetPair == null) rt(en, "ဂဏန်းမျိုး", "Distinct")
                            else rt(en, "ကိုက်ညီ", "Matched"),
                            if (q.targetPair == null) current.followingFrequency.size
                            else current.matchCount,
                            rt(en, "စုစုပေါင်း", "total"), en, Modifier.weight(1f))
                    }
                    if (q.targetPair != null && current.observedCount > 0) {
                        val percent = current.matchCount * 100.0 / current.observedCount
                        ResearchText(
                            rt(en, "မှတ်တမ်းအတွင်း ကိုက်ညီမှု", "Observed historical matches") +
                                " " + current.matchCount + " / " + current.observedCount +
                                " (" + String.format(Locale.US, "%.1f", percent) + "%)",
                            en, LedgerTextRole.BODY)
                    }
                    ResearchText(
                        rt(en, "မကိုက်ညီ ", "Other outcomes ") + current.otherCount +
                            " · " + rt(en, "Result မတွေ့ ", "Missing ") + current.missingCount +
                            " · " + rt(en, "မထွက်သေး ", "Pending ") + current.pendingCount +
                            " · " + rt(en, "ကာလအပြင် ", "Out of range ") + current.outsideCount,
                        en, LedgerTextRole.HELPER,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    ResearchText(rt(en,
                        "ဒီရလဒ်တွေဟာ သမိုင်းအတွင်း တွေ့ရှိမှုများသာဖြစ်ပြီး နောက်အကြိမ်အတွက် ခန့်မှန်းချက်မဟုတ်ပါ။",
                        "Historical observations only, not a prediction of the next draw."),
                        en, LedgerTextRole.HELPER,
                        MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (current.followingFrequency.isNotEmpty()) {
                item {
                    ResearchPanel(en, rt(en, "ဆက်ထွက်ခဲ့သောဂဏန်းတွဲများ", "Following pair frequencies")) {
                        val list = if (showAllFrequencies) current.followingFrequency
                            else current.followingFrequency.take(10)
                        list.chunked(5).forEach { row ->
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                row.forEach { entry ->
                                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .40f)) {
                                        Column(Modifier.padding(vertical = 7.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally) {
                                            ResearchText(entry.digit, en, LedgerTextRole.BODY)
                                            Text(entry.count.toString() + "×",
                                                style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        if (!showAllFrequencies && current.followingFrequency.size > 10) {
                            TextButton(onClick = { showAllFrequencies = true }) {
                                Text(rt(en, "ဂဏန်းတွဲအားလုံး ကြည့်ရန်", "View all pairs") +
                                    " (" + current.followingFrequency.size + ")")
                            }
                        }
                    }
                }
            }

            item {
                ResearchText(rt(en, "အထောက်အထားမှတ်တမ်း", "Historical evidence"),
                    en, LedgerTextRole.CARD_TITLE)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val filters = if (current.query.targetPair == null) listOf(
                        "all" to rt(en, "အားလုံး", "All"),
                        "recorded" to rt(en, "ရလဒ်ရှိ", "Observed"),
                        "unknown" to rt(en, "မရှိ/မပြည့်", "Unavailable"),
                    ) else listOf(
                        "all" to rt(en, "အားလုံး", "All"),
                        "matched" to rt(en, "ကိုက်ညီ", "Matched"),
                        "other" to rt(en, "အခြား", "Other"),
                        "unknown" to rt(en, "မရှိ/မပြည့်", "Unavailable"),
                    )
                    filters.forEach { (key, label) ->
                        FilterChip(selected = filter == key,
                            onClick = { filter = key; visible = 20 },
                            modifier = Modifier.weight(1f),
                            label = { Text(label, maxLines = 2,
                                style = MaterialTheme.typography.labelSmall) })
                    }
                }
                ResearchText(
                    rt(en, "ပြသထားသည့်မှတ်တမ်း ", "Showing ") +
                        displayed.size + " / " + filtered.size,
                    en, LedgerTextRole.HELPER, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (filtered.isEmpty()) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
                        Box(Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
                            ResearchText(rt(en, "ကိုက်ညီသောမှတ်တမ်း မတွေ့ပါ။",
                                "No records in this category."), en, LedgerTextRole.BODY)
                        }
                    }
                }
            } else {
                items(displayed, key = { it.source.date.toString() + it.source.session.name }) {
                    row -> ResearchEvidenceLine(row, en)
                }
                if (displayed.size < filtered.size) {
                    item {
                        TextButton(onClick = { visible += 20 },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(rt(en, "နောက်ထပ်မှတ်တမ်းများ", "Show more evidence"))
                        }
                    }
                }
            }
        } else {
            item {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f)) {
                    ResearchText(rt(en,
                        "ဂဏန်းနှင့် ဆက်စပ်မှုကို ရွေးပြီး စစ်ဆေးရန် နှိပ်ပါ။",
                        "Enter a pair, choose a relationship and run your research."),
                        en, LedgerTextRole.BODY, modifier = Modifier.padding(18.dp))
                }
            }
        }
    }

    picking?.let { choice ->
        val currentDate = if (choice == "start") customStart else customEnd
        val initialMillis = remember(choice, currentDate) {
            runCatching { LocalDate.parse(currentDate).atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli() }.getOrNull()
        }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(onDismissRequest = { picking = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate().toString()
                        if (choice == "start") customStart = selected else customEnd = selected
                    }
                    picking = null
                }) { Text(rt(en, "ရွေးမည်", "Select")) }
            },
            dismissButton = {
                TextButton(onClick = { picking = null }) {
                    Text(rt(en, "ပိတ်မည်", "Cancel"))
                }
            }) {
            DatePicker(state = picker)
        }
    }
}
