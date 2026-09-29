@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.live

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.feature.main.AppScaffold
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime

// ============================================================================
// Configuration — isolated so the polling/animation behavior can be tuned later
// ============================================================================

/** Live API endpoint (verified Shwe Myanmar 2D live feed). */
private const val LIVE_ENDPOINT = "https://luke.2dboss.com/api/luke/twod-result-live"

// The polling interval (LIVE_POLL_INTERVAL_MS) lives in LiveCollector.kt so the
// app-scoped background collector and this screen share one tunable constant.

/** HTTP timeouts in ms. */
private const val LIVE_CONNECT_TIMEOUT_MS = 8_000
private const val LIVE_READ_TIMEOUT_MS = 8_000

/** Presentation-only blink half-cycle for the LIVE number (never blocks data). */
private const val LIVE_BLINK_DURATION_MS = 700

/** Local time from which the evening (4:30 PM) session is the relevant one. */
private val EVENING_PERIOD_START: LocalTime = LocalTime.of(14, 0)

/** Sentinel the API uses for "not available yet". */
private const val PENDING = "--"

// ============================================================================
// Data model (in-memory only — never persisted to Room)
// ============================================================================

/** Full live feed snapshot. */
data class LiveFeedData(
    val date: String,
    val currentTime: String,
    /** LIVE 2D value currently streaming. */
    val live: String,
    /** LIVE SET / VALUE accompanying the streaming 2D. */
    val liveSet: String,
    val liveVal: String,
    /** Finalized-or-pending 12:01 morning session. */
    val morning: LiveSessionData,
    /** Finalized-or-pending 4:30 evening session. */
    val evening: LiveSessionData,
    /** 09:30 reference values. */
    val modern930: String,
    val internet930: String,
    /** 02:00 reference values. */
    val modern200: String,
    val internet200: String,
)

/** One result session (12:01 morning / 4:30 evening). */
data class LiveSessionData(
    val result: String,
    val set: String,
    val value: String,
    /** True only when the verified result field is no longer "--". */
    val finalized: Boolean,
)

/** Explicit UI states for the LIVE screen. */
sealed interface LiveUiState {
    /** First fetch has not completed yet. */
    data object Loading : LiveUiState

    /** Data available (fresh or retained). */
    data class Data(
        val feed: LiveFeedData,
        /** True when the last fetch failed and this snapshot is from an earlier cycle. */
        val stale: Boolean,
    ) : LiveUiState

    /** No data yet and the latest fetch failed. */
    data class Error(val retrying: Boolean) : LiveUiState
}

// ============================================================================
// API client (used by the app-scoped LiveCollector)
// ============================================================================

