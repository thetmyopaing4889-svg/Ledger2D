package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryProspectiveTrialClock
import com.myanmar.ledger2d.core.domain.CherryProspectiveTrialJournal
import com.myanmar.ledger2d.core.model.DrawIdentity
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

private val discoveryGreen = Color(0xFF16854A)
private val discoveryEdge = Color(0xFFF4CFDA)

private fun discoveryKindLabel(kind: CherryDiscoveryKind, en: Boolean): String =
    when (kind) {
        CherryDiscoveryKind.LONE -> if (en) "LONE" else "လုံးပိုင်"
        CherryDiscoveryKind.WAM -> if (en) "WAM" else "ဝမ်းချိန်း"
        CherryDiscoveryKind.BREAK -> if (en) "BREAK" else "ဘရိတ်"
    }

private fun discoveryError(error: Exception, en: Boolean): String =
    (if (en) "Action failed: " else "လုပ်ဆောင်မှု မအောင်မြင်ပါ — ") +
        (error.message ?: if (en) "Please retry." else "ထပ်မံစစ်ဆေးပါ။")

/**
 * Separate discovery workspace BELOW existing Math / Gemini / Champion.
 * Merely opening Math NEVER starts formula discovery or settles a result.
 * All service calculations and storage are dispatched off Main.
 */
