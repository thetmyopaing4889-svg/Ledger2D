@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.database.BetEntryEntity
import com.myanmar.ledger2d.core.database.BetEntryWithLines
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

// Entry ids are monotonically assigned when a message is accepted.
// Unlike updatedAt, the id is unchanged by later corrections.
internal fun betEntriesInSubmissionOrder(entries: List<BetEntryWithLines>): List<BetEntryWithLines> =
    entries.sortedBy { it.entry.id }

internal fun betEntryAmount(record: BetEntryWithLines): Long =
    record.lines.fold(0L) { sum, line -> Math.addExact(sum, line.amount) }

internal data class BetEntriesTotal(val entries: Int, val digits: Int, val amount: Long)

internal fun betEntriesTotal(entries: List<BetEntryWithLines>): BetEntriesTotal =
    BetEntriesTotal(
        entries = entries.size,
        digits = entries.sumOf { it.lines.size },
        amount = entries.fold(0L) { total, record -> Math.addExact(total, betEntryAmount(record)) },
    )

internal fun voucherSelectedEntryIds(
    entries: List<BetEntryWithLines>,
    customerId: Long,
    date: LocalDate,
    session: DrawSession,
    requestedIds: Collection<Long>,
): List<Long> {
    if (customerId <= 0L) return emptyList()
    val requested = requestedIds.toSet()
    return betEntriesInSubmissionOrder(entries)
        .filter { record ->
            record.entry.id in requested &&
                record.entry.customerId == customerId &&
                record.entry.drawDate == date &&
                record.entry.drawSession == session
        }
        .map { it.entry.id }
}

