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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard
import com.myanmar.ledger2d.core.design.LedgerTextRole
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.*
import com.myanmar.ledger2d.core.model.DrawSession
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun Long.moneyLabel(): String = NumberFormat.getIntegerInstance(Locale.US).format(this)
private fun LocalDate.dateLabel(): String = format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))

private fun selectionLabel(period: StatementPeriod, range: StatementRange): String = when (period) {
    StatementPeriod.DAY -> range.start.dateLabel()
    StatementPeriod.WEEK, StatementPeriod.CUSTOM -> range.start.dateLabel() + " – " + range.end.dateLabel()
    StatementPeriod.MONTH -> range.start.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
    StatementPeriod.YEAR -> range.start.year.toString()
}
private data class CustomerLedgerLine(
    val date: LocalDate,
    val session: DrawSession,
    val draws: List<StatementDraw>,
    val winner: String?,
    val closed: Boolean
) {
    val bet: Boolean get() = draws.isNotEmpty()
    val complete: Boolean get() = winner != null && bet
    val total: StatementTotals get() = CustomerStatementCalculator.totals(draws)
}
private fun buildLedgerLines(
    page: CustomerReportPage, period: StatementPeriod, session: DrawSession?,
    draws: List<StatementDraw>, winners: Map<Pair<LocalDate, DrawSession>, String>, closed: Set<LocalDate>
): List<CustomerLedgerLine> {
    val byKey = draws.groupBy { it.date to it.session }
    return CustomerReportFrame.slots(page, session, includeWeekend = period == StatementPeriod.DAY).map { slot ->
        val matching = byKey[slot.date to slot.session].orEmpty()
        CustomerLedgerLine(slot.date, slot.session, matching,
            matching.firstOrNull()?.winner ?: winners[slot.date to slot.session],
            slot.date in closed || slot.date.dayOfWeek.value > 5)
    }
}