@Composable
internal fun CherryDiscoveryPanel(
    service: CherryDiscoveryEngineService,
    target: DrawIdentity,
    en: Boolean,
) {
    var kind by remember(target) { mutableStateOf(CherryDiscoveryKind.LONE) }
    var uiState by remember(target) { mutableStateOf(CherryDiscoveryUiState()) }
    var journal by remember(target) { mutableStateOf(CherryProspectiveTrialJournal.empty()) }
    var currentEvidence by remember(target) {
        mutableStateOf<CherryDiscoveryFormulaEvidence?>(null)
    }
    var lockEvidence by remember(target) {
        mutableStateOf<Map<CherryDiscoveryKind, CherryDiscoveryFormulaEvidence>>(emptyMap())
    }
    var liveScanPosition by remember(target) { mutableStateOf<Int?>(null) }
    var previewLoading by remember(target) { mutableStateOf(false) }
    var loading by remember(target) { mutableStateOf(true) }
    var busy by remember(target) { mutableStateOf(false) }
    var running by remember(target) { mutableStateOf<Job?>(null) }
    var message by remember(target) { mutableStateOf<String?>(null) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    var confirmLock by remember(target) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(service, target) {
        onDispose { running?.cancel() }
    }

    LaunchedEffect(service, target) {
        loading = true
        try {
            // Only restores an existing search checkpoint; never searches.
            uiState = uiState.restore(service.savedCursors(target))
            journal = service.trials()
            error = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = discoveryError(failure, en)
        } finally {
            loading = false
        }
    }

    val cursors = uiState.cursors
    val choices = uiState.selectedIds
    val cursor = cursors[kind]
    val selectedId = choices[kind]
    val displayedId = uiState.displayId(kind)
    LaunchedEffect(service, target, kind, displayedId, cursor?.nextPosition) {
        currentEvidence = null
        previewLoading = displayedId != null
        if (displayedId != null) {
            try {
                currentEvidence = service.previewEvidence(target, kind, displayedId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = discoveryError(failure, en)
            } finally {
                previewLoading = false
            }
        }
    }

    val lock = journal.lockedTrials().firstOrNull { it.target == target }
    val result = journal.verifiedResults().firstOrNull { it.target == target }
    val canLockNow = Instant.now().isBefore(CherryProspectiveTrialClock.cutoff(target))
    val picked = CherryDiscoveryKind.entries.associateWith { choices[it] }
    val someSelected = picked.values.any { it != null }
    val selectedValid = picked.all { (category, id) ->
        id == null || id in cursors[category]?.offeredIds.orEmpty()
    }
    val selectable = !busy && !loading && lock == null && canLockNow &&
        someSelected && selectedValid
    val offered = cursor?.offeredIds.orEmpty()
        .sortedBy { cursor?.offeredPositions?.get(it) ?: Int.MAX_VALUE }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, discoveryEdge),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text(
                if (en) "Formula Discovery" else "Formula အသစ် ရှာဖွေရန်",
                color = AppColors.PrimaryDeep,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (en)
                    "Results-first research. Search only when you tap. This never replaces Gemini or Champion."
                else
                    "အတည်ပြု Result တွေမှ Formula အသစ်ရှာမယ်။ ခလုတ်နှိပ်မှ ရှာမယ်။ Gemini/Champion ကို မပြောင်းဘူး။",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${target.date} · " +
                    if (target.session.name == "MORNING") {
                        if (en) "AM" else "မနက်"
                    } else {
                        if (en) "PM" else "ညနေ"
                    },
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                CherryDiscoveryKind.entries.forEach { item ->
                    FilterChip(
                        selected = kind == item,
                        onClick = { kind = item; message = null },
                        enabled = !busy && !loading,
                        label = { Text(discoveryKindLabel(item, en)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp), color = discoveryGreen, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (en) "Restoring saved progress..." else "ရှာဖွေမှုမှတ်တမ်း ပြန်ဖတ်နေသည်…",
                        style = MaterialTheme.typography.bodySmall)
                }
            } else {
                val completedStages = cursor?.completedStages ?: 0
                val examined = (liveScanPosition ?: cursor?.nextPosition ?: 0)
                    .coerceIn(0, 828)
                Text(
                    if (en) "Stages $completedStages/4 · Scan $examined/828 · Offered ${offered.size}"
                    else "အဆင့် $completedStages/4 · စစ်ပြီး $examined/828 · တွေ့ပြီး ${offered.size} မျိုး",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppColors.PrimaryDeep,
                )
                LinearProgressIndicator(
                    progress = { (examined / 828f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = discoveryGreen,
                    trackColor = Color(0xFFE8F3EB),
                )
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            Modifier.size(22.dp), color = discoveryGreen, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (en) "Working off the UI thread…" else "နောက်ခံစနစ်မှာ ရှာဖွေနေသည်…",
                            style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { running?.cancel() }) {
                            Text(if (en) "Cancel" else "ရပ်မည်")
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            if (busy) return@Button
                            val requestedKind = kind
                            busy = true
                            error = null
                            message = null
                            liveScanPosition = cursors[requestedKind]?.nextPosition ?: 0
                            running = scope.launch {
                                val positions = Channel<Int>(Channel.CONFLATED)
                                val observing = launch {
                                    for (position in positions) liveScanPosition = position
                                }
                                try {
                                    val next = service.searchMore(
                                        target, requestedKind,
                                        onProgress = { position -> positions.trySend(position) })
                                    uiState = uiState.searched(requestedKind, next.cursor)
                                    if (next.cursor.exhausted) {
                                        message = if (en) "All formula alternatives checked."
                                            else "ရှာဖွေစရာ Formula အားလုံး စစ်ပြီးပြီ။"
                                    } else if (next.cursor.blocked) {
                                        message = if (en) "Previous completed draw is missing."
                                            else "အရင်အကြိမ် အတည်ပြု Result မပြည့်စုံသေးပါ။"
                                    } else if (next.cursor.newCandidate != null) {
                                        message = if (en) "A new distinct formula was found."
                                            else "အလုပ်လုပ်ပုံမတူသော Formula အသစ် ရှာတွေ့ပြီ။"
                                    }
                                } catch (_: CancellationException) {
                                    // No checkpoint changes on a cancelled operation.
                                } catch (failure: Exception) {
                                    error = discoveryError(failure, en)
                                } finally {
                                    positions.close()
                                    observing.cancel()
                                    liveScanPosition = null
                                    busy = false
                                    running = null
                                }
                            }
                        },
                        enabled = !busy && !cursor.let { it?.exhausted ?: false } &&
                            lock == null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (cursor == null) {
                                if (en) "Find formula" else "Formula အသစ် ရှာမည်"
                            } else {
                                if (en) "Search More" else "ထပ်ရှာမည်"
                            },
                        )
                    }
                    if (cursor != null) {
                        OutlinedButton(
                            onClick = { uiState = uiState.select(kind, null) },
                            enabled = !busy && lock == null,
                        ) { Text(if (en) "None" else "မရွေး") }
                    }
                }

                if (cursor?.blocked == true) {
                    Text(
                        if (en) "Cannot bridge a missing result; verify the prior draw."
                        else "Result ပျောက်နေတဲ့အကြိမ်ကို ကျော်ပြီး မတွက်နိုင်ပါ။ အရင်ပွဲကို စစ်ပါ။",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (cursor?.exhausted == true) {
                    Text(
                        if (en) "Search exhausted for this target and category."
                        else "ဒီအကြိမ်၊ ဒီအမျိုးအစားအတွက် ရှာဖွေမှု ပြီးဆုံးပြီ။",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val visibleEvidence = currentEvidence?.takeIf {
                    it.formula.kind == kind && it.formula.id == displayedId
                }
                if (previewLoading) {
                    Text(if (en) "Verifying formula and historical pairs…"
                        else "Formula နှင့် ကိုက်ညီခဲ့သော Result များကို ပြန်စစ်နေသည်…",
                        style = MaterialTheme.typography.bodySmall)
                } else if (displayedId != null && visibleEvidence == null) {
                    Text(if (en) "Formula evidence unavailable: history may have changed."
                        else "Formula အထောက်အထား ပြန်မစစ်နိုင်ပါ။ History ပြောင်းထားနိုင်သည်။",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                } else if (visibleEvidence != null) {
                    val shown = visibleEvidence
                    CherryDiscoveryFormulaCard(shown, en)
                    if (lock == null) {
                        Button(
                            enabled = !busy && !loading && !previewLoading,
                            onClick = { uiState = uiState.select(kind, shown.formula.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (selectedId == shown.formula.id) {
                                    if (en) "Selected for lock" else "Lock အတွက် ရွေးထားပြီး"
                                } else {
                                    if (en) "Select this formula for lock"
                                    else "ဒီ Formula ကို Lock အတွက် ရွေးမည်"
                                },
                            )
                        }
                    }
                }

                val best = cursor?.bestCandidate
                if (best != null) {
                    Text(
                        (if (en) "Best historical fit: " else "ရှာတွေ့သမျှ အကောင်းဆုံး Streak: ") +
                            "${best.historicalStreak} · ${best.expression}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = { uiState = uiState.select(kind, best.id) },
                        enabled = !busy && lock == null,
                    ) {
                        Text(if (en) "Select best" else "အကောင်းဆုံး Formula ကို ရွေးမည်")
                    }
                }

                if (offered.isNotEmpty()) {
                    Text(
                        if (en) "Previously found formulas (tap to select)"
                        else "ရှာတွေ့ထားသော Formula များ (နှိပ်၍ ရွေးနိုင်သည်)",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(158.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(offered, key = { it }) { id ->
                            val chosen = selectedId == id
                            Surface(
                                color = if (chosen) Color(0xFFE7F3EB) else Color(0xFFFFF6F8),
                                shape = RoundedCornerShape(9.dp),
                                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy &&
                                    lock == null) {
                                    uiState = uiState.select(kind, id)
                                },
                            ) {
                                Text(
                                    id,
                                    modifier = Modifier.padding(9.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (chosen) discoveryGreen else AppColors.PrimaryDeep,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
                if (message != null) {
                    Text(message.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        color = discoveryGreen)
                }
                if (error != null) {
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            HorizontalDivider(color = discoveryEdge)
            Text(
                if (en) "Lock before the result" else "Result မထွက်ခင် Formula အတည်ပြု Lock",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.PrimaryDeep,
            )
            CherryDiscoveryKind.entries.forEach { item ->
                val id = lock?.choices?.firstOrNull { it.kind == item }?.formulaId
                    ?: choices[item]
                Text(
                    discoveryKindLabel(item, en) + ": " +
                        (id ?: if (en) "No selection" else "မရွေးထား"),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (lock != null) {
                Text(
                    (if (en) "Locked (local unverified timestamp): "
                    else "Lock သိမ်းပြီး (ဖုန်းအချိန်အတည်မပြုရသေး): ") + lock.lockedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = discoveryGreen,
                )
            } else {
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        error = null
                        running = scope.launch {
                            try {
                                val details = mutableMapOf<CherryDiscoveryKind,
                                    CherryDiscoveryFormulaEvidence>()
                                for (category in CherryDiscoveryKind.entries) {
                                    val id = picked[category] ?: continue
                                    details[category] = requireNotNull(
                                        service.previewEvidence(target, category, id)) {
                                        "Chosen formula must still match the saved history"
                                    }
                                }
                                lockEvidence = details.toMap()
                                confirmLock = true
                            } catch (_: CancellationException) {
                            } catch (failure: Exception) {
                                error = discoveryError(failure, en)
                            } finally {
                                busy = false
                                running = null
                            }
                        }
                    },
                    enabled = selectable,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (en) "Review and Lock" else "Formula စစ်ပြီး Lock လုပ်မည်")
                }
                if (!canLockNow) Text(
                    if (en) "The result cutoff has passed. New locks are disabled."
                    else "Result အချိန်ကျော်သွားပြီးဖြစ်၍ Lock အသစ် မလုပ်နိုင်ပါ။",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (result != null) {
                Text(
                    (if (en) "Confirmed result: " else "အတည်ပြု Result: ") +
                        "${result.actualPair} · ${result.resultSource.name}",
                    color = discoveryGreen, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                )
                result.outcomes.forEach { outcome ->
                    Text(
                        discoveryKindLabel(outcome.kind, en) + ": " +
                            when (outcome.hit) {
                                true -> if (en) "Hit" else "မှန်"
                                false -> if (en) "Miss" else "မှား"
                                null -> if (en) "Abstained" else "မရွေးထား"
                            },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else if (lock != null) {
                Text(
                    if (en) "Waiting for a confirmed saved result."
                    else "အတည်ပြု Result ကို စောင့်စစ်ရန် ကျန်နေသည်။",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = {
                    if (busy) return@OutlinedButton
                    busy = true
                    error = null
                    running = scope.launch {
                        try {
                            journal = service.checkConfirmedResults()
                            message = if (en) "Saved results checked; pending locks stay pending."
                                else "သိမ်းထားသော Result များ စစ်ပြီးပြီ။ မထွက်သေးသောပွဲများကို မပြောင်းထားပါ။"
                        } catch (_: CancellationException) {
                        } catch (failure: Exception) {
                            error = discoveryError(failure, en)
                        } finally {
                            busy = false
                            running = null
                        }
                    }
                },
                enabled = !busy && !loading && journal.lockedTrials().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (en) "Check confirmed results" else "အတည်ပြု Result ပြန်စစ်မည်")
            }
            Text(
                if (en)
                    "Historical fits are not future wins. Local locks are not independently timestamp-verified. No search runs automatically."
                else
                    "သမိုင်းမှာ ကိုက်ညီမှုက နောက်ပွဲအနိုင်မဟုတ်ပါ။ Local Lock အချိန်ကို သီးခြားအတည်မပြုထားပါ။ အလိုအလျောက် Formula မရှာပါ။",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            val past = journal.lockedTrials().asReversed().take(5)
            if (past.isNotEmpty()) {
                HorizontalDivider(color = discoveryEdge)
                Text(if (en) "Recent locked trials" else "မကြာသေးမီ Lock မှတ်တမ်းများ",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall)
                past.forEach { item ->
                    val settled = journal.verifiedResults().firstOrNull { it.target == item.target }
                    Text(
                        "${item.target.date} · ${item.target.session} · " +
                            (settled?.actualPair ?: if (en) "Pending" else "မထွက်သေး"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (confirmLock && lock == null) {
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            title = { Text(if (en) "Lock this formula?" else "Formula ကို Lock လုပ်မလား?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("${target.date} · ${target.session}")
                    CherryDiscoveryKind.entries.forEach { item ->
                        val evidence = lockEvidence[item]?.formula
                        Text(
                            discoveryKindLabel(item, en) + ": " +
                                (evidence?.id ?: if (en) "Abstain" else "မရွေးထား"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (evidence != null) {
                            Text(
                                "${evidence.expression} + ${evidence.offset} (mod 10) = " +
                                    "${evidence.currentDigit} · " +
                                    (if (en) "Historical streak " else "သမိုင်းကိုက်ညီမှု ") +
                                    "${evidence.historicalStreak}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text(if (en)
                        "This local record cannot be edited after the result."
                        else "Lock လုပ်ပြီးရင် Result ထွက်ပြီးမှ ပြန်ပြင်မရပါ။",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && canLockNow &&
                        picked.all { (category, id) ->
                            id == null || lockEvidence[category]?.formula?.id == id
                        },
                    onClick = {
                        confirmLock = false
                        busy = true
                        error = null
                        running = scope.launch {
                            try {
                                journal = service.lock(target, picked, Instant.now())
                                message = if (en) "Formula locked locally."
                                    else "Formula ကို Local မှတ်တမ်းမှာ Lock သိမ်းပြီးပြီ။"
                            } catch (_: CancellationException) {
                            } catch (failure: Exception) {
                                error = discoveryError(failure, en)
                            } finally {
                                busy = false
                                running = null
                            }
                        }
                    },
                ) { Text(if (en) "Lock" else "Lock လုပ်မည်") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLock = false }) {
                    Text(if (en) "Cancel" else "မလုပ်တော့")
                }
            },
        )
    }
}

@Composable
private fun CherryDiscoveryFormulaCard(
    evidence: CherryDiscoveryFormulaEvidence,
    en: Boolean,
) {
    val formula = evidence.formula
    Surface(
        color = Color(0xFFFFF6F8),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, discoveryEdge),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(formula.expression, color = AppColors.PrimaryDeep,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium)
            Text(
                (if (en) "Calculation: " else "တွက်ချက်ပုံ: ") +
                    "(${formula.expression} + ${formula.offset}) mod 10",
                style = MaterialTheme.typography.bodySmall)
            Text(
                (if (en) "Output digit: " else "ထွက်ဂဏန်း: ") +
                    "${formula.currentDigit} · " +
                    (if (en) "Historical streak " else "သမိုင်းကိုက်ညီမှု ") +
                    "${formula.historicalStreak}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (en) "Matched historical 2D results:"
                else "သမိုင်းတွင် ကိုက်ညီခဲ့သော 2D Result များ:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            evidence.matched.take(10).forEach { draw ->
                Text(
                    "${draw.date} " +
                        (if (draw.session.name == "MORNING") "AM" else "PM") +
                        " · ${draw.digit}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (evidence.matched.size > 10) {
                Text(
                    if (en) "And ${evidence.matched.size - 10} earlier matched draws"
                    else "ယခင်ကိုက်ညီခဲ့သောပွဲ ${evidence.matched.size - 10} ပွဲ ထပ်ရှိသည်",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(if (en) "Historical fit only, not a verified forecast."
                else "အတိတ် Result ကိုက်ညီမှုသာဖြစ်ပြီး အနာဂတ်အနိုင် အထောက်အထားမဟုတ်ပါ။",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
