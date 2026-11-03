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
import com.myanmar.ledger2d.core.database.BetEntryWithLines
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.StatementPeriod
import com.myanmar.ledger2d.core.domain.StatementPeriods
import com.myanmar.ledger2d.core.domain.StatementRange
import com.myanmar.ledger2d.core.model.DrawSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class SettlementPreset { DRAW, DAY, WEEK, MONTH, CUSTOM }

@Composable
private fun SettlementDatePicker(label: String, text: String, onDate: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = text, onValueChange = {}, readOnly = true,
        label = { Text(label) }, singleLine = true,
        trailingIcon = { TextButton(onClick = { open = true }) { Text("ရွေး") } },
        modifier = Modifier.fillMaxWidth(),
    )
    if (open) {
        val initial = runCatching {
            LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("ရွေးမည်") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("မလုပ်ပါ") } },
        ) { DatePicker(state = state) }
    }
}

/** Independent entry point: Customer Dashboard -> full customer money statement JPEG. */
@Composable
fun CustomerSettlementJpegScreen(vm: LedgerViewModel, onBack: () -> Unit) {
    val language = LocalLanguage.current
    val agents by vm.agents.collectAsStateWithLifecycle()
    var agentId by rememberSaveable { mutableLongStateOf(language.defaultAgentId) }
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    var customerId by rememberSaveable { mutableLongStateOf(-1L) }
    var preset by rememberSaveable { mutableStateOf(SettlementPreset.WEEK) }
    var anchor by rememberSaveable { mutableStateOf(language.selectedDate) }
    var fromDate by rememberSaveable { mutableStateOf(language.selectedDate) }
    var toDate by rememberSaveable { mutableStateOf(language.selectedDate) }
    var draw by rememberSaveable { mutableStateOf(DrawSession.MORNING.name) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<CustomerSettlementSource?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(agents) {
        if (agents.isNotEmpty() && agents.none { it.id == agentId }) {
            agentId = agents.first().id
            customerId = -1L
        }
    }
    LaunchedEffect(agentId, customers) {
        if (customerId > 0L && customers.none { it.id == customerId }) customerId = -1L
    }

    if (source != null) {
        CustomerSettlementJpegPreview(source!!, onBack = { source = null })
        return
    }

    val anchorDate = runCatching { LocalDate.parse(anchor) }.getOrNull()
    val customStart = runCatching { LocalDate.parse(fromDate) }.getOrNull()
    val customEnd = runCatching { LocalDate.parse(toDate) }.getOrNull()
    val range: StatementRange? =
        if (anchorDate == null || customStart == null || customEnd == null) null
        else runCatching {
            when (preset) {
                SettlementPreset.DRAW, SettlementPreset.DAY ->
                    StatementRange(anchorDate, anchorDate)
                SettlementPreset.WEEK ->
                    StatementPeriods.range(StatementPeriod.WEEK, anchorDate, customStart, customEnd)
                SettlementPreset.MONTH ->
                    StatementPeriods.range(StatementPeriod.MONTH, anchorDate, customStart, customEnd)
                SettlementPreset.CUSTOM -> StatementRange(customStart, customEnd)
            }
        }.getOrNull()
    val chosenCustomer = customers.firstOrNull { it.id == customerId }
    val selectedSession = if (preset == SettlementPreset.DRAW)
        DrawSession.entries.firstOrNull { it.name == draw } else null

    AppScaffold(
        title = language.text("စာရင်းရှင်းတမ်းထုတ်ရန်", "Export customer statement"),
        onBack = onBack,
        includeQuickEntryFab = false,
    ) { padding ->
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(language.text("ထိုးသားနှင့် စာရင်းကာလ ရွေးချယ်ရန်",
                    "Choose customer and period"),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                ScopeDropdown(
                    language.text("ဒိုင်ရွေးရန်", "Select agent"),
                    agents.firstOrNull { it.id == agentId }?.name ?: "—",
                    agents.map { it.id to it.name }, agentId,
                ) { agentId = it; customerId = -1L; error = null }
            }
            item {
                ScopeDropdown(
                    language.text("ထိုးသားရွေးရန်", "Select customer"),
                    chosenCustomer?.name ?: language.text("ထိုးသားရွေးပါ", "Choose customer"),
                    customers.map { it.id to it.name }, customerId,
                    enabled = agentId > 0L,
                ) { customerId = it; error = null }
            }
            item {
                Text(language.text("စာရင်းရှင်းမည့်ကာလ", "Statement period"),
                    fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(
                        SettlementPreset.DRAW to language.text("တစ်ကြိမ်စာ", "One draw"),
                        SettlementPreset.DAY to language.text("တစ်ရက်စာ", "One day"),
                        SettlementPreset.WEEK to language.text("တစ်ပတ်စာ", "One week"),
                        SettlementPreset.MONTH to language.text("တစ်လစာ", "One month"),
                        SettlementPreset.CUSTOM to language.text("စိတ်ကြိုက်", "Custom"),
                    ).forEach { (option, label) ->
                        FilterChip(preset == option, onClick = { preset = option; error = null },
                            label = { Text(label) })
                    }
                }
            }
            if (preset == SettlementPreset.CUSTOM) {
                item { SettlementDatePicker(language.text("စရက်", "Start date"), fromDate) {
                    fromDate = it; error = null
                } }
                item { SettlementDatePicker(language.text("ဆုံးရက်", "End date"), toDate) {
                    toDate = it; error = null
                } }
            } else {
                item {
                    SettlementDatePicker(language.text("ရက်စွဲရွေးရန်", "Select date"), anchor) {
                        anchor = it; error = null
                    }
                }
            }
            if (preset == SettlementPreset.DRAW) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DrawSession.entries.forEach { session ->
                            FilterChip(
                                selected = draw == session.name,
                                onClick = { draw = session.name },
                                label = { Text(language.text(
                                    if (session == DrawSession.MORNING) "မနက်အကြိမ်" else "ညနေအကြိမ်",
                                    if (session == DrawSession.MORNING) "Morning" else "Evening",
                                )) },
                            )
                        }
                    }
                }
            }
            item {
                if (range != null) {
                    Text(
                        if (range.start == range.end) range.start.toString()
                        else "${range.start} မှ ${range.end} အထိ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else Text(language.text("ရက်စွဲကာလကို ပြန်စစ်ပါ", "Check the date range"),
                    color = MaterialTheme.colorScheme.error)
            }
            if (error != null) item {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            item {
                Button(
                    enabled = !busy && chosenCustomer != null && range != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    onClick = {
                        val customer = chosenCustomer ?: return@Button
                        val requestedRange = range ?: return@Button
                        val requestedSession = selectedSession
                        busy = true
                        error = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val records = vm.customerEntries(customer.id).first()
                                        .filter { requestedRange.contains(it.entry.drawDate) &&
                                            (requestedSession == null || it.entry.drawSession == requestedSession) }
                                    val winners = records.map { it.entry.drawDate to it.entry.drawSession }
                                        .distinct().associateWith { key ->
                                            vm.winner(key.first, key.second).first()?.digit
                                        }.mapNotNull { (key, winner) ->
                                            winner?.let { key to it }
                                        }.toMap()
                                    val prepared = CustomerSettlementSource(
                                        customer.name, requestedRange, records, winners,
                                    )
                                    CustomerSettlementCalculator.build(prepared, null)
                                    prepared
                                }
                            }
                            result.onSuccess { source = it }
                                .onFailure { error = it.message ?: "စာရင်းရှင်းတမ်း ပြင်ဆင်မရပါ။" }
                            busy = false
                        }
                    },
                ) {
                    if (busy) CircularProgressIndicator(
                        Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    ) else Text(language.text("JPEG စာရင်းရှင်းတမ်း အကြိုကြည့်ရန်", "Preview statement JPEG"))
                }
            }
            item {
                Text(
                    language.text(
                        "အတည်ပြုပြီးသော ပေါက်ဂဏန်းများဖြင့်သာ ငွေရှင်းတမ်းထုတ်မည်။ " +
                            "ကော်မရှင်ပြန်ပေးမှုကို Preview မှာ သီးခြားရွေးနိုင်ပြီး မူလစာရင်းသို့ မသိမ်းပါ။",
                        "Uses only confirmed winning results. Optional customer rebate is applied " +
                            "only to this JPEG, never to the ledger.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CustomerSettlementJpegPreview(source: CustomerSettlementSource, onBack: () -> Unit) {
    val language = LocalLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var commissionEnabled by rememberSaveable { mutableStateOf(false) }
    var percentText by rememberSaveable { mutableStateOf("5") }
    val basisPoints = if (commissionEnabled) customerRebateBasisPoints(percentText) else null
    val invalidPercent = commissionEnabled && basisPoints == null
    val document = remember(source, commissionEnabled, basisPoints) {
        if (invalidPercent) null else runCatching {
            CustomerSettlementCalculator.build(source, basisPoints)
        }.getOrNull()
    }
    val renderer by produceState<CustomerSettlementJpegRenderer?>(null, document) {
        if (document != null) {
            value = withContext(Dispatchers.Default) { CustomerSettlementJpegRenderer(document) }
        }
    }
    val pager = rememberPagerState(pageCount = { renderer?.pageCount ?: 1 })
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val exportNow: () -> Unit = {
        val current = renderer
        if (!exporting && current != null && !invalidPercent) {
            exporting = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { CustomerSettlementJpegExporter.save(context.applicationContext, current) }
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
            title = { Text("JPEG Export") }, text = { Text(content) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
    AppScaffold(
        title = language.text("ငွေစာရင်းရှင်းတမ်း အကြိုကြည့်ရန်", "Statement preview"),
        onBack = { if (!exporting) onBack() },
        includeQuickEntryFab = false,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = commissionEnabled, onCheckedChange = {
                    commissionEnabled = it
                })
                Text(language.text("Customer ကို ကော်မရှင်ပြန်ပေးမည်",
                    "Pay customer a commission rebate"), fontWeight = FontWeight.Bold)
            }
            if (commissionEnabled) {
                OutlinedTextField(
                    percentText, onValueChange = { percentText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(language.text("ကော်မရှင် (%)", "Commission (%)")) },
                    singleLine = true, isError = invalidPercent,
                )
                if (invalidPercent) Text(
                    language.text("0 မှ 100% အတွင်း၊ ဒသမ ၂ နေရာအထိ ထည့်ပါ။",
                        "Enter 0–100%, up to two decimal places."),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (renderer != null && !invalidPercent) {
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                        .background(Color(0xFFEDE7E5)),
                ) { index ->
                    val bitmap by produceState<Bitmap?>(null, renderer, index) {
                        val image = withContext(Dispatchers.Default) {
                            renderer!!.render(index)
                        }
                        value = image
                        awaitDispose { value = null; image.recycle() }
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
                    if (invalidPercent) Text(language.text("ကော်မရှင် % မှန်ကန်စွာ ထည့်ပါ။",
                        "Enter a valid commission percentage."))
                    else CircularProgressIndicator()
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = !exporting && pager.currentPage > 0,
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                ) { Text(language.text("ရှေ့", "Previous")) }
                Text("${pager.currentPage + 1} / ${renderer?.pageCount ?: 1}")
                TextButton(
                    enabled = !exporting && renderer != null &&
                        pager.currentPage < renderer!!.pageCount - 1,
                    onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                ) { Text(language.text("နောက်", "Next")) }
            }
            Button(
                enabled = !exporting && renderer != null && !invalidPercent,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) != PackageManager.PERMISSION_GRANTED
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
