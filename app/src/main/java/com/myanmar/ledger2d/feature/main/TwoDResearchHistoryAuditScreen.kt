package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard
import com.myanmar.ledger2d.core.domain.TwoDResearchHistoryAudit
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.util.Locale

private fun auditLabel(en: Boolean, mm: String, english: String) = if (en) english else mm

@Composable
private fun AuditText(
    text: String, en: Boolean, p: ResearchLabPalette,
    role: LedgerTextRole = LedgerTextRole.BODY,
    color: Color = p.ink, bold: Boolean = false,
) {
    Text(text, color = color, style = LedgerTypographyStandard.style(
        role, if (en) "en" else "my").let {
        if (bold) it.copy(fontWeight = FontWeight.SemiBold) else it
    })
}

private fun slotLabel(slot: DrawIdentity, en: Boolean): String =
    slot.date.toString() + "  ·  " + if (slot.session == DrawSession.MORNING)
        auditLabel(en, "မနက်", "AM") else auditLabel(en, "ညနေ", "PM")

@Composable
private fun AuditMetric(label: String, value: String, en: Boolean, p: ResearchLabPalette,
    modifier: Modifier = Modifier,
) {
    Surface(modifier, shape = RoundedCornerShape(13.dp),
        color = p.mutedSurface, border = BorderStroke(1.dp, p.outline)) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, color = p.ink,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold))
            AuditText(label, en, p, LedgerTextRole.HELPER, p.muted)
        }
    }
}

@Composable
private fun AuditSummaryRow(
    label: String, value: Int, en: Boolean, p: ResearchLabPalette,
) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        AuditText(label, en, p, LedgerTextRole.HELPER, p.muted)
        Text(value.toString(), color = p.ink,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold))
    }
}

/**
 * Transparent, read-only diagnostics for the *reconciled* results fed to
 * Research. Does not claim any missing record was an officially held draw.
 */
