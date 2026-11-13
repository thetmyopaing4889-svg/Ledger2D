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
    var mode by rememberSaveable { mutableStateOf(BetterMode.OVERLAP) }
    var detail by remember { mutableStateOf<BetterPick?>(null) }
    var showTickets by remember { mutableStateOf(false) }
    val picks = report?.select(strategy, mode).orEmpty()
    val pickLabel = when (mode) {
        BetterMode.OVERLAP -> tr("ဘုံဆုံမှတ်", "Confluence")
        BetterMode.RULES -> tr("မူအရ", "Rules")
        BetterMode.FORMULAS -> tr("တွက်နည်းအရ", "Formulas")
    }
    Scaffold(
        containerColor = Color(0xFFFFF7F9),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BETTER DATA", fontWeight = FontWeight.Black, color = AppColors.PrimaryDeep)
                        Text("Cherry Quant Preview", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(color = AppColors.PrimaryDeep, shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoGraph, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("လာမယ့် 2D အကြိမ်", "Next 2D draw"),
                                color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text("${target.date}  •  " + tr(
                            if (target.session == DrawSession.MORNING) "မနက်" else "ညနေ",
                            if (target.session == DrawSession.MORNING) "AM" else "PM",
                        ), color = Color.White, style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text(tr("မြန်မာစံတော်ချိန် • အတည်ပြုရလဒ်အခြေခံ",
                            "Myanmar time • verified result inputs only"),
                            color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Surface(color = Color.White, shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, AppColors.Stone)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr("9:30 Modern / Internet (ထပ်မံတွက်ချက်ရန်)",
                            "9:30 Modern / Internet (recalculate)"), fontWeight = FontWeight.Bold,
                            color = AppColors.PrimaryDeep)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = modernOverride,
                                onValueChange = { modernOverride = it.filter(Char::isDigit).take(2) },
                                label = { Text("Modern") },
                                placeholder = { Text(sourceModern ?: "00") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = internetOverride,
                                onValueChange = { internetOverride = it.filter(Char::isDigit).take(2) },
                                label = { Text("Internet") },
                                placeholder = { Text(sourceInternet ?: "00") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Text(
                            if (report?.has930 == true)
                                tr("9:30 ဒေတာပါဝင်ပြီး ပြန်တွက်ထားသည်။", "9:30 data included.")
                            else tr("9:30 ဒေတာမပြည့်စုံပါ။ သက်ဆိုင်ရာမူကို မတွက်ထားပါ။",
                                "9:30 inputs incomplete; dependent rules remain inactive."),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (modernOverride.isNotEmpty() || internetOverride.isNotEmpty()) {
                            TextButton(onClick = { modernOverride = ""; internetOverride = "" }) {
                                Text(tr("အလိုအလျောက်ဒေတာပြန်သုံးရန်", "Use saved data"))
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tr("ခန့်မှန်းနည်းလမ်း", "Prediction strategy"),
                        fontWeight = FontWeight.Bold, color = AppColors.Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = strategy == BetterStrategy.CURRENT,
                            onClick = { strategy = BetterStrategy.CURRENT },
                            label = { Text(tr("လက်ရှိအကြိမ်", "Current draw")) },
                        )
                        FilterChip(
                            selected = strategy == BetterStrategy.ALIVE,
                            onClick = { strategy = BetterStrategy.ALIVE },
                            label = { Text(tr("Alive Top 5", "Alive Top 5")) },
                        )
                    }
                    Text(tr("ရလဒ်အမျိုးအစား", "Result mode"),
                        fontWeight = FontWeight.Bold, color = AppColors.Ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = mode == BetterMode.OVERLAP,
                            onClick = { mode = BetterMode.OVERLAP },
                            label = { Text(tr("ဘုံဆုံမှတ်", "Overlap")) })
                        FilterChip(selected = mode == BetterMode.RULES,
                            onClick = { mode = BetterMode.RULES },
                            label = { Text(tr("မူအရ", "Rules")) })
                        FilterChip(selected = mode == BetterMode.FORMULAS,
                            onClick = { mode = BetterMode.FORMULAS },
                            label = { Text(tr("တွက်နည်းအရ", "Formulas")) })
                    }
                }
            }
            item {
                if (!ready) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Surface(color = Color.White, shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, AppColors.Stone)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.TrackChanges, null, tint = AppColors.Primary)
                                Spacer(Modifier.width(8.dp))
                                Text(pickLabel + " • " + tr("အများဆုံး ၃ တွဲ", "up to 3 pairs"),
                                    color = AppColors.PrimaryDeep, fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium)
                            }
                            if (picks.isEmpty()) {
                                Text(tr("ဒီနည်းလမ်းမှာ စည်းကမ်းကိုက်ညီတဲ့ ဂဏန်းတွဲ မတွေ့ပါ။ အခြား Mode ကို စမ်းကြည့်နိုင်သည်။",
                                    "No matching pair for this strategy and mode. Try another mode."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                picks.forEachIndexed { index, pick ->
                                    Surface(onClick = { detail = pick },
                                        color = if (index == 0) AppColors.Blush else Color.White,
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, AppColors.Stone)) {
                                        Row(Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            Surface(shape = RoundedCornerShape(12.dp),
                                                color = if (index == 0) AppColors.Primary else AppColors.PrimaryDeep) {
                                                Text(pick.number, Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                                                    color = Color.White, fontWeight = FontWeight.Black,
                                                    fontSize = 26.sp)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(if (index == 0) tr("အဓိက ရွေးချယ်မှု", "Main selection")
                                                    else tr("အရံ ရွေးချယ်မှု", "Reserve"),
                                                    fontWeight = FontWeight.Bold)
                                                Text(tr("မူ/တွက်နည်း ${pick.traces.size} ချက်",
                                                    "${pick.traces.size} rule/formula traces"),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(tr("တွက်နည်းကြည့်ရန်", "View winning DNA"),
                                                    color = AppColors.PrimaryDeep,
                                                    style = MaterialTheme.typography.labelMedium)
                                            }
                                            Icon(Icons.Default.ChevronRight, null, tint = AppColors.PrimaryDeep)
                                        }
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        clipboard.setText(AnnotatedString(picks.joinToString(" • ") { it.number }))
                                    }) { Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp)); Text(tr("ကူးမည်", "Copy")) }
                                    Button(onClick = {
                                        val record = "${target.date} ${target.session.name} | ${strategy.name}/${mode.name} | " +
                                            picks.joinToString(",") { it.number } + " | inputs=${modern ?: "-"}/${internet ?: "-"}"
                                        val next = (listOf(record) + tickets.lines().filter { it.isNotBlank() })
                                            .distinct().take(30).joinToString("\n")
                                        prefs.edit().putString("tickets", next).apply()
                                        tickets = next
                                    }) { Text(tr("ခန့်မှန်းချက် သိမ်းရန်", "Save ticket")) }
                                }
                            }
                            Text(tr("ဂဏန်းအနည်းငယ်သာ ပြထားသည်။ ခန့်မှန်းချက်သည် အာမခံချက်မဟုတ်ပါ။",
                                "Minimal selection only. These are rule outputs, not guaranteed outcomes."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (report != null) {
                item {
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, AppColors.Stone)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(tr("Engine အခြေအနေ", "Engine status"),
                                fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
                            Text(tr("အတည်ပြုသမိုင်း ${report.observedDraws} ကြိမ် • Current ${report.current.size} တွဲ • Alive ${report.alive.size} တွဲ",
                                "${report.observedDraws} confirmed draws • ${report.current.size} current pairs • ${report.alive.size} alive pairs"),
                                style = MaterialTheme.typography.bodySmall)
                            Text(tr("Alive မူ ${report.aliveIds.size} ခု • ဖြတ်မူဆန့်ကျင် ${report.cuts().size} တွဲ",
                                "${report.aliveIds.size} alive rules • ${report.cuts().size} conflicts"),
                                style = MaterialTheme.typography.bodySmall)
                            if (report.aliveIds.isEmpty()) Text(
                                tr("ဆက်တိုက်ကိုက်ညီမှု စမ်းသပ်ချက် မလုံလောက်သေးပါ။",
                                    "No qualifying unbroken-streak rule in available history."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (report.hints.isNotEmpty()) {
                    item {
                        Surface(color = Color.White, shape = RoundedCornerShape(18.dp),
                            border = BorderStroke(1.dp, AppColors.Stone)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(tr("တစ်လုံး / ဘရိတ် တွက်ချက်ရလဒ်", "Single-digit & break calculations"),
                                    fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
                                Text(tr("မူရင်းတွဲစပ်စည်းကမ်း မရှင်းသေးသဖြင့် 2D အတွဲအဖြစ် အတင်းမထုတ်ထားပါ။",
                                    "Unspecified pair conversions are not fabricated."),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                report.hints.take(8).forEach { hint -> HintRow(hint) }
                            }
                        }
                    }
                }
                item {
                    Text(tr("စမ်းသပ်ဗားရှင်း • အပိုင်း ၄/၆/၇/၈/၉ ထဲက တိကျသောမူအချို့ကိုသာ အသက်သွင်းထားသည်။ မူအားလုံးမပါသေးပါ။",
                        "Preview: selected explicit rules from sections 4/6/7/8/9; not the full rule library."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 18.dp))
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
