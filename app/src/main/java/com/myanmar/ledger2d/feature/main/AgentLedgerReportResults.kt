@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun Long.reportAgentMoney(): String =
    NumberFormat.getIntegerInstance(Locale.US).format(this)
private fun String.wrapAgentMoney(): String = replace(",", ",\u200B")

/**
 * Display frame only. Every working date has a slot even without bets.
 * Winners remain sourced solely from the operational winning_numbers table,
 * never from read-only History Result.
 */
internal data class AgentLedgerSlot(
    val date: LocalDate,
    val session: DrawSession,
    val draws: List<AgentStatementDraw>,
    val winner: String?,
    val closed: Boolean,
) {
    val totals: AgentStatementTotals get() = AgentStatementCalculator.summarize(draws)
}

internal object AgentLedgerFrame {
    fun rows(
        page: CustomerReportPage,
        period: StatementPeriod,
        selectedSession: DrawSession?,
        draws: List<AgentStatementDraw>,
        winners: Map<Pair<LocalDate, DrawSession>, String>,
        closed: Set<LocalDate>,
    ): List<AgentLedgerSlot> {
        val byDraw = draws.groupBy { it.date to it.session }
        return CustomerReportFrame.slots(
            page, selectedSession, includeWeekend = period == StatementPeriod.DAY,
        ).map { slot ->
            val matching = byDraw[slot.date to slot.session].orEmpty()
            AgentLedgerSlot(
                slot.date, slot.session, matching,
                matching.firstOrNull()?.winner ?: winners[slot.date to slot.session],
                slot.date in closed || slot.date.dayOfWeek.value > 5,
            )
        }
    }

    /** User-facing directions: first is user receives, second is user pays. */
    fun userDirections(draws: List<AgentStatementDraw>): Pair<Long, Long> {
        val (userPays, agentsPay) = AgentStatementCalculator.settlementByAgent(draws)
        return agentsPay to userPays
    }
}

private object AgentLedgerColors {
    val Wine = Color(0xFFA51639)
    val Dark = Color(0xFF82112D)
    val Header = Color(0xFFB63C5D)
    val Blue = Color(0xFFDFEFFC)
    val BlueInk = Color(0xFF175D91)
    val Lilac = Color(0xFFEDE8FB)
    val LilacInk = Color(0xFF62459A)
    val Rose = Color(0xFFFBE2EA)
    val RoseInk = Color(0xFF9A2447)
    val Amber = Color(0xFFFFF0D8)
    val AmberInk = Color(0xFF986116)
    val Green = Color(0xFFE3F5E8)
    val GreenInk = Color(0xFF17763E)
    val RedInk = Color(0xFFB42342)
    val SoftPink = Color(0xFFFCE9EF)
    val WeekTint = Color(0xFFF8EEF2)
    val WeekLabel = Color(0xFFF5DDE6)
    val Edge = Color(0xFFEBD9DF)
    val Ink = Color(0xFF211B20)
    val Muted = Color(0xFF70676E)
}

@Composable
private fun AgentMetricCard(
    label: String, value: String, icon: ImageVector, panel: Color, ink: Color,
    modifier: Modifier = Modifier, note: String? = null,
) {
    val lang = LocalLanguage.current
    Surface(
        modifier = modifier, color = panel, shape = RoundedCornerShape(14.dp),
        shadowElevation = 3.dp, tonalElevation = 0.dp,
        border = BorderStroke(1.dp, ink.copy(alpha = .12f)),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(icon, null, tint = ink, modifier = Modifier.size(14.dp))
                Text(label, style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang.code)
                    .copy(fontWeight = FontWeight.SemiBold, color = ink), maxLines = 2)
            }
            Text(value.wrapAgentMoney(),
                style = MaterialTheme.typography.titleSmall.copy(color = ink, fontWeight = FontWeight.Bold),
                maxLines = 3, overflow = TextOverflow.Visible, softWrap = true)
            if (note != null) Text(note.wrapAgentMoney(),
                style = MaterialTheme.typography.labelSmall.copy(color = ink),
                maxLines = 3, softWrap = true)
        }
    }
}

