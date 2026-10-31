@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.StatementPeriod
import com.myanmar.ledger2d.core.domain.StatementPeriods
import com.myanmar.ledger2d.core.domain.StatementRange
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal fun DrawSession.reviewLabel() =
    if (this == DrawSession.MORNING) "မနက်အကြိမ်" else "ညနေအကြိမ်"

internal fun LocalDate.reviewDate() = format(DateTimeFormatter.ofPattern("dd MMM yyyy"))

/** Unlike the bet-entry date field, a report range must not skip or shift weekends. */
@Composable
internal fun ReviewDatePicker(value: String, title: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = runCatching { LocalDate.parse(value).reviewDate() }.getOrDefault(value),
        onValueChange = {}, modifier = modifier.fillMaxWidth(), readOnly = true,
        singleLine = true, label = { Text(title) },
        trailingIcon = { TextButton(onClick = { open = true }) { Text("ရွေး") } },
    )
    if (open) {
        val selected = runCatching { LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
        val picker = rememberDatePickerState(initialSelectedDateMillis = selected)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let {
                        onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("ရွေးမည်") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("မလုပ်ပါ") } },
        ) { DatePicker(picker) }
    }
}

/**
 * A single selection screen shared by Report and Analysis.
 * The result screen has NO dropdowns, date pickers, explanations or quick-entry FAB.
 * Going back from a result keeps all selections intact.
 */
