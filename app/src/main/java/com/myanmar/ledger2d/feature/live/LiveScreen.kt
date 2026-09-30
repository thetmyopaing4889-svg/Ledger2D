@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.live

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.myanmar.ledger2d.core.database.HistoryResultEntity
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.feature.main.OperationalScaffold
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.Flow

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

/**
 * Explicit UI states for the LIVE screen. The hero snapshot is carried
 * separately from the raw feed so the top hero can keep showing the latest
 * known value even when today's feed is empty (new day before 09:30 data).
 */
sealed interface LiveUiState {
    /** First fetch has not completed yet (or no cached hero exists). */
    data object Loading : LiveUiState

    /** Latest known state (feed may be null until the first successful fetch). */
    data class Data(
        val feed: LiveFeedData?,
        val hero: LiveHeroSnapshot?,
        val heroLive: Boolean,
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
fun LiveScreen(
    historyFlow: Flow<List<HistoryResultEntity>>,
    onBack: () -> Unit,
) {
    val collector = LiveCollector.instance
    val state by collector.state.collectAsStateWithLifecycle()
    val history by historyFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedTab by rememberSaveable { mutableStateOf("live") }
    var selectedDateText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { collector.fetchCycle() }

    val selectedDate = selectedDateText.takeIf { it.isNotBlank() }?.let {
        runCatching { LocalDate.parse(it) }.getOrNull()
    }

    OperationalScaffold(
        title = if (selectedTab == "calendar" && selectedDate != null) "2D Calendar" else "2D LIVE",
        onBack = onBack,
        bottomBar = {
            LiveBottomBar(selectedTab) {
                selectedTab = it
                if (it == "live") selectedDateText = ""
            }
        },
        content = { padding ->
            when (val s = state) {
            LiveUiState.Loading, is LiveUiState.Error -> LiveUnavailableState(
                loading = s == LiveUiState.Loading,
                onRetry = { collector.fetchCycle() },
                modifier = Modifier.padding(padding),
            )
            is LiveUiState.Data -> {
                if (selectedTab == "live") {
                    LiveContent(s.feed, s.hero, s.heroLive, s.stale, Modifier.padding(padding))
                } else if (selectedDate != null) {
                    LiveCalendarDetail(
                        history = history.firstOrNull { it.date == selectedDate },
                        date = selectedDate,
                        onBackToCalendar = { selectedDateText = "" },
                        modifier = Modifier.padding(padding),
                    )
                } else {
                    LiveCalendar(
                        history = history,
                        onDateSelected = { selectedDateText = it.toString() },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
)
}

@Composable
private fun LiveContent(feed: LiveFeedData?, hero: LiveHeroSnapshot?, heroLive: Boolean, stale: Boolean, modifier: Modifier = Modifier) {
    val l = LocalLanguage.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LiveHero(hero, heroLive, stale)
        // Session cards render pending "--" rows when today's feed is not yet
        // available; they never turn into blank/error placeholders. Both final
        // sessions share one row so the whole day is visible at a glance.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiveSessionCard(l.text("မနက်", "Morning"), LIVE_SESSION_MORNING_LABEL, feed?.morning, Modifier.weight(1f))
            LiveSessionCard(l.text("ညနေ", "Evening"), LIVE_SESSION_EVENING_LABEL, feed?.evening, Modifier.weight(1f))
        }
        LiveReferenceTable(feed)
        Spacer(Modifier.height(4.dp))
    }
}


@Composable
private fun LiveBottomBar(selected: String, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = selected == "live",
            onClick = { onSelect("live") },
            icon = { Icon(Icons.Default.CheckCircle, null) },
            label = { Text("Live") },
        )
        NavigationBarItem(
            selected = selected == "calendar",
            onClick = { onSelect("calendar") },
            icon = { Icon(Icons.Default.CalendarMonth, null) },
            label = { Text("Calendar") },
        )
    }
}

@Composable
private fun LiveCalendar(
    history: List<HistoryResultEntity>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestDate = history.maxOfOrNull { it.date } ?: LocalDate.now()
    val earliestDate = history.minOfOrNull { it.date } ?: latestDate
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(latestDate).toString()) }
    val month = runCatching { YearMonth.parse(monthText) }.getOrElse { YearMonth.from(latestDate) }
    val historyByDate = remember(history) { history.associateBy { it.date } }
    val monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.ENGLISH))
    val minMonth = YearMonth.from(earliestDate)
    val maxMonth = YearMonth.from(latestDate)
    val canPrevious = month > minMonth
    val canNext = month < maxMonth
    val firstMonday = month.atDay(1).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastFriday = month.atEndOfMonth().with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, AppColors.Stone),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { if (canPrevious) monthText = month.minusMonths(1).toString() },
                    enabled = canPrevious,
                    modifier = Modifier.size(46.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryDeep),
                ) { Text("‹", fontSize = 28.sp) }
                Text(
                    monthLabel,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = AppColors.Ink,
                )
                Button(
                    onClick = { if (canNext) monthText = month.plusMonths(1).toString() },
                    enabled = canNext,
                    modifier = Modifier.size(46.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryDeep),
                ) { Text("›", fontSize = 28.sp) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("MON", "TUE", "WED", "THU", "FRI").forEach {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = AppColors.Blush,
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Ink,
                    )
                }
            }
        }

        var weekStart = firstMonday
        while (!weekStart.isAfter(lastFriday)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (offset in 0..4) {
                    val date = weekStart.plusDays(offset.toLong())
                    val inMonth = date.month == month.month && date.year == month.year
                    val result = if (inMonth) historyByDate[date] else null
                    if (inMonth) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onDateSelected(date) },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, AppColors.Stone),
                            shadowElevation = 1.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 9.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Surface(shape = CircleShape, color = AppColors.Blush) {
                                    Text(
                                        date.dayOfMonth.toString().padStart(2, '0'),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AppColors.Ink,
                                    )
                                }
                                Text(
                                    result?.morning2d ?: PENDING,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (result != null) AppColors.PrimaryDeep else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    result?.evening2d ?: PENDING,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = if (result != null) AppColors.PrimaryDeep else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            weekStart = weekStart.plusWeeks(1)
        }

        Text(
            "တစ်ရက်ကိုနှိပ်ပါက အသေးစိတ်ကြည့်နိုင်သည်",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveCalendarDetail(
    history: HistoryResultEntity?,
    date: LocalDate,
    onBackToCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateLabel = date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", java.util.Locale.ENGLISH))
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onBackToCalendar,
                modifier = Modifier.size(44.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.PrimaryDeep),
            ) { Text("‹", fontSize = 26.sp) }
            Text(
                dateLabel,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = AppColors.Ink,
            )
            Spacer(Modifier.size(44.dp))
        }

        LiveDetailResultCard("12:01 PM", history?.morningSet ?: PENDING, history?.morningValue ?: PENDING, history?.morning2d ?: PENDING)
        LiveDetailResultCard("4:30 PM", history?.eveningSet ?: PENDING, history?.eveningValue ?: PENDING, history?.evening2d ?: PENDING)

        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, AppColors.Stone),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("REFERENCE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = AppColors.Gold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("TIME", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = AppColors.Ink)
                    Text("MODERN", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = AppColors.Gold, textAlign = TextAlign.Center)
                    Text("INTERNET", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = AppColors.Gold, textAlign = TextAlign.Center)
                }
                HorizontalDivider()
                LiveDetailReferenceRow("09:30", history?.modern930 ?: PENDING, history?.internet930 ?: PENDING)
                LiveDetailReferenceRow("14:00", history?.modern200 ?: PENDING, history?.internet200 ?: PENDING)
            }
        }
    }
}