@Composable
private fun AgentClearingValue(label: String, amount: Long, color: Color, modifier: Modifier = Modifier) {
    val lang = LocalLanguage.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = color, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall, maxLines = 2)
        Text(amount.reportAgentMoney().wrapAgentMoney(),
            style = LedgerTypographyStandard.style(LedgerTextRole.MONEY, lang.code)
                .copy(color = color, fontWeight = FontWeight.Bold),
            softWrap = true, maxLines = 3, overflow = TextOverflow.Visible)
    }
}

@Composable
private fun AgentClearingCard(draws: List<AgentStatementDraw>, modifier: Modifier = Modifier) {
    val lang = LocalLanguage.current
    val totals = AgentStatementCalculator.summarize(draws)
    val (receive, pay) = AgentLedgerFrame.userDirections(draws)
    Surface(
        modifier = modifier, color = AgentLedgerColors.Green,
        shape = RoundedCornerShape(14.dp), shadowElevation = 3.dp, tonalElevation = 0.dp,
        border = BorderStroke(1.dp, AgentLedgerColors.GreenInk.copy(alpha = .14f)),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Handshake, null,
                    tint = AgentLedgerColors.GreenInk, modifier = Modifier.size(17.dp))
                Text(lang.text("ငွေရှင်းရန်", "Amount due"),
                    style = LedgerTypographyStandard.style(LedgerTextRole.CARD_TITLE, lang.code)
                        .copy(fontWeight = FontWeight.Bold, color = AgentLedgerColors.Ink))
            }
            when {
                draws.isEmpty() -> Text("—", color = AgentLedgerColors.Muted)
                totals.completedDraws == 0 -> Text(lang.text("ရလဒ်စောင့်", "Pending"),
                    color = AgentLedgerColors.Muted)
                receive > 0 && pay > 0 -> Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AgentClearingValue(lang.text("မိမိရရန်", "User receives"),
                        receive, AgentLedgerColors.GreenInk, Modifier.weight(1f))
                    AgentClearingValue(lang.text("မိမိပေးရန်", "User pays"),
                        pay, AgentLedgerColors.RedInk, Modifier.weight(1f))
                }
                receive > 0 -> AgentClearingValue(lang.text("မိမိရရန်", "User receives"),
                    receive, AgentLedgerColors.GreenInk)
                pay > 0 -> AgentClearingValue(lang.text("မိမိပေးရန်", "User pays"),
                    pay, AgentLedgerColors.RedInk)
                else -> AgentClearingValue(lang.text("ရှင်းရန်မရှိ", "Nothing due"),
                    0, AgentLedgerColors.GreenInk)
            }
            if (totals.pendingDraws > 0)
                Text(lang.text("စောင့် " + totals.pendingDraws + " ကြိမ်",
                    totals.pendingDraws.toString() + " pending"),
                    style = MaterialTheme.typography.labelSmall, color = AgentLedgerColors.Muted)
        }
    }
}

