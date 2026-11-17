package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryV3FormulaSelection
import com.myanmar.ledger2d.core.domain.CherryV2StreakRegistry
import com.myanmar.ledger2d.core.domain.CherryV2StreakMatch
import com.myanmar.ledger2d.core.domain.CherryV3WorkedExamples
import com.myanmar.ledger2d.core.domain.CherryV3WorkedExample
import com.myanmar.ledger2d.core.domain.CherryV2TimedSearch
import com.myanmar.ledger2d.core.model.DrawIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val v3Edge = Color(0xFFF4CFDA)
private val v3Blush = Color(0xFFFFF6F8)

 /** Star tiers represent only observed historical streak, never win odds. */
internal fun cherryV3HistoricalStars(streak: Int): String {
    val filled = when {
        streak >= 9 -> 5
        streak >= 7 -> 4
        streak >= 5 -> 3
        streak >= 4 -> 2
        else -> 1
    }
    return "★".repeat(filled) + "☆".repeat(5 - filled)
}

/**
 * Screen-level controller, NOT a LazyColumn-item scope. Scrolling the Math
 * list cannot accidentally cancel the user-started 30-second search. Leaving
 * Better Data or changing targets cancels it and checkpoints completed work.
 * Neither constructor nor restoring saved data ever starts discovery.
 */
