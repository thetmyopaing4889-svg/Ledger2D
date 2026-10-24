package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import com.myanmar.ledger2d.core.database.AgentSpecialLimitEntity
import com.myanmar.ledger2d.core.domain.BetParser
import com.myanmar.ledger2d.core.design.AppDimens
import com.myanmar.ledger2d.core.design.LocalLanguage

@Composable
fun AgentLimitScreen(vm: LedgerViewModel, agentId: Long, onBack: () -> Unit) {
    val l = LocalLanguage.current
    var dateText by rememberSaveable { mutableStateOf(l.selectedDate) }
    var session by rememberSaveable { mutableStateOf(l.selectedSession) }
    val date = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now() }
    val all by vm.agentAllLimit(agentId,date,session).collectAsStateWithLifecycle(initialValue = null)
    val specials by vm.agentSpecialLimits(agentId,date,session).collectAsStateWithLifecycle(initialValue = emptyList())
    var allText by rememberSaveable { mutableStateOf("") }
    var digit by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<AgentSpecialLimitEntity?>(null) }
    LaunchedEffect(all,date,session) { allText = all?.amount?.toString() ?: "" }
    pending?.let { value -> AlertDialog(onDismissRequest = { pending = null }, title = { Text(l.translate("အထူးကန့်သတ်ချက်ဖျက်မည်လား")) }, text = { Text("${value.digit} ${l.translate("ကို ဒီဒိုင်အောက်ရှိ ထိုးသားအားလုံးအတွက် ဖျက်မည်လား?")}") }, confirmButton = { TextButton(onClick = { vm.removeAgentSpecialLimit(value); pending = null }) { Text(l.translate("ဖျက်မည်")) } }, dismissButton = { TextButton(onClick = { pending = null }) { Text(l.translate("မလုပ်ပါ")) } }) }
    AppScaffold(l.translate("ဒိုင်အလိုက် ကန့်သတ်ပမာဏ"), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { DateInput(dateText,{dateText=it;l.setDate(it)},"ရက်စွဲ");Row{DrawSession.entries.forEach{draw->FilterChip(session==draw,{session=draw;l.setSession(draw)},label={Text(draw.label)})}} }
            item { Text(l.translate("ဒီဒိုင်အောက်က ထိုးသားအားလုံး၏ digit စုစုပေါင်း limit"), style = MaterialTheme.typography.titleLarge); Input(allText, { allText = it }, "အကွက်အားလုံးအတွက် limit", keyboard = KeyboardType.Number); Button(onClick = { vm.updateAgentAllLimit(agentId,date,session,allText) {} }) { Text(l.translate("သိမ်းမည်")) } }
            item { HorizontalDivider(); Text(l.translate("ရွေးချယ်ထားသော digit အတွက် limit"), style = MaterialTheme.typography.titleLarge); Input(digit, { digit = it.take(2) }, "ဂဏန်း", keyboard = KeyboardType.Number); Input(amount, { amount = it }, "ပမာဏ", keyboard = KeyboardType.Number); Button(onClick = { vm.addAgentSpecialLimit(agentId,date,session,digit,amount); digit = ""; amount = "" }, enabled = BetParser.validDigit(digit) && amount.toLongOrNull()?.let { it > 0 } == true) { Text(l.translate("သိမ်းမည်")) } }
            items(specials, key = { it.id }) { limit -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${limit.digit}  →  ${limit.amount.mmk()}"); TextButton(onClick = { pending = limit }) { Text(l.translate("ဖယ်ရှားမည်")) } } }
        }
    }
}
