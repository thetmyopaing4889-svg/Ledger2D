@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.AppContainer
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private data class HistoricPeriod(val start: LocalDate, val end: LocalDate)

private fun selectedHistoricPeriod(
    key: String, today: LocalDate, customStart: String, customEnd: String,
): HistoricPeriod? {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val first = today.withDayOfMonth(1)
    return when (key) {
        "today" -> HistoricPeriod(today, today)
        "week" -> HistoricPeriod(monday, today)
        "previousWeek" -> HistoricPeriod(monday.minusWeeks(1), monday.minusDays(1))
        "month" -> HistoricPeriod(first, today)
        "previousMonth" -> HistoricPeriod(first.minusMonths(1), first.minusDays(1))
        "custom" -> {
            val a = runCatching { LocalDate.parse(customStart) }.getOrNull()
            val b = runCatching { LocalDate.parse(customEnd) }.getOrNull()
            if (a == null || b == null || a > b || b > today) null else HistoricPeriod(a, b)
        }
        else -> null
    }
}

@Composable
fun Historical2DAnalysisScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    val language = LocalLanguage.current
    val en = language.code == "en"
    fun tr(my: String, english: String) = if (en) english else my
    val today = remember { LocalDate.now() }
    var periodKey by rememberSaveable { mutableStateOf("month") }
    var customStart by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toString()) }
    var customEnd by rememberSaveable { mutableStateOf(today.toString()) }
    var sessionKey by rememberSaveable { mutableStateOf("both") }
    var isResults by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedDigit by rememberSaveable { mutableStateOf<String?>(null) }
    val historyFlow = remember(container) { container.history.observeAll() }
    val dailyFlow = remember(container) { container.liveResults.observeAll() }
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val daily by dailyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val merged = remember(history, daily, today) {
        Historic2DDataSource.resolve(daily, history, today)
    }
    val period = selectedHistoricPeriod(periodKey, today, customStart, customEnd)
    val session = when (sessionKey) {
        "am" -> DrawSession.MORNING
        "pm" -> DrawSession.EVENING
        else -> null
    }
    val statistics = remember(merged, period, session) {
        period?.let { HistoricStatisticsCalculator.calculate(merged.draws, it.start, it.end, session) }
    }

    AppScaffold(
        title = tr("2D ဂဏန်းသုံးသပ်ချက်", "2D Analysis"),
        onBack = { if (isResults) isResults = false else onBack() },
        includeQuickEntryFab = false,
    ) { padding ->
        if (!isResults) {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    HistoricCard {
                        Text(tr("လေ့လာမည့်ကာလ ရွေးချယ်ရန်", "Select analysis period"),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(tr("အတိတ်ရလဒ်တွေကို ရက်စွဲနဲ့ အကြိမ်အလိုက် စစ်ဆေးနိုင်ပါတယ်။",
                            "Explore historical draws by date and session."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val presets = listOf(
                            "today" to tr("ယနေ့", "Today"),
                            "week" to tr("ယခုအပတ်", "This week"),
                            "previousWeek" to tr("အရင်အပတ်", "Last week"),
                            "month" to tr("ယခုလ", "This month"),
                            "previousMonth" to tr("အရင်လ", "Last month"),
                            "custom" to tr("စိတ်ကြိုက်", "Custom"),
                        )
                        presets.chunked(3).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { (id, label) ->
                                    FilterChip(
                                        selected = periodKey == id,
                                        onClick = { periodKey = id },
                                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                        if (periodKey == "custom") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) {
                                    HistoricDatePicker(tr("စတင်ရက်", "From"), customStart) { customStart = it }
                                }
                                Box(Modifier.weight(1f)) {
                                    HistoricDatePicker(tr("ပြီးဆုံးရက်", "To"), customEnd) { customEnd = it }
                                }
                            }
                        }
                        if (period != null) {
                            Text(period.start.toString() + "  —  " + period.end.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = AppColors.PrimaryDeep)
                        } else {
                            Text(tr("စတင်ရက်၊ ပြီးဆုံးရက်ကို ပြန်စစ်ပါ။", "Check the selected date range."),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {
                    HistoricCard {
                        Text(tr("အကြိမ်ရွေးချယ်ရန်", "Session"), fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "both" to tr("နှစ်ကြိမ်လုံး", "Both"),
                                "am" to tr("မနက်အကြိမ်", "Morning"),
                                "pm" to tr("ညနေအကြိမ်", "Evening"),
                            ).forEach { (id, label) ->
                                FilterChip(
                                    selected = sessionKey == id, onClick = { sessionKey = id },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(label, maxLines = 2, textAlign = TextAlign.Center) },
                                )
                            }
                        }
                    }
                }
                item {
                    HistoricCard {
                        Text(tr("ပါဝင်မည့် အကြောင်းအရာ", "What you can explore"), fontWeight = FontWeight.Bold)
                        HistoricFeatureRow(tr("ထွက်ဂဏန်းသုံးသပ်ချက်", "Draw statistics"),
                            tr("00–99၊ ထိပ်/ပိတ်၊ ပြောင်းပြန်နှင့် အကြိမ်မှတ်တမ်း",
                                "00–99, digits, reverse pairs and history"))
                        HistoricFeatureRow(tr("ပျောက်ဂဏန်း", "Missing numbers"),
                            tr("ကာလအတွင်း မထွက်သောဂဏန်းနှင့် နောက်ဆုံးတွေ့ချိန်",
                                "Not seen in period and last observed draw"))
                        HistoricFeatureRow(tr("မူဝါဒနှင့် ဖော်မြူလာ", "Rules and formulas"),
                            tr("မူဝါဒတွေကို နောက်မှ ထည့်သွင်းမည်", "Rules will be added later"))
                    }
                }
                item {
                    Button(
                        onClick = { isResults = true; tab = 0 },
                        enabled = period != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text(tr("သုံးသပ်ချက်ကြည့်မည်", "View analysis"))
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        } else if (statistics != null && period != null) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Surface(color = Color.White, tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(period.start.toString() + " — " + period.end.toString(),
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    tr("စစ်ဆေးပြီးသော ရလဒ်", "Observed results") + ": " + statistics.totalDraws +
                                        "  •  " + if (session == null) tr("နှစ်ကြိမ်လုံး", "Both") else
                                        if (session == DrawSession.MORNING) tr("မနက်", "Morning") else tr("ညနေ", "Evening"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { isResults = false }) { Text(tr("ပြောင်းရန်", "Change")) }
                        }
                    }
                }
                ScrollableTabRow(
                    selectedTabIndex = tab, edgePadding = 6.dp,
                    containerColor = Color.White,
                    contentColor = AppColors.PrimaryDeep,
                ) {
                    listOf(
                        tr("အကျဉ်းချုပ်", "Overview"), "00–99",
                        tr("ပျောက်ဂဏန်း", "Missing"), tr("မူဝါဒ", "Rules"),
                    ).forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index },
                            text = { Text(label, maxLines = 1) })
                    }
                }
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        if (period.end == today) {
                            HistoricNotice(tr(
                                "ယနေ့ Live ရလဒ်ကို အပြီးသတ်အတည်ပြုမှု မသိမ်းထားသေးသဖြင့် ဒီနေ့အကြိမ်တွေကို မတွက်ထားပါ။",
                                "Today's LIVE values are excluded until independently settled; no provisional result is counted."
                            ))
                        }
                        if (statistics.totalDraws == 0 && tab != 3) {
                            HistoricCard {
                                Text(tr("ဤကာလအတွက် ရလဒ်မရှိသေးပါ။", "No historical draw available for this period."),
                                    fontWeight = FontWeight.SemiBold)
                                Text(tr("Settings မှ History Result Update လုပ်ပြီး ပြန်ကြည့်ပါ။",
                                    "Update History Result in Settings, then return."),
                                    style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = onSettings) {
                                    Text(tr("Settings သို့သွားမည်", "Open Settings"))
                                }
                            }
                        }
                    }
                    if (tab == 0 && statistics.totalDraws > 0) {
                        item { HistoricOverview(statistics, en) }
                    }
                    if (tab == 1 && statistics.totalDraws > 0) {
                        item {
                            HistoricCard {
                                Text(tr("00–99 အထွက်ဇယား", "00–99 frequency"),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(tr("တစ်ကွက်ချင်း နှိပ်ပြီး အသေးစိတ်ကြည့်နိုင်သည်။",
                                    "Tap a number for its history and recency."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                statistics.counts.chunked(5).forEach { numbers ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        numbers.forEach { number ->
                                            val highlighted = number.count > 0
                                            Box(
                                                Modifier.weight(1f).height(56.dp)
                                                    .background(
                                                        if (highlighted) AppColors.Blush else Color(0xFFF4F1F2),
                                                        RoundedCornerShape(9.dp),
                                                    )
                                                    .border(1.dp, if (highlighted) AppColors.Stone else Color(0xFFE8E3E5),
                                                        RoundedCornerShape(9.dp))
                                                    .clickable { selectedDigit = number.digit },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(number.digit, fontWeight = FontWeight.Bold,
                                                        color = if (highlighted) AppColors.PrimaryDeep else Color.DarkGray)
                                                    Text(number.count.toString(), style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                        item {
                            HistoricCard {
                                Text(tr("ထိပ်ဂဏန်း အထွက်", "Tens frequency"), fontWeight = FontWeight.Bold)
                                statistics.tens.forEachIndexed { n, count ->
                                    HistoricBar(n.toString(), count, statistics.tens.maxOrNull() ?: 0)
                                }
                            }
                        }
                        item {
                            HistoricCard {
                                Text(tr("ပိတ်ဂဏန်း အထွက်", "Ones frequency"), fontWeight = FontWeight.Bold)
                                statistics.ones.forEachIndexed { n, count ->
                                    HistoricBar(n.toString(), count, statistics.ones.maxOrNull() ?: 0)
                                }
                            }
                        }
                    }
                    if (tab == 2 && statistics.totalDraws > 0) {
                        item { HistoricMissing(statistics, en, onDigit = { selectedDigit = it }) }
                    }
                    if (tab == 3) {
                        item {
                            HistoricCard {
                                Text(tr("မူဝါဒနှင့် ဖော်မြူလာများ", "Rules and formulas"),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(tr(
                                    "မူဝါဒစာကြည့်တိုက်အတွက် နေရာကို ပြင်ဆင်ထားသည်။ မူဝါဒအစစ်များကို နောက်မှ ထည့်သွင်းမည်။ ခန့်မှန်းဂဏန်းအတု မပြပါ။",
                                    "The rules library is reserved for verified user-supplied content. No sample rules or invented predictions are shown."
                                ), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item {
                        val primaryCount = merged.draws.count {
                            it.date >= period.start && it.date <= period.end &&
                                (session == null || it.session == session) && it.source == HistoricSource.DAILY
                        }
                        val historyCount = statistics.totalDraws - primaryCount
                        val conflicts = merged.conflicts.count {
                            it.date >= period.start && it.date <= period.end &&
                                (session == null || it.session == session)
                        }
                        HistoricCard {
                            Text(tr("အချက်အလက် အရင်းအမြစ်", "Data sources"), fontWeight = FontWeight.Bold)
                            Text(tr("နေ့စဉ်သိမ်းမှတ်တမ်း", "Daily saved") + ": " + primaryCount +
                                "  •  History: " + historyCount,
                                style = MaterialTheme.typography.bodySmall)
                            if (conflicts > 0) Text(
                                tr("တူညီသောအကြိမ်တွင် Source မကိုက်ညီမှု", "Source conflicts") +
                                    ": " + conflicts + " (" + tr("Primary ကိုသုံးထားသည်", "Primary selected") + ")",
                                style = MaterialTheme.typography.bodySmall, color = AppColors.PrimaryDeep)
                            Text(tr(
                                "ပျောက်ဂဏန်းဆိုသည်မှာ ရွေးထားသောကာလအတွင်း မတွေ့ခဲ့သောဂဏန်းဖြစ်ပြီး နောက်အကြိမ်ပေါက်မည့်ဂဏန်း မဟုတ်ပါ။",
                                "Missing and frequent numbers describe observed history, not future probabilities."
                            ), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    selectedDigit?.let { digit ->
        val detail = statistics?.counts?.firstOrNull { it.digit == digit }
        AlertDialog(
            onDismissRequest = { selectedDigit = null },
            title = { Text(tr("ဂဏန်း", "Number") + " " + digit) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("ရွေးထားသောကာလအတွင်း", "In selected period") + ": " +
                        (detail?.count ?: 0) + " " + tr("ကြိမ်", "draws"))
                    Text(tr("နောက်ဆုံးတွေ့ရသောအကြိမ်", "Last seen") + ": " +
                        (detail?.lastSeen?.let {
                            it.date.toString() + " • " + if (it.session == DrawSession.MORNING)
                                tr("မနက်", "AM") else tr("ညနေ", "PM")
                        } ?: tr("သိမ်းထားသည့် History တွင် မတွေ့ပါ", "Not found in available history")))
                    Text(tr("နောက်ဆုံးတွေ့ပြီးနောက် ရလဒ်ရှိခဲ့သောအကြိမ်", "Observed draws since") +
                        ": " + (detail?.observedDrawsSince?.toString() ?: "—"))
                    Text(tr("ပြောင်းပြန်ဂဏန်း", "Reverse") + ": " + digit.reversed())
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDigit = null }) {
                    Text(tr("ပိတ်မည်", "Close"))
                }
            },
        )
    }
}

@Composable
private fun HistoricDatePicker(label: String, date: String, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(date, maxLines = 1, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (open) {
        val initial = remember(date) {
            runCatching {
                LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()
        }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun HistoricCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.White, shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD8E3)),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
        )
    }
}

@Composable
private fun HistoricFeatureRow(title: String, subtitle: String) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoricNotice(message: String) {
    Surface(color = AppColors.Blush, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, null, tint = AppColors.PrimaryDeep,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = AppColors.Ink)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun HistoricBar(label: String, count: Int, maximum: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, modifier = Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold)
        LinearProgressIndicator(
            progress = { if (maximum == 0) 0f else count.toFloat() / maximum },
            modifier = Modifier.weight(1f).height(8.dp),
            color = AppColors.PrimaryDeep, trackColor = AppColors.Blush,
        )
        Text(count.toString(), modifier = Modifier.width(30.dp), textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun HistoricOverview(stats: HistoricStatistics, en: Boolean) {
    fun tr(my: String, english: String) = if (en) english else my
    val leading = stats.topNumbers.firstOrNull()
    HistoricCard {
        Text(tr("အဓိကအချက်အလက်", "Overview"), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HistoricMetric(Modifier.weight(1f), tr("အကြိမ်စုစုပေါင်း", "Draws"), stats.totalDraws.toString())
            HistoricMetric(Modifier.weight(1f), tr("ထွက်ဖူးသောဂဏန်း", "Distinct"), stats.distinctCount.toString())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HistoricMetric(Modifier.weight(1f), tr("အထွက်အများဆုံး", "Most frequent"),
                leading?.let { it.digit + " (" + it.count + ")" } ?: "—")
            HistoricMetric(Modifier.weight(1f), tr("ကာလအတွင်း ပျောက်", "Missing"),
                stats.missing.size.toString())
        }
    }
    HistoricCard {
        Text(tr("အထွက်အများဆုံး ၁၀ ကွက်", "Top 10 numbers"), fontWeight = FontWeight.Bold)
        stats.topNumbers.forEach { number ->
            HistoricBar(number.digit, number.count, leading?.count ?: 1)
        }
        if (stats.topNumbers.isEmpty()) {
            Text(tr("ရလဒ်မရှိပါ", "No results"))
        }
    }
    HistoricCard {
        Text(tr("ထိပ် / ပိတ် အနှစ်ချုပ်", "Tens / ones summary"),
            fontWeight = FontWeight.Bold)
        val topTens = stats.tens.indices.maxByOrNull { stats.tens[it] }
        val topOnes = stats.ones.indices.maxByOrNull { stats.ones[it] }
        Text(tr("ထိပ်အများဆုံး", "Top tens") + ": " +
            (topTens?.toString() ?: "—") + " (" + (topTens?.let { stats.tens[it] } ?: 0) + ")")
        Text(tr("ပိတ်အများဆုံး", "Top ones") + ": " +
            (topOnes?.toString() ?: "—") + " (" + (topOnes?.let { stats.ones[it] } ?: 0) + ")")
        Text(tr("အပူးဂဏန်း ထွက်ခဲ့သောအကြိမ်", "Double-number draws") +
            ": " + stats.doubleCount)
    }
    if (stats.reversePairs.any { it.firstCount + it.reverseCount > 0 }) {
        HistoricCard {
            Text(tr("ပြောင်းပြန်ဂဏန်းအတွဲ", "Reverse pairs"), fontWeight = FontWeight.Bold)
            stats.reversePairs.filter { it.firstCount + it.reverseCount > 0 }.take(8).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(pair.first + " / " + pair.reverse, fontWeight = FontWeight.SemiBold)
                    Text(pair.firstCount.toString() + " / " + pair.reverseCount)
                }
            }
        }
    }
    if (stats.dailyPairs.isNotEmpty()) {
        HistoricCard {
            Text(tr("မနက် / ညနေ တစ်ရက်တည်းရလဒ်များ", "Same-day AM / PM"),
                fontWeight = FontWeight.Bold)
            stats.dailyPairs.takeLast(8).asReversed().forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(pair.date.toString(), style = MaterialTheme.typography.bodySmall)
                    Text(pair.morning + "  →  " + pair.evening, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HistoricMetric(modifier: Modifier, label: String, value: String) {
    Surface(modifier, color = AppColors.Blush, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Text(value, style = MaterialTheme.typography.titleLarge,
                color = AppColors.PrimaryDeep, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistoricMissing(stats: HistoricStatistics, en: Boolean, onDigit: (String) -> Unit) {
    fun tr(my: String, english: String) = if (en) english else my
    val missing = stats.missing.sortedWith(
        compareBy<HistoricNumberStat> { it.lastSeen == null }
            .thenByDescending { it.observedDrawsSince ?: -1 }
            .thenBy { it.digit }
    )
    HistoricCard {
        Text(tr("ကာလအတွင်း မထွက်ခဲ့သောဂဏန်း", "Not seen in selected period"),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(tr("စုစုပေါင်း", "Total") + ": " + missing.size,
            style = MaterialTheme.typography.bodyMedium)
        Text(tr("ရက်စွဲကာလအတွင်း မထွက်ခဲ့ခြင်းသာဖြစ်ပြီး ပေါက်ရန်နီးလာခြင်း မဟုတ်ပါ။",
            "A missing number is not more likely to appear next."),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)
        missing.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { number ->
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                        onClick = { onDigit(number.digit) },
                        color = AppColors.Blush,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(number.digit, fontWeight = FontWeight.Bold,
                                color = AppColors.PrimaryDeep)
                        }
                    }
                }
            }
        }
    }
    HistoricCard {
        Text(tr("ကြာရှည်ပျောက်နေမှု (လက်ရှိဒေတာအရ)", "Observed recency"),
            fontWeight = FontWeight.Bold)
        missing.take(20).forEach { number ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(number.digit, fontWeight = FontWeight.Bold)
                Text(
                    number.observedDrawsSince?.let {
                        it.toString() + " " + tr("အကြိမ်", "draws")
                    } ?: tr("မှတ်တမ်းတွင် မတွေ့ပါ", "Not found"),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Text(tr("မရှိသော History အကြိမ်များကို ပျောက်အကြိမ်အဖြစ် မရေတွက်ပါ။",
            "Recency counts only observed draws; unavailable records are excluded."),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