@Composable
fun CustomerReviewWorkspace(
    vm: LedgerViewModel,
    mode: String,
    onBack: () -> Unit,
    initialAgentId: Long? = null,
    initialCustomerId: Long? = null,
) {
    require(mode == "report" || mode == "analysis")
    val lang = LocalLanguage.current
    val agents by vm.agents.collectAsStateWithLifecycle()
    val linked by vm.customer(initialCustomerId ?: 0L).collectAsStateWithLifecycle(initialValue = null)
    var agentId by rememberSaveable(mode, initialAgentId, initialCustomerId) {
        mutableLongStateOf(initialAgentId ?: lang.defaultAgentId)
    }
    var customerId by rememberSaveable(mode, initialCustomerId) {
        mutableLongStateOf(initialCustomerId ?: -1L)
    }
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    var period by rememberSaveable(mode) { mutableStateOf(StatementPeriod.DAY) }
    var dateText by rememberSaveable(mode) { mutableStateOf(lang.selectedDate) }
    var startText by rememberSaveable(mode) { mutableStateOf(lang.selectedDate) }
    var endText by rememberSaveable(mode) { mutableStateOf(lang.selectedDate) }
    var drawChoice by rememberSaveable(mode) { mutableStateOf("both") }
    var resultPage by rememberSaveable(mode) { mutableStateOf(false) }

    LaunchedEffect(linked?.id, linked?.agentId) {
        linked?.let { agentId = it.agentId; customerId = it.id }
    }
    LaunchedEffect(agents, agentId) {
        if (agents.isNotEmpty() && (agentId <= 0L || agents.none { it.id == agentId })) {
            agentId = agents.first().id
            customerId = -1L
        }
    }
    LaunchedEffect(agentId, customers, customerId) {
        if (customerId > 0L && customers.isNotEmpty() && customers.none { it.id == customerId }) customerId = -1L
    }

    val selectedDate = runCatching { LocalDate.parse(dateText) }.getOrNull()
    val range = if (mode == "report") {
        val start = runCatching { LocalDate.parse(startText) }.getOrNull()
        val end = runCatching { LocalDate.parse(endText) }.getOrNull()
        if (selectedDate != null && start != null && end != null) {
            runCatching { StatementPeriods.range(period, selectedDate, start, end) }.getOrNull()
        } else null
    } else selectedDate?.let { StatementRange(it, it) }
    val draw = if (mode == "analysis") {
        if (drawChoice == "pm") DrawSession.EVENING else DrawSession.MORNING
    } else when (drawChoice) {
        "am" -> DrawSession.MORNING
        "pm" -> DrawSession.EVENING
        else -> null
    }
    val canView = agentId > 0L && customerId != 0L && range != null && (mode != "analysis" || draw != null)
    val selectedAgentName = agents.firstOrNull { it.id == agentId }?.name ?: "Agent"
    val selectedCustomerName = if (customerId == -1L) "Customer အားလုံး"
        else customers.firstOrNull { it.id == customerId }?.name ?: "Customer"

    AppScaffold(
        title = if (resultPage) if (mode == "report") "စာရင်းရှင်းတမ်း" else "အရှုံးအမြတ်သုံးသပ်ချက်"
                else "စာရင်းရွေးချယ်ရန်",
        onBack = { if (resultPage) resultPage = false else onBack() },
        includeQuickEntryFab = false,
    ) { padding ->
        if (!resultPage) {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Text(if (mode == "report") "စာရင်းရှင်းတမ်း ရွေးချယ်ရန်" else "သုံးသပ်မည့်စာရင်း ရွေးချယ်ရန်",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    ScopeDropdown("Agent", selectedAgentName, agents.map { it.id to it.name }, agentId) {
                        agentId = it; customerId = -1L
                    }
                }
                item {
                    ScopeDropdown("ထိုးသား", selectedCustomerName,
                        listOf(-1L to "Customer အားလုံး") + customers.map { it.id to it.name },
                        customerId, enabled = agentId > 0L) { customerId = it }
                }
                if (mode == "report") {
                    item {
                        Text("အချိန်ကာလ", fontWeight = FontWeight.SemiBold)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            listOf(
                                StatementPeriod.DAY to "ရက်", StatementPeriod.WEEK to "ပတ်",
                                StatementPeriod.MONTH to "လ", StatementPeriod.YEAR to "နှစ်",
                                StatementPeriod.CUSTOM to "စိတ်ကြိုက်",
                            ).forEach { (p, title) ->
                                FilterChip(period == p, onClick = { period = p }, label = { Text(title) })
                            }
                        }
                    }
                }
                item {
                    if (mode == "report" && period == StatementPeriod.CUSTOM) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ReviewDatePicker(startText, "စရက်", Modifier.weight(1f)) { startText = it }
                            ReviewDatePicker(endText, "ဆုံးရက်", Modifier.weight(1f)) { endText = it }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                if (selectedDate != null) dateText =
                                    StatementPeriods.move(if (mode == "analysis") StatementPeriod.DAY else period, selectedDate, -1).toString()
                            }) { Icon(Icons.Default.ChevronLeft, "ယခင်ရက်စွဲ") }
                            Text(
                                if (range != null) {
                                    if (range.start == range.end) range.start.reviewDate()
                                    else "${range.start.reviewDate()} – ${range.end.reviewDate()}"
                                } else "ရက်စွဲမှားနေသည်",
                                Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            )
                            IconButton(onClick = {
                                if (selectedDate != null) dateText =
                                    StatementPeriods.move(if (mode == "analysis") StatementPeriod.DAY else period, selectedDate, 1).toString()
                            }) { Icon(Icons.Default.ChevronRight, "နောက်ရက်စွဲ") }
                        }
                        ReviewDatePicker(dateText, "ရက်စွဲ", onChange = { dateText = it })
                    }
                }
                item {
                    Text("အကြိမ်ရွေးရန်", fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val choices = if (mode == "report")
                            listOf("both" to "နှစ်ကြိမ်လုံး", "am" to "မနက်အကြိမ်", "pm" to "ညနေအကြိမ်")
                        else listOf("am" to "မနက်အကြိမ်", "pm" to "ညနေအကြိမ်")
                        choices.forEach { (id, title) ->
                            FilterChip(
                                selected = (if (mode == "analysis" && drawChoice == "both") "am" else drawChoice) == id,
                                onClick = { drawChoice = id }, label = { Text(title) },
                            )
                        }
                    }
                }
                if (range == null) item {
                    Text("ရွေးထားတဲ့ရက်စွဲတွေကို ပြန်စစ်ပါ", color = MaterialTheme.colorScheme.error)
                }
                item {
                    Button(
                        onClick = { resultPage = true },
                        enabled = canView,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Text(if (mode == "report") "စာရင်းကြည့်မည်" else "သုံးသပ်ချက်ကြည့်မည်")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        } else if (canView && range != null) {
            val subtitle = "$selectedAgentName • $selectedCustomerName"
            if (mode == "report") {
                CustomerStatementResults(
                    vm, agentId, customerId, period, range, draw,
                    subtitle, onReselect = { resultPage = false },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
                CustomerRiskReviewResults(
                    vm, agentId, customerId, range.start, draw ?: DrawSession.MORNING,
                    subtitle, onReselect = { resultPage = false },
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
        }
    }
}