internal class CherryV3DiscoveryController(
    private val service: CherryV2DiscoveryService,
    private val target: DrawIdentity,
    private val scope: CoroutineScope,
) {
    var kind by mutableStateOf(CherryDiscoveryKind.LONE)
        private set
    var loading by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var preparing by mutableStateOf(false)
        private set
    var elapsedSeconds by mutableFloatStateOf(0f)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var latestReceipt by mutableStateOf<CherryV2DiscoveryService.Receipt?>(null)
        private set

    private var previous: Map<CherryDiscoveryKind, CherryV2StreakRegistry?> by
        mutableStateOf(emptyMap())
    private var restoredKinds: Set<CherryDiscoveryKind> by mutableStateOf(emptySet())
    private var running: Job? = null
    private var detailJob: Job? = null
    var detailFormula by mutableStateOf<CherryV2StreakMatch?>(null)
        private set
    var detail by mutableStateOf<CherryV3WorkedExamples?>(null)
        private set
    var detailLoading by mutableStateOf(false)
        private set
    var detailError by mutableStateOf<String?>(null)
        private set

    /** Opening a calculation does NOT start the discovery scan. */
    fun openDetails(formula: CherryV2StreakMatch) {
        if (busy || loading || formula.kind != kind ||
            current?.orderedResults()?.none { it.formulaId == formula.formulaId } != false
        ) return
        detailJob?.cancel()
        detailFormula = formula
        detail = null
        detailError = null
        detailLoading = true
        val requestedKind = kind
        detailJob = scope.launch {
            try {
                val explained = service.explain(target, requestedKind, formula.formulaId)
                if (explained == null) {
                    detailError = "တွက်နည်းကို ပြန်စစ်မရပါ"
                } else {
                    detail = explained
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                detailError = failure.message ?: "တွက်နည်း ပြန်စစ်မရပါ"
            } finally {
                detailLoading = false
            }
        }
    }

    fun dismissDetails() {
        detailJob?.cancel()
        detailJob = null
        detailFormula = null
        detail = null
        detailError = null
        detailLoading = false
    }

    val current: CherryV2StreakRegistry?
        get() = previous[kind]

    fun choose(next: CherryDiscoveryKind) {
        if (busy || loading || next == kind) return
        dismissDetails()
        kind = next
        message = null
        error = null
        latestReceipt = null
        elapsedSeconds = 0f
        restore()
    }

    /** Read-only, one lazy query per target + selected category. */
    fun restore() {
        val requested = kind
        if (busy || loading || requested in restoredKinds) return
        loading = true
        scope.launch {
            try {
                val saved = service.saved(target, requested)
                previous = previous + (requested to saved)
                restoredKinds = restoredKinds + requested
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Unable to restore saved formulas"
            } finally {
                loading = false
            }
        }
    }

    fun search() {
        if (busy || loading) return
        val requested = kind
        busy = true
        preparing = true
        elapsedSeconds = 0f
        message = null
        error = null
        latestReceipt = null
        running = scope.launch {
            val started = Channel<Long>(Channel.CONFLATED)
            val meter = launch {
                for (startNanos in started) {
                    preparing = false
                    while (isActive) {
                        elapsedSeconds = ((System.nanoTime() - startNanos)
                            .coerceAtLeast(0L) / 1_000_000_000f)
                            .coerceIn(0f, 30f)
                        delay(100L)
                    }
                }
            }
            var cancelled = false
            try {
                val receipt = service.search(
                    target = target, kind = requested,
                    onSearchStarted = { started.trySend(it) },
                )
                previous = previous + (requested to receipt.result.cursor)
                restoredKinds = restoredKinds + requested
                latestReceipt = receipt
                elapsedSeconds = (receipt.result.elapsedNanos / 1_000_000_000f)
                    .coerceIn(0f, 30f)
                // No 'time finished' or engineering diagnostics in product UI.
                message = if (receipt.result.stopReason ==
                    CherryV2TimedSearch.StopReason.BLOCKED)
                    "ရှာဖွေရန် အရင် Result ၄ ကြိမ် အနည်းဆုံး လိုပါသည်"
                else null
            } catch (_: CancellationException) {
                // Search controller persists fully completed formulas on
                // cancel. Reload that saved checkpoint for accurate UI.
                cancelled = true
                message = null
            } catch (failure: Exception) {
                error = failure.message ?: "Formula search failed"
            } finally {
                meter.cancel()
                started.close()
                preparing = false
                busy = false
                running = null
                if (cancelled) {
                    restoredKinds = restoredKinds - requested
                    restore()
                }
            }
        }
    }

    fun cancel() {
        running?.cancel()
        dismissDetails()
    }
}

private fun v3KindName(kind: CherryDiscoveryKind, en: Boolean) = when (kind) {
    CherryDiscoveryKind.LONE -> if (en) "Lone" else "လုံးပိုင်"
    CherryDiscoveryKind.WAM -> if (en) "Wam" else "ဝမ်းချိန်း"
    CherryDiscoveryKind.BREAK -> if (en) "Break" else "ဘရိတ်"
}

/** Original Math tab frame retained. Discovery remains manual and isolated. */
@Composable
internal fun CherryV3DiscoveryPanel(
    controller: CherryV3DiscoveryController,
    target: DrawIdentity,
    en: Boolean,
    selected: Map<CherryDiscoveryKind, CherryV3FormulaSelection>,
    onFormulaSelected: (CherryV2StreakMatch) -> Unit,
    onFormulaDeselected: (CherryV2StreakMatch) -> Unit,
) {
    var pendingSelection by remember(controller) {
        mutableStateOf<CherryV2StreakMatch?>(null)
    }
    LaunchedEffect(controller) { controller.restore() }
    val saved = controller.current
    val results = saved?.orderedResults().orEmpty()
    val recommended = saved?.recommended()

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, v3Edge),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Distinct section title without changing the Math frame position.
            Surface(color = AppColors.PrimaryDeep, shape = RoundedCornerShape(10.dp)) {
                Text(
                    "Formula အသစ်ရှာမယ်",
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = 13.dp, vertical = 11.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "${target.date} · " + if (target.session.name == "MORNING") {
                    if (en) "AM" else "မနက်"
                } else {
                    if (en) "PM" else "ညနေ"
                },
                style = MaterialTheme.typography.labelMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                CherryDiscoveryKind.entries.forEach { item ->
                    FilterChip(
                        selected = controller.kind == item,
                        onClick = { controller.choose(item) },
                        enabled = !controller.busy && !controller.loading,
                        label = { Text(v3KindName(item, en), maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (controller.loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.PrimaryDeep,
                )
            }
            if (controller.busy) {
                if (controller.preparing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.PrimaryDeep,
                    )
                    Text(if (en) "Preparing results…" else "Result များ ပြင်ဆင်နေသည်…",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(
                        progress = { (controller.elapsedSeconds / 30f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.PrimaryDeep,
                        trackColor = v3Edge,
                    )
                    // ONE consistent digit style in both languages: 1/30.
                    Text(
                        (if (en) "Searching " else "ရှာဖွေနေသည် ") +
                            "${controller.elapsedSeconds.toInt()}/30",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(
                    onClick = { controller.cancel() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (en) "Cancel" else "ရပ်မည်")
                }
            } else {
                Button(
                    onClick = { controller.search() },
                    enabled = !controller.loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.PrimaryDeep),
                ) {
                    Text(if (saved == null || saved.nextIndex.signum() == 0) {
                        if (en) "Search" else "ရှာဖွေမည်"
                    } else {
                        if (en) "Continue search" else "ဆက်ရှာမည်"
                    })
                }
            }
            if (saved != null) {
                Text(
                    (if (en) "Checked " else "စစ်ဆေးမှု ") +
                        "${saved.nextIndex} / " +
                        (if (en) "Found " else "တွေ့ရှိမှု ") +
                        "${results.size} Formula",
                    color = AppColors.PrimaryDeep,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (recommended != null) {
                CherryV3FormulaCard(
                    recommended, controller, en,
                    highlighted = true,
                    selected = selected[recommended.kind]?.formulaId == recommended.formulaId,
                    onSelectRequest = { pendingSelection = it },
                )
            }
            if (results.isNotEmpty()) {
                Text(
                    if (en) "Methods" else "တွက်နည်းများ",
                    color = AppColors.PrimaryDeep,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                )
                results.forEach { formula ->
                    CherryV3FormulaCard(
                        formula, controller, en,
                        selected = selected[formula.kind]?.formulaId == formula.formulaId,
                        onSelectRequest = { pendingSelection = it },
                    )
                }
            }
            controller.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            controller.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    pendingSelection?.let { pending ->
        val isSelected = selected[pending.kind]?.let {
            it.formulaId == pending.formulaId &&
                it.digit == pending.predictedDigit
        } == true
        AlertDialog(
            onDismissRequest = { pendingSelection = null },
            title = {
                Text(if (isSelected) {
                    if(en) "Remove selected formula?" else "ရွေးထားသော Formula ကို ဖြုတ်မလား"
                } else {
                    if(en) "Select this formula?" else "ဒီ Formula ကို ရွေးချယ်မလား"
                })
            },
            text = {
                Text("${v3KindName(pending.kind, en)} ${pending.predictedDigit}  " +
                    cherryV3HistoricalStars(pending.historicalStreak))
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pending.target == target && !controller.busy) {
                        if (isSelected) onFormulaDeselected(pending)
                        else onFormulaSelected(pending)
                    }
                    pendingSelection = null
                }) {
                    Text(if(isSelected) {
                        if(en) "Deselect" else "မရွေးချယ်တော့ပါ"
                    } else {
                        if(en) "Select" else "ရွေးချယ်မည်"
                    })
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingSelection = null }) {
                    Text(if (en) "Cancel" else "မလုပ်တော့ပါ")
                }
            },
        )
    }
    val selected = controller.detailFormula
    if (selected != null) {
        Dialog(onDismissRequest = { controller.dismissDetails() }) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, v3Edge),
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 620.dp)
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (en) "Calculation" else "တွက်နည်းကြည့်ရန်",
                        color = AppColors.PrimaryDeep,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "${v3KindName(selected.kind, en)} ${selected.predictedDigit}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    when {
                        controller.detailLoading ->
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = AppColors.PrimaryDeep,
                            )
                        controller.detail != null -> {
                            val example = controller.detail!!
                            CherryV3ExampleSection(
                                if (en) "Previous draw" else "ရှေ့တစ်ကြိမ်",
                                example.previous, selected.kind, en,
                            )
                            HorizontalDivider(color = v3Edge)
                            CherryV3ExampleSection(
                                if (en) "This draw" else "ယခုအကြိမ်",
                                example.current, selected.kind, en,
                            )
                        }
                        controller.detailError != null ->
                            Text(controller.detailError.orEmpty(),
                                color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = { controller.dismissDetails() },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(if (en) "Close" else "ပိတ်ရန်")
                    }
                }
            }
        }
    }
}

