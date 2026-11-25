@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.domain.QuickFormat
import com.myanmar.ledger2d.core.domain.DrawSchedule
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** UI-only grouping; enum identities and parser remain unchanged. */
internal val betEntryFormatRows = listOf(
    listOf(QuickFormat.MANUAL),
    listOf(QuickFormat.POWER, QuickFormat.ASTROLOGY, QuickFormat.DOUBLES),
    listOf(QuickFormat.SIBLINGS, QuickFormat.COMBINATION, QuickFormat.COMBINATION_DOUBLES),
    listOf(QuickFormat.ROUND, QuickFormat.HEAD, QuickFormat.TAIL),
    listOf(QuickFormat.EVEN_ODD, QuickFormat.ODD_EVEN, QuickFormat.EVEN_EVEN, QuickFormat.ODD_ODD),
)

@Composable
internal fun BetEntryFormatGrid(selected: QuickFormat, enabled: Boolean, onSelect: (QuickFormat) -> Unit) {
    val language = LocalLanguage.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(language.translate("အမြန်ထည့်သွင်းပုံများ"), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = AppColors.PrimaryDeep)
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xEBFFFFFF),
            border = BorderStroke(1.dp, Color.White)) {
            Column(Modifier.fillMaxWidth().padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                betEntryFormatRows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { format ->
                            val active = selected == format
                            val shape = RoundedCornerShape(12.dp)
                            Box(
                                Modifier.weight(1f).heightIn(min = 48.dp).clip(shape)
                                    .background(if (active) AppColors.PrimaryDeep else Color(0xFFFFF5F8))
                                    .border(1.dp, if (active) Color.White else Color(0xFFEFC4D2), shape)
                                    .selectable(selected = active, enabled = enabled, role = Role.RadioButton,
                                        onClick = { onSelect(format) })
                                    .padding(horizontal = 3.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (format == QuickFormat.MANUAL) language.text("အထူး", "Special") else language.translate(format.label),
                                    color = if (active) Color.White else AppColors.PrimaryDeep,
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Date and draw selection share one row on ordinary phones, with a readable
 * stacked fallback for narrow layouts or enlarged system fonts. No new draw state. */
@Composable
internal fun BetEntryDateSessionRow(
    dateText: String,
    session: DrawSession,
    enabled: Boolean,
    onDate: (String) -> Unit,
    onSession: (DrawSession) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth >= 340.dp && LocalDensity.current.fontScale <= 1.25f
        if (compact) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BetEntryCompactDate(dateText, onDate, enabled, Modifier.weight(1f))
                BetEntrySessionSelector(session, onSession, enabled, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BetEntryCompactDate(dateText, onDate, enabled, Modifier.fillMaxWidth())
                BetEntrySessionSelector(session, onSession, enabled, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BetEntryCompactDate(value: String, onValue: (String) -> Unit, enabled: Boolean, modifier: Modifier) {
    val language = LocalLanguage.current
    var open by rememberSaveable { mutableStateOf(false) }
    val display = runCatching { LocalDate.parse(value).displayDate() }.getOrElse { value }
    Surface(onClick = { open = true }, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(15.dp), color = Color(0xEBFFFFFF),
        border = BorderStroke(1.dp, Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.CalendarMonth, contentDescription = language.translate("ရက်စွဲ"),
                tint = AppColors.PrimaryDeep, modifier = Modifier.size(20.dp))
            Text(display, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        }
    }
    // Same date picker and weekday adjustment as the existing DateInput.
    if (open && enabled) {
        val initial = runCatching {
            LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        var picked = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        while (!DrawSchedule.isWeekday(picked)) picked = picked.plusDays(1)
                        onValue(picked.toString())
                    }
                    open = false
                }) { Text(language.translate("ရွေးမည်")) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(language.translate("မလုပ်ပါ")) }
            }) { DatePicker(state) }
    }
}

@Composable
private fun BetEntrySessionSelector(session: DrawSession, onSession: (DrawSession) -> Unit,
    enabled: Boolean, modifier: Modifier) {
    val language = LocalLanguage.current
    SingleChoiceSegmentedButtonRow(modifier) {
        DrawSession.entries.forEachIndexed { i, draw ->
            SegmentedButton(
                selected = session == draw, onClick = { onSession(draw) },
                shape = SegmentedButtonDefaults.itemShape(i, DrawSession.entries.size), enabled = enabled,
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = AppColors.PrimaryDeep, activeContentColor = Color.White,
                    inactiveContainerColor = Color(0xEBFFFFFF), inactiveContentColor = AppColors.PrimaryDeep),
                icon = {},
            ) { Text(language.translate(draw.label), style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center) }
        }
    }
}

/** Exact approved profile-form Save surface, scoped to the betting action. */
@Composable
internal fun BetEntryConfirmButton(onClick: () -> Unit, modifier: Modifier, enabled: Boolean, saving: Boolean) {
    val saveEnabled = enabled && !saving
    val actionShape = RoundedCornerShape(27.dp)
    Button(
        onClick = onClick, enabled = saveEnabled,
        modifier = modifier.height(54.dp)
            .homeRubyShadow(radius = 27.dp, blur = 10.dp, offsetY = 4.dp,
                tint = if (saveEnabled) Color(0x52880E4F) else Color(0x16880E4F))
            .clip(actionShape)
            .background(Brush.verticalGradient(
                if (saveEnabled) listOf(Color(0xFFF44982), Color(0xFFBC0C50), Color(0xFF820A38))
                else listOf(Color(0xFFE9E1E5), Color(0xFFDAD1D6), Color(0xFFCAC0C7))))
            .border(1.dp, if (saveEnabled) Color(0xBBFFD7E5) else Color(0xA6FFFFFF), actionShape),
        shape = actionShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
            contentColor = Color.White, disabledContentColor = Color(0xFF857782)),
        elevation = null,
    ) {
        if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
        else Text(LocalLanguage.current.translate("Confirm"), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black)
    }
}