internal object LiveApi {
    /** Returns the parsed feed, or null on any network/parse failure (never throws). */
    fun fetch(): LiveFeedData? {
        return try {
            val connection = URL(LIVE_ENDPOINT).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = LIVE_CONNECT_TIMEOUT_MS
                connection.readTimeout = LIVE_READ_TIMEOUT_MS
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode !in 200..299) return null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val data = JSONObject(body).optJSONObject("data") ?: return null
                LiveFeedData(
                    date = data.nonBlankString("date", fallback = ""),
                    currentTime = data.nonBlankString("current_time", fallback = ""),
                    live = data.nonBlankString("live"),
                    liveSet = data.nonBlankString("live_set"),
                    liveVal = data.nonBlankString("live_val"),
                    morning = LiveSessionData(
                        result = data.nonBlankString("result_1200"),
                        set = data.nonBlankString("set_1200"),
                        value = data.nonBlankString("val_1200"),
                        finalized = data.nonBlankString("result_1200") != PENDING,
                    ),
                    evening = LiveSessionData(
                        result = data.nonBlankString("result_430"),
                        set = data.nonBlankString("set_430"),
                        value = data.nonBlankString("val_430"),
                        finalized = data.nonBlankString("result_430") != PENDING,
                    ),
                    modern930 = data.nonBlankString("modern_930"),
                    internet930 = data.nonBlankString("internet_930"),
                    modern200 = data.nonBlankString("modern_200"),
                    internet200 = data.nonBlankString("internet_200"),
                )
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Reads a string field, normalizing JSON null / blank / "null" to "--" (or a fallback). */
    private fun JSONObject.nonBlankString(name: String, fallback: String = PENDING): String {
        if (!has(name) || isNull(name)) return fallback
        val value = optString(name, "").trim()
        return if (value.isEmpty() || value == "null") fallback else value
    }
}

// ============================================================================
// Screen
// ============================================================================

@Composable
fun LiveScreen(onBack: () -> Unit) {
    // The collector is app-scoped: this screen renders the latest cached state
    // immediately and keeps observing it; closing the screen never stops the
    // scheduled background collection.
    val collector = LiveCollector.instance
    val state by collector.state.collectAsStateWithLifecycle()

    // One window-gated freshness check when the screen opens.
    LaunchedEffect(Unit) {
        if (liveWindowAction(LocalTime.now()) != LiveWindowAction.NONE) {
            collector.fetchCycle()
        }
    }

    AppScaffold(title = "2D LIVE", onBack = onBack) { padding ->
        when (val s = state) {
            LiveUiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.PrimaryDeep)
            }
            is LiveUiState.Error -> LiveErrorState(onRetry = { collector.fetchCycle() }, modifier = Modifier.padding(padding))
            is LiveUiState.Data -> LiveContent(s.feed, s.stale, Modifier.padding(padding))
        }
    }
}

@Composable
private fun LiveContent(feed: LiveFeedData, stale: Boolean, modifier: Modifier = Modifier) {
    val l = LocalLanguage.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiveHero(feed, stale)
        LiveResultCard(l.text("မနက်", "Morning"), "12:01 PM", feed.morning)
        LiveResultCard(l.text("ညနေ", "Evening"), "4:30 PM", feed.evening)
        LiveReferenceSection(feed)
        Spacer(Modifier.height(4.dp))
    }
}

// ---- Hero: 🔴 LIVE ↔ 🟢 ✓ Updated ---------------------------------------

/**
 * Hero session selection: while the morning 12:01 result is pending the hero
 * represents the morning LIVE period; after 2:00 PM local time it represents
 * the evening 4:30 period. The hero turns green only when the relevant
 * session's verified result field is no longer "--".
 */
private fun relevantHeroSession(feed: LiveFeedData): LiveSessionData =
    if (!LocalTime.now().isBefore(EVENING_PERIOD_START)) feed.evening else feed.morning

@Composable
private fun LiveHero(feed: LiveFeedData, stale: Boolean) {
    val l = LocalLanguage.current
    val heroSession = relevantHeroSession(feed)
    val heroFinalized = heroSession.finalized
    val heroTime = if (heroSession === feed.evening) "4:30 PM" else "12:01 PM"

    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = AppColors.PrimaryDeep), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFD41452), Color(0xFF8D123A)))).padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status row: red LIVE vs green Updated, plus an offline chip when stale.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (heroFinalized) {
                    Icon(Icons.Default.CheckCircle, null, tint = AppColors.Success, modifier = Modifier.size(20.dp))
                    Text("✓ " + l.text("အသစ်ဖြစ်ပြီး", "Updated"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
                } else {
                    LiveBlinkDot()
                    Text("LIVE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
                }
                if (stale) {
                    Surface(shape = RoundedCornerShape(999.dp), color = Color.White.copy(alpha = .18f)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CloudOff, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Text(l.text("နောက်ဆုံးရ", "Last data"), style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Hero number: subtle blink in LIVE mode; visually stable once finalized.
            BlinkingBox(enabled = !heroFinalized) {
                Text(
                    if (heroFinalized) heroSession.result else feed.live,
                    style = MaterialTheme.typography.displaySmall,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                if (heroFinalized) l.text("နောက်ဆုံးရလဒ်", "Final result") + " • " + heroTime
                else listOf(feed.date, feed.currentTime).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "LIVE" },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = .85f),
            )
            val chipSet = if (heroFinalized) heroSession.set else feed.liveSet
            val chipVal = if (heroFinalized) heroSession.value else feed.liveVal
            if (chipSet.isNotBlank() && chipSet != PENDING) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroChip("SET", chipSet)
                    HeroChip(l.text("တန်ဖိုး", "VALUE"), chipVal)
                }
            }
        }
    }
}