@Composable
private fun AgentReportSummary(
    draws: List<AgentStatementDraw>, period: StatementPeriod, agentCount: Int?,
) {
    val lang = LocalLanguage.current
    val total = AgentStatementCalculator.summarize(draws)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp), shadowElevation = 3.dp, tonalElevation = 0.dp,
        border = BorderStroke(1.dp, AgentLedgerColors.Edge),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (period == StatementPeriod.YEAR || period == StatementPeriod.CUSTOM)
                        lang.text("ကာလချုပ်", "Period total")
                    else lang.text("စာရင်းချုပ်", "Summary"),
                    style = LedgerTypographyStandard.style(LedgerTextRole.SECTION_TITLE, lang.code)
                        .copy(color = AgentLedgerColors.Ink, fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f))
                if (agentCount != null)
                    Text(lang.text("ဒိုင် " + agentCount + " ယောက်",
                        agentCount.toString() + " agents"),
                        style = MaterialTheme.typography.labelSmall,
                        color = AgentLedgerColors.Muted)
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AgentMetricCard(lang.text("ထိုးကွက်", "Slots"), total.digitSlots.reportAgentMoney(),
                    Icons.Default.Layers, AgentLedgerColors.Blue, AgentLedgerColors.BlueInk,
                    Modifier.weight(1f).fillMaxHeight())
                AgentMetricCard(lang.text("ထိုးကြေး", "Stake"), total.totalStake.reportAgentMoney(),
                    Icons.Default.Paid, AgentLedgerColors.Lilac, AgentLedgerColors.LilacInk,
                    Modifier.weight(1f).fillMaxHeight())
                AgentMetricCard(lang.text("လျော်ငွေ", "Payout"),
                    if (total.completedDraws > 0) total.payout.reportAgentMoney() else "—",
                    Icons.Default.EmojiEvents, AgentLedgerColors.Rose, AgentLedgerColors.RoseInk,
                    Modifier.weight(1f).fillMaxHeight(),
                    lang.text("ပေါက်ကြေး ", "Win stake ") +
                        (if (total.completedDraws > 0) total.winningStake.reportAgentMoney() else "—"))
            }
            // Exactly 1 + 2 grid units: commission occupies one card width,
            // clearing occupies two, with no extra full-width row.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AgentMetricCard(lang.text("ကော်မရှင်", "Commission"),
                    total.savedNetCommission.reportAgentMoney(),
                    Icons.Default.Paid, AgentLedgerColors.Amber, AgentLedgerColors.AmberInk,
                    Modifier.weight(1f).fillMaxHeight())
                AgentClearingCard(draws, Modifier.weight(2f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun AgentDue(draws: List<AgentStatementDraw>) {
    val lang = LocalLanguage.current
    val style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang.code)
        .copy(fontWeight = FontWeight.SemiBold)
    val t = AgentStatementCalculator.summarize(draws)
    if (draws.isEmpty()) {
        Text("—", style = style, color = AgentLedgerColors.Muted)
        return
    }
    if (t.completedDraws == 0) {
        Text(lang.text("စောင့်", "Pending"), style = style, color = AgentLedgerColors.Muted)
        return
    }
    val (receive, pay) = AgentLedgerFrame.userDirections(draws)
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        if (receive > 0) Text(receive.reportAgentMoney().wrapAgentMoney(),
            style = style, color = AgentLedgerColors.GreenInk,
            textAlign = TextAlign.End, softWrap = true, maxLines = 3)
        if (pay > 0) Text(("-" + pay.reportAgentMoney()).wrapAgentMoney(),
            style = style, color = AgentLedgerColors.RedInk,
            textAlign = TextAlign.End, softWrap = true, maxLines = 3)
        if (receive == 0L && pay == 0L)
            Text("0", style = style, color = AgentLedgerColors.Ink)
        if (t.pendingDraws > 0)
            Text(lang.text("စောင့်", "Pending"),
                style = MaterialTheme.typography.labelSmall, color = AgentLedgerColors.Muted)
    }
}

@Composable
private fun RowScope.AgentCell(
    value: String, weight: Float, header: Boolean = false,
    align: TextAlign = TextAlign.End, strong: Boolean = false,
) {
    val lang = LocalLanguage.current
    Text(if (header) value else value.wrapAgentMoney(), modifier = Modifier.weight(weight),
        style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang.code)
            .copy(fontWeight = if (strong || header) FontWeight.Bold else FontWeight.Medium,
                color = if (header) Color.White else AgentLedgerColors.Ink),
        textAlign = align, maxLines = 4, softWrap = true)
}

