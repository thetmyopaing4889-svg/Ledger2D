package com.myanmar.ledger2d.feature.live

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.myanmar.ledger2d.core.design.LedgerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Debug-only experiment. Proves repeated real SET HTML snapshots can be
 * requested and shown independently of Myanmar 12:01 cutoff.
 * It never writes Luke, Room, winners or official results.
 */
class SetSnapshotTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LedgerTheme(languageCode = "my") { SetContinuousLiveProbeScreen() }
        }
    }
}

private const val LIVE_INTERVAL_MS = 3_000L
private const val MAX_LIVE_ATTEMPTS = 16

/** No overlapping HTTP GETs; intervals are measured from request start. */
internal fun setLiveProbeDelayMs(elapsedMs: Long): Long =
    (LIVE_INTERVAL_MS - elapsedMs).coerceAtLeast(300L)
private val displayTime = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")
private val shortTime = DateTimeFormatter.ofPattern("HH:mm:ss")
private val preciseTime = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

/** Compare actual SET observations; repeat readings do not count as new data. */
internal fun setProbeSnapshotFingerprint(snapshot: SetMarketSnapshot): String =
    snapshot.sourceUpdateYangon.toInstant().toString() + "|" +
        snapshot.index + "|" + snapshot.tradingValueMbaht

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetContinuousLiveProbeScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var running by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SetProbeOutcome?>(null) }
    var latestSuccess by remember { mutableStateOf<SetProbeOutcome.Success?>(null) }
    var lastReadTime by remember { mutableStateOf("—") }
    var attempts by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }
    var changedCount by remember { mutableStateOf(0) }
    var previousFingerprint by remember { mutableStateOf<String?>(null) }
    var lastChangeLabel by remember { mutableStateOf("မစမ်းရသေး") }
    var trace by remember { mutableStateOf<List<String>>(emptyList()) }
    var recentAudit by remember { mutableStateOf<String?>(null) }

    // A LIVE test is *never* a background service. Leaving the app stops it.
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) running = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            running = false
            owner.lifecycle.removeObserver(observer)
        }
    }

    suspend fun fetchAndDisplayOnce() {
        fetching = true
        try {
            val reply = withContext(Dispatchers.IO) { SetSnapshotFetcher.probeOnce() }
            val seen = ZonedDateTime.now(YANGON)
            val startedAt = when (reply) {
                is SetProbeOutcome.Success -> reply.evidence.startedAt
                is SetProbeOutcome.Failure -> reply.evidence.startedAt
            }.atZone(YANGON).format(preciseTime)
            lastReadTime = seen.format(shortTime)
            attempts += 1
            result = reply
            when (reply) {
                is SetProbeOutcome.Success -> {
                    successCount += 1
                    val snapshot = reply.observation.snapshot
                    val fingerprint = setProbeSnapshotFingerprint(snapshot)
                    val changed = previousFingerprint != fingerprint
                    if (changed) {
                        changedCount += 1
                        lastChangeLabel = seen.format(shortTime)
                    }
                    previousFingerprint = fingerprint
                    latestSuccess = reply
                    val source = snapshot.sourceUpdateYangon.format(shortTime)
                    val kind = if (changed) "NEW" else "SAME"
                    val entry = "GET " + startedAt + " → " + seen.format(shortTime) + " " + kind +
                        " | SET " + snapshot.index + " | Value " +
                        snapshot.tradingValueMbaht + " | 2D " + snapshot.computed2d +
                        " | source " + source + " | " +
                        (snapshot.marketStatus ?: "Unknown") +
                        " | HTTP " + reply.httpStatus +
                        " | " + reply.observation.roundTripMs + "ms"
                    trace = (listOf(entry) + trace).take(32)
                }
                is SetProbeOutcome.Failure -> {
                    val entry = "GET " + startedAt + " → " + seen.format(shortTime) + " FAIL | HTTP " +
                        (reply.httpStatus?.toString() ?: "none") + " | " +
                        reply.reason + " | " + reply.roundTripMs + "ms"
                    trace = (listOf(entry) + trace).take(32)
                }
            }
        } finally {
            fetching = false
        }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        repeat(MAX_LIVE_ATTEMPTS) { index ->
            val begin = android.os.SystemClock.elapsedRealtime()
            fetchAndDisplayOnce()
            val last = result
            // Never retry access denied / rate-limited responses.
            if (last is SetProbeOutcome.Failure &&
                last.httpStatus in listOf(401, 403, 429)) {
                running = false
                return@LaunchedEffect
            }
            if (index == MAX_LIVE_ATTEMPTS - 1) {
                running = false
                return@LaunchedEffect
            }
            val used = android.os.SystemClock.elapsedRealtime() - begin
            delay(setLiveProbeDelayMs(used))
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("SET LIVE 3s Test") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("SET Snapshot — LIVE စမ်းသပ်မှု",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "12:00 စောင့်ရန်မလိုပါ။ SET Website မှ တကယ့်ဒေတာကို " +
                    "3 စက္ကန့်ခြား စမ်းဖတ်ပြီး ဂဏန်းပြောင်းလဲမှုကို ကြည့်နိုင်သည်။ " +
                    "စမ်းသပ်ချိန်မှာသာ အလုပ်လုပ်ပြီး Luke Result/History ကို မထိပါ။",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (!running && !fetching) {
                            running = true
                        }
                    },
                    enabled = !running && !fetching,
                    modifier = Modifier.weight(1f),
                ) { Text("LIVE စမ်းမည်") }
                OutlinedButton(
                    onClick = { running = false },
                    enabled = running,
                    modifier = Modifier.weight(1f),
                ) { Text("ရပ်မည်") }
            }
            OutlinedButton(
                onClick = {
                    if (!running && !fetching) scope.launch { fetchAndDisplayOnce() }
                },
                enabled = !running && !fetching,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Snapshot တစ်ကြိမ်ရယူမည်") }

            if (fetching) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("SET ကနေ ဖတ်နေသည်")
                }
            }
            Text(
                if (running) "LIVE TEST ဖွင့်ထားသည် • 3s target • အများဆုံး 16 ကြိမ်"
                else "LIVE TEST ရပ်ထားသည်",
                color = if (running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("SET LIVE PREVIEW — တကယ့် Snapshot",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold)
                    val last = latestSuccess
                    if (last == null) {
                        Text("Snapshot မရသေးပါ။ LIVE စမ်းမည် ကိုနှိပ်ပါ။")
                    } else {
                        val snapshot = last.observation.snapshot
                        val sourceAge = Duration.between(
                            snapshot.sourceUpdateYangon.toInstant(), Instant.now()
                        ).seconds
                        val market = snapshot.marketStatus.orEmpty()
                        val closed = market.equals("OffHour", true) ||
                            market.equals("Closed", true)
                        val fresh = sourceAge in 0L..90L && !closed
                        Text(
                            if (fresh) "SET Source လတ်ဆတ်နေသည် (Official Result မဟုတ်ပါ)"
                            else "SET Source အဟောင်း သို့မဟုတ် Market ပိတ်ထားနိုင်သည်",
                            color = if (fresh) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                        )
                        Text(snapshot.computed2d,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        ProbeLine("SET Index", snapshot.index)
                        ProbeLine("Trading Value (M.Baht)", snapshot.tradingValueMbaht)
                        ProbeLine("SET Source Update (Yangon)", snapshot.sourceUpdateYangon.format(displayTime))
                        ProbeLine("SET Source Update (Thai)", snapshot.sourceUpdateBangkok.format(displayTime))
                        ProbeLine("Market Status", snapshot.marketStatus ?: "မတွေ့")
                        ProbeLine("Source အသက်", sourceAge.toString() + " စက္ကန့်")
                        ProbeLine("နောက်ဆုံး GET", lastReadTime)
                        ProbeLine("Request Duration", last.observation.roundTripMs.toString() + " ms")
                        ProbeLine("HTTP", last.httpStatus.toString())
                    }
                    if (result is SetProbeOutcome.Failure) {
                        val failure = result as SetProbeOutcome.Failure
                        Text("နောက်ဆုံး Request Error: " + failure.reason,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("LIVE စမ်းသပ်ရလဒ်", style = MaterialTheme.typography.titleMedium)
                    ProbeLine("Request စုစုပေါင်း", attempts.toString())
                    ProbeLine("Snapshot ရရှိ", successCount.toString())
                    ProbeLine("Snapshot အသစ်/ပြောင်းလဲမှု", changedCount.toString())
                    ProbeLine("နောက်ဆုံး ဒေတာပြောင်းချိန်", lastChangeLabel)
                }
            }

            Text("စမ်းသပ် မှတ်တမ်း (အသစ်ဆုံးအပေါ်)",
                style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = {
                    val content = buildString {
                        appendLine("Cherry SET LIVE TEST / REAL DATA / 3s target interval")
                        appendLine("attempts=" + attempts + " successes=" +
                            successCount + " changes=" + changedCount)
                        trace.forEach { appendLine(it) }
                    }
                    val cb = context.getSystemService(
                        android.content.Context.CLIPBOARD_SERVICE
                    ) as android.content.ClipboardManager
                    cb.setPrimaryClip(android.content.ClipData.newPlainText(
                        "SET LIVE Test Trace", content
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("စမ်းသပ်မှတ်တမ်း ကူးယူရန်") }

            trace.forEach { entry ->
                Text(entry, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { recentAudit = SetMorningAuditStore.recentLog(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("မနက် SET Audit မှတ်တမ်း ကြည့်ရန်") }
            if (recentAudit != null) {
                Text(recentAudit.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "ဤစမ်းသပ်မှုက SET ဒေတာဖတ်ပြီး LIVE UI ပြနိုင်မနိုင်သာ စစ်သည်။ " +
                    "12:01 Cutoff နှင့် Luke Final ကို အတည်မပြုပါ။ " +
                    "3s သည် ကြိုးစားပို့မည့် interval ဖြစ်ပြီး HTTP ကြာလျှင် ပိုကြာနိုင်သည်။ " +
                    "Market ပိတ်လျှင် Snapshot မပြောင်းနိုင်ပါ။",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProbeLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
    }
}