@Composable
internal fun TwoDResearchHistoryAuditScreen(
    audit: TwoDResearchHistoryAudit, en: Boolean, p: ResearchLabPalette,
    onClose: () -> Unit, modifier: Modifier = Modifier,
) {
    var limit by remember { mutableIntStateOf(20) }
    val unverified = remember(audit) { audit.unverifiedOpenSlots.asReversed() }
    Column(modifier.fillMaxSize().background(p.background)) {
        LazyColumn(Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)) {
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = p.hero) {
                    Column(Modifier.fillMaxWidth()
                        .background(Brush.horizontalGradient(listOf(p.hero, p.heroLight)))
                        .padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("RESEARCH ATLAS  /  DATA AUDIT",
                                color = Color(0xFF9CE7D9),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold))
                            Icon(Icons.Default.FactCheck, null, tint = Color(0xFF9CE7D9))
                        }
                        Text(auditLabel(en, "သမိုင်းမှတ်တမ်း အရည်အသွေး",
                            "Verified history coverage"),
                            style = LedgerTypographyStandard.style(
                                LedgerTextRole.CARD_TITLE, if (en) "en" else "my"),
                            color = p.heroText)
                        Text(auditLabel(en,
                            "လက်ရှိ Research တွက်ချက်ရာတွင် အသုံးပြုသော မှတ်တမ်းများကိုသာ စစ်ထားသည်။",
                            "Audits only the reconciled records currently supplied to Research."),
                            color = Color(0xFFB8E0E2),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(19.dp), color = p.card,
                    border = BorderStroke(1.dp, p.outline)) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        AuditText(auditLabel(en, "အရေအတွက်နှိုင်းယှဉ်မှု",
                            "History coverage"), en, p, LedgerTextRole.CARD_TITLE, bold = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AuditMetric(auditLabel(en, "အတည်ပြုထား", "Verified"),
                                audit.verifiedSlots.toString(), en, p, Modifier.weight(1f))
                            AuditMetric(auditLabel(en, "ခန့်မှန်းဖွင့်အကြိမ်", "Open slots"),
                                audit.expectedOpenSlots.toString(), en, p, Modifier.weight(1f))
                            AuditMetric(auditLabel(en, "အတည်မပြုရသေး", "Unknown"),
                                audit.unverifiedCount.toString(), en, p, Modifier.weight(1f))
                        }
                        val ratio = audit.coverageRatio
                        AuditText(
                            if (ratio == null)
                                auditLabel(en, "သမိုင်းအစမှတ်တမ်းမရှိသေးပါ။",
                                    "No baseline verified history is available.")
                            else auditLabel(en, "မှတ်တမ်းပြည့်စုံမှု ",
                                    "Observed slot coverage ") +
                                String.format(Locale.US, "%.1f%%",
                                    ratio * 100.0),
                            en, p, LedgerTextRole.BODY, bold = true,
                        )
                        AuditText(auditLabel(en,
                            "ဒါက ပထမဆုံးအတည်ပြုထားတဲ့ရက်မှ စစ်ဆေးကန့်သတ်အကြိမ်မတိုင်ခင်အထိ ဖွင့်ရက်အကြိမ်များကိုသာ နှိုင်းယှဉ်တာပါ။ မသိရသေးသောအကြိမ်ကို Result ပျောက်ကြောင်း သက်သေဟု မယူဆပါ။",
                            "The window begins at the first verified date and ends before the research cutoff. An unverified open slot does NOT prove a draw was held or a result was lost."),
                            en, p, LedgerTextRole.HELPER, p.muted)
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(19.dp), color = p.card,
                    border = BorderStroke(1.dp, p.outline)) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        AuditText(auditLabel(en, "သီးခြားခွဲစစ်ထားမှု",
                            "Input reconciliation checks"), en, p,
                            LedgerTextRole.CARD_TITLE, bold = true)
                        AuditSummaryRow(auditLabel(en, "ဝင်လာသော Record", "Input rows"),
                            audit.sourceRows, en, p)
                        AuditSummaryRow(auditLabel(en, "ဂဏန်းတွဲမမှန်", "Invalid pairs"),
                            audit.invalidPairRows, en, p)
                        AuditSummaryRow(auditLabel(en, "ပိတ်ရက်/စနေတနင်္ဂနွေ",
                            "Closed days and weekends"), audit.nonWorkingDayRows, en, p)
                        AuditSummaryRow(auditLabel(en, "လက်ရှိအကြိမ်မတိုင်ခင် မဟုတ်",
                            "At or after cutoff"), audit.atOrAfterCutoffRows, en, p)
                        AuditSummaryRow(auditLabel(en, "တူညီသောအကြိမ် ထပ်နေမှု",
                            "Duplicate slot rows"), audit.duplicateSlotRows, en, p)
                        AuditSummaryRow(auditLabel(en, "အကြိမ်တူ Result မတူမှု",
                            "Conflicting slot rows"), audit.conflictingSlots, en, p)
                        AuditText(auditLabel(en,
                            "ဒီစစ်ဆေးချက်က ပြန်လည်ညှိနှိုင်းထားပြီးသား History ကိုသာ မြင်ရတယ်။ မူလ Source အားလုံးကြားက Conflict ကို အပြည့်အစုံ မစစ်နိုင်ပါ။",
                            "This inspects the already reconciled stream; it cannot independently audit all upstream provider conflicts or deletion locks."),
                            en, p, LedgerTextRole.HELPER, p.muted)
                    }
                }
            }
            item {
                AuditText(auditLabel(en, "အတည်မပြုရသေးသော ဖွင့်ရက်အကြိမ်များ",
                    "Unverified scheduled-open slots"), en, p,
                    LedgerTextRole.CARD_TITLE, bold = true)
                if (audit.earliestVerified != null) {
                    AuditText(auditLabel(en, "ပထမဆုံးအတည်ပြုထား",
                        "First verified") + ": " + slotLabel(audit.earliestVerified, en),
                        en, p, LedgerTextRole.HELPER, p.muted)
                }
                if (audit.latestVerified != null) {
                    AuditText(auditLabel(en, "နောက်ဆုံးအတည်ပြုထား",
                        "Last verified") + ": " + slotLabel(audit.latestVerified, en),
                        en, p, LedgerTextRole.HELPER, p.muted)
                }
            }
            if (unverified.isEmpty()) {
                item {
                    AuditText(auditLabel(en,
                        "ရွေးထားသောသမိုင်းကာလအတွင်း မအတည်ပြုရသေးတဲ့ ဖွင့်ရက်အကြိမ် မတွေ့ပါ။",
                        "No unverified open slots were found inside the available window."),
                        en, p, LedgerTextRole.BODY)
                }
            } else {
                items(unverified.take(limit), key = {
                    it.date.toString() + it.session.name
                }) { slot ->
                    Surface(shape = RoundedCornerShape(12.dp),
                        color = p.card, border = BorderStroke(1.dp, p.outline)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically) {
                            AuditText(slotLabel(slot, en), en, p, LedgerTextRole.BODY)
                            AuditText(auditLabel(en, "အတည်မပြုရသေး",
                                "Unverified"), en, p, LedgerTextRole.HELPER, p.muted)
                        }
                    }
                }
                if (limit < unverified.size) {
                    item {
                        TextButton(onClick = { limit += 25 },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = p.accent)) {
                            Text(auditLabel(en, "နောက်ထပ် ၂၅ ကြိမ်ကြည့်ရန်",
                                "Show 25 more"))
                        }
                    }
                }
            }
        }
        Surface(color = p.card, border = BorderStroke(1.dp, p.outline),
            shadowElevation = 6.dp) {
            OutlinedButton(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 9.dp)
                    .heightIn(min = 48.dp),
                border = BorderStroke(1.dp, p.accent),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = p.accent),
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(auditLabel(en, "Research သို့ပြန်ရန်", "Back to Research"))
            }
        }
    }
}