@Composable
private fun AgentTableHeader(
    view: String, groupedByAgent: Boolean,
    onToggleView: () -> Unit, onToggleGrouping: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null, searchOpen: Boolean = false,
) {
    val lang = LocalLanguage.current
    val doubleTap = if (onToggleGrouping == null) Modifier else
        Modifier.pointerInput(onToggleGrouping) {
            detectTapGestures(onDoubleTap = { onToggleGrouping.invoke() })
        }
    Column {
        Surface(color = AgentLedgerColors.Dark) {
            Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleView, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.ChevronLeft, lang.text("ဇယားပြောင်းရန်", "Switch view"),
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(if (view == "stake") lang.text("ထိုးစာရင်း", "Stakes")
                        else lang.text("ပေါက်စာရင်း", "Results"),
                        style = LedgerTypographyStandard.style(LedgerTextRole.CARD_TITLE, lang.code)
                            .copy(fontWeight = FontWeight.Bold, color = Color.White),
                        maxLines = 1)
                }
                if (onSearch != null)
                    IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                        Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            lang.text("ဒိုင်ရှာရန်", "Search agents"),
                            tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                IconButton(onClick = onToggleView, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Default.ChevronRight, lang.text("ဇယားပြောင်းရန်", "Switch view"),
                        tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
        Surface(color = AgentLedgerColors.Header) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1.25f).heightIn(min = 30.dp)
                    .then(if (onToggleGrouping == null) Modifier
                    else Modifier.combinedClickable(onClick = onToggleGrouping,
                        onDoubleClick = onToggleGrouping)),
                    contentAlignment = Alignment.CenterStart) {
                    Text(if (groupedByAgent) lang.text("ဒိုင်", "Agent")
                        else lang.text("ရက်/ကြိမ်", "Date/Draw"),
                        style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang.code)
                            .copy(fontWeight = FontWeight.Bold, color = Color.White),
                        maxLines = 2, softWrap = true,
                        textDecoration = if (onToggleGrouping != null) TextDecoration.Underline
                            else TextDecoration.None)
                }
                val labels = if (view == "stake") listOf(
                    lang.text("ကွက်", "Slots"), lang.text("ထိုးကြေး", "Stake"),
                    lang.text("ကော်မရှင်", "Commission"), lang.text("ရှင်းရန်", "Due")
                ) else listOf(
                    lang.text("ဂဏန်း", "Digit"), lang.text("ပေါက်ကြေး", "Win stake"),
                    lang.text("လျော်ငွေ", "Payout"), lang.text("ရှင်းရန်", "Due")
                )
                listOf(.65f, 1.25f, 1.15f, 1.27f).forEachIndexed { index, weight ->
                    Box(Modifier.weight(weight).then(doubleTap),
                        contentAlignment = Alignment.CenterEnd) {
                        Text(labels[index],
                            style = LedgerTypographyStandard.style(LedgerTextRole.TABLE, lang.code)
                                .copy(fontWeight = FontWeight.Bold, color = Color.White),
                            textAlign = TextAlign.End, maxLines = 2, softWrap = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.AgentDataColumns(
    draws: List<AgentStatementDraw>, view: String, winner: String? = null,
    groupTotal: Boolean = false,
) {
    val t = AgentStatementCalculator.summarize(draws)
    val lang = LocalLanguage.current
    val hasData = draws.isNotEmpty()
    if (view == "stake") {
        AgentCell(if (hasData) t.digitSlots.reportAgentMoney() else "—", .65f)
        AgentCell(if (hasData) t.totalStake.reportAgentMoney() else "—", 1.25f)
        AgentCell(if (hasData) t.savedNetCommission.reportAgentMoney() else "—", 1.15f)
    } else {
        AgentCell(if (groupTotal) "—" else winner ?: "—", .65f)
        AgentCell(if (t.completedDraws > 0) t.winningStake.reportAgentMoney() else "—", 1.25f)
        AgentCell(if (t.completedDraws > 0) t.payout.reportAgentMoney() else "—", 1.15f)
    }
    Box(Modifier.weight(1.27f), contentAlignment = Alignment.CenterEnd) {
        AgentDue(draws)
    }
}

@Composable
private fun AgentSessionRow(slot: AgentLedgerSlot, view: String, showDate: Boolean, onClick: () -> Unit) {
    val lang = LocalLanguage.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (showDate)
                Text(slot.date.format(DateTimeFormatter.ofPattern("dd EEE", Locale.US)),
                    style = MaterialTheme.typography.labelMedium,
                    color = AgentLedgerColors.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(if (slot.session == DrawSession.MORNING) lang.text("မနက်", "AM")
                else lang.text("ညနေ", "PM"),
                style = MaterialTheme.typography.labelSmall, color = AgentLedgerColors.Muted)
        }
        AgentDataColumns(slot.draws, view, slot.winner)
    }
}

@Composable
private fun AgentDayRows(
    slots: List<AgentLedgerSlot>, view: String, shade: Boolean,
    onOpen: (LocalDate, DrawSession) -> Unit,
) {
    Surface(color = if (shade) AgentLedgerColors.WeekTint else Color.White) {
        Column {
            slots.forEachIndexed { index, slot ->
                AgentSessionRow(slot, view, showDate = index == 0) {
                    onOpen(slot.date, slot.session)
                }
                if (index < slots.lastIndex)
                    HorizontalDivider(Modifier.padding(start = 42.dp),
                        thickness = .5.dp, color = AgentLedgerColors.Edge.copy(alpha = .45f))
            }
            HorizontalDivider(thickness = 1.15.dp, color = AgentLedgerColors.Edge)
        }
    }
}

@Composable
private fun AgentWeekBand(page: CustomerReportPage, date: LocalDate) {
    val lang = LocalLanguage.current
    val monday = CustomerReportFrame.weekStart(date)
    val start = maxOf(page.start, monday)
    val end = minOf(page.end, monday.plusDays(4))
    Surface(color = AgentLedgerColors.WeekLabel) {
        Text(lang.text("အပတ် ", "Week ") +
            (CustomerReportFrame.weekBandIndex(page, date) + 1) + " · " +
            start.format(DateTimeFormatter.ofPattern("dd MMM", Locale.US)) + " – " +
            end.format(DateTimeFormatter.ofPattern("dd MMM", Locale.US)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, color = AgentLedgerColors.Dark)
    }
}

@Composable
private fun AgentCalendarRows(
    page: CustomerReportPage, period: StatementPeriod, session: DrawSession?,
    draws: List<AgentStatementDraw>,
    winners: Map<Pair<LocalDate, DrawSession>, String>, closed: Set<LocalDate>,
    view: String, onOpen: (LocalDate, DrawSession) -> Unit,
) {
    val lang = LocalLanguage.current
    val slots = remember(page, period, session, draws, winners, closed) {
        AgentLedgerFrame.rows(page, period, session, draws, winners, closed)
    }
    if (slots.isEmpty()) {
        Text(lang.text("ဤကာလတွင် အလုပ်လုပ်ရက်မရှိပါ", "No working days"),
            Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodySmall)
        return
    }
    val groupWeeks = period == StatementPeriod.MONTH || period == StatementPeriod.YEAR ||
        period == StatementPeriod.CUSTOM
    var lastWeek: Long? = null
    slots.groupBy { it.date }.forEach { (date, daySlots) ->
        val week = CustomerReportFrame.weekBandIndex(page, date)
        if (groupWeeks && week != lastWeek) AgentWeekBand(page, date)
        lastWeek = week
        AgentDayRows(daySlots, view, week % 2L != 0L, onOpen)
    }
}

@Composable
private fun AgentTotalRow(label: String, draws: List<AgentStatementDraw>, view: String) {
    val lang = LocalLanguage.current
    val t = AgentStatementCalculator.summarize(draws)
    Surface(color = AgentLedgerColors.SoftPink) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1.25f)) {
                Text(label, style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold, color = AgentLedgerColors.Dark, maxLines = 2)
                if (t.pendingDraws > 0)
                    Text(lang.text("စောင့် " + t.pendingDraws, "Pending " + t.pendingDraws),
                        style = MaterialTheme.typography.labelSmall, color = AgentLedgerColors.Muted)
            }
            AgentDataColumns(draws, view, groupTotal = true)
        }
    }
}

