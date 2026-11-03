@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.main

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.StatementPeriod
import com.myanmar.ledger2d.core.domain.StatementPeriods
import com.myanmar.ledger2d.core.domain.StatementRange
import com.myanmar.ledger2d.core.model.DrawSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class AgentJpegPreset { DRAW, DAY, WEEK, MONTH, CUSTOM }

@Composable
private fun AgentJpegDatePicker(label: String, date: String, enabled: Boolean = true, onDate: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = date, onValueChange = {}, readOnly = true, enabled = enabled,
        label = { Text(label) }, singleLine = true,
        trailingIcon = { TextButton(enabled = enabled, onClick = { open = true }) { Text("ရွေး") } },
        modifier = Modifier.fillMaxWidth(),
    )
    if (open) {
        val initial = runCatching {
            LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(enabled = enabled, onClick = {
                    picker.selectedDateMillis?.let {
                        onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("ရွေးမည်") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("မလုပ်ပါ") } },
        ) { DatePicker(state = picker) }
    }
}

/** Agent Dashboard -> one-agent JPEG: customer TOTAL rows and mandatory commission summary. */
@Composable
fun AgentSettlementJpegScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val language = LocalLanguage.current
    val agents by vm.agents.collectAsStateWithLifecycle()
    var agentId by rememberSaveable { mutableLongStateOf(language.defaultAgentId) }
    var preset by rememberSaveable { mutableStateOf(AgentJpegPreset.WEEK) }
    var anchor by rememberSaveable { mutableStateOf(language.selectedDate) }
    var fromDate by rememberSaveable { mutableStateOf(language.selectedDate) }
    var toDate by rememberSaveable { mutableStateOf(language.selectedDate) }
    var draw by rememberSaveable { mutableStateOf(DrawSession.MORNING.name) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var document by remember { mutableStateOf<AgentSettlementDocument?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(agents) {
        if (agents.isNotEmpty() && agents.none { it.id == agentId }) agentId = agents.first().id
    }
    if (document != null) {
        val previewSession = if (preset == AgentJpegPreset.DRAW)
            DrawSession.entries.firstOrNull { it.name == draw } else null
        AgentSettlementJpegPreview(vm, agentId, previewSession, document!!,
            onBack = { document = null })
        return
    }

    val anchorDate = runCatching { LocalDate.parse(anchor) }.getOrNull()
    val from = runCatching { LocalDate.parse(fromDate) }.getOrNull()
    val to = runCatching { LocalDate.parse(toDate) }.getOrNull()
    val range = if (anchorDate == null || from == null || to == null) null else
        runCatching {
            when (preset) {
                AgentJpegPreset.DRAW, AgentJpegPreset.DAY -> StatementRange(anchorDate, anchorDate)
                AgentJpegPreset.WEEK -> StatementPeriods.range(StatementPeriod.WEEK, anchorDate, from, to)
                AgentJpegPreset.MONTH -> StatementPeriods.range(StatementPeriod.MONTH, anchorDate, from, to)
                AgentJpegPreset.CUSTOM -> StatementRange(from, to)
            }
        }.getOrNull()
    val selectedSession = if (preset == AgentJpegPreset.DRAW) {
        DrawSession.entries.firstOrNull { it.name == draw }
    } else null
    val selectedAgent = agents.firstOrNull { it.id == agentId }

    AppScaffold(
        title = language.text("ဒိုင်စာရင်းရှင်းတမ်းထုတ်ရန်", "Export agent statement"),
        onBack = onBack,
        includeQuickEntryFab = false,
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(language.text("ဒိုင်နှင့် စာရင်းကာလ ရွေးချယ်ရန်",
                    "Choose agent and statement period"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                ScopeDropdown(
                    language.text("ဒိုင်ရွေးရန်", "Select agent"),
                    selectedAgent?.name ?: language.text("ဒိုင်ရွေးပါ", "Choose agent"),
                    agents.map { it.id to it.name }, agentId, enabled = !busy,
                ) { agentId = it; error = null }
            }
            item {
                Text(language.text("စာရင်းရှင်းမည့်ကာလ", "Statement period"),
                    fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(
                        AgentJpegPreset.DRAW to language.text("တစ်ကြိမ်စာ", "One draw"),
                        AgentJpegPreset.DAY to language.text("တစ်ရက်စာ", "One day"),
                        AgentJpegPreset.WEEK to language.text("တစ်ပတ်စာ", "One week"),
                        AgentJpegPreset.MONTH to language.text("တစ်လစာ", "One month"),
                        AgentJpegPreset.CUSTOM to language.text("စိတ်ကြိုက်", "Custom"),
                    ).forEach { (option, label) ->
                        FilterChip(preset == option, onClick = { preset = option; error = null },
                            enabled = !busy, label = { Text(label) })
                    }
                }
            }
            if (preset == AgentJpegPreset.CUSTOM) {
                item { AgentJpegDatePicker(language.text("စရက်", "Start date"), fromDate, enabled = !busy) {
                    fromDate = it; error = null
                } }
                item { AgentJpegDatePicker(language.text("ဆုံးရက်", "End date"), toDate, enabled = !busy) {
                    toDate = it; error = null
                } }
            } else {
                item { AgentJpegDatePicker(language.text("ရက်စွဲရွေးရန်", "Select date"), anchor, enabled = !busy) {
                    anchor = it; error = null
                } }
            }
            if (preset == AgentJpegPreset.DRAW) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DrawSession.entries.forEach { session ->
                            FilterChip(selected = draw == session.name,
                                onClick = { draw = session.name }, enabled = !busy,
                                label = { Text(language.text(
                                    if (session == DrawSession.MORNING) "မနက်အကြိမ်" else "ညနေအကြိမ်",
                                    if (session == DrawSession.MORNING) "Morning" else "Evening",
                                )) })
                        }
                    }
                }
            }
            item {
                Text(
                    when {
                        range == null -> language.text("ရက်စွဲကာလကို ပြန်စစ်ပါ", "Check date range")
                        range.start == range.end -> range.start.toString()
                        else -> "${range.start} မှ ${range.end} အထိ"
                    },
                    color = if (range == null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
            item {
                Button(
                    enabled = !busy && selectedAgent != null && range != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    onClick = {
                        val chosen = selectedAgent ?: return@Button
                        val dates = range ?: return@Button
                        busy = true
                        error = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    vm.agentSettlementJpeg(chosen.id, chosen.name, dates, selectedSession)
                                }
                            }
                            result.onSuccess { document = it }
                                .onFailure { error = it.message ?: "ဒိုင်စာရင်းရှင်းတမ်း ပြင်ဆင်မရပါ။" }
                            busy = false
                        }
                    },
                ) {
                    if (busy) CircularProgressIndicator(
                        Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    ) else Text(language.text("JPEG စာရင်းရှင်းတမ်း အကြိုကြည့်ရန်",
                        "Preview agent statement JPEG"))
                }
            }
            item {
                Text(language.text(
                    "ထိုးသားတစ်ယောက်ချင်း၏ ကာလ TOTAL ကိုပြပြီး Agent Report မှ " +
                        "သိမ်းထားသောကော်မရှင်ကို အသုံးပြုမည်။ အတည်ပြုပေါက်ဂဏန်းမရှိလျှင် မထုတ်ပါ။",
                    "Shows one customer TOTAL per period. Uses saved Agent Report commission; " +
                        "all winners must be final.",
                ), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AgentSettlementJpegPreview(
    vm: LedgerViewModel,
    agentId: Long,
    selectedSession: DrawSession?,
    document: AgentSettlementDocument,
    onBack: () -> Unit,
) {
    val language = LocalLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Failed page preparation must show an error instead of crashing Compose.
    val preparation by produceState<Pair<AgentSettlementJpegRenderer?, String?>?>(null, document) {
        val prepared = withContext(Dispatchers.Default) {
            runCatching { AgentSettlementJpegRenderer(document) }
        }
        value = prepared.fold(
            onSuccess = { it to null },
            onFailure = { null to (it.message ?: "JPEG စာမျက်နှာ ပြင်ဆင်မရပါ။") },
        )
    }
    val renderer = preparation?.first
    val pager = rememberPagerState(pageCount = { renderer?.pageCount ?: 1 })
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val exportNow: () -> Unit = {
        val ready = renderer
        if (!exporting && ready != null) {
            exporting = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        // The user may edit/delete bets, change a commission or correct a
                        // result while preview is open. Never export stale money values.
                        val currentAgentName = vm.agents.value.firstOrNull { it.id == agentId }?.name
                            ?: error("ရွေးထားသောဒိုင် မရှိတော့ပါ။")
                        val latest = vm.agentSettlementJpeg(
                            agentId, currentAgentName, document.range, selectedSession,
                        )
                        check(latest == document) {
                            "စာရင်း သို့မဟုတ် ပေါက်ဂဏန်း ပြောင်းထားပါသည်။ " +
                                "ပြန်ရွေးပြီး Preview အသစ်ထုတ်ပါ။"
                        }
                        AgentSettlementJpegExporter.save(context.applicationContext, ready)
                    }
                }
                exporting = false
                message = result.fold(
                    onSuccess = { language.text(
                        "JPEG ပုံ $it ပုံကို Gallery › Cherry 2D Ledger တွင် သိမ်းပြီးပါပြီ။",
                        "Saved $it JPEG images to Gallery › Cherry 2D Ledger.",
                    ) },
                    onFailure = { language.text(
                        "JPEG သိမ်းမရပါ။ ${it.message ?: "ပြန်စမ်းကြည့်ပါ။"}",
                        "Export failed: ${it.message ?: "Please retry."}",
                    ) },
                )
            }
        }
    }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) exportNow()
        else message = language.text("Gallery သိမ်းရန် Storage permission လိုအပ်ပါသည်။",
            "Storage permission is required.")
    }
    BackHandler(enabled = exporting) {}
    message?.let { content ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("JPEG Export") },
            text = { Text(content) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
    AppScaffold(
        title = language.text("ဒိုင်စာရင်းရှင်းတမ်း အကြိုကြည့်ရန်", "Agent statement preview"),
        onBack = { if (!exporting) onBack() }, includeQuickEntryFab = false,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (renderer != null) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFFEDE7E5)),
                ) { index ->
                    val ready = renderer
                    val bitmap by produceState<Bitmap?>(null, ready, index) {
                        if (ready != null) {
                            val image = withContext(Dispatchers.Default) { ready.render(index) }
                            value = image
                            awaitDispose { value = null; image.recycle() }
                        }
                    }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (bitmap == null) CircularProgressIndicator()
                        else Image(
                            bitmap!!.asImageBitmap(),
                            contentDescription = "JPEG ${index + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (preparation == null) CircularProgressIndicator()
                    else Text(preparation?.second ?: "JPEG ပြင်ဆင်မရပါ။")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = !exporting && pager.currentPage > 0,
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) {
                    Text(language.text("ရှေ့", "Previous"))
                }
                Text("${pager.currentPage + 1} / ${renderer?.pageCount ?: 1}")
                TextButton(
                    enabled = !exporting && renderer != null &&
                        pager.currentPage < renderer!!.pageCount - 1,
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                ) { Text(language.text("နောက်", "Next")) }
            }
            Button(
                enabled = !exporting && renderer != null,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        ContextCompat.checkSelfPermission(context,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                    ) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    else exportNow()
                },
            ) {
                if (exporting) CircularProgressIndicator(
                    Modifier.size(21.dp), color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                ) else Text("Export JPEG — Gallery")
            }
        }
    }
}
