package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryV2StreakRegistry
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

    val current: CherryV2StreakRegistry?
        get() = previous[kind]

    fun choose(next: CherryDiscoveryKind) {
        if (busy || loading || next == kind) return
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
                message = when (receipt.result.stopReason) {
                    CherryV2TimedSearch.StopReason.BLOCKED ->
                        "အရင်အကြိမ် အတည်ပြု Result မပြည့်စုံသေးပါ"
                    CherryV2TimedSearch.StopReason.TIME_LIMIT ->
                        "ရှာဖွေချိန်ပြည့်ပါပြီ · ဆက်ရှာနိုင်သည်"
                }
            } catch (_: CancellationException) {
                // Search controller persists fully completed formulas on
                // cancel. Reload that saved checkpoint for accurate UI.
                cancelled = true
                message = "ရှာဖွေမှု ရပ်လိုက်ပါပြီ"
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
    }
}

private fun v3KindName(kind: CherryDiscoveryKind, en: Boolean) = when (kind) {
    CherryDiscoveryKind.LONE -> if (en) "Lone" else "လုံးပိုင်"
    CherryDiscoveryKind.WAM -> if (en) "Wam" else "ဝမ်းချိန်း"
    CherryDiscoveryKind.BREAK -> if (en) "Break" else "ဘရိတ်"
}

/** Real on-screen test path, under original Gemini 210; no Champion writes. */
@Composable
internal fun CherryV3DiscoveryPanel(
    controller: CherryV3DiscoveryController,
    target: DrawIdentity,
    en: Boolean,
) {
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
            Text(
                if (en) "Formula အသစ်ရှာမယ်" else "Formula အသစ်ရှာမယ်",
                color = AppColors.PrimaryDeep,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "${target.date} · " +
                    if (target.session.name == "MORNING") {
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
                Text(
                    if (en) "Reading saved progress…" else "သိမ်းထားသော Formula ပြန်ဖတ်နေသည်…",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (controller.busy) {
                if (controller.preparing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.PrimaryDeep,
                    )
                    Text(if (en) "Preparing confirmed history…"
                        else "အတည်ပြု History ပြင်ဆင်နေသည်…",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    LinearProgressIndicator(
                        progress = { (controller.elapsedSeconds / 30f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = AppColors.PrimaryDeep,
                        trackColor = v3Edge,
                    )
                    Text(
                        if (en)
                            "Searching: ${controller.elapsedSeconds.toInt()}/30 sec"
                        else
                            "ရှာနေသည် · ${controller.elapsedSeconds.toInt()}/၃၀ စက္ကန့်",
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
                        if (en) "Search formulas" else "ရှာဖွေမည်"
                    } else {
                        if (en) "Continue search · 30s" else "ဆက်ရှာမည် · ၃၀ စက္ကန့်"
                    })
                }
            }

            if (saved != null) {
                Text(
                    if (en)
                        "Checked ${saved.nextIndex} formulas · ${results.size} streak tiers"
                    else
                        "စစ်ပြီး ${saved.nextIndex} မျိုး · တွေ့ပြီး ${results.size} အဆင့်",
                    color = AppColors.PrimaryDeep,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            controller.latestReceipt?.let { receipt ->
                Text(
                    if (en)
                        "This round: ${receipt.result.inspectedThisBatch} checked · " +
                            "${receipt.result.distinctBehaviorsEvaluated} distinct · " +
                            "${receipt.result.equivalentBehaviorsSkipped} equivalent"
                    else
                        "ဒီအကြိမ် ${receipt.result.inspectedThisBatch} မျိုးစစ် · " +
                            "${receipt.result.distinctBehaviorsEvaluated} မျိုးကွဲ · " +
                            "${receipt.result.equivalentBehaviorsSkipped} မျိုးတူပယ်",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (recommended != null) {
                HorizontalDivider(color = v3Edge)
                Text(
                    if (en) "Top historical fit" else "ဦးစားပေးရွေးကွက်",
                    fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep,
                )
                Text(
                    "${recommended.historicalStreak}" +
                        (if (recommended.historicalStreak >= 10) "+" else "") +
                        (if (en) " consecutive" else " ပွဲဆက်") +
                        " · ${recommended.expression} + ${recommended.offset} → " +
                        recommended.predictedDigit,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (results.isNotEmpty()) {
                Text(if (en) "First-found streak methods" else "ပထမတွေ့ခဲ့သော တွက်နည်းများ",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall)
                results.forEach { formula ->
                    Surface(
                        color = v3Blush,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                "${formula.historicalStreak}" +
                                    (if (formula.historicalStreak >= 10) "+" else "") +
                                    (if (en) " consecutive" else " ပွဲဆက်"),
                                fontWeight = FontWeight.Bold,
                                color = AppColors.PrimaryDeep,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                "${formula.expression} + ${formula.offset} (mod 10) → " +
                                    formula.predictedDigit,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            controller.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = AppColors.PrimaryDeep)
            }
            controller.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
            Text(
                if (en)
                    "Historical fits only, not future wins. No automatic Champion changes."
                else
                    "သမိုင်းကိုက်ညီမှုသာ ဖြစ်သည်။ Champion ကို အလိုအလျောက် မပြောင်းပါ။",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
