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
import com.myanmar.ledger2d.core.database.AgentSpecialLimitEntity
import com.myanmar.ledger2d.core.domain.BetParser
import com.myanmar.ledger2d.core.design.AppDimens

@Composable
fun AgentLimitScreen(vm: LedgerViewModel, agentId: Long, onBack: () -> Unit) {
    val all by vm.agentAllLimit(agentId).collectAsStateWithLifecycle(initialValue = null)
    val specials by vm.agentSpecialLimits(agentId).collectAsStateWithLifecycle(initialValue = emptyList())
    var allText by rememberSaveable { mutableStateOf("") }
    var digit by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<AgentSpecialLimitEntity?>(null) }
    LaunchedEffect(all) { allText = all?.amount?.toString() ?: "" }
    pending?.let { value -> AlertDialog(onDismissRequest = { pending = null }, title = { Text("အထူးကန့်သတ်ချက်ဖျက်မည်လား") }, text = { Text("${value.digit} ကို ဒီဒိုင်အောက်ရှိ ထိုးသားအားလုံးအတွက် ဖျက်မည်လား?") }, confirmButton = { TextButton(onClick = { vm.removeAgentSpecialLimit(value); pending = null }) { Text("ဖျက်မည်") } }, dismissButton = { TextButton(onClick = { pending = null }) { Text("မလုပ်ပါ") } }) }
    AppScaffold("ဒိုင်အလိုက် ကန့်သတ်ပမာဏ", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppDimens.screen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("ဒီဒိုင်အောက်က ထိုးသားအားလုံး၏ digit စုစုပေါင်း limit", style = MaterialTheme.typography.titleLarge); Input(allText, { allText = it }, "အကွက်အားလုံးအတွက် limit", keyboard = KeyboardType.Number); Button(onClick = { vm.updateAgentAllLimit(agentId, allText) {} }) { Text("သိမ်းမည်") } }
            item { HorizontalDivider(); Text("ရွေးချယ်ထားသော digit အတွက် limit", style = MaterialTheme.typography.titleLarge); Input(digit, { digit = it.take(2) }, "ဂဏန်း", keyboard = KeyboardType.Number); Input(amount, { amount = it }, "ပမာဏ", keyboard = KeyboardType.Number); Button(onClick = { vm.addAgentSpecialLimit(agentId, digit, amount); digit = ""; amount = "" }, enabled = BetParser.validDigit(digit) && amount.toLongOrNull()?.let { it > 0 } == true) { Text("သိမ်းမည်") } }
            items(specials, key = { it.id }) { limit -> Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("${limit.digit}  →  ${limit.amount.mmk()}"); TextButton(onClick = { pending = limit }) { Text("ဖယ်ရှားမည်") } } }
        }
    }
}