@Composable
private fun StatementDue(draws: List<StatementDraw>, compact: Boolean) {
    val lang = LocalLanguage.current
    if (draws.isEmpty()) {
        Text(lang.text("မရှိ", "None"), fontSize = if (compact) 10.sp else 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (draws.none { it.resultKnown }) {
        Text(lang.text("စောင့်", "Pending"), fontSize = if (compact) 10.sp else 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val (receive,pay) = CustomerStatementCalculator.settlementByCustomer(draws)
    val pending = statementDrawCounts(draws).awaiting
    Column(horizontalAlignment = if (compact) Alignment.End else Alignment.Start) {
        if (receive == 0L && pay == 0L)
            Text(lang.text("ရှင်းရန်မရှိ", "No amount due"),
                fontSize = if (compact) 11.sp else 13.sp)
        if (receive > 0L)
            Text(lang.text(if (compact) "ရ " else "မိမိရရန် ", if (compact) "In " else "User receives ") + receive.moneyLabel(),
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold, lineHeight = if (compact) 15.sp else 18.sp,
                color=ReportPalette.GreenInk)
        if (pay > 0L)
            Text(lang.text(if (compact) "ပေး " else "မိမိပေးရန် ", if (compact) "Out " else "User pays ") + pay.moneyLabel(),
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.SemiBold, lineHeight = if (compact) 15.sp else 18.sp,
                color=ReportPalette.GreenInk)
        if (pending > 0)
            Text(lang.text("စောင့် $pending ကြိမ်", "$pending pending"),
                fontSize = if (compact) 10.5.sp else 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private object ReportPalette {
    val Wine = Color(0xFFA51639)
    val WineDark = Color(0xFF82112D)
    val Blue = Color(0xFFDFEFFC)
    val BlueInk = Color(0xFF175D91)
    val Rose = Color(0xFFFBE2EA)
    val RoseInk = Color(0xFF9A2447)
    val Green = Color(0xFFE3F5E8)
    val GreenInk = Color(0xFF17763E)
    val SoftPink = Color(0xFFFCE9EF)
    val WeekTint = Color(0xFFF8EEF2)
    val WeekLabel = Color(0xFFF5DDE6)
    val Edge = Color(0xFFEBD9DF)
    val Ink = Color(0xFF211B20)
    val Muted = Color(0xFF70676E)
}

@Composable
private fun StatementMetric(
    label:String, value:String, icon:ImageVector, panel:Color, ink:Color,
    modifier:Modifier=Modifier, note:String?=null
) {
    Surface(
        modifier, color=panel, shape=RoundedCornerShape(14.dp),
        shadowElevation=3.dp, tonalElevation=0.dp,
        border=BorderStroke(1.dp,ink.copy(alpha=.12f))
    ) {
        Column(Modifier.heightIn(min=78.dp).padding(horizontal=11.dp,vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Icon(icon,null,tint=ink,modifier=Modifier.size(16.dp))
                Text(label,style=MaterialTheme.typography.labelSmall,
                    color=ReportPalette.Ink,maxLines=2)
            }
            Text(value,style=MaterialTheme.typography.titleLarge.copy(
                color=ink,fontWeight=FontWeight.Bold),
                maxLines=2)
            if(note!=null) Text(note,style=MaterialTheme.typography.labelSmall,
                color=ink,maxLines=2)
        }
    }
}

@Composable
private fun DueMetricLine(label:String,amount:Long,compact:Boolean) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,style=MaterialTheme.typography.labelSmall,
            color=ReportPalette.GreenInk,maxLines=1)
        Text(amount.moneyLabel(),
            style=(if(compact)MaterialTheme.typography.titleSmall
                else MaterialTheme.typography.titleLarge).copy(
                fontWeight=FontWeight.Bold,color=ReportPalette.GreenInk),
            modifier=Modifier.weight(1f),textAlign=TextAlign.End,softWrap=true)
    }
}

@Composable
private fun SettlementMetric(draws:List<StatementDraw>,modifier:Modifier=Modifier) {
    val lang=LocalLanguage.current
    val counts=statementDrawCounts(draws)
    val (receive,pay)=CustomerStatementCalculator.settlementByCustomer(draws)
    val both=receive>0L&&pay>0L
    Surface(
        modifier,color=ReportPalette.Green,shape=RoundedCornerShape(14.dp),
        shadowElevation=3.dp,tonalElevation=0.dp,
        border=BorderStroke(1.dp,ReportPalette.GreenInk.copy(alpha=.12f))
    ) {
        Column(Modifier.heightIn(min=78.dp).padding(horizontal=11.dp,vertical=8.dp),
            verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Handshake,null,tint=ReportPalette.GreenInk,
                    modifier=Modifier.size(16.dp))
                Text(lang.text("ငွေရှင်းရန်","Amount due"),
                    style=MaterialTheme.typography.labelSmall,color=ReportPalette.Ink)
            }
            if(draws.isEmpty()) {
                Text("—",style=MaterialTheme.typography.titleLarge.copy(
                    fontWeight=FontWeight.Bold,color=ReportPalette.GreenInk))
            } else if(counts.completed==0) {
                Text(lang.text("ရလဒ်စောင့်","Pending"),
                    style=MaterialTheme.typography.titleMedium.copy(
                    fontWeight=FontWeight.Bold,color=ReportPalette.GreenInk))
            } else {
                if(receive>0L) DueMetricLine(lang.text("ရရန်","In"),receive,both)
                if(pay>0L) DueMetricLine(lang.text("ပေးရန်","Out"),pay,both)
                if(receive==0L&&pay==0L) Text(
                    lang.text("ရှင်းရန်မရှိ","No amount due"),
                    style=MaterialTheme.typography.titleSmall,color=ReportPalette.GreenInk)
            }
            if(counts.awaiting>0) Text(
                lang.text("စောင့် "+counts.awaiting+" ကြိမ်",counts.awaiting.toString()+" pending"),
                style=MaterialTheme.typography.labelSmall,color=ReportPalette.Muted)
        }
    }
}

@Composable
private fun StatementSummary(draws:List<StatementDraw>,customerCount:Int?,period:StatementPeriod) {
    val lang=LocalLanguage.current
    val total=CustomerStatementCalculator.totals(draws)
    val counts=statementDrawCounts(draws)
    Surface(
        color=MaterialTheme.colorScheme.surface,
        shape=RoundedCornerShape(20.dp),
        border=BorderStroke(1.dp,ReportPalette.Edge),
        shadowElevation=3.dp,tonalElevation=0.dp,
        modifier=Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(11.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(if(period==StatementPeriod.YEAR||period==StatementPeriod.CUSTOM)
                        lang.text("ကာလချုပ်","Period total") else lang.text("စာရင်းချုပ်","Summary"),
                    style=LedgerTypographyStandard.style(LedgerTextRole.SECTION_TITLE,lang.code)
                        .copy(color=ReportPalette.Ink),
                    modifier=Modifier.weight(1f))
                if(customerCount!=null) Text(
                    lang.text("ထိုးသား "+customerCount+" ယောက်",customerCount.toString()+" customers"),
                    style=MaterialTheme.typography.labelSmall,color=ReportPalette.Muted)
            }
            Row(Modifier.height(IntrinsicSize.Max),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                StatementMetric(lang.text("ထိုးကွက်","Slots"),total.slots.moneyLabel(),
                    Icons.Default.Layers,ReportPalette.Blue,ReportPalette.BlueInk,Modifier.weight(1f).fillMaxHeight())
                StatementMetric(lang.text("ထိုးကြေး","Stake"),total.totalBet.moneyLabel(),
                    Icons.Default.Paid,ReportPalette.Blue,ReportPalette.BlueInk,Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.height(IntrinsicSize.Max),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                val stake=if(counts.completed>0) total.winningStake.moneyLabel() else "—"
                val payout=if(counts.completed>0) total.payout.moneyLabel() else "—"
                StatementMetric(lang.text("လျော်ငွေ","Payout"),payout,
                    Icons.Default.EmojiEvents,ReportPalette.Rose,ReportPalette.RoseInk,
                    Modifier.weight(1f).fillMaxHeight(),lang.text("ပေါက်ကြေး ","Win stake ")+stake)
                SettlementMetric(draws,Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun RowScope.LedgerText(
    text:String, weight:Float, header:Boolean=false, align:TextAlign=TextAlign.Start,
    strong:Boolean=false, textColor:Color?=null
) {
    val lang=LocalLanguage.current
    Text(
        if(header) text else text.replace(",", ",\u200B"),
        modifier=Modifier.weight(weight),
        style=LedgerTypographyStandard.style(LedgerTextRole.TABLE,lang.code).copy(
            fontWeight=if(header||strong) FontWeight.Bold else FontWeight.Medium,
            color=textColor?:if(header) Color.White else ReportPalette.Ink),
        textAlign=align,softWrap=true,maxLines=4
    )
}

/**
 * Header navigation replaces stand-alone grouping and table-view dropdowns.
 * Arrows switch stake/results. The leading title switches customer/combined,
 * and double-tapping any other header label is an optional shortcut.
 * A visible ⇄ marker and status line make gestures discoverable.
 */
@Composable
private fun LedgerHeader(
    view:String,groupedByCustomer:Boolean=false,
    onToggleView:()->Unit,onToggleGrouping:(()->Unit)?=null,
    onSearch:(()->Unit)?=null,searchOpen:Boolean=false
) {
    val lang=LocalLanguage.current
    val doubleTap=if(onToggleGrouping==null)Modifier else
        Modifier.pointerInput(onToggleGrouping) {
            detectTapGestures(onDoubleTap={onToggleGrouping?.invoke()})
        }
    val firstLabel=when {
        onToggleGrouping!=null && groupedByCustomer -> lang.text("ထိုးသား ⇄","Customer ⇄")
        onToggleGrouping!=null -> lang.text("ရက်/ကြိမ် ⇄","Date/Draw ⇄")
        else -> lang.text("ရက်/ကြိမ်","Date/Draw")
    }
    Surface(color=ReportPalette.Wine) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=5.dp),
                horizontalArrangement=Arrangement.spacedBy(2.dp),
                verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1.25f).heightIn(min=38.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Box(
                        Modifier.width(22.dp).height(38.dp).clickable(onClick=onToggleView),
                        contentAlignment=Alignment.Center
                    ) {
                        Icon(Icons.Default.ChevronLeft,
                            lang.text("ထိုးစာရင်း/ပေါက်စာရင်း ပြောင်းရန်","Switch table view"),
                            tint=Color.White,modifier=Modifier.size(20.dp))
                    }
                    Box(
                        Modifier.weight(1f).heightIn(min=38.dp)
                            .then(if(onToggleGrouping!=null)
                                Modifier.combinedClickable(
                                    onClick={onToggleGrouping?.invoke()},
                                    onDoubleClick={onToggleGrouping?.invoke()}
                                ) else Modifier),
                        contentAlignment=Alignment.CenterStart
                    ) {
                        Text(firstLabel,
                            style=LedgerTypographyStandard.style(LedgerTextRole.TABLE,lang.code)
                                .copy(fontWeight=FontWeight.Bold,color=Color.White),
                            maxLines=3,softWrap=true)
                    }
                }
                Box(Modifier.weight(.65f).then(doubleTap),
                    contentAlignment=Alignment.CenterEnd) {
                    LedgerHeaderLabel(
                        if(view=="stake")lang.text("ကွက်","Slots")
                        else lang.text("ဂဏန်း","Digit"))
                }
                Box(Modifier.weight(1.25f).then(doubleTap),
                    contentAlignment=Alignment.CenterEnd) {
                    LedgerHeaderLabel(
                        if(view=="stake")lang.text("ထိုးကြေး","Stake")
                        else lang.text("ပေါက်ကြေး","Win stake"))
                }
                Row(Modifier.weight(1.15f).heightIn(min=38.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).then(doubleTap),
                        contentAlignment=Alignment.CenterEnd) {
                        LedgerHeaderLabel(lang.text("လျော်ငွေ","Payout"))
                    }
                    Box(Modifier.width(22.dp).height(38.dp).clickable(onClick=onToggleView),
                        contentAlignment=Alignment.Center) {
                        Icon(Icons.Default.ChevronRight,
                            lang.text("ထိုးစာရင်း/ပေါက်စာရင်း ပြောင်းရန်","Switch table view"),
                            tint=Color.White,modifier=Modifier.size(20.dp))
                    }
                }
                Box(Modifier.weight(1.27f).then(doubleTap),
                    contentAlignment=Alignment.CenterEnd) {
                    LedgerHeaderLabel(lang.text("ရှင်းရန်","Due"))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start=9.dp,end=6.dp,bottom=3.dp),
                horizontalArrangement=Arrangement.SpaceBetween,
                verticalAlignment=Alignment.CenterVertically
            ) {
                if(onToggleGrouping!=null) Text(
                    if(groupedByCustomer)lang.text("ထိုးသားအလိုက်","By customer")
                    else lang.text("အားလုံးစုစုပေါင်း","Combined"),
                    style=MaterialTheme.typography.labelSmall,
                    color=Color.White.copy(alpha=.86f))
                else Spacer(Modifier.width(1.dp))
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(if(view=="stake")lang.text("ထိုးစာရင်း","Stakes")
                        else lang.text("ပေါက်စာရင်း","Results"),
                        style=MaterialTheme.typography.labelSmall,
                        fontWeight=FontWeight.SemiBold,color=Color.White)
                    if(onSearch!=null) {
                        Spacer(Modifier.width(7.dp))
                        Box(
                            Modifier.size(30.dp).clickable(onClick=onSearch),
                            contentAlignment=Alignment.Center
                        ) {
                            Icon(if(searchOpen)Icons.Default.Close else Icons.Default.Search,
                                lang.text("ထိုးသားရှာရန်","Search customers"),
                                tint=Color.White,modifier=Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerHeaderLabel(label:String) {
    val lang=LocalLanguage.current
    Text(label,style=LedgerTypographyStandard.style(LedgerTextRole.TABLE,lang.code)
        .copy(fontWeight=FontWeight.Bold,color=Color.White),
        textAlign=TextAlign.End,softWrap=true,maxLines=3)
}

@Composable
private fun LedgerEntry(
    line:CustomerLedgerLine,view:String,shade:Boolean,showDate:Boolean,onClick:()->Unit
) {
    val lang=LocalLanguage.current
    val total=line.total
    val color=if(shade) ReportPalette.WeekTint else Color.White
    Surface(onClick=onClick,color=color) {
        Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=5.dp),
            horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1.25f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                if(showDate) Text(
                    line.date.format(DateTimeFormatter.ofPattern("dd EEE",Locale.US)),
                    style=MaterialTheme.typography.labelMedium.copy(color=ReportPalette.Ink),
                    fontWeight=FontWeight.SemiBold,maxLines=1)
                Text(if(line.session==DrawSession.MORNING) lang.text("မနက်","AM")
                    else lang.text("ညနေ","PM"),
                    style=MaterialTheme.typography.labelSmall.copy(color=ReportPalette.Muted))
            }
            val isClosed=line.closed&&!line.bet
            val number=if(view=="stake") {
                if(line.bet)total.slots.moneyLabel()
                else if(isClosed)lang.text("ပိတ်","Closed") else "—"
            } else line.winner?:if(isClosed)lang.text("ပိတ်","Closed") else "—"
            LedgerText(number,.65f,align=TextAlign.End)
            val stake=if(view=="stake") {
                if(line.bet)total.totalBet.moneyLabel() else "—"
            } else if(line.complete)total.winningStake.moneyLabel() else "—"
            LedgerText(stake,1.25f,align=TextAlign.End)
            LedgerText(if(line.complete)total.payout.moneyLabel() else "—",1.15f,align=TextAlign.End)
            Box(Modifier.weight(1.27f),contentAlignment=Alignment.CenterEnd) {
                if(line.bet && line.winner==null)
                    Text(lang.text("စောင့်","Pending"),style=MaterialTheme.typography.labelSmall,
                        color=ReportPalette.Muted)
                else if(!line.bet)
                    Text("—",style=MaterialTheme.typography.labelSmall,color=ReportPalette.Muted)
                else StatementDue(line.draws,true)
            }
        }
    }
    HorizontalDivider(color=ReportPalette.Edge,thickness=.6.dp)
}

@Composable
private fun WeekBand(page:CustomerReportPage,date:LocalDate,index:Int) {
    val lang=LocalLanguage.current
    val monday=CustomerReportFrame.weekStart(date)
    val start=maxOf(page.start,monday)
    val end=minOf(page.end,monday.plusDays(4))
    Surface(color=ReportPalette.WeekLabel) {
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=3.dp)) {
            Text(lang.text("အပတ် ","Week ")+(index+1)+" · "+
                start.format(DateTimeFormatter.ofPattern("dd MMM",Locale.US))+" – "+
                end.format(DateTimeFormatter.ofPattern("dd MMM",Locale.US)),
                style=MaterialTheme.typography.labelSmall,
                fontWeight=FontWeight.SemiBold,color=ReportPalette.WineDark)
        }
    }
}

@Composable
private fun LedgerRows(
    page:CustomerReportPage,period:StatementPeriod,session:DrawSession?,
    draws:List<StatementDraw>,winners:Map<Pair<LocalDate,DrawSession>,String>,
    closed:Set<LocalDate>,view:String,onOpen:(LocalDate,DrawSession)->Unit
) {
    val lang=LocalLanguage.current
    val rows=remember(page,period,session,draws,winners,closed) {
        buildLedgerLines(page,period,session,draws,winners,closed)
    }
    if(rows.isEmpty()) {
        Text(lang.text("ဤကာလတွင် အလုပ်လုပ်ရက်မရှိပါ","No working days"),
            Modifier.fillMaxWidth().padding(12.dp),style=MaterialTheme.typography.bodySmall)
    } else {
        val grouped=period==StatementPeriod.MONTH||period==StatementPeriod.YEAR||
            period==StatementPeriod.CUSTOM
        var lastWeek:Long?=null
        var previousDate:LocalDate?=null
        rows.forEach { line ->
            val week=CustomerReportFrame.weekBandIndex(page,line.date)
            if(grouped&&week!=lastWeek) WeekBand(page,line.date,week.toInt())
            lastWeek=week
            LedgerEntry(line,view,week%2L!=0L,line.date!=previousDate) {
                onOpen(line.date,line.session)
            }
            previousDate=line.date
        }
    }
}

@Composable
private fun LedgerTotal(draws:List<StatementDraw>,view:String,monthTotal:Boolean) {
    val lang=LocalLanguage.current
    val totals=CustomerStatementCalculator.totals(draws)
    val resultExists=totals.completedDraws>0
    Surface(color=ReportPalette.SoftPink) {
        Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=7.dp),
            horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1.25f)) {
                Text(if(monthTotal)lang.text("လချုပ်","Month total")
                    else lang.text("စုစုပေါင်း","Total"),
                    style=MaterialTheme.typography.labelMedium,
                    fontWeight=FontWeight.Bold,color=ReportPalette.WineDark)
                val pending=statementDrawCounts(draws).awaiting
                if(pending>0)Text(lang.text("စောင့် "+pending,"Pending "+pending),
                    style=MaterialTheme.typography.labelSmall,color=ReportPalette.Muted)
            }
            LedgerText(if(view=="stake")totals.slots.moneyLabel() else "—",.65f,
                align=TextAlign.End,strong=true,textColor=ReportPalette.WineDark)
            LedgerText(if(view=="stake")totals.totalBet.moneyLabel()
                else if(resultExists)totals.winningStake.moneyLabel() else "—",
                1.25f,align=TextAlign.End,strong=true,textColor=ReportPalette.WineDark)
            LedgerText(if(resultExists)totals.payout.moneyLabel() else "—",
                1.15f,align=TextAlign.End,strong=true,textColor=ReportPalette.WineDark)
            Box(Modifier.weight(1.27f),contentAlignment=Alignment.CenterEnd) {
                StatementDue(draws,true)
            }
        }
    }
}

