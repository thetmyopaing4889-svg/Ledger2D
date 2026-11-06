package com.myanmar.ledger2d.feature.main

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.myanmar.ledger2d.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myanmar.ledger2d.core.database.BetEntryWithLines
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.model.DrawSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class VoucherPreparation(val renderer: VoucherRenderer?, val error: String?)

/** The selected IDs come from the previous customer's selection screen, never from an export history. */
@Composable
fun VoucherPreviewScreen(
    vm: LedgerViewModel,
    customerId: Long,
    date: LocalDate,
    session: DrawSession,
    selectedIds: List<Long>,
    onBack: () -> Unit,
) {
    val language = LocalLanguage.current
    val context = LocalContext.current
    val sourceTypeface = remember(context) {
        ResourcesCompat.getFont(context, R.font.noto_sans_myanmar_ui_regular)
    }
    val customer by vm.customer(customerId).collectAsStateWithLifecycle(initialValue = null)
    val observed by produceState<List<BetEntryWithLines>?>(null, customerId, date, session) {
        vm.customerEntries(customerId, date, session).collect { value = it }
    }
    val selected = remember(observed, selectedIds, customerId, date, session) {
        observed?.let { entries ->
            val validIds = voucherSelectedEntryIds(entries, customerId, date, session, selectedIds).toSet()
            betEntriesInSubmissionOrder(entries).filter { it.entry.id in validIds }
        }
    }
    val selectionValid = selected != null && selected.isNotEmpty() &&
        selected.size == selectedIds.distinct().size
    val document = remember(selected, customer, selectionValid) {
        if (selectionValid && customer != null && customer!!.id == customerId) {
            voucherDocument(customer!!.name, date, session, selected!!)
        } else null
    }
    // Measure all multiline rows outside the Compose UI thread.
    val preparation by produceState<VoucherPreparation?>(null, document, sourceTypeface) {
        if (document != null) {
            value = withContext(Dispatchers.Default) {
                runCatching { VoucherRenderer(document, sourceTypeface) }.fold(
                    onSuccess = { VoucherPreparation(it, null) },
                    onFailure = { VoucherPreparation(null, it.message ?: "စာမျက်နှာပြုလုပ်၍ မရပါ") },
                )
            }
        }
    }
    val renderer = preparation?.renderer

    if (renderer == null) {
        AppScaffold(
            title = language.text("ဘောင်ချာအကြိုကြည့်ရန်", "Voucher preview"),
            onBack = onBack,
            includeQuickEntryFab = false,
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (observed == null || customer == null || (document != null && preparation == null)) {
                    CircularProgressIndicator()
                } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(preparation?.error ?: language.text(
                        "ရွေးထားသောစာရင်းအချို့ မရှိတော့ပါ။ စာရင်းကို ပြန်ရွေးပါ။",
                        "Some selected transactions are no longer available. Select them again.",
                    ))
                    TextButton(onClick = onBack) { Text(language.text("နောက်သို့", "Back")) }
                }
            }
        }
        return
    }
    VoucherPreviewPages(renderer, onBack)
}

@Composable
private fun VoucherPreviewPages(renderer: VoucherRenderer, onBack: () -> Unit) {
    val language = LocalLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { renderer.pageCount })
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = exporting) { /* Avoid cancelling a Gallery export in progress. */ }

    val exportNow: () -> Unit = {
        if (!exporting) {
            exporting = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching { VoucherGalleryExporter.save(context.applicationContext, renderer) }
                }
                exporting = false
                message = result.fold(
                    onSuccess = { count -> language.text(
                        "JPEG ပုံ $count ပုံကို Gallery › Cherry 2D Ledger တွင် သိမ်းပြီးပါပြီ။",
                        "Saved $count JPEG image(s) to Gallery › Cherry 2D Ledger.",
                    ) },
                    onFailure = { error -> language.text(
                        "Gallery ထဲ သိမ်းမရပါ။ ${error.message ?: "ပြန်စမ်းကြည့်ပါ။"}",
                        "Gallery export failed: ${error.message ?: "Please retry."}",
                    ) },
                )
            }
        }
    }
    val legacyPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) exportNow()
        else message = language.text(
            "Android 8/9 တွင် Gallery သိမ်းရန် Storage ခွင့်ပြုချက် လိုအပ်ပါသည်။",
            "Storage permission is required to save images on Android 8/9.",
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(language.text("JPEG Export", "JPEG Export")) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null }) {
                    Text(language.text("အဆင်ပြေ", "OK"))
                }
            },
        )
    }

    AppScaffold(
        title = language.text("ဘောင်ချာအကြိုကြည့်ရန်", "Voucher preview"),
        onBack = { if (!exporting) onBack() },
        includeQuickEntryFab = false,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .background(Color(0xFFEDE7E5)),
            ) { index ->
                // Every 1080 x 1920 Bitmap uses about 8 MiB.
                // Release it as soon as its pager page leaves composition.
                val bitmap by produceState<Bitmap?>(null, renderer, index) {
                    value = null
                    val pageBitmap = withContext(Dispatchers.Default) { renderer.render(index) }
                    value = pageBitmap
                    awaitDispose {
                        value = null
                        pageBitmap.recycle()
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (bitmap == null) CircularProgressIndicator()
                    else Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "ဘောင်ချာ စာမျက်နှာ ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    enabled = pagerState.currentPage > 0 && !exporting,
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                ) { Text(language.text("ရှေ့", "Previous")) }
                Text(language.text(
                    "စာမျက်နှာ ${pagerState.currentPage + 1} / ${renderer.pageCount}",
                    "Page ${pagerState.currentPage + 1} / ${renderer.pageCount}",
                ))
                TextButton(
                    enabled = pagerState.currentPage < renderer.pageCount - 1 && !exporting,
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                ) { Text(language.text("နောက်", "Next")) }
            }
            Button(
                enabled = !exporting,
                onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                        ContextCompat.checkSelfPermission(
                            context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    else exportNow()
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (exporting) CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                ) else Text("Export JPEG — Gallery")
            }
        }
    }
}
