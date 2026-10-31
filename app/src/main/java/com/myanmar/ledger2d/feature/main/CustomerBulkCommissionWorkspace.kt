@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.domain.DrawRatePolicy
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.model.DrawSession
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Only the chosen agent's customers are affected.
 * A bulk edit does not synthesize a false "current rate": customers may differ.
 */
@Composable
fun CustomerBulkCommissionWorkspace(
    vm: LedgerViewModel, agentId: Long, date: LocalDate, session: DrawSession
) {
    val customers by vm.customers(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    val revision by vm.revision.collectAsStateWithLifecycle()
    val commissionTotal by produceState(0L, agentId, date, session, revision) {
        value = vm.agentReport(agentId,date,session,true).calculation.commission
    }
    val start = remember { DrawRatePolicy.currentSession(LocalDateTime.now()) }
    var mode by rememberSaveable(agentId) { mutableStateOf("FORWARD") }
    var startDate by rememberSaveable(agentId) { mutableStateOf(start.date.toString()) }
    var startSession by rememberSaveable(agentId) { mutableStateOf(start.session) }
    var percent by rememberSaveable(agentId) { mutableStateOf("") }
    var pending by remember { mutableStateOf(false) }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var success by remember { mutableStateOf("") }
    val amount=percent.toBigDecimalOrNull()
    val validRate=amount!=null && amount>=BigDecimal.ZERO && amount<=BigDecimal(100) &&
        amount.movePointRight(2).stripTrailingZeros().scale()<=0
    val effective=runCatching { LocalDate.parse(startDate) }.getOrNull()
    val today=DrawRatePolicy.currentSession(LocalDateTime.now())
    val validStart=effective!=null && DrawSchedule.isWeekday(effective) &&
        DrawRatePolicy.key(effective,startSession)>=DrawRatePolicy.key(today.date,today.session)
    val canSubmit=customers.isNotEmpty() && validRate && !processing && (mode=="DRAW" || validStart)
    val targetDate=if(mode=="DRAW")date else effective
    val targetSession=if(mode=="DRAW")session else startSession

    if(pending && canSubmit) AlertDialog(
        onDismissRequest={pending=false},
        title={Text("Customer အားလုံး၏ ကော်မရှင် ပြောင်းမည်လား")},
        text={
            Text("ရွေးထားသော Agent အောက်က Customer "+customers.size+" ယောက်လုံး\n"+
                "ကော်မရှင် "+percent+"%\n"+
                "Session: "+targetDate+" • "+targetSession.label+"\n\n"+
                if(mode=="DRAW")
                    "ဒီ Session တစ်ခုတည်းရှိ ထိုးစာရင်းအားလုံးကို ပြန်တွက်မည်။ တခြား Session မထိပါ။"
                else
                    "ဒီ Session ကစပြီး အနာဂတ်နှုန်းအသစ် သက်ရောက်မည်။ အတိတ် Session မထိပါ။"
            )
        },
        confirmButton={
            TextButton({
                pending=false
                processing=true
                error=""
                success=""
                if(mode=="DRAW") vm.overrideCommissionForAgentDraw(agentId,date,session,percent,
                    { count->processing=false;success="Customer "+count+" ယောက်အတွက် ပြန်တွက်ပြီးပါပြီ" },
                    { message->processing=false;error=message })
                else if(effective!=null) vm.scheduleCommissionForAgent(agentId,percent,effective,startSession,
                    { count->processing=false;success="Customer "+count+" ယောက်အတွက် သိမ်းပြီးပါပြီ" },
                    { message->processing=false;error=message })
            }){Text("အတည်ပြုမည်")}
        },
        dismissButton={TextButton({pending=false}){Text("မလုပ်တော့ပါ")}},
    )
    Column(verticalArrangement=Arrangement.spacedBy(11.dp)) {
        Text("Customer အားလုံး၏ ကော်မရှင်နှုန်း",
            style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        Text("ရွေးထားသော Agent အောက်က Customer "+customers.size+" ယောက်အတွက်သာ",
            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.primary)
        Text("ဒီ Session ၏ စုစုပေါင်းကော်မရှင်: "+commissionTotal.mmk(),
            style=MaterialTheme.typography.bodyMedium)
        Text("Customer တစ်ယောက်ချင်း လက်ရှိနှုန်းတွေ မတူနိုင်ပါ။ ဒီနေရာမှာ နှုန်းအသစ်ကိုသာ ရိုက်ထည့်ပါ။",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(mode=="FORWARD",{mode="FORWARD";error="";success="";percent=""},label={Text("ပုံမှန်နှုန်း ပြောင်းရန်")})
            FilterChip(mode=="DRAW",{mode="DRAW";error="";success="";percent=""},label={Text("ဒီ Session သီးသန့်")})
        }
        if(mode=="FORWARD") {
            Text("နှုန်းအသစ် စတင်သက်ရောက်မည့် Session",style=MaterialTheme.typography.labelMedium)
            DateInput(startDate,{startDate=it;success=""},"စတင်မည့်ရက်")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DrawSession.entries.forEach { draw->
                    FilterChip(startSession==draw,{startSession=draw;success=""},label={Text(draw.label)})
                }
            }
        } else Text("ရွေးထားသော "+date+" • "+session.label+" အတွက်သာ ပြန်တွက်မည်။",
            style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(percent,{
            percent=it.filter { ch->ch.isDigit() || ch=='.' }.take(6)
            success="";error=""
        },Modifier.fillMaxWidth(),label={Text("Customer အားလုံးအတွက် ကော်မရှင် %")},singleLine=true)
        if(customers.isEmpty()) Text("ဒီ Agent မှာ Customer မရှိသေးပါ",
            color=MaterialTheme.colorScheme.error)
        if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
        if(success.isNotBlank()) Text(success,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
        Button(onClick={pending=true},enabled=canSubmit,modifier=Modifier.fillMaxWidth()) {
            Text(if(processing)"သိမ်းဆည်းနေသည်…" else if(mode=="DRAW")"Customer အားလုံး ဒီ Session ပြန်တွက်မည်" else "Customer အားလုံးအတွက် သိမ်းမည်")
        }
    }
}