@Composable
private fun HeroChip(label: String, value: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .16f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .8f))
            Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}

@Composable
private fun LiveBlinkDot() {
    BlinkingBox(enabled = true) {
        Box(Modifier.size(12.dp).background(Color(0xFFFF3158), CircleShape))
    }
}

/**
 * Presentation-only blink. The infinite alpha fade never observes or gates
 * data — a new API value renders through this wrapper immediately, and the
 * wrapper simply stops animating (stable) when `enabled` is false.
 */
@Composable
private fun BlinkingBox(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    val alpha by rememberInfiniteTransition(label = "live-blink").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(LIVE_BLINK_DURATION_MS), RepeatMode.Reverse),
        label = "live-blink-alpha",
    )
    Box(Modifier.graphicsLayer { this.alpha = alpha }) { content() }
}

// ============================================================================
// Result cards (12:01 / 4:30)
// ============================================================================

@Composable
private fun LiveResultCard(sessionLabel: String, time: String, session: LiveSessionData) {
    val l = LocalLanguage.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, AppColors.Stone), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.width(5.dp).height(20.dp), shape = RoundedCornerShape(3.dp), color = AppColors.Primary) {}
                Text("$sessionLabel • $time", Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppColors.Ink)
                if (session.finalized) {
                    Icon(Icons.Default.CheckCircle, null, tint = AppColors.Success, modifier = Modifier.size(18.dp))
                    Text(l.text("ရပြီး", "Updated"), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AppColors.Success)
                } else {
                    LiveBlinkDot()
                    Text(l.text("စောင့်နေ", "Waiting"), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (session.finalized) {
                LiveValueRow("2D", session.result, highlight = true)
                LiveValueRow("SET", session.set)
                LiveValueRow(l.text("တန်ဖိုး", "VALUE"), session.value)
            } else {
                Text(
                    l.text("ရလဒ် မထွက်သေးပါ", "Result not announced yet"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LiveValueRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            style = if (highlight) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = if (highlight) AppColors.PrimaryDeep else AppColors.Ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ============================================================================
// Reference section (Modern / Internet) — secondary information
// ============================================================================

@Composable
private fun LiveReferenceSection(feed: LiveFeedData) {
    val l = LocalLanguage.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, AppColors.Stone.copy(alpha = .8f)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.width(5.dp).height(20.dp), shape = RoundedCornerShape(3.dp), color = AppColors.Gold) {}
                Text(l.text("အကြည့်စာရင်း", "Reference"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppColors.Ink)
                Spacer(Modifier.weight(1f))
                Text(feed.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LiveRefTime("9:30 AM", feed.modern930, feed.internet930)
            HorizontalDivider()
            LiveRefTime("2:00 PM", feed.modern200, feed.internet200)
        }
    }
}

@Composable
private fun LiveRefTime(time: String, modern: String, internet: String) {
    val l = LocalLanguage.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(time, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
        LiveValueRow(l.text("မော်ဒန်", "Modern"), modern)
        LiveValueRow(l.text("အင်တာနက်", "Internet"), internet)
    }
}

// ============================================================================
// Error state (no data yet + fetch failed)
// ============================================================================

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
        Text(l.text("လိုင်းဒေတာ ရယူ၍ မရပါ။ ပြန်စမ်းကြည့်ပါ။", "Could not fetch live data. Try again."), Modifier.padding(top = 4.dp).padding(horizontal = 24.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
