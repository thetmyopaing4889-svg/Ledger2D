package com.myanmar.ledger2d.feature.main

import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.core.design.LocalLanguage

/**
 * Isolated, read-only typography experiment. Nothing here changes the app theme,
 * settings, existing cards, database, or exported JPEG renderer.
 */
internal data class FontLabToken(
    val key: String,
    val english: String,
    val myanmar: String,
    val sizeSp: Int,
    val lineSp: Int,
    val weight: FontWeight,
)

internal val fontLabTokens = listOf(
    FontLabToken("appBar", "Agent statement", "ဒိုင်စာရင်းရှင်းတမ်း", 19, 25, FontWeight.SemiBold),
    FontLabToken("section", "Weekly results", "အပတ်စဉ် ရလဒ်အချက်အလက်", 17, 23, FontWeight.SemiBold),
    FontLabToken("card", "Commission earnings", "ကော်မရှင်ရငွေ", 16, 23, FontWeight.SemiBold),
    FontLabToken("body", "Manage the ledger for each agent and customer.", "ဒိုင်နှင့် ထိုးသားအလိုက် စာရင်းများကို စီမံပါ။", 14, 21, FontWeight.Normal),
    FontLabToken("helper", "These selections appear automatically in every dashboard feature.", "လုပ်ဆောင်ချက်တိုင်းတွင် ရွေးထားသော ဒိုင်နှင့် ထိုးသားကို အလိုအလျောက်ပြပါမည်။", 13, 19, FontWeight.Normal),
    FontLabToken("button", "View statement", "စာရင်းကြည့်ရန်", 14, 20, FontWeight.Medium),
    FontLabToken("money", "1,234,567,890 MMK", "1,234,567,890 ကျပ်", 18, 24, FontWeight.Bold),
)

@Composable
fun FontLabScreen(onBack: () -> Unit) {
    val language = LocalLanguage.current
    // Keep the lab language independent of the saved app-language preference.
    var sampleMyanmar by rememberSaveable { mutableStateOf(language.code != "en") }
    var scalePercent by rememberSaveable { mutableIntStateOf(100) }
    val scale = scalePercent / 100f
    // This is the device's system-named Myanmar family, NOT a bundled Noto font.
    // OEMs may substitute a different font or return the default family.
    val deviceMyanmar = remember {
        FontFamily(Typeface.create("sans-serif-myanmar", Typeface.NORMAL))
    }

    AppScaffold(
        title = language.text("စာလုံးစမ်းသပ်ခန်း", "Typography Lab"),
        onBack = onBack,
        includeQuickEntryFab = false,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    language.text(
                        "Font စမ်းသပ်ခန်းသာဖြစ်သည်။ အခြား Screen များ၊ App Setting နှင့် JPEG များကို မပြောင်းပါ။",
                        "Preview only. This does not change other screens, saved settings, or JPEGs.",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                Text(language.text("စာသားစမ်းမည့် ဘာသာစကား", "Sample language"),
                    style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !sampleMyanmar,
                        onClick = { sampleMyanmar = false },
                        label = { Text("English") },
                    )
                    FilterChip(
                        selected = sampleMyanmar,
                        onClick = { sampleMyanmar = true },
                        label = { Text("မြန်မာ") },
                    )
                }
            }
            item {
                Text(language.text("စမ်းသပ်အရွယ်အစား", "Preview scale"),
                    style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100, 115, 130).forEach { percent ->
                        FilterChip(
                            selected = scalePercent == percent,
                            onClick = { scalePercent = percent },
                            label = { Text(percent.toString() + "%") },
                        )
                    }
                }
                Text(
                    language.text(
                        "ဒီ % က စမ်းသပ်ခန်းထဲက စာသားများကိုသာ ချဲ့တာဖြစ်ပြီး Android ရဲ့ Font Setting မဟုတ်ပါ။",
                        "Scale applies only to these samples; it does not alter Android font settings.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            item {
                FontLabCandidate(
                    title = language.text("လက်ရှိ App Font", "Current app font"),
                    detail = "Compose SansSerif + Android fallback",
                    family = FontFamily.SansSerif,
                    myanmar = sampleMyanmar,
                    scale = scale,
                )
            }
            item {
                FontLabCandidate(
                    title = language.text("ဖုန်းပါ Myanmar Font", "Device Myanmar family"),
                    detail = "Android sans-serif-myanmar (device supplied)",
                    family = deviceMyanmar,
                    myanmar = sampleMyanmar,
                    scale = scale,
                )
            }
            item {
                Text(
                    language.text(
                        "သတိပြုရန် — ဒုတိယ Font သည် ဖုန်းထဲမှ ရယူထားခြင်းဖြစ်သည်။ Noto Sans Myanmar အစစ်ကို APK ထဲ မထည့်ရသေးပါ။ ဖုန်းအလိုက် မူလ Font နှင့် တူနိုင်သည်။",
                        "Important: the second sample uses the device's Myanmar typeface. Noto Sans Myanmar is not bundled. It may match the default font on some phones.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun FontLabCandidate(
    title: String,
    detail: String,
    family: FontFamily,
    myanmar: Boolean,
    scale: Float,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            fontLabTokens.forEach { role ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        role.key + "  •  " + role.sizeSp + "sp / " + role.lineSp + "sp",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (myanmar) role.myanmar else role.english,
                        style = TextStyle(
                            fontFamily = family,
                            fontSize = (role.sizeSp * scale).sp,
                            lineHeight = (role.lineSp * scale).sp,
                            fontWeight = role.weight,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Text(
                if (myanmar) "ဒိုင် / ထိုးသားရွေးချယ်မှု နမူနာ" else "Dependent selector example",
                style = MaterialTheme.typography.labelSmall,
            )
            OutlinedTextField(
                value = if (myanmar) "ဒိုင်ရွေးပြီးမှ ထိုးသားရွေးပါ" else "Select an agent before choosing a customer",
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = {
                    Text(
                        if (myanmar) "ထိုးသားရွေးရန်" else "Select customer",
                        style = TextStyle(fontFamily = family, fontSize = (12 * scale).sp),
                    )
                },
                textStyle = TextStyle(
                    fontFamily = family,
                    fontSize = (16 * scale).sp,
                    lineHeight = (24 * scale).sp,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
