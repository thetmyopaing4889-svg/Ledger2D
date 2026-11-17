package com.myanmar.ledger2d.feature.main

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.LedgerApplication
import com.myanmar.ledger2d.core.backup.CherryBackupManager
import com.myanmar.ledger2d.core.backup.CherryBackupPreview
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri

/**
 * Explicit user actions only. Android Storage Access Framework grants file
 * access without broad storage permission; passwords are kept in Compose
 * memory only and discarded after completion or cancel.
 */
@Composable
internal fun CherryBackupSettings() {
    val context=LocalContext.current
    val lang=LocalLanguage.current
    val en=lang.code=="en"
    fun tr(mm:String,english:String)=if(en) english else mm
    val manager=remember(context) {
        CherryBackupManager(context, (context.applicationContext as LedgerApplication)
            .container.database)
    }
    val scope=rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<CherryBackupPreview?>(null) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    var restartNeeded by remember { mutableStateOf(false) }

    fun resetSecrets() {
        password=""
        repeatPassword=""
        mode=null
        restoreUri=null
        preview=null
    }
    val saveLauncher=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if(uri==null) { resetSecrets() }
        else {
            busy=true
            val secret=password.toCharArray()
            scope.launch {
                try {
                    val result=withContext(Dispatchers.IO) { manager.backup(uri,secret) }
                    status=tr("Backup သိမ်းပြီးပါပြီ။","Backup saved successfully.") +
                        "  " + result.databaseRows.toString() + " rows"
                    error=null
                } catch(t:Exception) {
                    error=t.message ?: tr("Backup မအောင်မြင်ပါ။","Backup failed.")
                } finally {
                    secret.fill('\u0000')
                    busy=false
                    resetSecrets()
                }
            }
        }
    }
    val loadLauncher=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if(uri==null) resetSecrets()
        else {
            busy=true
            val secret=password.toCharArray()
            scope.launch {
                try {
                    val result=withContext(Dispatchers.IO) { manager.inspect(uri,secret) }
                    restoreUri=uri
                    preview=result
                    mode="confirm"
                    error=null
                } catch(t:Exception) {
                    error=t.message ?: tr("Backup ဖိုင် မဖတ်နိုင်ပါ။","Backup cannot be read.")
                    resetSecrets()
                } finally {
                    secret.fill('\u0000')
                    busy=false
                }
            }
        }
    }

    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(tr("ဒေတာသိမ်းဆည်းခြင်း","Backup and restore"),
            style=MaterialTheme.typography.titleMedium,
            color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold)
        Surface(shape=RoundedCornerShape(16.dp), color=Color.White,
            border=BorderStroke(1.dp,Color(0xFFF4CFDA))) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick={mode="backup";error=null;status=null},
                    enabled=!busy&&!restartNeeded,modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CloudDownload,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Backup သိမ်းရန်","Save full backup"))
                }
                OutlinedButton(onClick={mode="restore";error=null;status=null},
                    enabled=!busy&&!restartNeeded,modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CloudUpload,null,Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Restore ပြန်လုပ်ရန်","Restore full backup"))
                }
                Row(verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Lock,null,tint=AppColors.PrimaryDeep,
                        modifier=Modifier.size(16.dp))
                    Text(tr("Password ဖြင့်ကာကွယ်ထားသော Backup ဖိုင်",
                        "Password-protected encrypted archive"),
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(tr(
                    "ဒိုင်/ထိုးသား၊ ငွေစာရင်း၊ ရလဒ်၊ Formula ရွေးချယ်မှု၊ " +
                        "ရှာဖွေမှတ်တမ်းနှင့် အောင်ချက်ဒေတာများ ပါဝင်သည်။",
                    "Includes ledger, results, formula choices, search checkpoints and success records."),
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(tr("ဒေတာ စစ်ဆေး/သိမ်းဆည်းနေပါသည်…",
                "Validating and saving data…"),
                style=MaterialTheme.typography.bodySmall)
        }
        status?.let { Text(it,color=AppColors.PrimaryDeep,
            style=MaterialTheme.typography.bodySmall) }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error,
            style=MaterialTheme.typography.bodySmall) }
        if(restartNeeded) {
            Text(tr("Restore ပြီးပါပြီ။ App ကို အပြည့်ပိတ်ပြီး ပြန်ဖွင့်ပါ။ " +
                "ပြန်မဖွင့်မီ ငွေစာရင်း မပြင်ပါနှင့်။",
                "Restore complete. Fully close and reopen the app before using it. " +
                    "Do not edit ledger data until relaunch."),
                color=AppColors.PrimaryDeep,fontWeight=FontWeight.Bold)
        }
    }

    if(restartNeeded) {
        AlertDialog(
            onDismissRequest={},
            title={Text(tr("Restore အောင်မြင်ပါပြီ","Restore completed"))},
            text={Text(tr(
                "ဒေတာဟောင်းကို အသုံးမပြုမိစေရန် App ကို ပိတ်ပါ။ " +
                    "ပြီးလျှင် Home Screen မှ Cherry 2D ကို ပြန်ဖွင့်ပါ။",
                "Close the app to activate restored data, then reopen Cherry 2D " +
                    "from your home screen."))},
            confirmButton={
                Button(onClick={
                    (context as? android.app.Activity)?.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                }) {
                    Text(tr("App ကိုပိတ်မည်","Close app"))
                }
            }
        )
    }
    if(mode=="backup" || mode=="restore") {
        val backingUp=mode=="backup"
        AlertDialog(
            onDismissRequest={if(!busy)resetSecrets()},
            title={Text(if(backingUp) tr("Full Backup","Create full backup")
                else tr("Full Restore","Restore backup"))},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(if(backingUp)
                        tr("Password အနည်းဆုံး ၈ လုံး သတ်မှတ်ပါ။ မေ့သွားလျှင် ဖိုင်ကို ပြန်ဖွင့်မရပါ။",
                            "Choose at least 8 characters. A lost password cannot be recovered.")
                    else tr("Backup ဖိုင်မှာ သုံးခဲ့သော Password ကို ရိုက်ပါ။",
                        "Enter the password used for the backup file."),
                        style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(password,{password=it},singleLine=true,
                        label={Text("Password")},
                        visualTransformation=PasswordVisualTransformation())
                    if(backingUp) {
                        OutlinedTextField(repeatPassword,{repeatPassword=it},singleLine=true,
                            label={Text(tr("Password ထပ်ရိုက်ပါ","Confirm password"))},
                            visualTransformation=PasswordVisualTransformation())
                    }
                }
            },
            confirmButton={
                TextButton(
                    enabled=!busy && password.length>=8 &&
                        (!backingUp || password==repeatPassword),
                    onClick={
                        if(backingUp) {
                            saveLauncher.launch("Cherry2D_" +
                                java.time.LocalDate.now().toString()+".cherrybackup")
                        } else loadLauncher.launch(arrayOf("*/*"))
                    }
                ) { Text(if(backingUp) tr("ဖိုင်သိမ်းရန်","Choose save location")
                    else tr("ဖိုင်ရွေးရန်","Choose backup file")) }
            },
            dismissButton={TextButton(onClick={resetSecrets}) {
                Text(tr("မလုပ်တော့ပါ","Cancel"))
            }}
        )
    }
    if(mode=="confirm" && restoreUri!=null && preview!=null) {
        val details=preview!!
        AlertDialog(
            onDismissRequest={if(!busy)resetSecrets()},
            title={Text(tr("Restore အတည်ပြုရန်","Confirm restore"))},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(tr("Backup ရက်စွဲ: ","Backup date: ") + details.createdAt)
                    Text(tr("စာရင်းအချက်အလက်: ","Database rows: ") +
                        details.databaseRows.toString())
                    Text(tr("Setting ဖိုင်: ","Preference files: ") +
                        details.preferenceFiles.toString())
                    Text(tr("လက်ရှိ App ဒေတာကို အစားထိုးမည်။ အရင်ဒေတာ Recovery Copy " +
                        "သိမ်းပြီးမှ Restore လုပ်မည်။",
                        "This replaces current app data. A local recovery copy is created first."),
                        color=MaterialTheme.colorScheme.error)
                }
            },
            confirmButton={
                TextButton(enabled=!busy,onClick={
                    val uri=restoreUri ?: return@TextButton
                    val secret=password.toCharArray()
                    busy=true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { manager.restore(uri,secret) }
                            status=tr("Restore အောင်မြင်ပါပြီ။","Restore successful.")
                            error=null
                            restartNeeded=true
                        } catch(t:Exception) {
                            error=t.message ?: tr("Restore မအောင်မြင်ပါ။",
                                "Restore failed; previous data was protected.")
                        } finally {
                            secret.fill('\u0000')
                            busy=false
                            resetSecrets()
                        }
                    }
                }) { Text(tr("Restore လုပ်မည်","Restore now")) }
            },
            dismissButton={TextButton(enabled=!busy,onClick={resetSecrets}) {
                Text(tr("မလုပ်တော့ပါ","Cancel"))
            }}
        )
    }
}
