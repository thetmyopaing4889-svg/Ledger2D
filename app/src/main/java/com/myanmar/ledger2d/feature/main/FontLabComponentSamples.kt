package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Read-only, mock-data preview of the risky current UI proportions.
 * Production composables, navigation and screen styles are never called or changed.
 */
@Composable
internal fun FontLabComponentSamples(myanmar: Boolean, scale: Float, family: FontFamily) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (myanmar) "၁။ Home — အပတ်စဉ် Card" else "1. Home — weekly card",
            style = MaterialTheme.typography.labelSmall, color = muted)
        Card(modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Text("◉") }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(if (myanmar) "အပတ်စဉ် ရလဒ်အချက်အလက်" else "Weekly results",
                            style = labStyle(family, 17, 23, FontWeight.Black, scale),
                            color = textColor)
                        Text("2 Nov – 6 Nov 2026",
                            style = labStyle(family, 12, 17, FontWeight.Bold, scale),
                            color = muted)
                    }
                    Box(Modifier.width(78.dp), contentAlignment = Alignment.Center) {
                        Text("‹    ›", style = labStyle(family, 17, 23, FontWeight.Bold, scale))
                    }
                }
                Row(Modifier.fillMaxWidth().height(38.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(48.dp))
                    listOf("2 Mon", "3 Tue", "4 Wed", "5 Thu", "6 Fri").forEach { day ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(day, style = labStyle(family, 11, 16, FontWeight.Medium, scale),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                listOf(
                    (if (myanmar) "မနက်" else "Morning") to listOf("97", "64", "35", "--", "--"),
                    (if (myanmar) "ညနေ" else "Evening") to listOf("08", "14", "--", "--", "--"),
                ).forEach { (label, numbers) ->
                    Row(Modifier.fillMaxWidth().height(56.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.width(48.dp),
                            style = labStyle(family, 11, 16, FontWeight.Bold, scale),
                            maxLines = 1, softWrap = false)
                        numbers.forEach { number ->
                            Box(Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center) {
                                Text(number,
                                    style = labStyle(family, 14, 20, FontWeight.Bold, scale),
                                    maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        Text(if (myanmar) "၂။ Home — ကော်မရှင် Card" else "2. Home — commission card",
            style = MaterialTheme.typography.labelSmall, color = muted)
        Card(modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (myanmar) "ကော်မရှင်ရငွေ" else "Commission earnings",
                    style = labStyle(family, 14, 19, FontWeight.Bold, scale),
                    color = textColor)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            (if (myanmar) "မနက်အကြိမ်" else "AM") to "15,000",
                            (if (myanmar) "ညနေအကြိမ်" else "PM") to "25,000",
                            (if (myanmar) "ယနေ့စုစုပေါင်း" else "Today total") to "40,000",
                        ).forEach { (label, value) ->
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, Modifier.weight(1f),
                                    style = labStyle(family, 11, 15, FontWeight.Normal, scale),
                                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = muted)
                                Text(value, style = labStyle(family, 11, 15, FontWeight.Bold, scale),
                                    color = textColor)
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (myanmar) "ယနေ့အထိ ကော်မရှင်ငွေ စုစုပေါင်း" else "Total to date",
                            style = labStyle(family, 11, 15, FontWeight.Normal, scale),
                            maxLines = 2, overflow = TextOverflow.Ellipsis, color = muted)
                        Text(if (myanmar) "1,234,567 ကျပ်" else "1,234,567 MMK",
                            style = labStyle(family, 17, 23, FontWeight.Bold, scale),
                            maxLines = 2, color = textColor)
                    }
                }
            }
        }

        Text(if (myanmar) "၃။ ဒိုင်/ထိုးသား ရွေးချယ်မှု" else "3. Agent / customer selectors",
            style = MaterialTheme.typography.labelSmall, color = muted)
        LabSelector(family, myanmar, scale, agent = true, modifier = Modifier.fillMaxWidth())
        LabSelector(family, myanmar, scale, agent = false, modifier = Modifier.fillMaxWidth())
        Text(if (myanmar) "အကျဉ်း ၂ ကော်လံ နမူနာ" else "Narrow two-column stress test",
            style = MaterialTheme.typography.labelSmall, color = muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LabSelector(family, myanmar, scale, agent = true, modifier = Modifier.weight(1f))
            LabSelector(family, myanmar, scale, agent = false, modifier = Modifier.weight(1f))
        }

        Text(if (myanmar) "၄။ စာရင်းဇယား — ငွေပမာဏ" else "4. Financial table — long amounts",
            style = MaterialTheme.typography.labelSmall, color = muted)
        Card(modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val titles = if (myanmar)
                    listOf("ရက်/အကြိမ်", "ထိုးကြေး", "ပေါက်ကြေး", "လျော်ငွေ")
                else listOf("Date/draw", "Stake", "Winning", "Payout")
                val values = listOf("04/11 AM", "125,000,000", "10,000", "850,000,000")
                listOf(titles, values).forEachIndexed { rowIndex, texts ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        texts.forEachIndexed { i, value ->
                            Text(value,
                                modifier = Modifier.weight(if (i == 0) 1.4f else 1.05f),
                                style = labStyle(family, 11, 16,
                                    if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal, scale),
                                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                                color = textColor)
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Text(
            if (myanmar) "ဒီနမူနာတွေက တကယ့် App အချက်အလက်မဟုတ်ပါ။"
            else "Preview amounts and numbers are not live ledger data.",
            style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun LabSelector(
    family: FontFamily, myanmar: Boolean, scale: Float, agent: Boolean, modifier: Modifier,
) {
    val label = when {
        agent && myanmar -> "ဒိုင်ရွေးရန်"
        agent -> "Select agent"
        myanmar -> "ထိုးသားရွေးရန်"
        else -> "Select customer"
    }
    val value = when {
        agent && myanmar -> "ဒိုင်ရွေးရန်"
        agent -> "Select agent"
        myanmar -> "ဒိုင်ရွေးပြီးမှ ထိုးသားရွေးပါ"
        else -> "Select an agent before choosing a customer"
    }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = agent,
        label = { Text(label, style = labStyle(family, 12, 17, FontWeight.Normal, scale)) },
        textStyle = labStyle(family, 16, 24, FontWeight.Normal, scale),
        // Intentionally no singleLine constraint: current production fields can wrap.
        modifier = modifier,
    )
}

private fun labStyle(
    family: FontFamily, size: Int, line: Int, weight: FontWeight, scale: Float,
) = TextStyle(
    fontFamily = family,
    fontSize = (size * scale).sp,
    lineHeight = (line * scale).sp,
    fontWeight = weight,
)