@Composable
private fun PeriodLedger(
    page:CustomerReportPage,period:StatementPeriod,session:DrawSession?,
    draws:List<StatementDraw>,winners:Map<Pair<LocalDate,DrawSession>,String>,
    closed:Set<LocalDate>,view:String,
    onToggleView:()->Unit,onToggleGrouping:(()->Unit)?=null,
    onOpen:(LocalDate,DrawSession)->Unit
) {
    Surface(
        color=Color.White,shape=RoundedCornerShape(18.dp),
        border=BorderStroke(1.dp,ReportPalette.Edge),modifier=Modifier.fillMaxWidth()
    ) {
        Column {
            LedgerHeader(view,
                onToggleView=onToggleView,onToggleGrouping=onToggleGrouping)
            LedgerRows(page,period,session,draws,winners,closed,view,onOpen)
            LedgerTotal(CustomerReportFrame.drawsOnPage(page,draws),view,
                period==StatementPeriod.MONTH||period==StatementPeriod.YEAR||
                    period==StatementPeriod.CUSTOM)
        }
    }
}

/**
 * By-customer view stays one uninterrupted table: customer subtotal rows
 * and the expanded date rows share one column header, no nested customer cards.
 */
@Composable
private fun CustomerGroupLedger(
    page:CustomerReportPage,period:StatementPeriod,session:DrawSession?,
    customers:List<Pair<Long,String>>,draws:List<StatementDraw>,
    winners:Map<Pair<LocalDate,DrawSession>,String>,closed:Set<LocalDate>,
    view:String,expandedId:Long,onToggle:(Long)->Unit,
    onToggleView:()->Unit,onToggleGrouping:()->Unit,
    search:String,searchOpen:Boolean,onSearch:()->Unit,
    onSearchChange:(String)->Unit,onCloseSearch:()->Unit,
    onOpen:(Long,LocalDate,DrawSession)->Unit
) {
    val lang=LocalLanguage.current
    Surface(
        color=Color.White,shape=RoundedCornerShape(18.dp),
        border=BorderStroke(1.dp,ReportPalette.Edge),modifier=Modifier.fillMaxWidth()
    ) {
        Column {
            LedgerHeader(view,groupedByCustomer=true,
                onToggleView=onToggleView,onToggleGrouping=onToggleGrouping,
                onSearch=onSearch,searchOpen=searchOpen)
            if(searchOpen) {
                OutlinedTextField(
                    value=search,onValueChange=onSearchChange,
                    placeholder={Text(lang.text("ထိုးသားရှာရန်","Search customers"))},
                    singleLine=true,modifier=Modifier.fillMaxWidth()
                        .padding(horizontal=9.dp,vertical=4.dp),
                    shape=RoundedCornerShape(14.dp),
                    trailingIcon={
                        IconButton(onClick=onCloseSearch) {
                            Icon(Icons.Default.Close,lang.text("ပိတ်ရန်","Close"))
                        }
                    }
                )
            }
            if(customers.isEmpty()) {
                Text(lang.text("ထိုးသားစာရင်းမရှိ","No matching customers"),
                    Modifier.padding(12.dp),style=MaterialTheme.typography.bodySmall)
                // Preserve the selected date/draw frame both for an empty period
                // and for a search query with no matching customers.
                LedgerRows(page,period,session,emptyList(),winners,closed,view){ d,draw->
                    onOpen(-1L,d,draw)
                }
            }
            customers.forEach { (id,name) ->
                val own=draws.filter { it.customerId==id }
                val total=CustomerStatementCalculator.totals(own)
                val open=expandedId==id
                Surface(color=ReportPalette.SoftPink,
                    onClick={ onToggle(id) }) {
                    Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=7.dp),
                        horizontalArrangement=Arrangement.spacedBy(4.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1.25f)) {
                            Text(name,style=MaterialTheme.typography.labelMedium,
                                fontWeight=FontWeight.Bold,color=ReportPalette.WineDark,
                                maxLines=2)
                            Icon(if(open)Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null,modifier=Modifier.size(15.dp),tint=ReportPalette.Wine)
                        }
                        LedgerText(if(view=="stake")total.slots.moneyLabel() else "—",
                            .65f,align=TextAlign.End,strong=true)
                        LedgerText(if(view=="stake")total.totalBet.moneyLabel()
                            else if(total.completedDraws>0)total.winningStake.moneyLabel() else "—",
                            1.25f,align=TextAlign.End,strong=true)
                        LedgerText(if(total.completedDraws>0)total.payout.moneyLabel() else "—",
                            1.15f,align=TextAlign.End,strong=true)
                        Box(Modifier.weight(1.27f),contentAlignment=Alignment.CenterEnd) {
                            StatementDue(own,true)
                        }
                    }
                }
                HorizontalDivider(color=ReportPalette.Edge,thickness=.8.dp)
                if(open) {
                    LedgerRows(page,period,session,own,winners,closed,view){ d,s->
                        onOpen(id,d,s)
                    }
                }
            }
            LedgerTotal(draws.filter { line-> customers.any { it.first==line.customerId } },
                view,period==StatementPeriod.MONTH||period==StatementPeriod.YEAR||
                    period==StatementPeriod.CUSTOM)
        }
    }
}

