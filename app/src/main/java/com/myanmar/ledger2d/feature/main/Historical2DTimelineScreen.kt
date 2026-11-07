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
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
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
    var openGroup by rememberSaveable { mutableStateOf("1") }
    var openCheck by rememberSaveable { mutableStateOf("") }
    var streakStream by remember(target.session) { mutableStateOf(
        if (target.session == DrawSession.MORNING) "morning" else "evening"
    ) }
    val overviewScroll = rememberScrollState()
    BackHandler(enabled = openCheck.isNotEmpty()) { openCheck = "" }
    var rulesOpen by remember { mutableStateOf(false) }


    // The overview is for CHOOSING a check. Detailed outcomes are shown only
    // on the next screen, so result data never competes with navigation.
    val sections = listOf(
        TwoDSectionSpec(
            key = "1", title = tr("၁။ ယခုထွက်မယ့်အကြိမ်", "1. Current draw"),
            caption = tr("လက်ရှိအကြိမ်အတွက် စစ်ဆေးချက်များ", "Checks for the selected draw"),
            icon = Icons.Default.Troubleshoot,
            methods = listOf(
                TwoDMethodSpec("02", tr("လက်ရှိအကြိမ်အတွက် ယခင်ထွက်ရှိမှုမှတ်တမ်း", "Same weekday and draw history"),
                    tr("နေ့တူ၊ အကြိမ်တူ ယခင်မှတ်တမ်း", "Matching weekday and session"), Icons.Default.History),
                TwoDMethodSpec("03", tr("ယခုအပတ်အတွက် ယခင် စုံ/မ ထွက်ရှိမှု", "Historical parity sequence"),
                    tr("စုံ/မ ပုံစံတူ အပတ်များ", "Matching parity patterns"), Icons.Default.AutoGraph),
                TwoDMethodSpec("06", tr("မထွက်တာကြာနေသော ဂဏန်းနှင့် ဂဏန်းတွဲများ", "Long-absent digits and pairs"),
                    tr("0–9 နှင့် 00–99 ပျောက်နေသောအကြိမ်ရေ", "Single digits and 2D pairs"), Icons.Default.GridView),
                TwoDMethodSpec("08", tr("ယခင်ထွက်ဂဏန်းပေါ် အခြေခံ၍ လက်ရှိအကြိမ်အတွက် စစ်ဆေးခြင်း", "Match previous results"),
                    tr("ရှေ့အကြိမ်ထွက်ဂဏန်းများနဲ့ တိုက်စစ်", "Compare preceding results"), Icons.Default.Search),
            ),
        ),
        TwoDSectionSpec(
            key = "2", title = tr("၂။ ယခုအပတ်အခြေအနေ", "2. This week"),
            caption = tr("ဒီအပတ်အတွင်း စစ်ဆေးချက်များ", "This week's activity"),
            icon = Icons.Default.DateRange,
            methods = listOf(
                TwoDMethodSpec("05", tr("အသစ်/အဟောင်း ဂဏန်းစီးဆင်းမှု", "New / old digit flow"),
                    tr("ဒီအပတ် ထွက်ပြီး/မထွက်သေးသောဂဏန်း", "Observed and unseen digits"), Icons.Default.SwapHoriz),
                TwoDMethodSpec("07", tr("အထူးဂဏန်းအုပ်စုများ", "Special number groups"),
                    tr("အပူး၊ ညီကို၊ ပါဝါ၊ နက္ခတ်", "Doubles, siblings, power, astrology"), Icons.Default.StarOutline),
            ),
        ),
        TwoDSectionSpec(
            key = "3", title = tr("၃။ ပြီးခဲ့တဲ့အပတ်", "3. Previous week"),
            caption = tr("ပြီးခဲ့တဲ့အပတ်ကို ပြန်လည်စစ်ဆေးရန်", "Review last week's records"),
            icon = Icons.Default.EventRepeat,
            methods = listOf(
                TwoDMethodSpec("04", tr("ပြီးခဲ့တဲ့အပတ် ထွက်ဂဏန်းစာရင်းချုပ်", "Previous week's results summary"),
                    tr("ထွက်ဂဏန်းအကြိမ်ရေနှင့် ပျောက်ဂဏန်း", "Frequencies and missing digits"), Icons.Default.BarChart),
                TwoDMethodSpec("01", tr("ပြီးခဲ့တဲ့အပတ် ပျောက်ဂဏန်းထွက်ရှိမှု", "Missing-digit return history"),
                    tr("ပျောက်ဂဏန်းတွေ ဒီအပတ် ပြန်ဆင်းမှု", "Observed missing-digit returns"), Icons.Default.Restore),
            ),
        ),
    )
    val currentMethod = sections.flatMap { it.methods }.firstOrNull { it.key == openCheck }
    val scheme = MaterialTheme.colorScheme
    Scaffold(
        containerColor = scheme.background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(scheme.background)
                    .statusBarsPadding().heightIn(min = 60.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (openCheck.isNotEmpty()) openCheck = "" else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = tr("ပြန်ရန်", "Back"))
                }
                Column(Modifier.weight(1f)) {
                    Text(tr("2D စစ်ဆေးမှု", "2D Analysis"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    if (openCheck.isNotEmpty()) {
                        Text(tr("စစ်ဆေးချက် အသေးစိတ်", "Check details"),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant)
                    }
                }
                if (openCheck.isEmpty()) {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Tune, contentDescription = tr("ဆက်တင်", "Settings"))
                    }
                }
            }
        },
        bottomBar = {
            if (openCheck.isEmpty()) {
                NavigationBar(
                    containerColor = scheme.surface,
                    tonalElevation = 5.dp,
                ) {
                    NavigationBarItem(
                        selected = true, onClick = { openCheck = "" },
                        icon = { Icon(Icons.Default.Analytics, null) },
                        label = { Text(tr("စစ်ဆေးမှု", "Analysis"), maxLines = 1) },
                    )
                    NavigationBarItem(
                        selected = false, onClick = onLegacy,
                        icon = { Icon(Icons.Default.MenuBook, null) },
                        label = { Text(tr("လေ့လာရန်", "Research"), maxLines = 1) },
                    )
                    NavigationBarItem(
                        selected = false, onClick = { rulesOpen = true },
                        icon = { Icon(Icons.Default.Rule, null) },
                        label = { Text(tr("မူ", "Rules"), maxLines = 1) },
                    )
                }
            }
        },
    ) { inset ->
        if (openCheck.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(inset)
                    .background(Brush.verticalGradient(listOf(
                        scheme.primaryContainer.copy(alpha = 0.28f),
                        scheme.background, scheme.background,
                    )))
                    .verticalScroll(overviewScroll)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(tr("စစ်ဆေးမည့် အကြိမ်", "Choose a draw"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = scheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 7.dp,
                ) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { datePickerMode = "target" },
                                modifier = Modifier.weight(1.1f).heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text(date.toString(), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            Surface(
                                shape = RoundedCornerShape(15.dp),
                                color = scheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1.2f),
                            ) {
                                Row(Modifier.padding(3.dp)) {
                                    listOf(
                                        DrawSession.MORNING to tr("မနက်", "AM"),
                                        DrawSession.EVENING to tr("ညနေ", "PM"),
                                    ).forEach { (session, label) ->
                                        val active = target.session == session
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (active) scheme.primaryContainer
                                                else scheme.surfaceVariant.copy(alpha = 0.2f),
                                            shadowElevation = if (active) 2.dp else 0.dp,
                                            onClick = {
                                                userChosen = true
                                                selectedSession = session.name
                                            },
                                        ) {
                                            Box(Modifier.defaultMinSize(minHeight = 44.dp)
                                                .padding(horizontal = 3.dp),
                                                contentAlignment = Alignment.Center) {
                                                Text(label,
                                                    color = if (active) scheme.onPrimaryContainer
                                                        else scheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.History, contentDescription = null,
                                tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            Text(tr("မှတ်တမ်းကာလ", "History period"),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            Box {
                                TextButton(onClick = { rangeMenu = true }) {
                                    Text(lookbackLabel, maxLines = 1,
                                        style = MaterialTheme.typography.labelLarge)
                                    Icon(Icons.Default.KeyboardArrowDown, null)
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
                                            leadingIcon = if (lookback == key) {
                                                { Icon(Icons.Default.Check, null) }
                                            } else null,
                                        )
                                    }
                                }
                            }
                        }
                        if (lookback == "custom") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { datePickerMode = "start" },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                ) { Text(customStart, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1) }
                                OutlinedButton(
                                    onClick = { datePickerMode = "end" },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp),
                                ) { Text(customEnd, style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1) }
                            }
                            if (!customValid) {
                                Text(tr("ရက်စွဲအစဉ်ကို ပြန်စစ်ပါ", "Check date range"),
                                    color = scheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (!validTarget) {
                    TwoDStatusMessage(tr("ရွေးထားသောနေ့မှာ 2D အကြိမ်ပိတ်ထားသည်။",
                        "No trading draw on the selected date."), true)
                } else if (report == null) {
                    TwoDStatusMessage(tr("ရက်စွဲအပိုင်းအခြားကို ပြန်စစ်ပါ",
                        "Check the selected date range"), true)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("စစ်ဆေးနည်းများ", "Analysis checks"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Surface(shape = RoundedCornerShape(12.dp),
                        color = scheme.secondaryContainer) {
                        Text(tr("စုစုပေါင်း ၈ ခု", "8 checks"),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSecondaryContainer)
                    }
                }
                sections.forEach { section ->
                    TwoDSectionCard(
                        section = section,
                        expanded = openGroup == section.key,
                        enabled = report != null,
                        onToggle = { openGroup = if (openGroup == section.key) "" else section.key },
                        onMethod = { openCheck = it },
                        en = en,
                    )
                }
                if (merged.draws.isEmpty()) {
                    TwoDStatusMessage(tr("ထွက်ဂဏန်းမှတ်တမ်း မတွေ့သေးပါ။",
                        "No verified results found yet."), false)
                    TextButton(onClick = onSettings) { Text(tr("ဆက်တင်", "Settings")) }
                }
                Text(tr("ရှိတဲ့ထွက်ဂဏန်းမှတ်တမ်းတွေအပေါ်မှာပဲ စစ်ဆေးထားပါတယ်။ နောက်အကြိမ်ထွက်ဂဏန်းကို အာမခံထားတာ မဟုတ်ပါ။",
                    "Based only on recorded results, not a prediction or a guarantee."),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
        } else {
            val method = currentMethod
            Column(
                Modifier.fillMaxSize().padding(inset)
                    .background(Brush.verticalGradient(listOf(
                        scheme.primaryContainer.copy(alpha = 0.24f),
                        scheme.background,
                    )))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (method != null && report != null) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = scheme.surface,
                        shadowElevation = 8.dp,
                        tonalElevation = 3.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.fillMaxWidth().background(
                            Brush.horizontalGradient(listOf(
                                scheme.primaryContainer.copy(alpha = 0.8f),
                                scheme.surface,
                            )))
                            .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(tr("စစ်ဆေးနည်း", "Analysis check") + " " + method.key,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary)
                            Text(method.title, style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold)
                            Text(method.subtitle, style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant)
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.4f))
                            Text(date.toString() + "  ·  " +
                                (if (target.session == DrawSession.MORNING) tr("မနက်အကြိမ်", "Morning")
                                 else tr("ညနေအကြိမ်", "Evening")) +
                                "  ·  " + lookbackLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant)
                        }
                    }
                    TwoDPrimaryFinding(openCheck, report, en, streakStream)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = scheme.surface,
                        tonalElevation = 1.dp,
                        shadowElevation = 3.dp,
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(tr("အသေးစိတ်မှတ်တမ်း", "Detailed records"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold)
                            HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                            TwoDAnalysisDetails(openCheck, report, en,
                                selectedStreakStream = streakStream,
                                onStreakStreamChange = { streakStream = it })
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                } else {
                    TwoDStatusMessage(tr("ဒီစစ်ဆေးချက်အတွက် အချက်အလက်မရှိပါ။",
                        "No details are available for this check."), false)
                }
            }
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


private data class TwoDMethodSpec(
    val key: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private data class TwoDSectionSpec(
    val key: String,
    val title: String,
    val caption: String,
    val icon: ImageVector,
    val methods: List<TwoDMethodSpec>,
)

@Composable
private fun TwoDSectionCard(
    section: TwoDSectionSpec,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onMethod: (String) -> Unit,
    en: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        color = scheme.surface,
        tonalElevation = if (expanded) 3.dp else 1.dp,
        shadowElevation = if (expanded) 9.dp else 5.dp,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle)
                    .background(Brush.horizontalGradient(listOf(
                        scheme.primaryContainer.copy(alpha = if (expanded) 0.62f else 0.34f),
                        scheme.surface,
                    )))
                    .defaultMinSize(minHeight = 74.dp)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = scheme.surface,
                    shadowElevation = 2.dp,
                    tonalElevation = 2.dp,
                ) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Icon(section.icon, null, tint = scheme.primary,
                            modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                    Text(section.caption, style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
                Surface(shape = RoundedCornerShape(9.dp),
                    color = scheme.secondaryContainer.copy(alpha = 0.85f)) {
                    Text(section.methods.size.toString(),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, color = scheme.onSecondaryContainer)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (en) "Expand or collapse" else "ဖွင့်/ပိတ်",
                    tint = scheme.primary,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(230)) + fadeIn(tween(190)),
                exit = shrinkVertically(tween(190)) + fadeOut(tween(130)),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                    section.methods.forEachIndexed { index, method ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable(enabled = enabled) { onMethod(method.key) }
                                .defaultMinSize(minHeight = 64.dp)
                                .padding(horizontal = 7.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = scheme.surfaceVariant.copy(alpha = 0.7f),
                                tonalElevation = 1.dp,
                            ) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    Icon(method.icon, null, tint = scheme.primary,
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(method.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant)
                                Text(method.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.ChevronRight, null,
                                tint = scheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp))
                        }
                        if (index < section.methods.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 59.dp, end = 8.dp),
                                color = scheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TwoDStatusMessage(message: String, warning: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (warning) scheme.errorContainer else scheme.surfaceVariant,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Default.Info, null,
                tint = if (warning) scheme.onErrorContainer else scheme.onSurfaceVariant)
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = if (warning) scheme.onErrorContainer else scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TwoDPrimaryFinding(
    id: String, report: TwoDAnalysisReport, en: Boolean, streakStream: String,
) {
    fun tr(mm: String, english: String) = if (en) english else mm
    val scheme = MaterialTheme.colorScheme
    val noData = tr("မှတ်တမ်းမရှိသေး", "No records yet")
    val none = tr("မရှိ", "None")
    // Display only observed history, never a fabricated forecast.
    val finding = when (id) {
        "02" -> Triple(tr("ယခင်အထွက်အများဆုံး", "Most observed pair"),
            report.a02.numbers.firstOrNull()?.let {
                it.digit + "  ·  " + it.count + tr(" ကြိမ်", " times")
            } ?: noData, tr("နေ့တူ၊ အကြိမ်တူမှတ်တမ်း", "Same weekday and draw"))
        "03" -> Triple(tr("တူညီသောအပတ်", "Matching weeks"),
            if (!report.a03.eligible) noData
            else report.a03.matched.size.toString() + tr(" ပတ်", " weeks"),
            tr("မှတ်တမ်းပေါ် အခြေခံထားသည်", "Historical matches only"))
        "06" -> {
            val stream = report.a06.getValue(streakStream)
            val longest = stream.fullNumbers.firstOrNull()
            Triple(tr("မထွက်တာကြာနေသော ဂဏန်းတွဲ", "Longest absent pair"),
                if (stream.observedDraws == 0 || longest == null) noData
                else longest.value + "  ·  " + longest.observedDrawsSince + tr(" ကြိမ်", " draws"),
                (when (streakStream) {
                    "morning" -> tr("မနက်အကြိမ်", "Morning draws")
                    "evening" -> tr("ညနေအကြိမ်", "Evening draws")
                    else -> tr("မနက်/ညနေ ပေါင်း", "Morning and evening")
                }) + tr(" · ရှိသောမှတ်တမ်းအရ", " · available history"))
        }
        "08" -> Triple(tr("ရှေ့အကြိမ်နှစ်ခုလုံး တူခဲ့မှု", "Both preceding results matched"),
            if (report.a08.firstReference == null || report.a08.secondReference == null)
                noData else report.a08.both.matches.size.toString() + tr(" ကြိမ်", " matches"),
            tr("ယခင်ထွက်ဂဏန်းမှတ်တမ်းအရ", "Recorded historical matches"))
        "05" -> Triple(tr("ဒီအပတ် ပျောက်နေသော ဂဏန်း", "This week's unseen digits"),
            if (report.a05.transitions.isEmpty()) noData
            else report.a05.remainingDigits.joinToString(" ").ifEmpty { none },
            tr("ရှိထားသော ထွက်ဂဏန်းပေါ်တွက်ထားသည်", "Based on available results"))
        "07" -> Triple(tr("ဒီအပတ် ပြန်ကျအကြိမ်", "Exact repeats this week"),
            if (report.a07.observedDraws == 0) noData
            else report.a07.repeatedNumbers.sumOf { it.count }.toString() +
                tr(" ကြိမ်", " times"),
            tr("ရှိထားသော ထွက်ဂဏန်းမှတ်တမ်းအရ", "Based on recorded draws"))
        "04" -> Triple(tr("ပြီးခဲ့တဲ့အပတ် ပျောက်ဂဏန်း", "Missing digits last week"),
            if (report.a04.observedDraws == 0) noData
            else report.a04.remainingDigits.joinToString(" ").ifEmpty { none },
            tr("ရှိထားသော ထွက်ဂဏန်းပေါ်တွက်ထားသည်", "Based on available results"))
        "01" -> Triple(tr("ဒီအပတ် ပျောက်ဂဏန်းပြန်ဆင်း", "Missing digits observed returning"),
            if (report.a04.observedDraws == 0) noData
            else report.a01.results.count { it.thisWeekFirstSeen != null }.toString() +
                tr(" လုံး", " digits"),
            tr("ရှိထားသောမှတ်တမ်းတွေပေါ် တွက်ထားသည်", "Based on available records"))
        else -> Triple(tr("စစ်ဆေးချက်", "Analysis"), noData, "")
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = scheme.primaryContainer,
        shadowElevation = 5.dp,
        tonalElevation = 3.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.horizontalGradient(listOf(
                    scheme.primaryContainer, scheme.secondaryContainer.copy(alpha = 0.78f),
                ))
            ).padding(horizontal = 18.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(finding.first, style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimaryContainer)
            Text(finding.second, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = scheme.onPrimaryContainer)
            Text(finding.third, style = MaterialTheme.typography.labelSmall,
                color = scheme.onPrimaryContainer.copy(alpha = 0.82f))
        }
    }
}