@Composable
fun CustomerBetEntriesWorkspace(
    vm: LedgerViewModel,
    agentId: Long,
    customerId: Long,
    date: LocalDate,
    session: DrawSession,
    onEditEntry: (Long) -> Unit,
    onVoucherPreview: (List<Long>) -> Unit,
) {
    val customerList by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    val entriesFlow = remember(agentId, customerId, date, session) {
        if (customerId == -1L) vm.agentEntries(agentId, date, session)
        else vm.customerEntries(customerId, date, session)
    }
    val observed by entriesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val entries = remember(observed) { betEntriesInSubmissionOrder(observed) }
    val names = remember(customerList) { customerList.associate { it.id to it.name } }
    var view by rememberSaveable(agentId, customerId) { mutableStateOf("GROUP") }
    var expanded by remember { mutableStateOf<BetEntryWithLines?>(null) }
    var actions by remember { mutableStateOf<BetEntryWithLines?>(null) }
    var pendingDelete by remember { mutableStateOf<BetEntryEntity?>(null) }
    var error by remember { mutableStateOf("") }
    var voucherMode by rememberSaveable(agentId, customerId, date.toString(), session.name) { mutableStateOf(false) }
    var voucherIds by rememberSaveable(agentId, customerId, date.toString(), session.name) {
        mutableStateOf(arrayListOf<Long>())
    }
    val selectedVoucherIds = voucherSelectedEntryIds(entries, customerId, date, session, voucherIds)
    val toggleVoucherId: (Long) -> Unit = { id ->
        if (entries.any { it.entry.id == id }) {
            voucherIds = ArrayList(if (id in voucherIds) voucherIds.filterNot { it == id } else voucherIds + id)
        }
    }

    if (error.isNotBlank()) AlertDialog(
        onDismissRequest = { error = "" },
        title = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းဖျက်၍ မရပါ")) },
        text = { Text(error) },
        confirmButton = { TextButton({ error = "" }) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("OK")) } },
    )
    expanded?.let { record ->
        AlertDialog(
            onDismissRequest = { expanded = null },
            title = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("အကွက်အသေးစိတ်")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(transactionDisplayText(record.entry.inputFormat, record.entry.sourceText), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    HorizontalDivider()
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 330.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(record.lines.sortedBy { it.digit }, key = { it.id }) { line ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(line.digit)
                                Text(line.amount.mmk(), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(record.lines.size.toString() + com.myanmar.ledger2d.core.design.LocalLanguage.current.text(" ကွက်", " digits"))
                        Text(betEntryAmount(record).mmk(), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = { TextButton({ expanded = null }) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ပိတ်မည်")) } },
        )
    }
    actions?.let { record ->
        AlertDialog(
            onDismissRequest = { actions = null },
            title = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းလုပ်ဆောင်ချက်")) },
            text = { Text(transactionDisplayText(record.entry.inputFormat, record.entry.sourceText)) },
            confirmButton = {
                TextButton({ actions = null; onEditEntry(record.entry.id) }) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ပြင်မည်")) }
            },
            dismissButton = {
                TextButton({ actions = null; pendingDelete = record.entry }) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဖျက်မည်")) }
            },
        )
    }
    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းဖျက်မည်လား")) },
            text = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဒီစာရင်းကို အပြီးဖျက်မလား?")) },
            confirmButton = {
                TextButton({
                    vm.deleteBet(entry) { message -> error = message }
                    pendingDelete = null
                }) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("ဖျက်မည်")) }
            },
            dismissButton = { TextButton({ pendingDelete = null }) { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("မလုပ်ပါ")) } },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (customerId > 0L && entries.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    voucherMode = !voucherMode
                    voucherIds = arrayListOf()
                }) {
                    Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text(
                        if (voucherMode) "မလုပ်တော့ပါ" else "ဘောင်ချာထုတ်ရန်",
                        if (voucherMode) "Cancel" else "Create voucher",
                    ))
                }
            }
            if (voucherMode) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text(
                        "စာရင်းများကို ရွေးပါ", "Select transactions",
                    ), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        voucherIds = if (selectedVoucherIds.size == entries.size) arrayListOf()
                            else ArrayList(entries.map { it.entry.id })
                    }) {
                        Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text(
                            if (selectedVoucherIds.size == entries.size) "အားလုံးဖြုတ်" else "အားလုံးရွေး",
                            if (selectedVoucherIds.size == entries.size) "Clear all" else "Select all",
                        ))
                    }
                }
            }
        }
        if (customerId == -1L) {
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer အားလုံး • ") + entries.size + " Entries",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = view == "GROUP",
                    onClick = { view = "GROUP" },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("Customer အလိုက်"), maxLines = 1) },
                )
                SegmentedButton(
                    selected = view == "MIXED",
                    onClick = { view = "MIXED" },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းအားလုံး"), maxLines = 1) },
                )
            }
        }
        if (entries.isEmpty()) {
            EmptyState(com.myanmar.ledger2d.core.design.LocalLanguage.current.translate("စာရင်းမရှိသေးပါ"), "ရွေးထားသော Session အတွက် အတည်ပြုထားသောစာရင်း မရှိသေးပါ")
        } else if (customerId == -1L && view == "GROUP") {
            entries.groupBy { it.entry.customerId }.forEach { (id, customerEntries) ->
                val groupTotal = betEntriesTotal(customerEntries)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(names[id] ?: "Customer #" + id, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            Text(groupTotal.entries.toString() + " Entries",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        customerEntries.forEachIndexed { index, record ->
                            BetEntriesRow(index + 1, record, null,
                                onDetails = { expanded = record }, onActions = { actions = record })
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        TotalsBar("ဒီ Customer စုစုပေါင်း", groupTotal)
                    }
                }
            }
        } else {
            entries.forEachIndexed { index, record ->
                BetEntriesRow(
                    index + 1, record,
                    if (customerId == -1L) names[record.entry.customerId] ?: "Customer #" + record.entry.customerId else null,
                    onDetails = { expanded = record },
                    onActions = { actions = record },
                    selectionMode = voucherMode && customerId > 0L,
                    selected = record.entry.id in selectedVoucherIds,
                    onToggleSelection = { toggleVoucherId(record.entry.id) },
                )
            }
            TotalsBar("စုစုပေါင်း", betEntriesTotal(entries))
        }
        if (voucherMode && customerId > 0L && entries.isNotEmpty()) {
            Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text(
                "ရွေးထားသောစာရင်း ${selectedVoucherIds.size} ခု",
                "${selectedVoucherIds.size} selected",
            ), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Button(
                onClick = { onVoucherPreview(selectedVoucherIds) },
                enabled = selectedVoucherIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(com.myanmar.ledger2d.core.design.LocalLanguage.current.text(
                    "ဘောင်ချာအကြိုကြည့်ရန်", "Preview voucher",
                ))
            }
        }
    }
}

@Composable
private fun BetEntriesRow(
    number: Int,
    record: BetEntryWithLines,
    customer: String?,
    onDetails: () -> Unit,
    onActions: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (selectionMode) onToggleSelection?.invoke() else onDetails() },
            onLongClick = { if (!selectionMode) onActions() },
        ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelection?.invoke() })
            } else {
                Text(number.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (customer != null) Text(customer, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                // Never format, normalize, reorder, or truncate the Messenger message.
                Text(transactionDisplayText(record.entry.inputFormat, record.entry.sourceText), style = MaterialTheme.typography.bodyMedium)
                Text(record.lines.size.toString() + com.myanmar.ledger2d.core.design.LocalLanguage.current.text(" ကွက်", " digits"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(betEntryAmount(record).mmk(), fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
                if (!selectionMode) {
                    IconButton(onClick = onActions) {
                        Icon(Icons.Default.MoreVert, contentDescription = "စာရင်းပြင်ရန် သို့မဟုတ် ဖျက်ရန်")
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsBar(label: String, total: BetEntriesTotal) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppColors.Champagne,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Bold)
                Text(total.entries.toString() + " Entries • " + total.digits + com.myanmar.ledger2d.core.design.LocalLanguage.current.text(" ကွက်", " digits"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(total.amount.mmk(), fontWeight = FontWeight.Black,
                color = AppColors.Primary, textAlign = TextAlign.End)
        }
    }
}
