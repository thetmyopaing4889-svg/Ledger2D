@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.feature.main.AppScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Raw values exactly as the API returns them (strings, commas kept, no reformatting). */
data class LiveSessionData(
    val modern: String,
    val internet: String,
    val set: String,
    val value: String,
    val result: String,
)

data class LiveDayData(
    val date: String,
    val morning: LiveSessionData,
    val evening: LiveSessionData,
)

/** In-memory only — never persisted to Room. */
sealed interface LiveUiState {
    data object Loading : LiveUiState
    data class Data(val data: LiveDayData, val refreshing: Boolean, val stale: Boolean) : LiveUiState
    data class Error(val retrying: Boolean) : LiveUiState
}

private const val ENDPOINT = "https://backend.shwemyanmar2d.com/api/lv/twod-result"
private const val POLL_INTERVAL_MS = 10_000L

class LiveViewModel : ViewModel() {
    private val _state = MutableStateFlow<LiveUiState>(LiveUiState.Loading)
    val state: StateFlow<LiveUiState> = _state.asStateFlow()

    private suspend fun fetchOnce() {
        val hadData = _state.value is LiveUiState.Data
        if (hadData) _state.update { s -> (s as LiveUiState.Data).copy(refreshing = true) }
        val next = withContext(Dispatchers.IO) { LiveApi.fetch() }
        _state.update { s ->
            when {
                next != null -> LiveUiState.Data(next, refreshing = false, stale = false)
                s is LiveUiState.Data -> s.copy(refreshing = false, stale = true) // keep last valid data
                else -> LiveUiState.Error(retrying = false)
            }
        }
    }

    fun retryNow() = viewModelScope.launch { fetchOnce() }
}

private object LiveApi {
    /** Returns the latest-day payload, or null on any network/parse failure (never throws). */
    fun fetch(): LiveDayData? = try {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(body).optJSONArray("data") ?: return null
            if (data.length() == 0) return null
            val entry = data.getJSONObject(0)
            LiveDayData(
                date = entry.optString("date", ""),
                morning = LiveSessionData(
                    modern = entry.optString("modern_930", "--"),
                    internet = entry.optString("internet_930", "--"),
                    set = entry.optString("set_1200", "--"),
                    value = entry.optString("val_1200", "--"),
                    result = entry.optString("result_1200", "--"),
                ),
                evening = LiveSessionData(
                    modern = entry.optString("modern_200", "--"),
                    internet = entry.optString("internet_200", "--"),
                    set = entry.optString("set_430", "--"),
                    value = entry.optString("val_430", "--"),
                    result = entry.optString("result_430", "--"),
                ),
            )
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
fun LiveScreen(onBack: () -> Unit) {
    val vm: LiveViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    // Poll only while the screen is visible; re-fetch immediately on return to the screen.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                vm.retryNow()
                delay(POLL_INTERVAL_MS)
            }
        }
    }
    AppScaffold(title = "2D LIVE", onBack = onBack) { padding ->
        when (val s = state) {
            LiveUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.PrimaryDeep)
            }
            is LiveUiState.Error -> LiveErrorState(onRetry = { vm.retryNow() }, modifier = Modifier.padding(padding))
            is LiveUiState.Data -> LiveContent(s.data, s.stale, s.refreshing, Modifier.padding(padding))
        }
    }
}

// ---- UI ----

@Composable
private fun LiveContent(data: LiveDayData, stale: Boolean, refreshing: Boolean, modifier: Modifier = Modifier) {
    val l = LocalLanguage.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiveStatusHeader(data.date, stale, refreshing)
        LiveSessionCard(l.text("မနက်", "Morning"), "09:30", "12:01", data.morning)
        LiveSessionCard(l.text("ညနေ", "Evening"), "14:00", "16:30", data.evening)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun LiveStatusHeader(date: String, stale: Boolean, refreshing: Boolean) {
    val l = LocalLanguage.current
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.elevatedCardColors(containerColor = AppColors.PrimaryDeep), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)) {
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFFD41452), Color(0xFF8D123A)))).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).background(Color(0xFFFF3158).copy(alpha = .35f), CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(21.dp).background(Color(0xFFFF173D), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(8.dp).background(Color.White, CircleShape))
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("2D LIVE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1, softWrap = false)
                Text(
                    when {
                        stale -> l.text("ချိတ်ဆက်မှုပြတ် — နောက်ဆုံးရ ဒေတာပြသနေသည်", "Connection lost — showing last valid data")
                        refreshing -> l.text("ပြန်ရယူနေသည်…", "Refreshing…")
                        else -> l.text("တိုက်ရိုက် နောက်ဆုံးရ", "Live") + (if (date.isNotBlank()) " • $date" else "")
                    },
                    style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, color = Color.White.copy(alpha = .85f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (stale) {
                Icon(Icons.Default.CloudOff, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(20.dp))
            } else {
                Icon(Icons.Default.Wifi, null, tint = Color.White.copy(alpha = .9f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun LiveSessionCard(sessionLabel: String, modernTime: String, setTime: String, session: LiveSessionData) {
    val l = LocalLanguage.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.width(5.dp).height(20.dp), shape = RoundedCornerShape(3.dp), color = AppColors.Primary) {}
            Text(sessionLabel, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppColors.Ink)
        }
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, AppColors.Stone), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveTimeLabel(modernTime)
                LiveValueRow(l.text("မော်ဒန်", "Modern"), session.modern)
                LiveValueRow(l.text("အင်တာနက်", "Internet"), session.internet)
                HorizontalDivider()
                LiveTimeLabel(setTime)
                LiveValueRow("SET", session.set)
                LiveValueRow(l.text("တန်ဖိုး", "VALUE"), session.value)
                LiveResultRow("2D", session.result)
            }
        }
    }
}

@Composable
private fun LiveTimeLabel(time: String) {
    Text(time, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
}

@Composable
private fun LiveValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppColors.PrimaryDeep, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun LiveResultRow(label: String, value: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = AppColors.Blush, border = BorderStroke(1.dp, AppColors.Stone.copy(alpha = .7f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
            Surface(shape = CircleShape, color = AppColors.PrimaryDeep) {
                Text(value, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun LiveErrorState(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val l = LocalLanguage.current
    Column(modifier = modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(Modifier.size(64.dp), shape = CircleShape, color = AppColors.Blush, border = BorderStroke(1.dp, AppColors.Stone)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CloudOff, null, tint = AppColors.PrimaryDeep, modifier = Modifier.size(28.dp))
            }
        }
        Text(l.text("ချိတ်ဆက်မှု မရှိပါ", "No connection"), Modifier.padding(top = 14.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppColors.Ink)
        Text(l.text("စာရင်းအသစ်ရယူ၍ မရပါ။ ကြိုးစားကြည့်ပါ။", "Could not fetch live data. Try again."), Modifier.padding(top = 4.dp).padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(
            onClick = onRetry,
            Modifier.padding(top = 16.dp).width(160.dp).height(44.dp),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryDeep, contentColor = Color.White),
        ) {
            Text(l.text("ပြန်စမ်းမည်", "Retry"), fontWeight = FontWeight.Bold)
        }
    }
}
