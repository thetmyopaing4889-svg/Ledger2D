@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MenuBook
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard
import com.myanmar.ledger2d.core.design.LedgerTextRole
import kotlinx.coroutines.delay
import java.time.ZoneId
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
        "last7" -> HistoricPeriod(today.minusDays(6), today)
        "last14" -> HistoricPeriod(today.minusDays(13), today)
        "last28" -> HistoricPeriod(today.minusDays(27), today)
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


private val auroraPlum = Color(0xFF390D37)
private val auroraCherry = Color(0xFF852454)
private val auroraRose = Color(0xFFD5497D)
private val auroraInk = Color(0xFF332331)
private val auroraMuted = Color(0xFF876C7C)
private val auroraCanvas = Color(0xFFF8F3F7)
private val auroraSoft = Color(0xFFF8EAF1)
private val auroraGradient = Brush.linearGradient(
    listOf(auroraPlum, auroraCherry, auroraRose, Color(0xFFF18CA0))
)

@Composable
fun Historical2DAnalysisScreen(
    container: AppContainer, onBack: () -> Unit, onSettings: () -> Unit,
) {
    val language = LocalLanguage.current
    val en = language.code == "en"
    fun tr(my: String, english: String) = if (en) english else my
    var today by remember { mutableStateOf(LocalDate.now(ZoneId.of("Asia/Yangon"))) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            today = LocalDate.now(ZoneId.of("Asia/Yangon"))
        }
    }
    var periodKey by rememberSaveable { mutableStateOf("week") }
    var sessionKey by rememberSaveable { mutableStateOf("both") }
    var customStart by rememberSaveable { mutableStateOf(today.withDayOfMonth(1).toString()) }
    var customEnd by rememberSaveable { mutableStateOf(today.toString()) }
    var draftPeriod by rememberSaveable { mutableStateOf(periodKey) }
    var draftSession by rememberSaveable { mutableStateOf(sessionKey) }
    var draftStart by rememberSaveable { mutableStateOf(customStart) }
    var draftEnd by rememberSaveable { mutableStateOf(customEnd) }
    var filterOpen by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var patternTab by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var sorting by rememberSaveable { mutableStateOf(0) }
    var chosenNumber by rememberSaveable { mutableStateOf<String?>(null) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showAllMissing by rememberSaveable { mutableStateOf(false) }

    val historyFlow = remember(container) { container.history.observeAll() }
    val dailyFlow = remember(container) { container.liveResults.observeAll() }
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val daily by dailyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val merged = remember(history, daily, today) { Historic2DDataSource.resolve(daily, history, today) }
    val period = selectedHistoricPeriod(periodKey, today, customStart, customEnd)
    val session = when (sessionKey) {
        "am" -> DrawSession.MORNING
        "pm" -> DrawSession.EVENING
        else -> null
    }
    val stats = remember(merged, period, session) {
        period?.let { HistoricStatisticsCalculator.calculate(merged.draws, it.start, it.end, session) }
    }
    val filteredHistory = remember(merged, period, session) {
        if (period == null) emptyList() else merged.draws
            .filter { it.date in period.start..period.end && (session == null || it.session == session) }
            .sortedWith(compareByDescending<HistoricDraw> { it.date }
                .thenByDescending { it.session.ordinal })
    }
    val navLabels = listOf(
        tr("သုံးသပ်", "Insights"), tr("ဂဏန်း", "Explorer"),
        tr("ပုံစံ", "Patterns"), tr("ပျောက်", "Missing"), tr("မူ", "Rules"),
    )
    val periods = listOf(
        "week" to tr("ယခုအပတ်", "This week"),
        "previousWeek" to tr("အရင်အပတ်", "Last week"),
        "last7" to tr("၇ ရက်", "7 days"),
        "last14" to tr("၁၄ ရက်", "14 days"),
        "last28" to tr("၂၈ ရက်", "28 days"),
        "today" to tr("ယနေ့", "Today"),
        "month" to tr("ယခုလ", "This month"),
        "previousMonth" to tr("အရင်လ", "Last month"),
        "custom" to tr("စိတ်ကြိုက်", "Custom"),
    )
    Scaffold(
        containerColor = auroraCanvas,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(auroraCanvas).statusBarsPadding()
                    .height(58.dp).padding(start = 7.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = auroraPlum)
                }
                Column(Modifier.weight(1f)) {
                    Text("2D Analysis", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = auroraInk)
                    Text(
                        (periods.firstOrNull { it.first == periodKey }?.second ?: "") +
                            " · " + when (sessionKey) {
                                "am" -> tr("မနက်", "AM")
                                "pm" -> tr("ညနေ", "PM")
                                else -> tr("နှစ်ကြိမ်လုံး", "Both")
                            },
                        style = MaterialTheme.typography.labelSmall, color = auroraMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    Surface(
                        onClick = {
                            draftPeriod = periodKey
                            draftSession = sessionKey
                            draftStart = customStart
                            draftEnd = customEnd
                            filterOpen = true
                        },
                        shape = CircleShape, color = auroraSoft,
                    ) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Tune, tr("စစ်ထုတ်ရန်", "Filter"), tint = auroraCherry)
                        }
                    }
                    DropdownMenu(
                        expanded = filterOpen,
                        onDismissRequest = { filterOpen = false },
                        modifier = Modifier.width(308.dp),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(tr("သုံးသပ်ချက် စစ်ထုတ်ရန်", "Analysis filters"),
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                            Text(tr("အချိန်ကာလ", "Period"),
                                style = MaterialTheme.typography.labelMedium, color = auroraMuted)
                            periods.chunked(2).forEach { options ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    options.forEach { (id, label) ->
                                        FilterChip(
                                            selected = draftPeriod == id, onClick = { draftPeriod = id },
                                            modifier = Modifier.weight(1f),
                                            label = { Text(label, style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        )
                                    }
                                    if (options.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                            if (draftPeriod == "custom") {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.weight(1f)) {
                                        HistoricDatePicker(tr("စရက်", "From"), draftStart) { draftStart = it }
                                    }
                                    Box(Modifier.weight(1f)) {
                                        HistoricDatePicker(tr("ဆုံးရက်", "To"), draftEnd) { draftEnd = it }
                                    }
                                }
                            }
                            Text(tr("အကြိမ်", "Draw"), style = MaterialTheme.typography.labelMedium,
                                color = auroraMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "both" to tr("နှစ်ကြိမ်", "Both"),
                                    "am" to tr("မနက်", "AM"),
                                    "pm" to tr("ညနေ", "PM"),
                                ).forEach { (id, label) ->
                                    FilterChip(
                                        selected = draftSession == id, onClick = { draftSession = id },
                                        modifier = Modifier.weight(1f),
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    )
                                }
                            }
                            val valid = selectedHistoricPeriod(draftPeriod, today, draftStart, draftEnd) != null
                            if (!valid) {
                                Text(tr("ရက်စွဲများကို ပြန်စစ်ပါ", "Check the dates"),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    periodKey = "week"; sessionKey = "both"; filterOpen = false
                                }, modifier = Modifier.weight(1f)) {
                                    Text(tr("မူလ", "Reset"))
                                }
                                Button(
                                    onClick = {
                                        periodKey = draftPeriod; sessionKey = draftSession
                                        customStart = draftStart; customEnd = draftEnd
                                        filterOpen = false
                                    },
                                    enabled = valid, modifier = Modifier.weight(1f),
                                ) { Text(tr("အတည်ပြု", "Apply")) }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Box(
                Modifier.fillMaxWidth().background(auroraCanvas)
                    .navigationBarsPadding().padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Surface(color = Color.White, shadowElevation = 13.dp,
                    shape = RoundedCornerShape(23.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 5.dp)) {
                        navLabels.forEachIndexed { index, label ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                                    .background(if (tab == index) auroraSoft else Color.Transparent)
                                    .clickable { tab = index }
                                    .padding(vertical = 8.dp, horizontal = 1.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                val tint = if (tab == index) auroraCherry else Color(0xFFA69AA3)
                                when (index) {
                                    0 -> Icon(Icons.Default.Analytics, null, tint, Modifier.size(21.dp))
                                    1 -> Icon(Icons.Default.GridView, null, tint, Modifier.size(21.dp))
                                    2 -> Icon(Icons.Default.BarChart, null, tint, Modifier.size(21.dp))
                                    3 -> Icon(Icons.Default.ManageSearch, null, tint, Modifier.size(21.dp))
                                    else -> Icon(Icons.Default.MenuBook, null, tint, Modifier.size(21.dp))
                                }
                                Text(label,
                                    style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, language.code)
                                        .copy(fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium),
                                    color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier.fillMaxSize().padding(padding).background(
                Brush.verticalGradient(listOf(Color(0xFFF9EFF5), auroraCanvas, Color(0xFFFEFCFD)))
            )
        ) {
            Crossfade(targetState = tab, animationSpec = tween(210), label = "analysisTabs") { active ->
                if (active == 4) {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        item {
                            AuroraPanel {
                                // Unchanged user-rules placeholder from Build 957.
                                Text(tr("မူဝါဒနှင့် ဖော်မြူလာများ", "Rules and formulas"),
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(tr(
                                    "မူဝါဒစာကြည့်တိုက်အတွက် နေရာကို ပြင်ဆင်ထားသည်။ မူဝါဒအစစ်များကို နောက်မှ ထည့်သွင်းမည်။ ခန့်မှန်းဂဏန်းအတု မပြပါ။",
                                    "The rules library is reserved for verified user-supplied content. No sample rules or invented predictions are shown."
                                ), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                } else if (period == null || stats == null) {
                    AuroraEmpty(tr("ရွေးထားသောရက်စွဲကို ပြန်စစ်ပါ", "Check the date range"), null)
                } else if (stats.totalDraws == 0) {
                    AuroraEmpty(tr("ဒီကာလအတွက် အတည်ပြုရလဒ် မရှိသေးပါ",
                        "No confirmed results in this period"), onSettings)
                } else when (active) {
                    0 -> AuroraInsights(stats, filteredHistory, period, en,
                        { showHistory = true }, { chosenNumber = it })
                    1 -> AuroraExplorer(stats, en, search, { search = it },
                        sorting, { sorting = it }, { chosenNumber = it })
                    2 -> AuroraPatterns(stats, en, patternTab, { patternTab = it })
                    else -> AuroraMissing(stats, en, { chosenNumber = it }, { showAllMissing = true })
                }
            }
        }
    }
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text(tr("ရလဒ်မှတ်တမ်း", "Result history")) },
            text = {
                LazyColumn(Modifier.heightIn(max = 470.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filteredHistory.size) { i ->
                        val draw = filteredHistory[i]
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(draw.date.toString(), style = MaterialTheme.typography.bodySmall)
                            Text((if (draw.session == DrawSession.MORNING) "AM" else "PM") +
                                "  " + draw.digit, fontWeight = FontWeight.Bold, color = auroraCherry)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) {
                Text(tr("ပိတ်မည်", "Close"))
            } },
        )
    }
    if (showAllMissing && stats != null) {
        val all = stats.missing.sortedWith(
            compareByDescending<HistoricNumberStat> { it.observedDrawsSince ?: -1 }
                .thenBy { it.digit })
        AlertDialog(
            onDismissRequest = { showAllMissing = false },
            title = { Text(tr("ပျောက်ဂဏန်းအားလုံး", "All missing numbers") + " (" + all.size + ")") },
            text = {
                LazyColumn(Modifier.heightIn(max = 470.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(all.chunked(5).size) { i ->
                        AuroraNumberRow(all.chunked(5)[i], 1) {
                            showAllMissing = false; chosenNumber = it
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAllMissing = false }) {
                Text(tr("ပိတ်မည်", "Close"))
            } },
        )
    }
    chosenNumber?.let { digit ->
        val detail = stats?.counts?.firstOrNull { it.digit == digit }
        AlertDialog(
            onDismissRequest = { chosenNumber = null },
            title = { Text(tr("ဂဏန်း", "Number") + " " + digit,
                color = auroraCherry, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("ရွေးထားသောကာလအတွင်း", "In selected period") + ": " +
                        (detail?.count ?: 0) + " " + tr("ကြိမ်", "draws"))
                    Text(tr("နောက်ဆုံးတွေ့ရသောအကြိမ်", "Last seen") + ": " +
                        (detail?.lastSeen?.let {
                            it.date.toString() + " · " +
                                (if (it.session == DrawSession.MORNING) "AM" else "PM")
                        } ?: tr("ရရှိထားသောမှတ်တမ်းတွင် မတွေ့ပါ", "Not found in available history")))
                    Text(tr("နောက်ဆုံးတွေ့ပြီးနောက် အကြိမ်", "Observed draws since") +
                        ": " + (detail?.observedDrawsSince?.toString() ?: "—"))
                    Text(tr("ပြောင်းပြန်ဂဏန်း", "Reverse") + ": " + digit.reversed())
                }
            },
            confirmButton = { TextButton(onClick = { chosenNumber = null }) {
                Text(tr("ပိတ်မည်", "Close"))
            } },
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
private fun AuroraPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(22.dp), shadowElevation = 5.dp) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}
@Composable
private fun AuroraEmpty(message: String, onSettings: (() -> Unit)?) {
    Column(Modifier.fillMaxSize().padding(17.dp)) {
        AuroraPanel {
            Text(message, style = MaterialTheme.typography.titleMedium, color = auroraInk)
            if (onSettings != null) {
                Text("History Result", style = MaterialTheme.typography.labelMedium,
                    color = auroraMuted)
                TextButton(onClick = onSettings) { Text("Settings") }
            }
        }
    }
}
@Composable
private fun AuroraMiniHeader(title: String, subtitle: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(auroraGradient)) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 30.dp, y = (-40).dp)
            .size(115.dp).background(Color.White.copy(alpha = .08f), CircleShape))
        Column(Modifier.padding(horizontal = 17.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color(0xFFFFE5EF), style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
private fun AuroraHero(stats: HistoricStatistics, period: HistoricPeriod, en: Boolean) {
    fun tr(my: String, english: String) = if (en) english else my
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(auroraGradient)) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 58.dp, y = (-65).dp)
            .size(185.dp).background(Color.White.copy(alpha = .08f), CircleShape))
        Box(Modifier.align(Alignment.BottomStart).offset(x = (-65).dp, y = 82.dp)
            .size(170.dp).background(Color.White.copy(alpha = .06f), CircleShape))
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("CHERRY'S ANALYTICS", color = Color(0xFFFFD9E9),
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(tr("2D သုံးသပ်ချက်", "Your 2D insights"), color = Color.White,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AuroraHeroMetric(Modifier.weight(1f), tr("အကြိမ်စုစုပေါင်း", "Completed draws"),
                    stats.totalDraws.toString())
                AuroraHeroMetric(Modifier.weight(1f), tr("နောက်ဆုံးရလဒ်", "Last result"),
                    stats.lastResult?.digit ?: "—")
            }
            Text(period.start.toString() + "  —  " + period.end.toString(),
                style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFE3ED))
        }
    }
}
@Composable
private fun AuroraHeroMetric(modifier: Modifier, label: String, value: String) {
    Column(modifier.clip(RoundedCornerShape(16.dp))
        .background(Color.White.copy(alpha = .15f)).padding(12.dp)) {
        Text(label, color = Color(0xFFFFE0EA),
            style = MaterialTheme.typography.labelSmall, maxLines = 2)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineLarge, maxLines = 1)
    }
}
@Composable
private fun AuroraInsights(
    stats: HistoricStatistics, history: List<HistoricDraw>, period: HistoricPeriod,
    en: Boolean, onHistory: () -> Unit, onDigit: (String) -> Unit,
) {
    fun tr(my: String, english: String) = if (en) english else my
    val days = history.groupBy { it.date }.toList().sortedByDescending { it.first }.take(8)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { AuroraHero(stats, period, en) }
        item {
            AuroraPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("အထွက်အများဆုံး", "Most frequent"),
                        style = MaterialTheme.typography.titleMedium,
                        color = auroraInk, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    Text(tr("ထွက်ဖူး", "Unique") + " " + stats.distinctCount,
                        style = MaterialTheme.typography.labelMedium, color = auroraMuted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    stats.topNumbers.take(4).forEachIndexed { i, number ->
                        Surface(onClick = { onDigit(number.digit) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp),
                            color = if (i == 0) auroraCherry else auroraSoft) {
                            Column(Modifier.padding(vertical = 11.dp, horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(number.digit, style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (i == 0) Color.White else auroraCherry)
                                Text(number.count.toString() + "×",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (i == 0) Color.White else auroraMuted)
                            }
                        }
                    }
                    repeat((4 - stats.topNumbers.size).coerceAtLeast(0)) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            AuroraPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("နောက်ဆုံးရလဒ်များ", "Recent draws"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = auroraInk,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onHistory) {
                        Text(tr("အားလုံး", "View all"), color = auroraCherry,
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(days.size) { index ->
                        val d = days[index]
                        val am = d.second.firstOrNull { it.session == DrawSession.MORNING }
                        val pm = d.second.firstOrNull { it.session == DrawSession.EVENING }
                        Column(Modifier.width(93.dp).clip(RoundedCornerShape(15.dp))
                            .background(auroraSoft).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(d.first.toString().substring(5), color = auroraMuted,
                                style = MaterialTheme.typography.labelSmall)
                            Text("AM  " + (am?.digit ?: "—"), fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium, color = auroraCherry)
                            Text("PM  " + (pm?.digit ?: "—"), fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium, color = auroraCherry)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("ပျောက်ဂဏန်း", "Missing") + "  " + stats.missing.size,
                    style = MaterialTheme.typography.labelMedium, color = auroraMuted)
                Text(tr("အပူး", "Doubles") + "  " + stats.doubleCount,
                    style = MaterialTheme.typography.labelMedium, color = auroraMuted)
            }
        }
    }
}
@Composable
private fun AuroraNumberRow(numbers: List<HistoricNumberStat>, maximum: Int, onClick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        numbers.forEach { number ->
            val strong = maximum > 0 && number.count == maximum
            Surface(onClick = { onClick(number.digit) },
                modifier = Modifier.weight(1f).height(53.dp),
                shape = RoundedCornerShape(11.dp),
                color = if (strong) auroraCherry else if (number.count > 0)
                    Color(0xFFF3D8E5) else Color(0xFFF2EFF2)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Text(number.digit, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (strong) Color.White else auroraInk)
                    Text(number.count.toString() + "×",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (strong) Color.White else auroraMuted)
                }
            }
        }
        repeat(5 - numbers.size) { Spacer(Modifier.weight(1f)) }
    }
}
@Composable
private fun AuroraExplorer(
    stats: HistoricStatistics, en: Boolean, search: String, onSearch: (String) -> Unit,
    sort: Int, onSort: (Int) -> Unit, onClick: (String) -> Unit,
) {
    fun tr(my: String, english: String) = if (en) english else my
    val max = stats.counts.maxOfOrNull { it.count } ?: 0
    val numbers = when (sort) {
        1 -> stats.counts.sortedWith(compareByDescending<HistoricNumberStat> { it.count }.thenBy { it.digit })
        2 -> stats.counts.sortedWith(compareBy<HistoricNumberStat> { it.count }.thenBy { it.digit })
        else -> stats.counts
    }.filter { it.digit.contains(search.trim()) }
    val rows = numbers.chunked(5)
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { AuroraMiniHeader(tr("ဂဏန်းရှာဖွေ", "Number explorer"),
            tr("00–99 အထွက်အကြိမ်ရေ", "Frequency across 00–99")) }
        item {
            AuroraPanel {
                OutlinedTextField(value = search,
                    onValueChange = { if (it.length <= 2) onSearch(it.filter(Char::isDigit)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(tr("ဂဏန်းရှာရန်", "Search 00–99")) },
                    leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    textStyle = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(tr("ဂဏန်းစဉ်", "00–99"), tr("အများဆုံး", "Most"),
                        tr("အနည်းဆုံး", "Least")).forEachIndexed { i, label ->
                        FilterChip(selected = sort == i, onClick = { onSort(i) },
                            modifier = Modifier.weight(1f),
                            label = { Text(label, style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    }
                }
            }
        }
        item { Text(tr("အရောင်ရင့်သည့်ဂဏန်းများ ပိုမိုထွက်ခဲ့သည်",
            "Highlighted numbers have higher observed counts"),
            style = MaterialTheme.typography.labelSmall, color = auroraMuted) }
        items(rows.size) { i -> AuroraNumberRow(rows[i], max, onClick) }
    }
}
@Composable
private fun AuroraBar(label: String, count: Int, maximum: Int) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, modifier = Modifier.width(29.dp), color = auroraInk,
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(auroraSoft)) {
            if (count > 0 && maximum > 0) Box(
                Modifier.fillMaxWidth(count.toFloat() / maximum).fillMaxHeight()
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(auroraRose, auroraCherry))))
        }
        Text(count.toString(), modifier = Modifier.width(27.dp),
            color = auroraCherry, style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End)
    }
}
@Composable
private fun AuroraPatterns(
    stats: HistoricStatistics, en: Boolean, active: Int, onChange: (Int) -> Unit,
) {
    fun tr(my: String, english: String) = if (en) english else my
    val names = listOf(tr("ထိပ်", "Tens"), tr("ပိတ်", "Ones"),
        tr("အတွဲ", "Pairs"), tr("အပူး", "Doubles"))
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { AuroraMiniHeader(tr("ဂဏန်းပုံစံများ", "Digit patterns"),
            tr("ထိပ်၊ ပိတ်၊ ပြောင်းပြန်နှင့် အပူး", "Tens, ones, reverse pairs and doubles")) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                names.forEachIndexed { i, label ->
                    FilterChip(selected = active == i, onClick = { onChange(i) },
                        modifier = Modifier.weight(1f),
                        label = { Text(label, style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }
            }
        }
        item {
            AuroraPanel {
                when (active) {
                    0, 1 -> {
                        Text(if (active == 0) tr("ထိပ်ဂဏန်း အထွက်", "Tens frequency")
                            else tr("ပိတ်ဂဏန်း အထွက်", "Ones frequency"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = auroraInk)
                        val values = if (active == 0) stats.tens else stats.ones
                        val max = values.maxOrNull() ?: 0
                        values.forEachIndexed { i, count ->
                            AuroraBar(i.toString(), count, max)
                        }
                    }
                    2 -> {
                        Text(tr("ပြောင်းပြန်ဂဏန်းအတွဲ", "Reverse pairs"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = auroraInk)
                        val pairs = stats.reversePairs.filter { it.firstCount + it.reverseCount > 0 }
                        if (pairs.isEmpty()) Text(tr("မရှိသေးပါ", "No pairs"), color = auroraMuted)
                        pairs.take(12).forEach { pair ->
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(pair.first + " / " + pair.reverse,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold, color = auroraInk)
                                Text(pair.firstCount.toString() + " / " + pair.reverseCount,
                                    style = MaterialTheme.typography.bodyMedium, color = auroraCherry)
                            }
                        }
                    }
                    else -> {
                        Text(tr("အပူးဂဏန်း", "Double-number draws") + "  " + stats.doubleCount,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = auroraInk)
                        val doubles = (0..9).map { stats.counts[it * 11] }
                        val max = doubles.maxOfOrNull { it.count } ?: 0
                        doubles.forEach { AuroraBar(it.digit, it.count, max) }
                    }
                }
            }
        }
    }
}
@Composable
private fun AuroraMissing(
    stats: HistoricStatistics, en: Boolean,
    onClick: (String) -> Unit, onAll: () -> Unit,
) {
    fun tr(my: String, english: String) = if (en) english else my
    val missing = stats.missing.sortedWith(
        compareByDescending<HistoricNumberStat> { it.observedDrawsSince ?: -1 }
            .thenBy { it.digit })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { AuroraMiniHeader(tr("ပျောက်ဂဏန်း", "Missing numbers"),
            tr("ကာလအတွင်း မတွေ့ခဲ့သော ဂဏန်းများ",
                "Not observed in the selected period")) }
        item {
            AuroraPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("မထွက်ခဲ့သောဂဏန်း", "Not observed"),
                            color = auroraMuted, style = MaterialTheme.typography.labelMedium)
                        Text(missing.size.toString(), color = auroraCherry,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold)
                    }
                    Text(tr("အတည်ပြုရလဒ်", "Completed draws") + ": " + stats.totalDraws,
                        color = auroraMuted, style = MaterialTheme.typography.labelSmall)
                }
                Text(tr("မထွက်တာကြာခြင်းသည် နောက်အကြိမ်ပေါက်နိုင်ခြေ တိုးခြင်းမဟုတ်ပါ။",
                    "A longer absence does not increase the chance of the next draw."),
                    color = auroraMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (missing.isNotEmpty()) {
            item {
                AuroraPanel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("နောက်ဆုံးတွေ့ခဲ့ချိန်", "Last observed"),
                            style = MaterialTheme.typography.titleMedium,
                            color = auroraInk, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = onAll) {
                            Text(tr("အားလုံး", "View all"), color = auroraCherry)
                        }
                    }
                    missing.take(6).forEach { number ->
                        Surface(onClick = { onClick(number.digit) },
                            color = auroraSoft, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(number.digit, color = auroraCherry,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.width(45.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(number.lastSeen?.date?.toString() ?:
                                        tr("မှတ်တမ်းတွင် မတွေ့ပါ", "Not found in available history"),
                                        color = auroraInk, style = MaterialTheme.typography.labelMedium)
                                    Text(number.observedDrawsSince?.let {
                                        it.toString() + " " + tr("အကြိမ်ကျော်", "observed draws since")
                                    } ?: tr("မသိနိုင်ပါ", "Unknown"),
                                        color = auroraMuted, style = MaterialTheme.typography.labelSmall)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = auroraCherry)
                            }
                        }
                    }
                }
            }
        }
    }
}