@Composable
private fun AgentReportTable(
    page: CustomerReportPage, period: StatementPeriod, session: DrawSession?,
    draws: List<AgentStatementDraw>,
    winners: Map<Pair<LocalDate, DrawSession>, String>, closed: Set<LocalDate>,
    view: String, grouped: Boolean, agentNames: Map<Long, String>,
    search: String, searchOpen: Boolean, expandedAgent: Long,
    onToggleView: () -> Unit, onToggleGrouping: (() -> Unit)?,
    onToggleSearch: () -> Unit, onSearchChange: (String) -> Unit,
    onCloseSearch: () -> Unit, onToggleAgent: (Long) -> Unit,
    onOpen: (Long, LocalDate, DrawSession) -> Unit,
) {
    val lang = LocalLanguage.current
    val visiblePageDraws = draws.filter { !it.date.isBefore(page.start) && !it.date.isAfter(page.end) }
    val listedIds = visiblePageDraws.map { it.agentId }.distinct()
        .sortedWith(compareBy<Long> { agentNames[it]?.lowercase(Locale.ROOT) ?: it.toString() }
            .thenBy { it })
        .filter { (agentNames[it] ?: "Agent #$it").contains(search, ignoreCase = true) }
    val shownDraws = if (grouped) visiblePageDraws.filter { it.agentId in listedIds }
        else visiblePageDraws
    Surface(color = Color.White, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AgentLedgerColors.Edge),
        modifier = Modifier.fillMaxWidth()) {
        Column {
            AgentTableHeader(view, groupedByAgent = grouped,
                onToggleView = onToggleView, onToggleGrouping = onToggleGrouping,
                onSearch = if (grouped) onToggleSearch else null, searchOpen = searchOpen)
            if (grouped && searchOpen) {
                OutlinedTextField(search, onSearchChange,
                    placeholder = { Text(lang.text("ဒိုင်ရှာရန်", "Search agents")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        IconButton(onClick = onCloseSearch) {
                            Icon(Icons.Default.Close, lang.text("ပိတ်ရန်", "Close"))
                        }
                    })
            }
            if (grouped) {
                if (listedIds.isEmpty()) {
                    Text(lang.text("ကိုက်ညီသော ဒိုင်မရှိပါ", "No matching agents"),
                        Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    AgentCalendarRows(page, period, session, emptyList(), winners, closed, view) {
                        date, draw -> onOpen(-1L, date, draw)
                    }
                }
                listedIds.forEach { id ->
                    val agentDraws = visiblePageDraws.filter { it.agentId == id }
                    val name = agentNames[id] ?: "Agent #$id"
                    val open = id == expandedAgent
                    Surface(color = AgentLedgerColors.SoftPink, onClick = { onToggleAgent(id) }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1.25f)) {
                                Text(name, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold, color = AgentLedgerColors.Dark,
                                    maxLines = 2)
                                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    null, modifier = Modifier.size(15.dp),
                                    tint = AgentLedgerColors.Wine)
                            }
                            AgentDataColumns(agentDraws, view, groupTotal = true)
                        }
                    }
                    HorizontalDivider(thickness = .8.dp, color = AgentLedgerColors.Edge)
                    if (open)
                        AgentCalendarRows(page, period, session, agentDraws,
                            winners, closed, view) { date, draw -> onOpen(id, date, draw) }
                }
            } else {
                AgentCalendarRows(page, period, session, visiblePageDraws,
                    winners, closed, view) { date, draw -> onOpen(-1L, date, draw) }
            }
            AgentTotalRow(if (period == StatementPeriod.MONTH || period == StatementPeriod.YEAR ||
                period == StatementPeriod.CUSTOM) lang.text("လချုပ်", "Month total")
                else lang.text("စုစုပေါင်း", "Total"), shownDraws, view)
        }
    }
}