@Composable
private fun DetailValue(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun DrawLedgerSheet(
    date:LocalDate,session:DrawSession,draws:List<StatementDraw>,
    stakes:List<StatementStake>,winner:String?,onClose:()->Unit
) {
    val lang=LocalLanguage.current
    val total=CustomerStatementCalculator.totals(draws)
    val rates=stakes.map { it.payoutRate }.distinct().sorted().joinToString(" / ") { it.toString() }
    val digits=stakes.groupBy { it.digit }.toSortedMap().toList()
    ModalBottomSheet(onDismissRequest=onClose) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max=680.dp),
            contentPadding=PaddingValues(start=12.dp,end=12.dp,bottom=28.dp),
            verticalArrangement=Arrangement.spacedBy(10.dp)
        ) {
            item {
                Surface(
                    color=Color.White,shape=RoundedCornerShape(18.dp),
                    border=BorderStroke(1.dp,ReportPalette.Edge)
                ) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                            Surface(
                                color=ReportPalette.Wine,shape=RoundedCornerShape(4.dp),
                                modifier=Modifier.width(5.dp).height(50.dp)
                            ) {}
                            Column {
                                Text(date.dateLabel(),style=MaterialTheme.typography.titleMedium,
                                    fontWeight=FontWeight.Bold,color=ReportPalette.WineDark)
                                Text(if(session==DrawSession.MORNING)
                                    lang.text("မနက်အကြိမ် အသေးစိတ်","Morning detail")
                                    else lang.text("ညနေအကြိမ် အသေးစိတ်","Evening detail"),
                                    style=MaterialTheme.typography.labelMedium,color=ReportPalette.Muted)
                            }
                        }
                        HorizontalDivider(color=ReportPalette.Edge)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(lang.text("ပေါက်ဂဏန်း","Winning digit"),
                                    style=MaterialTheme.typography.labelSmall,color=ReportPalette.Muted)
                                Text(winner?:lang.text("ရလဒ်စောင့်","Pending"),
                                    style=MaterialTheme.typography.titleMedium,
                                    fontWeight=FontWeight.Bold,color=ReportPalette.WineDark)
                            }
                            Column(Modifier.weight(1f),horizontalAlignment=Alignment.End) {
                                Text(lang.text("လျော်ကြေးအဆ","Payout rate"),
                                    style=MaterialTheme.typography.labelSmall,color=ReportPalette.Muted)
                                Text(if(rates.isBlank())"—" else rates+" x",
                                    style=MaterialTheme.typography.titleMedium,
                                    fontWeight=FontWeight.Bold,color=ReportPalette.Ink)
                            }
                        }
                        HorizontalDivider(color=ReportPalette.Edge)
                        DetailValue(lang.text("ထိုးကွက်","Slots"),total.slots.moneyLabel())
                        DetailValue(lang.text("ထိုးကြေး","Stake"),total.totalBet.moneyLabel())
                        DetailValue(lang.text("ပေါက်ကြေး","Winning stake"),
                            if(winner!=null&&draws.isNotEmpty())total.winningStake.moneyLabel() else "—")
                        DetailValue(lang.text("လျော်ငွေ","Payout"),
                            if(winner!=null&&draws.isNotEmpty())total.payout.moneyLabel() else "—")
                        Surface(color=ReportPalette.Green,shape=RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement=Arrangement.SpaceBetween,
                                verticalAlignment=Alignment.CenterVertically) {
                                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,
                                    horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Handshake,null,tint=ReportPalette.GreenInk,
                                        modifier=Modifier.size(19.dp))
                                    Text(lang.text("ငွေရှင်းရန်","Amount due"),
                                        style=MaterialTheme.typography.labelMedium,
                                        color=ReportPalette.GreenInk,fontWeight=FontWeight.Bold)
                                }
                                StatementDue(draws,false)
                            }
                        }
                    }
                }
            }
            if(digits.isEmpty()) {
                item {
                    Text(lang.text("ထိုးစာရင်းမရှိ","No entries"),
                        style=MaterialTheme.typography.bodySmall,
                        modifier=Modifier.padding(8.dp))
                }
            } else {
                item {
                    Surface(color=Color.White,shape=RoundedCornerShape(18.dp),
                        border=BorderStroke(1.dp,ReportPalette.Edge)) {
                        Column {
                            Text(lang.text("ဂဏန်းအလိုက်စာရင်း","Stakes by digit"),
                                Modifier.padding(12.dp),style=MaterialTheme.typography.titleSmall,
                                fontWeight=FontWeight.Bold,color=ReportPalette.Ink)
                            Surface(color=ReportPalette.SoftPink) {
                                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),
                                    horizontalArrangement=Arrangement.SpaceBetween) {
                                    Text(lang.text("ဂဏန်း","Digit"),
                                        style=MaterialTheme.typography.labelMedium,
                                        fontWeight=FontWeight.Bold,color=ReportPalette.WineDark)
                                    Text(lang.text("ထိုးကြေး / လျော်ငွေ","Stake / Payout"),
                                        style=MaterialTheme.typography.labelMedium,
                                        fontWeight=FontWeight.Bold,color=ReportPalette.WineDark)
                                }
                            }
                        }
                    }
                }
                items(digits) { pair ->
                    val amount=pair.second.fold(0L){acc,line->Math.addExact(acc,line.amount)}
                    val payout=if(pair.first==winner)pair.second.fold(0L){acc,line->
                        Math.addExact(acc,Math.multiplyExact(line.amount,line.payoutRate))
                    } else 0L
                    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(pair.first,style=MaterialTheme.typography.bodyMedium,
                            color=if(pair.first==winner)ReportPalette.WineDark else ReportPalette.Ink,
                            fontWeight=if(pair.first==winner)FontWeight.Bold else FontWeight.Normal)
                        Text(amount.moneyLabel()+" / "+if(winner==null)"—" else payout.moneyLabel(),
                            style=MaterialTheme.typography.bodyMedium,
                            color=ReportPalette.Ink)
                    }
                    HorizontalDivider(color=ReportPalette.Edge)
                }
            }
        }
    }
}