@Composable
private fun LiveDetailResultCard(time: String, set: String, value: String, digit: String) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, AppColors.Stone),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().background(AppColors.PrimaryDeep).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(time, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = Color.White)
                Text("SET", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center)
                Text("VALUE", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center)
                Text("2D", Modifier.weight(.7f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .8f), textAlign = TextAlign.End)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("", Modifier.weight(1f))
                Text(set, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppColors.Ink, textAlign = TextAlign.Center)
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppColors.Ink, textAlign = TextAlign.Center)
                Text(digit, Modifier.weight(.7f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = AppColors.Gold, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun LiveDetailReferenceRow(time: String, modern: String, internet: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(time, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
        Text(modern, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppColors.Ink, textAlign = TextAlign.Center)
        Text(internet, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppColors.Ink, textAlign = TextAlign.Center)
    }
}

// ---- Hero: persistent; 🔴 LIVE only while collection is active -------------

@Composable
private fun LiveHero(hero: LiveHeroSnapshot?, isLive: Boolean, stale: Boolean) {
    val l = LocalLanguage.current
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.elevatedCardColors(containerColor = AppColors.PrimaryDeep), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)) {
        Column(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFFD41452), Color(0xFF8D123A)))).padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Status row: red LIVE only in live mode, otherwise stable Updated.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLive) {
                    LiveBlinkDot()
                    Text("LIVE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
                } else {
                    Icon(Icons.Default.CheckCircle, null, tint = AppColors.Success, modifier = Modifier.size(20.dp))
                    Text("✓ " + l.text("အသစ်ဖြစ်ပြီး", "Updated"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
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
            if (hero == null) {
                // No data known at all: honest pending state, not an error.
                Text("--", style = MaterialTheme.typography.displaySmall, fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text(l.text("စောင့်နေသည်", "Waiting for live data"), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .85f))
            } else {
                // Hero number: subtle blink in LIVE mode; stable otherwise.
                BlinkingBox(enabled = isLive) {
                    Text(
                        hero.result,
                        style = MaterialTheme.typography.displaySmall,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    if (isLive) listOf(hero.date, hero.sessionLabel).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "LIVE" }
                    else l.text("နောက်ဆုံးရလဒ်", "Latest final") + " • " + hero.sessionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = .85f),
                )
                if (hero.set.isNotBlank() && hero.set != LIVE_PENDING) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroChip("SET", hero.set)
                        HeroChip(l.text("တန်ဖိုး", "VALUE"), hero.value)
                    }
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
// Session cards (12:01 / 4:30) — two compact cards side by side; pending shows
// "--" rows, never blank/error. Data and session meaning are unchanged.
// ============================================================================

@Composable
private fun LiveSessionCard(sessionLabel: String, time: String, session: LiveSessionData?, modifier: Modifier = Modifier) {
    val l = LocalLanguage.current
    val finalized = session?.finalized == true
    Card(modifier, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, AppColors.Stone), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header: session name + exact final time, with Updated/Pending.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(sessionLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = AppColors.Ink)
                    Text(time, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
                }
                if (finalized) {
                    Icon(Icons.Default.CheckCircle, null, tint = AppColors.Success, modifier = Modifier.size(16.dp))
                    Text(l.text("ရပြီး", "Updated"), Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AppColors.Success)
                } else {
                    Text(l.text("စောင့်နေ", "Pending"), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Prominent 2D number (or "--" while pending) — the card's focal value.
            Text(
                if (finalized) session?.result ?: PENDING else PENDING,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displaySmall,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = if (finalized) AppColors.PrimaryDeep else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // SET / VALUE stay readable on half-width cards; "--" while pending.
            LiveValueRow("SET", if (finalized) session?.set ?: PENDING else PENDING)
            LiveValueRow(l.text("တန်ဖိုး", "VALUE"), if (finalized) session?.value ?: PENDING else PENDING)
        }
    }
}

@Composable
private fun LiveValueRow(label: String, value: String, highlight: Boolean = false, pending: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            value,
            style = if (highlight) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = when {
                pending -> MaterialTheme.colorScheme.onSurfaceVariant
                highlight -> AppColors.PrimaryDeep
                else -> AppColors.Ink
            },
            maxLines = 1,
            softWrap = false,
        )
    }
}

// ============================================================================
// Reference section (Modern / Internet) — secondary information, never finals
// ============================================================================

@Composable
private fun LiveReferenceTable(feed: LiveFeedData?) {
    val l = LocalLanguage.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, AppColors.Stone.copy(alpha = .8f)), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Gold-accented header with the feed date kept visible.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.width(4.dp).height(16.dp), shape = RoundedCornerShape(2.dp), color = AppColors.Gold) {}
                Text(l.text("အကြည့်စာရင်း", "Reference"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = AppColors.Ink)
                Spacer(Modifier.weight(1f))
                Text(feed?.date ?: PENDING, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AppColors.Gold)
            }
            // Compact grid: one row per reference time, one column per source.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("", Modifier.weight(1f))
                Text(l.text("မော်ဒန်", "Modern"), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AppColors.Gold, textAlign = TextAlign.Center)
                Text(l.text("အင်တာနက်", "Internet"), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = AppColors.Gold, textAlign = TextAlign.Center)
            }
            LiveRefRow("9:30 AM", feed?.modern930 ?: PENDING, feed?.internet930 ?: PENDING)
            HorizontalDivider()
            LiveRefRow("2:00 PM", feed?.modern200 ?: PENDING, feed?.internet200 ?: PENDING)
        }
    }
}

@Composable
private fun LiveRefRow(time: String, modern: String, internet: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(time, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
        Text(modern, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppColors.Ink, textAlign = TextAlign.Center)
        Text(internet, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AppColors.Ink, textAlign = TextAlign.Center)
    }
}

// ============================================================================
// Unavailable state — spinner only for a genuine first fetch; otherwise the
// retryable offline presentation. Never an indefinite spinner.
// ============================================================================

@Composable
private fun LiveUnavailableState(loading: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val l = LocalLanguage.current
    if (loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AppColors.PrimaryDeep)
        }
        return
    }
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