@Composable
fun AgentLedgerReportResults(
    vm: LedgerViewModel, agentId: Long, period: StatementPeriod, range: StatementRange,
    session: DrawSession?, subtitle: String, onReselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lang = LocalLanguage.current
    val revision by vm.revision.collectAsStateWithLifecycle()
    val agents by vm.agents.collectAsStateWithLifecycle()
    val winners by vm.winners.collectAsStateWithLifecycle()
    val closedDays by vm.closedDays.collectAsStateWithLifecycle()
    val data by produceState<AgentStatement?>(null, agentId, range, session, revision) {
        value = null
        value = vm.agentStatement(agentId, range, session)
    }
    val customerNames by produceState<Map<Long, String>>(emptyMap(), data?.agentIds, revision) {
        value = data?.let { vm.agentStatementCustomerNames(it.agentIds) }.orEmpty()
    }
    val agentNames = agents.associate { it.id to it.name }
    val pages = remember(period, range) { CustomerReportFrame.pages(period, range) }
    val initialMonth = remember(period, range) {
        val today = LocalDate.now()
        if (period == StatementPeriod.YEAR && range.contains(today))
            pages.indexOfFirst { !today.isBefore(it.start) && !today.isAfter(it.end) }.coerceAtLeast(0)
        else 0
    }
    var monthIndex by rememberSaveable(agentId, period, range.start, range.end) {
        mutableIntStateOf(initialMonth)
    }
    val page = pages[monthIndex.coerceIn(0, pages.lastIndex)]
    var tableView by rememberSaveable(agentId) { mutableStateOf("stake") }
    var groupView by rememberSaveable(agentId) { mutableStateOf("agents") }
    var search by rememberSaveable(agentId) { mutableStateOf("") }
    var searchOpen by rememberSaveable(agentId) { mutableStateOf(false) }
    var expandedAgent by rememberSaveable(agentId) { mutableLongStateOf(-1L) }
    var detailDate by rememberSaveable(agentId) { mutableStateOf<String?>(null) }
    var detailSession by rememberSaveable(agentId) { mutableStateOf<String?>(null) }
    var detailAgentId by rememberSaveable(agentId) { mutableLongStateOf(-1L) }

    val winnerMap = remember(winners, range) {
        winners.filter { range.contains(it.date) }
            .associate { (it.date to it.session) to it.digit }
    }
    val closed = remember(closedDays) { closedDays.map { it.date }.toSet() }
    val statement = data
    val switchView = { tableView = if (tableView == "stake") "win" else "stake" }
    val switchGrouping = {
        groupView = if (groupView == "agents") "total" else "agents"
        search = ""
        searchOpen = false
        expandedAgent = -1L
    }
    val selection = range.start.reviewDate() +
        (if (range.start == range.end) "" else " – " + range.end.reviewDate()) +
        " · " + (session?.reviewLabel() ?: lang.text("နှစ်ကြိမ်လုံး", "Both draws"))

    LazyColumn(modifier, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, AgentLedgerColors.Edge)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Surface(color = AgentLedgerColors.SoftPink,
                        shape = RoundedCornerShape(32.dp)) {
                        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Paid, null, tint = AgentLedgerColors.Wine,
                                modifier = Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(subtitle,
                            style = LedgerTypographyStandard.style(LedgerTextRole.APP_HEADER, lang.code)
                                .copy(color = AgentLedgerColors.Ink, fontWeight = FontWeight.Bold),
                            maxLines = 2)
                        Text(selection, style = MaterialTheme.typography.labelSmall,
                            color = AgentLedgerColors.Muted, maxLines = 3)
                    }
                    TextButton(onClick = onReselect,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                        Text(lang.text("ပြန်ရွေးရန်", "Change"),
                            style = MaterialTheme.typography.labelMedium,
                            color = AgentLedgerColors.Wine)
                    }
                }
            }
        }
        if (statement == null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else {
            item {
                AgentReportSummary(statement.draws, period,
                    if (agentId == -1L) statement.agentIds.size else null)
            }
            if (CustomerReportFrame.showMonthPager(period, range)) {
                item {
                    Row(Modifier.fillMaxWidth().height(42.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (monthIndex > 0) {
                                monthIndex--; expandedAgent = -1L; detailDate = null
                            }
                        }, enabled = monthIndex > 0) {
                            Icon(Icons.Default.ChevronLeft, lang.text("ယခင်လ", "Previous month"),
                                tint = if (monthIndex > 0) AgentLedgerColors.Wine
                                    else AgentLedgerColors.Muted)
                        }
                        Text(page.start.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)),
                            fontWeight = FontWeight.Bold, color = AgentLedgerColors.Dark)
                        IconButton(onClick = {
                            if (monthIndex < pages.lastIndex) {
                                monthIndex++; expandedAgent = -1L; detailDate = null
                            }
                        }, enabled = monthIndex < pages.lastIndex) {
                            Icon(Icons.Default.ChevronRight, lang.text("နောက်လ", "Next month"),
                                tint = if (monthIndex < pages.lastIndex) AgentLedgerColors.Wine
                                    else AgentLedgerColors.Muted)
                        }
                    }
                }
            }
            item {
                AgentReportTable(
                    page = page, period = period, session = session, draws = statement.draws,
                    winners = winnerMap, closed = closed, view = tableView,
                    grouped = agentId == -1L && groupView == "agents",
                    agentNames = agentNames, search = search, searchOpen = searchOpen,
                    expandedAgent = expandedAgent,
                    onToggleView = switchView,
                    onToggleGrouping = if (agentId == -1L) switchGrouping else null,
                    onToggleSearch = { searchOpen = !searchOpen; if (!searchOpen) search = "" },
                    onSearchChange = { search = it },
                    onCloseSearch = { search = ""; searchOpen = false },
                    onToggleAgent = { id -> expandedAgent = if (expandedAgent == id) -1L else id },
                    onOpen = { id, date, draw ->
                        detailAgentId = if (agentId != -1L) agentId else id
                        detailDate = date.toString()
                        detailSession = draw.name
                    }
                )
            }
        }
    }

    val selectedDate = detailDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val selectedSession = detailSession?.let {
        runCatching { DrawSession.valueOf(it) }.getOrNull()
    }
    if (selectedDate != null && selectedSession != null && statement != null) {
        val detailDraws = statement.draws.filter {
            it.date == selectedDate && it.session == selectedSession &&
                (detailAgentId == -1L || it.agentId == detailAgentId)
        }
        ModalBottomSheet(onDismissRequest = { detailDate = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(selectedDate.reviewDate() + " · " + selectedSession.reviewLabel(),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (detailDraws.isEmpty()) {
                    Text(lang.text("ဤအကြိမ်တွင် စာရင်းမရှိပါ", "No entries in this draw"),
                        Modifier.padding(vertical = 14.dp))
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 640.dp),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(detailDraws,
                            key = { it.agentId.toString() + it.date + it.session.name }) { draw ->
                            AgentStatementDrawDetail(draw,
                                agentNames[draw.agentId] ?: "Agent #" + draw.agentId,
                                customerNames, detailAgentId == -1L)
                        }
                    }
                }
            }
        }
    }
}