@Composable
private fun PageCaption(page:CustomerReportPage,period:StatementPeriod) {
    val lang=LocalLanguage.current
    val monthNames=listOf("ဇန်နဝါရီ","ဖေဖော်ဝါရီ","မတ်","ဧပြီ","မေ","ဇွန်",
        "ဇူလိုင်","ဩဂုတ်","စက်တင်ဘာ","အောက်တိုဘာ","နိုဝင်ဘာ","ဒီဇင်ဘာ")
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        val name=if(period==StatementPeriod.DAY||period==StatementPeriod.WEEK)
            page.start.dateLabel()+if(page.start==page.end) "" else " – "+page.end.dateLabel()
            else lang.text(monthNames[page.start.monthValue-1]+" "+page.start.year,
                page.start.format(DateTimeFormatter.ofPattern("MMM yyyy",Locale.US)))
        Text(name,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Bold)
        if(period==StatementPeriod.CUSTOM &&
            (page.start.dayOfMonth!=1 || page.end.dayOfMonth!=page.end.lengthOfMonth()))
            Text(page.start.dayOfMonth.toString()+"–"+page.end.dayOfMonth,
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CustomerLedgerReportResults(
    vm:LedgerViewModel,agentId:Long,customerId:Long,period:StatementPeriod,range:StatementRange,
    session:DrawSession?,subtitle:String,onReselect:()->Unit,modifier:Modifier=Modifier
) {
    val lang=LocalLanguage.current
    val revision by vm.revision.collectAsStateWithLifecycle()
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue=emptyList())
    val winners by vm.winners.collectAsStateWithLifecycle()
    val closedDays by vm.closedDays.collectAsStateWithLifecycle()
    val data by produceState<CustomerStatement?>(null,agentId,customerId,range,session,revision) {
        value=null
        value=vm.customerStatement(agentId,customerId,range,session)
    }
    val pages=remember(period,range){CustomerReportFrame.pages(period,range)}
    val initial=remember(period,range) {
        val today=LocalDate.now()
        if(period==StatementPeriod.YEAR && range.contains(today))
            pages.indexOfFirst { !today.isBefore(it.start)&&!today.isAfter(it.end) }.coerceAtLeast(0)
        else 0
    }
    var monthIndex by rememberSaveable(agentId,customerId,period,range.start,range.end) {
        mutableIntStateOf(initial)
    }
    val page=pages[monthIndex.coerceIn(0,pages.lastIndex)]
    var tableView by rememberSaveable(agentId,customerId) { mutableStateOf("stake") }
    var groupView by rememberSaveable(agentId,customerId) { mutableStateOf("customers") }
    var search by rememberSaveable(agentId,customerId) { mutableStateOf("") }
    var searchOpen by rememberSaveable(agentId,customerId) { mutableStateOf(false) }
    var expandedCustomer by rememberSaveable(agentId,customerId) { mutableLongStateOf(-1L) }
    var detailDate by rememberSaveable(agentId,customerId) { mutableStateOf<String?>(null) }
    var detailDraw by rememberSaveable(agentId,customerId) { mutableStateOf<String?>(null) }
    var detailCustomer by rememberSaveable(agentId,customerId) { mutableLongStateOf(-1L) }

    val winnerMap=remember(winners,range) {
        winners.filter { range.contains(it.date) }.associate { (it.date to it.session) to it.digit }
    }
    val closed=remember(closedDays){closedDays.map {it.date}.toSet()}
    val statement=data
    val reportNames by produceState<Map<Long,String>>(
        emptyMap(),statement?.customers?.keys,revision
    ) {
        value=statement?.let { vm.customerStatementNames(it.customers.keys) }.orEmpty()
    }
    val pageDraws=statement?.let { CustomerReportFrame.drawsOnPage(page,it.draws) }.orEmpty()
    val selectionText=selectionLabel(period,range)+" · "+
        (session?.let {
            if(it==DrawSession.MORNING)lang.text("မနက်အကြိမ်","Morning")
            else lang.text("ညနေအကြိမ်","Evening")
        }?:lang.text("နှစ်ကြိမ်လုံး","Both draws"))

    LazyColumn(modifier,contentPadding=PaddingValues(horizontal=12.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)) {
        item {
            Surface(color=Color.White,shape=RoundedCornerShape(20.dp),
                border=BorderStroke(1.dp,ReportPalette.Edge)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=11.dp,vertical=10.dp),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    Surface(color=ReportPalette.SoftPink,shape=RoundedCornerShape(32.dp)) {
                        Box(Modifier.size(38.dp),contentAlignment=Alignment.Center) {
                            Icon(Icons.Default.Person,null,tint=ReportPalette.Wine,modifier=Modifier.size(22.dp))
                        }
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(subtitle,style=LedgerTypographyStandard.style(LedgerTextRole.APP_HEADER,lang.code)
                            .copy(color=ReportPalette.Ink),maxLines=2)
                        Text(selectionText,style=MaterialTheme.typography.labelSmall,
                            color=ReportPalette.Muted,maxLines=3)
                    }
                    TextButton(onClick=onReselect,contentPadding=PaddingValues(horizontal=4.dp,vertical=4.dp)) {
                        Text(lang.text("ပြန်ရွေးရန်","Change"),
                            style=MaterialTheme.typography.labelMedium,
                            color=ReportPalette.Wine)
                    }
                }
            }
        }

        if(statement==null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        } else {
            item {
                StatementSummary(statement.draws,
                    if(customerId==-1L) statement.customers.size else null,period)
            }

            // Multi-month navigation is needed only for selections
            // covering multiple calendar months.
            if(CustomerReportFrame.showMonthPager(period,range)) {
                item {
                    Row(Modifier.fillMaxWidth().height(42.dp),
                        horizontalArrangement=Arrangement.SpaceBetween,
                        verticalAlignment=Alignment.CenterVertically) {
                        IconButton(
                            onClick={ if(monthIndex>0) {
                                monthIndex--;detailDate=null;expandedCustomer=-1L
                            } },
                            enabled=monthIndex>0
                        ) {
                            Icon(Icons.Default.ChevronLeft,lang.text("ယခင်လ","Previous month"),
                                tint=if(monthIndex>0)ReportPalette.Wine else ReportPalette.Muted)
                        }
                        PageCaption(page,period)
                        IconButton(
                            onClick={ if(monthIndex<pages.lastIndex) {
                                monthIndex++;detailDate=null;expandedCustomer=-1L
                            } },
                            enabled=monthIndex<pages.lastIndex
                        ) {
                            Icon(Icons.Default.ChevronRight,lang.text("နောက်လ","Next month"),
                                tint=if(monthIndex<pages.lastIndex)ReportPalette.Wine else ReportPalette.Muted)
                        }
                    }
                }
            }

            if(customerId==-1L && groupView=="customers") {
                val activeIds=pageDraws.map{it.customerId}.toSet()
                val list=statement.customers.keys.asSequence()
                    .filter{it in activeIds}
                    .map{id->id to (reportNames[id]
                        ?:customers.firstOrNull{it.id==id}?.name?:"Customer #"+id)}
                    .filter{(_,name)->name.contains(search,ignoreCase=true)}
                    .sortedWith(compareBy<Pair<Long,String>>{it.second.lowercase(Locale.ROOT)}
                        .thenBy{it.first}).toList()
                item {
                    CustomerGroupLedger(
                        page,period,session,list,pageDraws,winnerMap,closed,
                        tableView,expandedCustomer,
                        onToggle={id->expandedCustomer=if(expandedCustomer==id)-1L else id},
                        onToggleView={tableView=if(tableView=="stake")"win" else "stake"},
                        onToggleGrouping={
                            groupView="total";search="";searchOpen=false;expandedCustomer=-1L
                        },
                        search=search,searchOpen=searchOpen,
                        onSearch={searchOpen=!searchOpen;if(!searchOpen)search=""},
                        onSearchChange={search=it},
                        onCloseSearch={search="";searchOpen=false},
                        onOpen={id,d,draw->
                            detailDate=d.toString();detailDraw=draw.name;detailCustomer=id
                        }
                    )
                }
            } else {
                item {
                    PeriodLedger(
                        page,period,session,statement.draws,winnerMap,closed,tableView,
                        onToggleView={tableView=if(tableView=="stake")"win" else "stake"},
                        onToggleGrouping=if(customerId==-1L) ({
                            groupView="customers";search="";searchOpen=false;expandedCustomer=-1L
                        }) else null
                    ) {d,draw->
                        detailDate=d.toString();detailDraw=draw.name;detailCustomer=-1L
                    }
                }
            }
        }
    }

    val day=detailDate?.let{runCatching{LocalDate.parse(it)}.getOrNull()}
    val selectedDraw=detailDraw?.let{runCatching{DrawSession.valueOf(it)}.getOrNull()}
    if(day!=null && selectedDraw!=null && statement!=null) {
        val draws=statement.draws.filter {
            it.date==day && it.session==selectedDraw &&
                (detailCustomer==-1L || it.customerId==detailCustomer)
        }
        val stakes=statement.stakes.filter {
            it.date==day && it.session==selectedDraw &&
                (detailCustomer==-1L || it.customerId==detailCustomer)
        }
        DrawLedgerSheet(day,selectedDraw,draws,stakes,
            draws.firstOrNull()?.winner?:winnerMap[day to selectedDraw],
            onClose={detailDate=null;detailDraw=null})
    }
}
