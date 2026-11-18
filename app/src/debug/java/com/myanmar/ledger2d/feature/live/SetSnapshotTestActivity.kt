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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.myanmar.ledger2d.core.design.LedgerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

class SetSnapshotTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LedgerTheme(languageCode = "my") { SetSnapshotTestScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetSnapshotTestScreen() {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SetProbeOutcome?>(null) }
    var recentAudit by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("SET Snapshot Test") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("SET ဒေတာဖတ်ခြင်း စမ်းသပ်မှု", style = MaterialTheme.typography.titleLarge)
            Text(
                "ခလုတ်နှိပ်လျှင် SET Website ကို Request တစ်ကြိမ်သာ ပို့မည်။ " +
                    "Luke LIVE၊ History နှင့် System Result ကို မထိပါ။",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    if (!loading) {
                        loading = true
                        result = null
                        scope.launch {
                            result = withContext(Dispatchers.IO) { SetSnapshotFetcher.probeOnce() }
                            loading = false
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (loading) "ရယူနေသည်..." else "Snapshot ရယူမည်") }
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("SET Website မှ အဖြေစောင့်နေသည်", Modifier.padding(start = 12.dp))
                }
            }

            when (val state = result) {
                is SetProbeOutcome.Success -> {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text("ဒေတာရရှိပြီး", color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold)
                            ProbeLine("SET Index", state.observation.snapshot.index)
                            ProbeLine("Trading Value (M.Baht)", state.observation.snapshot.tradingValueMbaht)
                            Text(state.observation.snapshot.computed2d,
                                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary)
                            ProbeLine("Source Update (Thai)", state.observation.snapshot.sourceUpdateBangkok.format(
                                DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")))
                            ProbeLine("Source Update (Yangon)", state.observation.snapshot.sourceUpdateYangon.format(
                                DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")))
                            ProbeLine("Market Status", state.observation.snapshot.marketStatus ?: "မတွေ့")
                            ProbeLine("HTTP", state.httpStatus.toString())
                            ProbeLine("Response Time", state.observation.roundTripMs.toString() + " ms")
                            ProbeLine("HTML Size", state.htmlBytes.toString() + " bytes")
                        }
                    }
                }
                is SetProbeOutcome.Failure -> {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("ဒေတာမရရှိသေး", color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold)
                            ProbeLine("Error", state.reason)
                            ProbeLine("HTTP", state.httpStatus?.toString() ?: "—")
                            ProbeLine("Response Time", state.roundTripMs.toString() + " ms")
                        }
                    }
                }
                null -> Unit
            }
            Button(
                onClick = { recentAudit = SetMorningAuditStore.recentLog(context) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("SET မှတ်တမ်းကြည့်ရန်") }
            if (recentAudit != null) {
                Text(recentAudit.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
            Text("ဒါက Connection/Parser Test သာဖြစ်ပြီး 12:01 Result စစ်ဆေးမှု မဟုတ်ပါ။",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProbeLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
