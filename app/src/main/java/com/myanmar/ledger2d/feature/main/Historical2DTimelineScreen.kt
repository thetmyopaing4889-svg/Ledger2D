@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.AppContainer
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private val twoDZone = ZoneId.of("Asia/Yangon")

@Composable
fun Historical2DTimelineScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onLegacy: () -> Unit,
) {
    val en = LocalLanguage.current.code == "en"
    fun tr(mm: String, english: String) = if (en) english else mm
    var now by remember { mutableStateOf(LocalDateTime.now(twoDZone)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = LocalDateTime.now(twoDZone)
        }
    }

    val closedFlow = remember(container) { container.closedDays.observeAll() }
    val historyFlow = remember(container) { container.history.observeAll() }
    val dailyFlow = remember(container) { container.liveResults.observeAll() }
    val winnerFlow = remember(container) { container.winners.observeAll() }
    val lockFlow = remember(container) { container.database.manualWinnerLockDao().observeAll() }
    val closed by closedFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val daily by dailyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val winners by winnerFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val locks by lockFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val closedDates = remember(closed) { closed.map { it.date }.toSet() }
    val defaultTarget = remember(now, closedDates) {
        var candidate = DrawSchedule.nextDraw(now)
        while (!DrawSchedule.isWorkingDate(candidate.date, closedDates)) {
            candidate = DrawIdentity(candidate.date.plusDays(1), DrawSession.MORNING)
        }
        candidate
    }
    var userChosen by rememberSaveable { mutableStateOf(false) }
    var selectedDate by rememberSaveable { mutableStateOf(defaultTarget.date.toString()) }
    var selectedSession by rememberSaveable { mutableStateOf(defaultTarget.session.name) }
    LaunchedEffect(defaultTarget, userChosen) {
        if (!userChosen) {
            selectedDate = defaultTarget.date.toString()
            selectedSession = defaultTarget.session.name
        }
    }
    val date = runCatching { LocalDate.parse(selectedDate) }.getOrDefault(defaultTarget.date)
    val target = DrawIdentity(date, DrawSession.valueOf(selectedSession))

    var lookback by rememberSaveable { mutableStateOf("all") }
    var rangeMenu by remember { mutableStateOf(false) }
    var datePickerMode by remember { mutableStateOf<String?>(null) }
    var customStart by rememberSaveable { mutableStateOf(date.minusMonths(1).toString()) }
    var customEnd by rememberSaveable { mutableStateOf(date.minusDays(1).toString()) }
    val start = runCatching { LocalDate.parse(customStart) }.getOrNull()
    val end = runCatching { LocalDate.parse(customEnd) }.getOrNull()
    val customValid = start != null && end != null && !start.isAfter(end)
    val range = when (lookback) {
        "week" -> TwoDHistoryRange(date.minusWeeks(1), date)
        "month" -> TwoDHistoryRange(date.minusMonths(1), date)
        "custom" -> TwoDHistoryRange(start, end)
        else -> TwoDHistoryRange()
    }
    val lookbackLabel = when (lookback) {
        "week" -> tr("၁ ပတ်", "1 week")
        "month" -> tr("၁ လ", "1 month")
        "custom" -> tr("စိတ်ကြိုက်", "Custom")
        else -> tr("ရှိသမျှမှတ်တမ်း", "All history")
    }

    val merged = remember(daily, history, winners, locks, closedDates,
        now.toLocalDate(), now.hour, now.minute) {
        val verified = Historic2DDataSource.resolve(
            daily, history, now.toLocalDate(), now.toLocalTime(), winners, locks,
        )
        verified.copy(
            draws = verified.draws.filterNot { it.date in closedDates },
            conflicts = verified.conflicts.filterNot { it.date in closedDates },
        )
    }
    val validTarget = DrawSchedule.isWorkingDate(date, closedDates)
    val report = remember(merged, target, closedDates, range, validTarget, lookback, customValid) {
        if (validTarget && (lookback != "custom" || customValid))
            TwoDAnalysisEngine.calculate(merged.draws, target, closedDates, range)
        else null
    }
    var openGroup by rememberSaveable { mutableStateOf("") }
    var openCheck by rememberSaveable { mutableStateOf("") }
    var rulesOpen by remember { mutableStateOf(false) }

    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(scheme.background)
                    .statusBarsPadding().height(58.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = tr("ပြန်ရန်", "Back"))
                }
                Text(tr("2D စစ်ဆေးမှု", "2D Analysis"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onLegacy) {
                    Icon(Icons.Default.History, contentDescription =
                        tr("အရင်စစ်ဆေးမှုများ", "Previous analysis tools"))
                }
                TextButton(onClick = { rulesOpen = true }) {
                    Text(tr("မူ", "Rules"), fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) { inset ->
        Column(
            Modifier.fillMaxSize().padding(inset)
                .background(Brush.verticalGradient(listOf(
                    scheme.primaryContainer.copy(alpha = 0.16f), scheme.background, scheme.background,
                )))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 3.dp, tonalElevation = 1.dp,
                color = scheme.surface,
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("စစ်ဆေးမည့် အကြိမ်", "Target draw"),
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { datePickerMode = "target" },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(date.toString(), maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        FilterChip(
                            selected = target.session == DrawSession.MORNING,
                            onClick = { userChosen = true; selectedSession = DrawSession.MORNING.name },
                            label = { Text(tr("မနက်", "AM")) },
                        )
                        FilterChip(
                            selected = target.session == DrawSession.EVENING,
                            onClick = { userChosen = true; selectedSession = DrawSession.EVENING.name },
                            label = { Text(tr("ညနေ", "PM")) },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("History", "History"),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { rangeMenu = true }) {
                                Text(lookbackLabel, style = MaterialTheme.typography.labelMedium)
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = rangeMenu,
                                onDismissRequest = { rangeMenu = false },
                            ) {
                                listOf(
                                    "all" to tr("ရှိသမျှမှတ်တမ်း", "All history"),
                                    "week" to tr("၁ ပတ်", "1 week"),
                                    "month" to tr("၁ လ", "1 month"),
                                    "custom" to tr("စိတ်ကြိုက်ရက်စွဲ", "Custom dates"),
                                ).forEach { (key, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { lookback = key; rangeMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    if (lookback == "custom") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { datePickerMode = "start" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) { Text(customStart, style = MaterialTheme.typography.labelSmall) }
                            OutlinedButton(
                                onClick = { datePickerMode = "end" },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) { Text(customEnd, style = MaterialTheme.typography.labelSmall) }
                        }
                        if (!customValid) Text(tr("ရက်စွဲအစဉ်ကို ပြန်စစ်ပါ", "Check date range"),
                            color = scheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (!validTarget) {
                Text(tr("ရွေးထားသောနေ့မှာ 2D အကြိမ်ပိတ်ထားသည်။",
                    "No trading draw on the selected date."),
                    style = MaterialTheme.typography.bodyMedium, color = scheme.error)
            } else if (report == null) {
                Text(tr("ရက်စွဲအပိုင်းအခြားကို ပြန်စစ်ပါ", "Check date range"),
                    color = scheme.error)
            } else {
                val a06 = report.a06.getValue(
                    if (target.session == DrawSession.MORNING) "morning" else "evening")
                val longMissing = a06.fullNumbers.firstOrNull()
                val equalMissing = if (longMissing == null) emptyList()
                    else a06.fullNumbers.takeWhile {
                        it.observedDrawsSince == longMissing.observedDrawsSince
                    }
                val group1 = listOfNotNull(
                    if (a06.observedDraws > 0 && longMissing != null) {
                        tr("ပျောက်နေတဲ့ 2D", "Longest missing 2D") to
                            ((if (equalMissing.size > 1)
                                equalMissing.size.toString() + tr(" လုံးတူ", " numbers tied")
                            else longMissing.value) +
                                " · " + longMissing.observedDrawsSince +
                                tr(" ကြိမ် (ရှိသမျှမှတ်တမ်း)", " observed draws"))
                    } else null,
                    if (report.a08.both.matches.isNotEmpty()) {
                        tr("ရှေ့ ၂ ကြိမ်တူ History", "Both-reference matches") to
                            (report.a08.both.matches.size.toString() + tr(" ကြိမ်", " matches"))
                    } else if (report.a03.matched.isNotEmpty()) {
                        tr("စုံ/မ အစဉ်တူ", "Same parity sequence") to
                            (report.a03.matched.size.toString() + tr(" ပတ်", " weeks"))
                    } else if (report.a02.samples > 0) {
                        tr("နေ့/အကြိမ်တူ မှတ်တမ်း", "Same day/draw history") to
                            (report.a02.samples.toString() + tr(" ကြိမ်", " draws"))
                    } else null,
                )
                val flow = report.a05
                val activeGroups = report.a07.groups.filterValues { it > 0 }
                val repeatsThisWeek = report.a07.repeatedNumbers.sumOf { it.count }
                val secondWeekHighlight = if (repeatsThisWeek > 0) {
                    tr("ပြန်ကျ", "Exact 2D repeats") to
                        (repeatsThisWeek.toString() + tr(" ကြိမ်", " events"))
                } else if (activeGroups.isNotEmpty()) {
                    tr("ထွက်ပြီးသောအုပ်စုများ", "Observed special groups") to
                        activeGroups.entries.joinToString(" · ") {
                            it.key + " " + it.value
                        }
                } else {
                    tr("အထူးဂဏန်းအုပ်စု", "Special groups") to
                        tr("မတွေ့သေး", "No observed groups yet")
                }
                val group2 = if (flow.transitions.isEmpty()) {
                    listOf(tr("ယခုအပတ်", "This week") to
                        tr("Result မရှိသေး", "No confirmed results yet"))
                } else listOf(
                    (if (flow.completeThroughTarget)
                        tr("မပေါ်သေးတဲ့ဂဏန်း", "Unseen single digits")
                    else tr("ရရှိသောမှတ်တမ်း၌ မပေါ်", "Unseen in available records")) to
                        (flow.remainingDigits.joinToString(" ").ifEmpty { tr("မရှိ", "None") }),
                    secondWeekHighlight,
                )
                val returned = report.a01.results.count { it.firstSeenVerified && it.thisWeekFirstSeen != null }
                val group3 = listOf(
                    (if (report.a04.complete) tr("ကျန်ဂဏန်း", "Absent single digits")
                        else tr("တွေ့ရှိထားသမျှအရ မပေါ်", "Not seen in available records")) to
                        report.a04.remainingDigits.joinToString(" ").ifEmpty { tr("မရှိ", "None") },
                    tr("ဒီအပတ် ပြန်ပေါ်", "Returned this week") to
                        (returned.toString() + tr(" လုံး", " digits")),
                )
                TimelineAnalysisGroup(
                    title = tr("၁။ လက်ရှိအကြိမ်", "1. Current draw"),
                    previews = group1.ifEmpty { listOf(tr("မှတ်တမ်း", "History") to
                        tr("အချက်အလက် မလုံလောက်သေး", "Not enough records yet")) },
                    methods = listOf(
                        "02" to tr("နေ့/အကြိမ်တူ အထွက်", "Same day/draw frequency"),
                        "03" to tr("စုံ/မ အစဉ်တူ", "Parity sequence matches"),
                        "06" to tr("ပျောက်နေတဲ့ ဂဏန်းများ", "Ongoing missing digits"),
                        "08" to tr("ရှေ့ဂဏန်းနဲ့ တိုက်စစ်", "Previous results matching"),
                    ),
                    expanded = openGroup == "1", selectedMethod = openCheck,
                    onToggle = { openGroup = if (openGroup == "1") "" else "1"; openCheck = "" },
                    onMethod = { openCheck = if (openCheck == it) "" else it },
                    report = report, en = en,
                )
                TimelineAnalysisGroup(
                    title = tr("၂။ ယခုအပတ်အခြေအနေ", "2. This week"),
                    previews = group2,
                    methods = listOf(
                        "05" to tr("အသစ်/အဟောင်း စီးဆင်းမှု", "New / old digit flow"),
                        "07" to tr("အထူးဂဏန်းအုပ်စုများ", "Special number groups"),
                    ),
                    expanded = openGroup == "2", selectedMethod = openCheck,
                    onToggle = { openGroup = if (openGroup == "2") "" else "2"; openCheck = "" },
                    onMethod = { openCheck = if (openCheck == it) "" else it },
                    report = report, en = en,
                )
                TimelineAnalysisGroup(
                    title = tr("၃။ ပြီးခဲ့တဲ့အပတ်", "3. Last week"),
                    previews = group3,
                    methods = listOf(
                        "04" to tr("ပြီးခဲ့တဲ့အပတ် အထွက်စာရင်း", "Previous week's results"),
                        "01" to tr("ကျန်ဂဏန်း ပြန်ပေါ်မှု", "Missing-digit returns"),
                    ),
                    expanded = openGroup == "3", selectedMethod = openCheck,
                    onToggle = { openGroup = if (openGroup == "3") "" else "3"; openCheck = "" },
                    onMethod = { openCheck = if (openCheck == it) "" else it },
                    report = report, en = en,
                )
                if (merged.draws.isEmpty()) {
                    Text(tr("Result မတွေ့။ အတည်ပြုမှတ်တမ်း မရှိသေးပါ။",
                        "No verified results found."),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant)
                    TextButton(onClick = onSettings) { Text(tr("ဆက်တင်", "Settings")) }
                }
                Text(tr("တွေ့ရှိထားသော History မှတ်တမ်းကိုသာ စစ်ဆေးထားသည်။ နောက်အကြိမ်အတွက် အာမခံချက် မဟုတ်ပါ။",
                    "Based on verified history only, not a prediction or guarantee."),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (rulesOpen) {
        AlertDialog(
            onDismissRequest = { rulesOpen = false },
            title = { Text(tr("မူဝါဒနှင့် ဖော်မြူလာများ", "Rules and formulas")) },
            text = { Text(tr(
                "မူဝါဒစာကြည့်တိုက်အတွက် နေရာကို ပြင်ဆင်ထားသည်။ မူဝါဒအစစ်များကို နောက်မှ ထည့်သွင်းမည်။ ခန့်မှန်းဂဏန်းအတု မပြပါ။",
                "The rules library is reserved for verified user-supplied content. No sample rules or invented predictions are shown.",
            )) },
            confirmButton = { TextButton(onClick = { rulesOpen = false }) {
                Text(tr("ပိတ်မည်", "Close"))
            } },
        )
    }

    datePickerMode?.let { mode ->
        val current = when (mode) {
            "start" -> customStart
            "end" -> customEnd
            else -> selectedDate
        }
        val initialMillis = remember(mode, current) {
            runCatching { LocalDate.parse(current).atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli() }.getOrNull()
        }
        val picker = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { datePickerMode = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        val value = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                        when (mode) {
                            "start" -> customStart = value
                            "end" -> customEnd = value
                            else -> { userChosen = true; selectedDate = value }
                        }
                    }
                    datePickerMode = null
                }) { Text(tr("အတည်ပြု", "OK")) }
            },
            dismissButton = { TextButton(onClick = { datePickerMode = null }) {
                Text(tr("မလုပ်တော့ပါ", "Cancel"))
            } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun TimelineAnalysisGroup(
    title: String,
    previews: List<Pair<String, String>>,
    methods: List<Pair<String, String>>,
    expanded: Boolean,
    selectedMethod: String,
    onToggle: () -> Unit,
    onMethod: (String) -> Unit,
    report: TwoDAnalysisReport,
    en: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surface,
        shadowElevation = if (expanded) 7.dp else 3.dp,
        tonalElevation = if (expanded) 3.dp else 1.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle)
                    .defaultMinSize(minHeight = 54.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (en) "Expand/collapse" else "ဖွင့်/ပိတ်",
                    tint = scheme.primary)
            }
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                previews.take(2).forEach { (label, answer) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top) {
                        Text(label, color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f))
                        Text(answer, style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
            ) {
                Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HorizontalDivider(color = scheme.outline.copy(alpha = 0.3f))
                    methods.forEach { (key, label) ->
                        Surface(
                            color = scheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(13.dp),
                            tonalElevation = if (selectedMethod == key) 2.dp else 0.dp,
                        ) {
                            Column {
                                Row(
                                    Modifier.fillMaxWidth().clickable { onMethod(key) }
                                        .defaultMinSize(minHeight = 48.dp)
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f))
                                    Icon(if (selectedMethod == key) Icons.Default.KeyboardArrowUp
                                        else Icons.Default.KeyboardArrowDown,
                                        null, tint = scheme.primary)
                                }
                                AnimatedVisibility(selectedMethod == key) {
                                    Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                        HorizontalDivider(color = scheme.outline.copy(alpha = 0.2f))
                                        TwoDAnalysisDetails(key, report, en)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
