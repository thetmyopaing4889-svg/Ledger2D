@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.AppContainer
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.BetterDataQuantEngine
import com.myanmar.ledger2d.core.domain.BetterHint
import com.myanmar.ledger2d.core.domain.BetterMethod
import com.myanmar.ledger2d.core.domain.BetterMode
import com.myanmar.ledger2d.core.domain.BetterPick
import com.myanmar.ledger2d.core.domain.BetterStrategy
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId

private val betterYangon = ZoneId.of("Asia/Yangon")

@Composable
fun BetterDataScreen(container: AppContainer, onBack: () -> Unit) {
    val en = LocalLanguage.current.code == "en"
    fun tr(mm: String, english: String) = if (en) english else mm
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("cherry_better_data_tickets", 0) }
    var tickets by remember { mutableStateOf(prefs.getString("tickets", "").orEmpty()) }
    var now by remember { mutableStateOf(LocalDateTime.now(betterYangon)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = LocalDateTime.now(betterYangon)
        }
    }
    val historyFlow = remember(container) { container.history.observeAll() }
    val dailyFlow = remember(container) { container.liveResults.observeAll() }
    val winnerFlow = remember(container) { container.winners.observeAll() }
    val lockFlow = remember(container) { container.database.manualWinnerLockDao().observeAll() }
    val closedFlow = remember(container) { container.closedDays.observeAll() }
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = null)
    val daily by dailyFlow.collectAsStateWithLifecycle(initialValue = null)
    val winners by winnerFlow.collectAsStateWithLifecycle(initialValue = null)
    val locks by lockFlow.collectAsStateWithLifecycle(initialValue = null)
    val closed by closedFlow.collectAsStateWithLifecycle(initialValue = null)
    val ready = history != null && daily != null && winners != null && locks != null && closed != null
    val closedDates = remember(closed) { closed.orEmpty().map { it.date }.toSet() }
    val target = remember(now, closedDates) {
        var next = DrawSchedule.nextDraw(now)
        while (!DrawSchedule.isWorkingDate(next.date, closedDates)) {
            next = DrawIdentity(next.date.plusDays(1), DrawSession.MORNING)
        }
        next
    }
    val merged = remember(history, daily, winners, locks, closedDates, now) {
        Historic2DDataSource.resolve(
            liveDaily = daily.orEmpty(), history = history.orEmpty(),
            today = now.toLocalDate(), now = now.toLocalTime(),
            winners = winners.orEmpty(), manualLocks = locks.orEmpty(),
        ).draws.filterNot { it.date in closedDates }
    }
    val dailyForTarget = daily.orEmpty().firstOrNull { it.date == target.date }
    var modernOverride by rememberSaveable(target.date.toString()) { mutableStateOf("") }
    var internetOverride by rememberSaveable(target.date.toString()) { mutableStateOf("") }
    val sourceModern = dailyForTarget?.modern930?.takeIf { it.matches(Regex("[0-9]{2}")) }
    val sourceInternet = dailyForTarget?.internet930?.takeIf { it.matches(Regex("[0-9]{2}")) }
    val modern = modernOverride.takeIf { it.length == 2 } ?: sourceModern
    val internet = internetOverride.takeIf { it.length == 2 } ?: sourceInternet
    val report = remember(merged, target, modern, internet, ready) {
        if (ready) BetterDataQuantEngine.calculate(merged, target, modern, internet) else null
    }
    var strategy by rememberSaveable { mutableStateOf(BetterStrategy.CURRENT) }
    // Until the user chooses a mode, open the first mode which actually has results.
    // Never silently relabel a formula output as an overlap.
    var selectedMode by rememberSaveable { mutableStateOf<BetterMode?>(null) }
    val suggestedMode = when {
        report?.select(strategy, BetterMode.OVERLAP)?.isNotEmpty() == true -> BetterMode.OVERLAP
        report?.select(strategy, BetterMode.RULES)?.isNotEmpty() == true -> BetterMode.RULES
        else -> BetterMode.FORMULAS
    }
    val mode = selectedMode ?: suggestedMode
    var detail by remember { mutableStateOf<BetterPick?>(null) }
    var showTickets by remember { mutableStateOf(false) }
    var show930 by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var savedNotice by remember { mutableStateOf(false) }
    val picks = report?.select(strategy, mode).orEmpty()
    val pickLabel = when (mode) {
        BetterMode.OVERLAP -> tr("မူနဲ့တွက်နည်း ဘုံတူ", "Rule & formula overlap")
        BetterMode.RULES -> tr("မူအရ", "From rules")
        BetterMode.FORMULAS -> tr("တွက်နည်းအရ", "From formulas")
    }
    Scaffold(
        containerColor = Color(0xFFFFF7F9),
        topBar = {
            TopAppBar(
                title = {
                    Text("BETTER DATA", fontWeight = FontWeight.Black, color = AppColors.PrimaryDeep)
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
                actions = { IconButton(onClick = { showTickets = true }) {
                    Icon(Icons.Default.History, tr("ခန့်မှန်းမှတ်တမ်း", "Saved tickets"))
                } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    color = AppColors.PrimaryDeep,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(tr("လာမယ့် 2D အကြိမ်", "Next 2D draw"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.9f))
                            Text("${target.date}  •  " + tr(
                                if (target.session == DrawSession.MORNING) "မနက်" else "ညနေ",
                                if (target.session == DrawSession.MORNING) "AM" else "PM"
                            ), style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black, color = Color.White)
                        }
                        Icon(Icons.Default.CalendarToday, null, tint = Color.White,
                            modifier = Modifier.size(24.dp))
                    }
                }
            }
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, AppColors.Stone)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(15.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Text(tr("ဒီအကြိမ်အတွက် ရွေးထားသောဂဏန်း", "Selected numbers for this draw"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
                        Text(tr("တွက်ချက်နည်းလမ်း", "Calculation approach"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = strategy == BetterStrategy.CURRENT,
                                onClick = { strategy = BetterStrategy.CURRENT; selectedMode = null; savedNotice = false },
                                label = { Text(tr("လက်ရှိအကြိမ်", "Current draw")) }
                            )
                            FilterChip(
                                selected = strategy == BetterStrategy.ALIVE,
                                onClick = { strategy = BetterStrategy.ALIVE; selectedMode = null; savedNotice = false },
                                label = { Text(tr("ဆက်တိုက်ကိုက်ညီမူ", "Alive rules")) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = mode == BetterMode.OVERLAP,
                                onClick = { selectedMode = BetterMode.OVERLAP; savedNotice = false },
                                label = { Text(tr("ဘုံတူ", "Overlap")) }
                            )
                            FilterChip(
                                selected = mode == BetterMode.RULES,
                                onClick = { selectedMode = BetterMode.RULES; savedNotice = false },
                                label = { Text(tr("မူအရ", "Rules")) }
                            )
                            FilterChip(
                                selected = mode == BetterMode.FORMULAS,
                                onClick = { selectedMode = BetterMode.FORMULAS; savedNotice = false },
                                label = { Text(tr("တွက်နည်းအရ", "Formulas")) }
                            )
                        }
                        Text(pickLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppColors.PrimaryDeep,
                            fontWeight = FontWeight.SemiBold)
                        if (!ready) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else if (picks.isEmpty()) {
                            Surface(color = AppColors.Blush, shape = RoundedCornerShape(15.dp)) {
                                Column(
                                    Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    Text(
                                        if (strategy == BetterStrategy.ALIVE)
                                            tr("လက်ရှိ ဆက်တိုက်ကိုက်ညီမူ မတွေ့သေးပါ။",
                                                "No qualifying alive rules yet.")
                                        else if (mode == BetterMode.OVERLAP)
                                            tr("နှစ်ဖက်လုံးမှာ တူတဲ့ဂဏန်း မတွေ့ပါ။",
                                                "No shared pair between rules and formulas.")
                                        else tr("ဒီနည်းနဲ့ ဂဏန်းမတွေ့သေးပါ။",
                                            "No pair found with this method."),
                                        fontWeight = FontWeight.SemiBold,
                                        color = AppColors.PrimaryDeep
                                    )
                                    Text(
                                        tr("အခြားနည်းလမ်းကို ပြောင်းကြည့်နိုင်ပါတယ်။",
                                            "You can switch to another method."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (strategy == BetterStrategy.ALIVE &&
                                        report?.current?.isNotEmpty() == true) {
                                        TextButton(onClick = {
                                            strategy = BetterStrategy.CURRENT
                                            selectedMode = null
                                        }) { Text(tr("လက်ရှိအကြိမ်ရလဒ် ကြည့်ရန်", "Show current draw")) }
                                    }
                                }
                            }
                        } else {
                            Text(
                                tr("ဂဏန်းကိုနှိပ်ပြီး ဘာကြောင့်ရွေးထားသလဲ ကြည့်နိုင်တယ်။",
                                    "Tap a number to see why it was selected."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                picks.forEachIndexed { index, pick ->
                                    Surface(
                                        onClick = { detail = pick },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (index == 0) AppColors.Primary else AppColors.Blush,
                                        border = if (index == 0) null else
                                            BorderStroke(1.dp, AppColors.Stone)
                                    ) {
                                        Column(
                                            Modifier.padding(vertical = 15.dp, horizontal = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(if (index == 0) tr("အဓိက", "Main")
                                                else tr("အရံ", "Other"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (index == 0) Color.White else AppColors.PrimaryDeep)
                                            Text(
                                                pick.number,
                                                fontSize = 30.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (index == 0) Color.White else AppColors.PrimaryDeep
                                            )
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { clipboard.setText(
                                        AnnotatedString(picks.joinToString(" • ") { it.number })
                                    ) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.ContentCopy, null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(tr("ကူးမည်", "Copy"))
                                }
                                Button(
                                    onClick = {
                                        val record = "${target.date} ${target.session.name} | ${strategy.name}/${mode.name} | " +
                                            picks.joinToString(",") { it.number } + " | inputs=${modern ?: "-"}/${internet ?: "-"}"
                                        val next = (listOf(record) + tickets.lines().filter { it.isNotBlank() })
                                            .distinct().take(30).joinToString("\n")
                                        val written = prefs.edit().putString("tickets", next).commit()
                                        if (written) { tickets = next; savedNotice = true }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.BookmarkBorder, null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(tr("သိမ်းမည်", "Save"))
                                }
                            }
                            if (savedNotice) {
                                Text(tr("ရွေးချယ်မှုကို မှတ်တမ်းသိမ်းပြီးပါပြီ။",
                                        "Selection saved to history."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppColors.Success)
                            }
                        }
                        Text(
                            tr("မူနှင့်တွက်နည်းအရ ရွေးထားခြင်းဖြစ်ပြီး ထွက်မည့်ဂဏန်းကို အာမခံခြင်းမဟုတ်ပါ။",
                                "Rule-based selections, not guaranteed results."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                Surface(
                    color = Color.White, shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, AppColors.Stone)
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
                        TextButton(
                            onClick = { show930 = !show930 },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Tune, null, tint = AppColors.PrimaryDeep)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("9:30 အချက်အလက်ထည့်ရန်", "Add 9:30 inputs"),
                                color = AppColors.PrimaryDeep, modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start)
                            Icon(if (show930) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, tint = AppColors.PrimaryDeep)
                        }
                        if (show930) {
                            Text(tr("Modern / Internet ဂဏန်းရရှိမှ ထည့်ပါ။ မရှိလည်း တွက်ချက်နိုင်ပါတယ်။",
                                "Enter Modern / Internet values only when available."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = modernOverride,
                                    onValueChange = {
                                        modernOverride = it.filter { c -> c in '0'..'9' }.take(2)
                                    },
                                    label = { Text("Modern") },
                                    placeholder = { Text(sourceModern ?: "—") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = internetOverride,
                                    onValueChange = {
                                        internetOverride = it.filter { c -> c in '0'..'9' }.take(2)
                                    },
                                    label = { Text("Internet") },
                                    placeholder = { Text(sourceInternet ?: "—") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                if (report?.has930 == true)
                                    tr("9:30 အချက်အလက် ပါဝင်ပြီးဖြစ်ပါတယ်။", "9:30 inputs included.")
                                else tr("9:30 ဒေတာ မပြည့်စုံသေးပါ။", "9:30 data not yet complete."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (modernOverride.isNotEmpty() || internetOverride.isNotEmpty()) {
                                TextButton(onClick = { modernOverride = ""; internetOverride = "" }) {
                                    Text(tr("သိမ်းထားသောဒေတာကို ပြန်သုံးမည်", "Use saved data"))
                                }
                            }
                        }
                    }
                }
            }
            if (report != null) {
                item {
                    Surface(
                        color = Color.White, shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, AppColors.Stone)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
                            TextButton(onClick = { showDetails = !showDetails },
                                modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.Info, null, tint = AppColors.PrimaryDeep)
                                Spacer(Modifier.width(8.dp))
                                Text(tr("အသေးစိတ်တွက်ချက်မှုများ", "Calculation details"),
                                    color = AppColors.PrimaryDeep,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Start)
                                Icon(if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, tint = AppColors.PrimaryDeep)
                            }
                            if (showDetails) {
                                Text(
                                    tr("သမိုင်းရလဒ် ${report.observedDraws} ကြိမ်ကို အခြေခံထားသည်။",
                                        "Using ${report.observedDraws} earlier result records."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    tr("လက်ရှိမူရလဒ် ${report.current.size} တွဲ • ဆက်တိုက်ကိုက်ညီမူ ${report.aliveIds.size} ခု",
                                        "${report.current.size} current pairs • ${report.aliveIds.size} alive rules"),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (report.aliveIds.isEmpty()) {
                                    Text(
                                        tr("ဆက်တိုက်ကိုက်ညီမူ သက်သေမလုံလောက်သေးပါ။",
                                            "No qualifying alive rules in the available history."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (report.hints.isNotEmpty()) {
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    Text(tr("ဝမ်းချိန်း / လုံးပိုင် / ဘရိတ်ရလဒ်များ",
                                        "Single-digit and break results"),
                                        fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
                                    Text(tr("2D အတွဲပြောင်းနည်း မရှင်းသေးသော တွက်ချက်ချက်များကို ဒီနေရာမှာသာ ပြထားသည်။",
                                        "Unspecified pair conversions are not invented."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    report.hints.take(10).forEach { hint ->
                                        Spacer(Modifier.height(8.dp))
                                        HintRow(hint)
                                    }
                                }
                                if (report.cuts().isNotEmpty()) {
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    Text(
                                        tr("မူအရ ဖြတ်မူဆန့်ကျင်နေသောဂဏန်း ${report.cuts().size} တွဲရှိသည်။",
                                            "${report.cuts().size} pairs have cut-rule conflicts."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Text(
                        tr("စမ်းသပ်ဗားရှင်း • မူအချို့ကိုသာ ထည့်သွင်းထားသေးသည်။",
                            "Preview • only selected rules are implemented so far."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }
            }
        }
    }
    detail?.let { pick ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text("Winning DNA • ${pick.number}", color = AppColors.PrimaryDeep) },
            text = {
                Column(Modifier.heightIn(max = 390.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(tr("မူ/တွက်နည်းများ", "Rule and formula traces"),
                        fontWeight = FontWeight.Bold)
                    Column(Modifier.weight(1f, fill = false).fillMaxWidth()
                        .then(Modifier)) {
                        pick.traces.forEach { trace ->
                            Text("• ${trace.name}", fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodySmall)
                            Text(trace.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                        }
                        pick.performance.forEach { perf ->
                            Text("${perf.id}: ${perf.hits}/${perf.samples} • streak ${perf.currentStreak}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (pick.cuts.isNotEmpty()) Text(
                            tr("ဖြတ်မူဆန့်ကျင်မှု: ", "Cut conflicts: ") + pick.cuts.joinToString("; "),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) {
                Text(tr("ပိတ်ရန်", "Close")) } },
        )
    }
    if (showTickets) {
        AlertDialog(
            onDismissRequest = { showTickets = false },
            title = { Text(tr("သိမ်းထားသောခန့်မှန်းချက်များ", "Saved prediction tickets")) },
            text = {
                Column(Modifier.heightIn(max = 400.dp)) {
                    val rows = tickets.lines().filter { it.isNotBlank() }
                    if (rows.isEmpty()) Text(tr("မသိမ်းရသေးပါ။", "No saved tickets yet."))
                    rows.take(20).forEach { row ->
                        Text(row, style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTickets = false }) { Text(tr("ပိတ်ရန်", "Close")) } },
        )
    }
}

@Composable private fun HintRow(hint: BetterHint) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(hint.name, fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall)
            Text(hint.explanation, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(hint.value, modifier = Modifier.padding(start = 8.dp),
            fontWeight = FontWeight.Black, color = AppColors.PrimaryDeep)
    }
}
