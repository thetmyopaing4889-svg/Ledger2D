@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.AppContainer
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard
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
    // Null means the first Room emission has not arrived. An empty list is
    // legitimate observed state, and must never be confused with "still loading".
    val closed by closedFlow.collectAsStateWithLifecycle(initialValue = null)
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = null)
    val daily by dailyFlow.collectAsStateWithLifecycle(initialValue = null)
    val winners by winnerFlow.collectAsStateWithLifecycle(initialValue = null)
    val locks by lockFlow.collectAsStateWithLifecycle(initialValue = null)
    val inputsReady = closed != null && history != null && daily != null &&
        winners != null && locks != null
    val closedDates = remember(closed) { closed.orEmpty().map { it.date }.toSet() }
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
    // Date/session are derived synchronously until the user explicitly chooses.
    // Avoid a later effect changing the default after a transient first frame.
    val date = if (userChosen)
        runCatching { LocalDate.parse(selectedDate) }.getOrDefault(defaultTarget.date)
    else defaultTarget.date
    val session = if (userChosen)
        runCatching { DrawSession.valueOf(selectedSession) }.getOrDefault(defaultTarget.session)
    else defaultTarget.session
    val target = DrawIdentity(date, session)

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
            daily.orEmpty(), history.orEmpty(), now.toLocalDate(), now.toLocalTime(),
            winners.orEmpty(), locks.orEmpty(),
        )
        verified.copy(
            draws = verified.draws.filterNot { it.date in closedDates },
            conflicts = verified.conflicts.filterNot { it.date in closedDates },
        )
    }
    val validTarget = DrawSchedule.isWorkingDate(date, closedDates)
    val report = remember(merged, target, closedDates, range, validTarget,
        lookback, customValid, inputsReady) {
        if (inputsReady && validTarget)
            TwoDAnalysisEngine.calculate(merged.draws, target, closedDates, range)
        else null
    }
    var openGroup by rememberSaveable { mutableStateOf("1") }
    var openCheck by rememberSaveable { mutableStateOf("") }
    var activeTwoDTab by rememberSaveable { mutableStateOf("analysis") }
    // Research state belongs to the parent, surviving tab switches within this session.
    val researchSession = remember { ResearchLabSessionState(defaultTarget.date) }
    var streakStream by remember(target.session) { mutableStateOf(
        if (target.session == DrawSession.MORNING) "morning" else "evening"
    ) }
    BackHandler(enabled = openCheck.isNotEmpty() || activeTwoDTab == "research" ||
        activeTwoDTab == "rules") {
        when {
            openCheck.isNotEmpty() -> openCheck = ""
            activeTwoDTab == "research" && researchSession.auditVisible ->
                researchSession.auditVisible = false
            activeTwoDTab == "research" && researchSession.guideVisible ->
                researchSession.guideVisible = false
            researchSession.resultVisible -> researchSession.resultVisible = false
            else -> activeTwoDTab = "analysis"
        }
    }


    // Group overview previews come from actual confirmed analysis data.
    // Full evidence remains a separate, optional view.
    val sections = listOf(
        TwoDSectionSpec(
            key = "1", title = tr("လက်ရှိအကြိမ်", "Current draw"),
            caption = tr("လက်ရှိအကြိမ်အတွက် စစ်ဆေးချက်များ", "Checks for the selected draw"),
            icon = Icons.Default.Troubleshoot,
            methods = listOf(
                TwoDMethodSpec("02", TwoDAnalysisText.title("02", en),
                    TwoDAnalysisText.subtitle("02", en), Icons.Default.History),
                TwoDMethodSpec("03", TwoDAnalysisText.title("03", en),
                    TwoDAnalysisText.subtitle("03", en), Icons.Default.AutoGraph),
                TwoDMethodSpec("06", TwoDAnalysisText.title("06", en),
                    TwoDAnalysisText.subtitle("06", en), Icons.Default.GridView),
                TwoDMethodSpec("08", TwoDAnalysisText.title("08", en),
                    TwoDAnalysisText.subtitle("08", en), Icons.Default.Search),
            ),
        ),
        TwoDSectionSpec(
            key = "2", title = tr("ယခုအပတ်", "This week"),
            caption = tr("ဒီအပတ်အတွင်း စစ်ဆေးချက်များ", "This week's activity"),
            icon = Icons.Default.DateRange,
            methods = listOf(
                TwoDMethodSpec("05", TwoDAnalysisText.title("05", en),
                    TwoDAnalysisText.subtitle("05", en), Icons.Default.SwapHoriz),
                TwoDMethodSpec("07", TwoDAnalysisText.title("07", en),
                    TwoDAnalysisText.subtitle("07", en), Icons.Default.StarOutline),
            ),
        ),
        TwoDSectionSpec(
            key = "3", title = tr("ပြီးခဲ့တဲ့အပတ်", "Previous week"),
            caption = tr("ပြီးခဲ့တဲ့အပတ်ကို ပြန်လည်စစ်ဆေးရန်", "Review last week's records"),
            icon = Icons.Default.EventRepeat,
            methods = listOf(
                TwoDMethodSpec("04", TwoDAnalysisText.title("04", en),
                    TwoDAnalysisText.subtitle("04", en), Icons.Default.BarChart),
                TwoDMethodSpec("01", TwoDAnalysisText.title("01", en),
                    TwoDAnalysisText.subtitle("01", en), Icons.Default.Restore),
            ),
        ),
    )
    val currentMethod = sections.flatMap { it.methods }.firstOrNull { it.key == openCheck }
    val scheme = MaterialTheme.colorScheme
    val researchChrome = activeTwoDTab == "research" && openCheck.isEmpty()
    val researchInk = Color(0xFF112D3A)
    Scaffold(
        containerColor = if (researchChrome) researchInk else scheme.background,

        topBar = {
            Row(
                Modifier.fillMaxWidth()
                    .background(if (researchChrome) researchInk else scheme.background)
                    .statusBarsPadding().heightIn(min = 58.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = {
                    when {
                        openCheck.isNotEmpty() -> openCheck = ""
                        researchChrome && researchSession.auditVisible ->
                            researchSession.auditVisible = false
                        researchChrome && researchSession.guideVisible ->
                            researchSession.guideVisible = false
                        researchChrome && researchSession.resultVisible ->
                            researchSession.resultVisible = false
                        researchChrome -> activeTwoDTab = "analysis"
                        activeTwoDTab == "rules" -> activeTwoDTab = "analysis"
                        else -> onBack()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = tr("ပြန်ရန်", "Back"),
                        tint = if (researchChrome) Color.White else scheme.onSurface)
                }
                Text(when {
                    activeTwoDTab == "research" && openCheck.isEmpty() ->
                        tr("2D လေ့လာရန်", "2D Research")
                    activeTwoDTab == "summary" && openCheck.isEmpty() ->
                        tr("2D အနှစ်ချုပ်", "2D Summary")
                    activeTwoDTab == "rules" && openCheck.isEmpty() ->
                        tr("2D မူဝါဒ", "2D Rules")
                    else -> tr("2D စစ်ဆေးမှု", "2D Analysis")
                },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    color = if (researchChrome) Color.White else scheme.onSurface,
                    overflow = TextOverflow.Ellipsis)
                // The Summary title bar intentionally has no Settings action.
                // Keep the signature/navigation contracts unchanged.
            }
        },
        bottomBar = {
            if (openCheck.isEmpty()) {
                Surface(
                    color = if (researchChrome) researchInk else scheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 7.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        TwoDNavAction(
                            modifier = Modifier.weight(1f), active = activeTwoDTab == "analysis",
                            researchChrome = researchChrome,
                            label = tr("စစ်ဆေးမှု", "Analysis"),
                            icon = Icons.Default.Analytics,
                            onClick = { activeTwoDTab = "analysis"; openCheck = "" },
                        )
                        TwoDNavAction(
                            modifier = Modifier.weight(1f), active = activeTwoDTab == "summary",
                            researchChrome = researchChrome,
                            label = tr("အနှစ်ချုပ်", "Summary"),
                            icon = Icons.Default.Summarize,
                            onClick = { activeTwoDTab = "summary"; openCheck = "" },
                        )
                        TwoDNavAction(
                            modifier = Modifier.weight(1f),
                            active = activeTwoDTab == "research",
                            researchChrome = researchChrome,
                            label = tr("လေ့လာရန်", "Research"),
                            icon = Icons.Default.MenuBook,
                            onClick = { activeTwoDTab = "research"; openCheck = "" },
                        )
                        TwoDNavAction(
                            modifier = Modifier.weight(1f), active = activeTwoDTab == "rules",
                            researchChrome = researchChrome,
                            label = tr("မူ", "Rules"),
                            icon = Icons.Default.Rule,
                            onClick = { activeTwoDTab = "rules"; openCheck = "" },
                        )
                    }
                }
            }
        },
    ) { inset ->
        if (openCheck.isEmpty() && activeTwoDTab == "summary") {
            // Summary uses the exact verified historical report computed above.
            // No demo fixture is ever used as a fallback for absent data.
            TwoDSummaryScreen(
                date = date,
                session = session,
                en = en,
                report = if (lookback == "custom" && !customValid) null else report,
                closedDates = closedDates,
                isLoading = !inputsReady,
                validTarget = validTarget,
                validHistoryRange = lookback != "custom" || customValid,
                modifier = Modifier.fillMaxSize().padding(inset),
                onPickDate = { datePickerMode = "target" },
                onChangeSession = { chosen ->
                    selectedDate = date.toString()
                    selectedSession = chosen.name
                    userChosen = true
                },
                onOpenDetail = { openCheck = it },
            )
        } else if (openCheck.isEmpty() && activeTwoDTab == "rules") {
            TwoDRulesLibraryScreen(
                en = en,
                modifier = Modifier.fillMaxSize().padding(inset),
            )
        } else if (openCheck.isEmpty() && activeTwoDTab == "research") {
            TwoDResearchLabScreen(
                draws = merged.draws,
                closedDates = closedDates,
                cutoff = defaultTarget,
                en = en,
                ready = inputsReady,
                session = researchSession,
                modifier = Modifier.fillMaxSize().padding(inset),
            )
        } else if (openCheck.isEmpty()) {
            val selectedSection = sections.first { it.key == openGroup }
            Column(
                Modifier.fillMaxSize().padding(inset)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Draw selection, not a tall decorative filter card.
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { datePickerMode = "target" },
                        modifier = Modifier.weight(1.05f).heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 5.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.CalendarMonth, null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(date.toString(), maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Surface(modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = scheme.surfaceVariant) {
                        Row(Modifier.padding(2.dp)) {
                            listOf(
                                DrawSession.MORNING to tr("မနက်", "AM"),
                                DrawSession.EVENING to tr("ညနေ", "PM"),
                            ).forEach { (session, label) ->
                                val active = target.session == session
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (active) scheme.primaryContainer else Color.Transparent,
                                    shadowElevation = if (active) 2.dp else 0.dp,
                                    onClick = {
                                        // Snapshot the *visible* default date before switching
                                        // to an explicit user selection.
                                        selectedDate = date.toString()
                                        selectedSession = session.name
                                        userChosen = true
                                    },
                                ) {
                                    Box(Modifier.defaultMinSize(minHeight = 42.dp),
                                        contentAlignment = Alignment.Center) {
                                        Text(label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // Approved 3 tags in one row. Tapping changes the result grid in place.
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    sections.forEach { section ->
                        TwoDAnalysisGroupTag(
                            modifier = Modifier.weight(1f), text = section.title,
                            selected = openGroup == section.key,
                            onClick = { openGroup = section.key; rangeMenu = false },
                        )
                    }
                }

                // Weekly status checks have fixed week scopes and do not use this filter.
                if (openGroup != "2") {
                    Row(Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.History, null,
                            modifier = Modifier.size(15.dp),
                            tint = scheme.onSurfaceVariant)
                        Text(tr("မှတ်တမ်း", "History"),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { rangeMenu = true },
                                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) {
                                Text(lookbackLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Icon(Icons.Default.KeyboardArrowDown, null,
                                    modifier = Modifier.size(17.dp))
                            }
                            DropdownMenu(expanded = rangeMenu,
                                onDismissRequest = { rangeMenu = false }) {
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
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            OutlinedButton(onClick = { datePickerMode = "start" },
                                modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                                contentPadding = PaddingValues(horizontal = 5.dp)) {
                                Text(customStart,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = { datePickerMode = "end" },
                                modifier = Modifier.weight(1f).heightIn(min = 42.dp),
                                contentPadding = PaddingValues(horizontal = 5.dp)) {
                                Text(customEnd,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (!customValid) {
                            TwoDStatusMessage(tr("ရက်စွဲအစဉ်ကို ပြန်စစ်ပါ",
                                "Check date range"), true)
                        }
                    }
                }

                if (!inputsReady) {
                    // Keep one stable screen frame while the initial Room
                    // snapshots arrive; never flash a fake empty result grid.
                    Box(Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = scheme.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    if (!validTarget) {
                        TwoDStatusMessage(tr("ရွေးထားသောနေ့မှာ 2D အကြိမ်ပိတ်ထားသည်။",
                            "No trading draw on the selected date."), true)
                    } else if (lookback == "custom" && !customValid && openGroup != "2") {
                        TwoDStatusMessage(tr("ရက်စွဲအပိုင်းအခြားကို ပြန်စစ်ပါ",
                            "Check the selected date range"), true)
                    }

                    // The confirmed-data renderer uses the EXACT same
                    // approved adaptive grid and glass components as DEMO.
                    // Nonworking dates / invalid historical filters cannot
                    // fabricate results; show the user-approved text instead.
                    if (report != null &&
                        (openGroup == "2" || lookback != "custom" || customValid)) {
                        // Scope Cherry's bright red to the Analysis result cards.
                        // Never alter the shared theme or Research's separate chrome.
                        val analysisCardColors = scheme.copy(
                            secondary = AppColors.Primary,
                            onSecondary = Color.White,
                            secondaryContainer = AppColors.Blush,
                            onSecondaryContainer = AppColors.PrimaryDeep,
                        )
                        MaterialTheme(colorScheme = analysisCardColors) {
                            TwoDAnalysisLiveGrid(
                                group = selectedSection.key,
                                report = report,
                                en = en,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                onOpenDetail = { openCheck = it },
                            )
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = scheme.secondaryContainer.copy(alpha = .35f),
                        ) {
                            Text(
                                tr("အချက်အလက် မရရှိနိုင်သေးပါ",
                                    "Information not yet available"),
                                modifier = Modifier.padding(12.dp),
                                color = scheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                
                }
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

                    // User-edited titles and the existing language-aware typography contract.
                    Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(method.title, style = LedgerTypographyStandard.style(
                            LedgerTextRole.CARD_TITLE, if (en) "en" else "my"),
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val currentMonday = date.minusDays((date.dayOfWeek.value - 1).toLong())
                        val shownPeriod = when (openCheck) {
                            "04", "01" -> {
                                val previousMonday = currentMonday.minusWeeks(1)
                                previousMonday.toString() + " – " + previousMonday.plusDays(4)
                            }
                            "05", "07" -> currentMonday.toString() + " – " + date
                            else -> date.toString() + " · " +
                                (if (target.session == DrawSession.MORNING)
                                    tr("မနက်အကြိမ်", "Morning")
                                 else tr("ညနေအကြိမ်", "Evening"))
                        }
                        Text(shownPeriod +
                            (if (openCheck in listOf("02", "03", "06", "08", "01"))
                                " · " + lookbackLabel else ""),
                            style = LedgerTypographyStandard.style(
                                LedgerTextRole.HELPER, if (en) "en" else "my"),
                            color = scheme.onSurfaceVariant)
                    }
                    if (openCheck == "02") {
                        TwoDA02Dashboard(report.a02, en)

                    } else {
                        TwoDAnalysisDetails(openCheck, report, en,
                            selectedStreakStream = streakStream,
                            onStreakStreamChange = { streakStream = it })
                    }
                    Spacer(Modifier.height(20.dp))
                } else {
                    TwoDStatusMessage(tr("ဒီစစ်ဆေးချက်အတွက် အချက်အလက်မရှိပါ။",
                        "No details are available for this check."), false)
                }
            }
        }
    }


    datePickerMode?.let { mode ->
        val current = when (mode) {
            "start" -> customStart
            "end" -> customEnd
            else -> date.toString()
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
private fun TwoDBentoGroup(
    section: TwoDSectionSpec,
    selected: Boolean,
    featured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (featured) 26.dp else 23.dp)
    val wine = Color(0xFF66213F)
    val rose = Color(0xFFB7375C)
    val ink = if (selected) Color.White else scheme.onSurface
    val secondary = if (selected) Color.White.copy(alpha = 0.84f)
        else scheme.onSurfaceVariant
    Box(modifier.padding(bottom = if (selected) 5.dp else 3.dp)) {
        // The offset underlay gives the active tile a physical, layered edge.
        Box(Modifier.matchParentSize().offset(y = if (selected) 5.dp else 3.dp)
            .background(if (selected) Color(0xFF41142D)
                else scheme.primary.copy(alpha = 0.15f), shape))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = if (selected) wine else scheme.surface,
            tonalElevation = if (selected) 5.dp else 1.dp,
            shadowElevation = if (selected) 13.dp else 5.dp,
            onClick = onClick,
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(
                        if (selected) Brush.linearGradient(
                            listOf(wine, rose, Color(0xFFD06879)))
                        else Brush.linearGradient(listOf(
                            scheme.surface, scheme.primaryContainer.copy(alpha = 0.34f))),
                    )
                    .heightIn(min = if (featured) 120.dp else 112.dp),
            ) {
                Canvas(Modifier.matchParentSize()) {
                    val center = Offset(size.width * 0.94f, size.height * 0.05f)
                    val radius = size.minDimension * if (featured) 0.63f else 0.77f
                    drawCircle(
                        color = (if (selected) Color.White else scheme.primary)
                            .copy(alpha = if (selected) 0.13f else 0.05f),
                        radius = radius, center = center,
                    )
                    drawCircle(
                        color = (if (selected) Color.White else scheme.primary)
                            .copy(alpha = if (selected) 0.22f else 0.10f),
                        radius = radius * 0.64f, center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                Column(
                    Modifier.align(Alignment.CenterStart)
                        .padding(horizontal = if (featured) 19.dp else 13.dp,
                            vertical = 13.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) Color.White.copy(alpha = 0.19f)
                                else scheme.primaryContainer.copy(alpha = 0.76f),
                        ) {
                            Icon(section.icon, null,
                                modifier = Modifier.padding(9.dp).size(
                                    if (featured) 24.dp else 21.dp),
                                tint = if (selected) Color.White else scheme.primary)
                        }
                        Text(section.key + " / 03",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = secondary)
                        if (featured) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.NorthEast, null, tint = ink,
                                modifier = Modifier.size(21.dp))
                        }
                    }
                    Text(section.title,
                        color = ink,
                        fontWeight = FontWeight.Bold,
                        style = if (featured) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.titleSmall,
                        maxLines = if (featured) 2 else 3,
                        overflow = TextOverflow.Ellipsis)
                    Text(section.methods.size.toString() + " " +
                            (if (section.title.startsWith("1.") ||
                                section.title.startsWith("2.") ||
                                section.title.startsWith("3.")) "checks" else "စစ်ဆေးနည်း"),
                        style = MaterialTheme.typography.labelMedium,
                        color = secondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TwoDMethodDirectory(
    section: TwoDSectionSpec,
    enabled: Boolean,
    en: Boolean,
    onMethod: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(width = 4.dp, height = 29.dp)
                .background(scheme.primary, RoundedCornerShape(3.dp)))
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(section.caption, style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant)
            }
            Text(section.methods.size.toString() + " / 08",
                style = MaterialTheme.typography.labelMedium, color = scheme.primary,
                fontWeight = FontWeight.Bold)
        }
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = scheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 4.dp,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                section.methods.forEachIndexed { index, method ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = enabled) { onMethod(method.key) }
                            .defaultMinSize(minHeight = 66.dp)
                            .padding(horizontal = 3.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = scheme.primaryContainer.copy(alpha = 0.55f),
                        ) {
                            Box(Modifier.size(41.dp), contentAlignment = Alignment.Center) {
                                Icon(method.icon, null, tint = scheme.primary,
                                    modifier = Modifier.size(21.dp))
                            }
                        }
                        Column(Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(method.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (enabled) scheme.onSurface
                                    else scheme.onSurfaceVariant)
                            Text(method.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Surface(shape = RoundedCornerShape(9.dp),
                            color = scheme.surfaceVariant) {
                            Icon(Icons.Default.ChevronRight, null,
                                tint = scheme.primary,
                                modifier = Modifier.padding(5.dp).size(20.dp))
                        }
                    }
                    if (index != section.methods.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 54.dp, end = 5.dp),
                            color = scheme.outlineVariant.copy(alpha = 0.45f),
                        )
                    }
                }
            }
        }
    }
}


/** Only short, observed findings go in previews. Detailed evidence lives in the method view. */
internal data class TwoDQuickFact(val label: String, val value: String, val note: String = "")

internal fun twoDQuickFacts(id: String, report: TwoDAnalysisReport, en: Boolean): List<TwoDQuickFact> {
    fun tr(mm: String, english: String) = if (en) english else mm
    val noData = tr("မှတ်တမ်းမရှိ", "No records")
    val none = tr("မရှိ", "None")
    val times = tr(" ကြိမ်", " times")
    fun leading(counts: List<TwoDCount>): Pair<String, String> {
        val top = counts.maxOfOrNull { it.count } ?: 0
        if (top <= 0) return noData to ""
        val tied = counts.filter { it.count == top }
        val display = if (tied.size <= 2) tied.joinToString(" · ") { it.digit }
            else tied.size.toString() + tr(" မျိုးတူ", " tied")
        return display to top.toString() + times + if (tied.size > 1) tr("စီ", " each") else ""
    }
    fun fact(label: String, pair: Pair<String, String>) = TwoDQuickFact(label, pair.first, pair.second)
    fun numbers(values: Collection<Char>) = values.joinToString(" · ").ifEmpty { none }
    return when (id) {
        "02" -> listOf(
            fact(tr("ဂဏန်းတွဲ 00–99", "2D pairs 00–99"),
                if (report.a02.samples == 0) noData to "" else leading(report.a02.numbers)),
            fact(tr("ဂဏန်း 0–9", "Digits 0–9"),
                if (report.a02.samples == 0) noData to "" else leading(report.a02.singles)),
            fact(tr("ဂဏန်း အနည်းဆုံး (ပေါ်ဖူး)", "Least observed digit (>0)"),
                if (report.a02.samples == 0) noData to ""
                else {
                    val least = report.a02.singles.filter { it.count > 0 }.minOfOrNull { it.count }
                    if (least == null) noData to "" else {
                        val tied = report.a02.singles.filter { it.count == least }
                        (if (tied.size <= 2) tied.joinToString(" · ") { it.digit }
                         else tied.size.toString() + tr(" မျိုးတူ", " tied")) to
                            (least.toString() + times + if (tied.size > 1) tr("စီ", " each") else "")
                    }
                }),
        )
        "03" -> {
            val a = report.a03
            listOf(
                TwoDQuickFact(tr("ပုံစံတူအပတ်", "Matching weeks"),
                    if (!a.eligible) tr("စစ်ဆေးရန်မပြည့်", "Not eligible")
                    else a.matched.size.toString() + tr(" ပတ်", " weeks")),
                fact(tr("နောက်အမျိုးအစား", "Following class"),
                    if (!a.eligible) noData to ""
                    else if (a.matched.isEmpty()) none to ""
                    else {
                        val top = a.nextParity.values.maxOrNull() ?: 0
                        val ties = a.nextParity.filterValues { it == top && top > 0 }.keys
                        if (ties.isEmpty()) noData to ""
                        else (ties.joinToString(" · ") { twoDParityLabel(it, en) }) to
                            (top.toString() + times)
                    }),
            )
        }
        "06" -> {
            val key = if (report.target.session == DrawSession.MORNING) "morning" else "evening"
            val stream = report.a06[key]
            fun longest(values: List<TwoDStreak>): Pair<String, String> {
                if (stream == null || stream.observedDraws == 0 || values.isEmpty())
                    return noData to ""
                val longest = values.maxOf { it.observedDrawsSince }
                val ties = values.filter { it.observedDrawsSince == longest }
                val display = if (ties.size <= 2) ties.joinToString(" · ") { it.value }
                    else ties.size.toString() + tr(" မျိုးတူ", " tied")
                val lowerBound = ties.any { it.historyStartTruncated } || stream.hasUnknownOpenSlots
                return display to ((if (lowerBound) "≥" else "") + longest.toString() +
                    tr(" အကြိမ် မတွေ့", " draws absent"))
            }
            listOf(
                fact(tr("အကြာဆုံးမထွက်သောတွဲ", "Longest absent pair"),
                    longest(stream?.fullNumbers.orEmpty())),
                fact(tr("အကြာဆုံးမထွက်သောဂဏန်း", "Longest absent digit"),
                    longest(stream?.singleDigits.orEmpty())),
            )
        }
        "08" -> {
            val a = report.a08
            val ready = a.firstReference != null && a.secondReference != null
            listOf(
                TwoDQuickFact(tr("ရှေ့နှစ်ကြိမ်တူ", "Both prior results"),
                    if (!ready) noData else a.both.matches.size.toString() + times),
                TwoDQuickFact(tr("တစ်ကြိမ်ချင်းတူ", "Individual matches"),
                    if (!ready) noData else
                        a.firstOnly.matches.size.toString() + " / " + a.secondOnly.matches.size.toString(),
                    tr("ရှေ့ ၁ / ရှေ့ ၂", "First / second")),
            )
        }
        "05" -> {
            val a = report.a05
            val last = a.transitions.lastOrNull()
            listOf(
                TwoDQuickFact(tr("ယခုအပတ် ကျန်ဂဏန်း", "Unseen digits this week"),
                    if (last == null) noData else numbers(a.remainingDigits),
                    if (!a.completeThroughTarget) tr("ရှိသမျှမှတ်တမ်းအရ", "Available records only") else ""),
                TwoDQuickFact(tr("နောက်ဆုံး အသစ်ဂဏန်း", "Latest new digits"),
                    if (last == null) noData else if (!last.classificationVerified)
                        tr("အတည်မပြုနိုင်", "Unverified")
                    else numbers(last.newlySeenDigits)),
            )
        }
        "07" -> {
            val a = report.a07
            listOf(
                TwoDQuickFact(tr("ယခုအပတ် ပြန်ကျ", "Exact repeats this week"),
                    if (a.observedDraws == 0) noData
                    else a.repeatedNumbers.sumOf { it.count }.toString() + times),
                fact(tr("အထူးအုပ်စု အများဆုံး", "Top special group"),
                    if (a.observedDraws == 0) noData to "" else {
                        val max = a.groups.values.maxOrNull() ?: 0
                        if (max <= 0) none to ""
                        else a.groups.filterValues { it == max }.keys
                            .joinToString(" · ") { twoDGroupLabel(it, en) } to (max.toString() + times)
                    }),
            )
        }
        "04" -> {
            val a = report.a04
            val label = if (a.complete) tr("ပြီးခဲ့တဲ့အပတ် ပျောက်ဂဏန်း", "Missing digits last week")
                else tr("မှတ်တမ်းအရ မတွေ့သောဂဏန်း", "Digits not in available draws")
            listOf(
                TwoDQuickFact(label, if (a.observedDraws == 0) noData else numbers(a.remainingDigits)),
                TwoDQuickFact(tr("ပြီးခဲ့တဲ့အပတ် ပြန်ကျ", "Last week's repeats"),
                    if (a.observedDraws == 0) noData
                    else a.repeatedNumbers.sumOf { it.count }.toString() + times),
            )
        }
        "01" -> {
            val a = report.a01
            val observed = a.results.count { it.thisWeekFirstSeen != null }
            val single = a.results.singleOrNull()
            val companion = single?.commonCompanions.orEmpty()
            val highest = companion.maxOfOrNull { it.count } ?: 0
            val partners = companion.filter { it.count == highest && highest > 0 }
            listOf(
                TwoDQuickFact(
                    if (a.previousWeekComplete) tr("ပြီးခဲ့တဲ့အပတ် ပျောက်ဂဏန်း", "Last week's missing digits")
                    else tr("မှတ်တမ်းအရ မတွေ့သောဂဏန်း", "Unseen in available draws"),
                    if (report.a04.observedDraws == 0) noData else numbers(a.missingDigits)),
                TwoDQuickFact(tr("ဒီအပတ် ပြန်တွေ့", "Observed returns this week"),
                    if (report.a04.observedDraws == 0) noData
                    else observed.toString() + tr(" လုံး", " digits"),
                    if (single != null && partners.isNotEmpty())
                        tr("သမိုင်းတွဲဖက်", "Historic companion") + ": " +
                            single.digit.toString() + "→" +
                            partners.joinToString(" · ") { it.companion.toString() }
                    else ""),
            )
        }
        else -> emptyList()
    }
}

private fun twoDGroupQuickFacts(
    key: String, report: TwoDAnalysisReport, en: Boolean,
): List<TwoDQuickFact> = when (key) {
    "1" -> when {
        report.a02.samples > 0 -> twoDQuickFacts("02", report, en)
        report.a03.eligible && report.a03.matched.isNotEmpty() -> twoDQuickFacts("03", report, en)
        report.a08.both.matches.isNotEmpty() -> twoDQuickFacts("08", report, en)
        report.a06.values.any { it.observedDraws > 0 } -> twoDQuickFacts("06", report, en)
        else -> twoDQuickFacts("02", report, en)
    }
    "2" -> listOf(twoDQuickFacts("05", report, en)[0],
                   twoDQuickFacts("07", report, en)[0])
    else -> listOf(twoDQuickFacts("04", report, en)[0],
                   twoDQuickFacts("01", report, en)[1])
}

@Composable
private fun TwoDQuickFactsRow(facts: List<TwoDQuickFact>) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            facts.take(2).forEach { fact ->
                Column(
                    Modifier.weight(1f)
                        .background(scheme.surfaceVariant.copy(alpha = 0.58f),
                            RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(fact.label, style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(fact.value, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    if (fact.note.isNotEmpty()) {
                        Text(fact.note, style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        facts.drop(2).forEach { fact ->
            Row(
                Modifier.fillMaxWidth()
                    .background(scheme.surfaceVariant.copy(alpha = 0.58f),
                        RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(fact.label, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant, maxLines = 2)
                Text(fact.value + if (fact.note.isNotBlank()) " · " + fact.note else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TwoDGroupOverviewCard(
    title: String, facts: List<TwoDQuickFact>, enabled: Boolean, onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(17.dp),
        color = scheme.surface, tonalElevation = 1.dp, shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ChevronRight, null, tint = scheme.primary)
            }
            if (facts.isEmpty()) {
                Text("—", color = scheme.onSurfaceVariant)
            } else {
                TwoDQuickFactsRow(facts)
            }
        }
    }
}

@Composable
private fun TwoDFocusedMethodCard(
    method: TwoDMethodSpec, facts: List<TwoDQuickFact>, onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), color = scheme.surface,
        tonalElevation = 1.dp, shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(method.title, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ChevronRight, null, tint = scheme.primary)
            }
            if (facts.isNotEmpty()) TwoDQuickFactsRow(facts)
        }
    }
}

@Composable
private fun TwoDNavAction(
    modifier: Modifier,
    active: Boolean,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    researchChrome: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val mint = Color(0xFF78E4D2)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        color = if (active && researchChrome) Color(0xFF27515A)
            else if (active) scheme.primaryContainer else Color.Transparent,
        shadowElevation = if (active) 3.dp else 0.dp,
        onClick = onClick,
    ) {
        Column(Modifier.defaultMinSize(minHeight = 58.dp)
            .padding(horizontal = 3.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)) {
            Icon(icon, null, modifier = Modifier.size(21.dp),
                tint = if (researchChrome) (if (active) mint else Color(0xFFB4C8CE))
                    else if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (researchChrome) (if (active) mint else Color(0xFFB4C8CE))
                    else if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
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
            Triple(tr("ပျောက်ဂဏန်းတွဲ", "Longest absent pair"),
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
        "01" -> Triple(tr("ဒီအပတ် ပျောက်ဂဏန်းပြန်ပေါ်", "Missing digits observed returning"),
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