@Composable
private fun CherryV3FormulaCard(
    formula: CherryV2StreakMatch,
    controller: CherryV3DiscoveryController,
    en: Boolean,
    highlighted: Boolean = false,
    selected: Boolean,
    onSelectRequest: (CherryV2StreakMatch) -> Unit,
) {
    Surface(
        onClick = { onSelectRequest(formula) },
        enabled = !controller.busy && !controller.loading,
        color = if (highlighted || selected) Color(0xFFFFE1EB) else v3Blush,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(
            if (highlighted || selected) 1.5.dp else 0.5.dp,
            if (highlighted || selected) AppColors.PrimaryDeep else v3Edge,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (highlighted) Text(
                if (en) "Top pick" else "ဦးစားပေးရွေးကွက်",
                color = AppColors.PrimaryDeep,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${formula.historicalStreak}" +
                        (if (formula.historicalStreak >= 10) "+" else "") +
                        (if (en) " consecutive correct" else " ကြိမ်ဆက်တိုက်မှန်"),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(cherryV3HistoricalStars(formula.historicalStreak),
                    color = AppColors.PrimaryDeep,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "${v3KindName(formula.kind, en)} ${formula.predictedDigit}",
                fontWeight = FontWeight.Bold,
                color = AppColors.PrimaryDeep,
                style = MaterialTheme.typography.titleMedium,
            )
            if (selected) {
                Text(if (en) "Selected" else "ရွေးထားသည်",
                    color = AppColors.PrimaryDeep,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall)
            }
            TextButton(
                onClick = { controller.openDetails(formula) },
                enabled = !controller.busy && !controller.loading,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (en) "View calculation" else "တွက်နည်းကြည့်ရန်",
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun CherryV3ExampleSection(
    heading: String,
    example: CherryV3WorkedExample,
    kind: CherryDiscoveryKind,
    en: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(heading, color = AppColors.PrimaryDeep,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium)
        Text(
            "${example.draw.date} · " +
                if (example.draw.session.name == "MORNING") {
                    if (en) "AM" else "မနက်"
                } else {
                    if (en) "PM" else "ညနေ"
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        example.inputs.forEach { row ->
            Text(row, style = MaterialTheme.typography.bodySmall)
        }
        Surface(color = v3Blush, shape = RoundedCornerShape(9.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                example.steps.forEach { step ->
                    Text(step, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${v3KindName(kind, en)} ${example.digit}",
                    color = AppColors.PrimaryDeep,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        example.actualPair?.let { actual ->
            Text(
                (if (en) "Actual result " else "အမှန်တကယ်ထွက် ") +
                    actual +
                    (if (en) " · matched" else " · ကိုက်ညီ"),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
