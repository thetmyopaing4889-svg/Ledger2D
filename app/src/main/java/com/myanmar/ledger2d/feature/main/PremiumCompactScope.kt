package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.AppColors
import com.myanmar.ledger2d.core.design.LocalLanguage
import com.myanmar.ledger2d.core.profile.ProfileKind

internal data class CherryScopePerson(val kind: ProfileKind, val id: Long, val name: String)

private val scopeShape = RoundedCornerShape(22.dp)
private val scopeActionShape = RoundedCornerShape(13.dp)

/** Compact result context, not a second selection page. Supports one or two
 * identities, aggregate scopes, date ranges and a both-draw label.
 * Only real positive IDs request profile photos. No result or selection state. */
@Composable
internal fun PremiumCompactScope(
    vm: LedgerViewModel,
    agent: CherryScopePerson?,
    customer: CherryScopePerson?,
    dateText: String,
    sessionLabel: String,
    onReselect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier.fillMaxWidth()
            .shadow(5.dp, scopeShape, ambientColor = Color(0x26880E4F), spotColor = Color(0x35880E4F))
            .clip(scopeShape)
            .background(Brush.linearGradient(listOf(Color.White, Color(0xFFFFF8FB), Color(0xFFF6D5E2))))
            .border(1.5.dp, Color.White, scopeShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        val wide = maxWidth >= 280.dp && LocalDensity.current.fontScale <= 1.3f
        if (wide) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScopePeople(vm, agent, customer, Modifier.weight(if (agent != null && customer != null) 1.5f else 1f))
                Box(Modifier.width(1.dp).height(56.dp)
                    .background(Color(0x44A31340), RoundedCornerShape(1.dp)))
                ScopeDateAction(dateText, sessionLabel, onReselect, Modifier.weight(1f))
            }
        } else {
            // Accessibility fallback only: retain both identities and full text.
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScopePeople(vm, agent, customer, Modifier.fillMaxWidth())
                ScopeDateAction(dateText, sessionLabel, onReselect, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ScopePeople(vm: LedgerViewModel, agent: CherryScopePerson?, customer: CherryScopePerson?, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (agent != null) ScopePortrait(vm, agent, Modifier.weight(1f))
        if (agent != null && customer != null) {
            Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                listOf(.35f, .65f, 1f).forEach { alpha ->
                    Icon(Icons.Default.ChevronRight, contentDescription = null,
                        tint = AppColors.PrimaryDeep.copy(alpha = alpha), modifier = Modifier.size(12.dp))
                }
            }
        }
        if (customer != null) ScopePortrait(vm, customer, Modifier.weight(1f))
    }
}

@Composable
private fun ScopePortrait(vm: LedgerViewModel, person: CherryScopePerson, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (person.id > 0L) {
            CherryProfileAvatar(vm.profilePhotos, person.kind, person.id, Modifier.size(40.dp))
        } else {
            // Aggregate is a group glyph, never a fabricated individual portrait.
            Box(Modifier.size(40.dp).clip(CircleShape)
                .background(Color(0xFFF3D6E2)).border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Group, contentDescription = null,
                    tint = AppColors.PrimaryDeep, modifier = Modifier.size(23.dp))
            }
        }
        Text(person.name, style = MaterialTheme.typography.labelMedium,
            color = AppColors.PrimaryDeep, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun ScopeDateAction(dateText: String, sessionLabel: String, onReselect: () -> Unit, modifier: Modifier) {
    val lang = LocalLanguage.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(dateText, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold, color = AppColors.Ink, textAlign = TextAlign.Center)
            Surface(shape = RoundedCornerShape(9.dp), color = Color(0xFFF1CDDC),
                border = BorderStroke(1.dp, Color.White)) {
                Text(sessionLabel, Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall, color = AppColors.PrimaryDeep,
                    textAlign = TextAlign.Center)
            }
        }
        Button(
            onClick = onReselect,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .clip(scopeActionShape)
                .background(Brush.verticalGradient(listOf(Color(0xFFDA4276), Color(0xFF98123F)))),
            shape = scopeActionShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .85f)),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Text(lang.text("ပြန်ရွေးရန်", "Reselect"), style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}
