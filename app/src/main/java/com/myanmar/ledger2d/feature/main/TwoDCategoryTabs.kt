package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.design.LedgerTextRole
import com.myanmar.ledger2d.core.design.LedgerTypographyStandard

/**
 * User-approved one-row selection system: 3 options occupy 3 equal cells;
 * 4 occupy 4, 5 occupy 5. Never wrap into a 2x2 grid or show all
 * category detail panes simultaneously.
 */
@Composable
internal fun TwoDCategoryTabs(
    options: List<Pair<String, String>>,
    selected: String,
    en: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    require(options.size in 1..5) { "The single-row selector supports up to five categories." }
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (options.size >= 5) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { (key, label) ->
            val active = key == selected
            Surface(
                onClick = { onSelect(key) },
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (active) colors.primaryContainer
                    else colors.surfaceVariant.copy(alpha = .48f),
                border = BorderStroke(
                    if (active) 1.7.dp else .7.dp,
                    if (active) colors.primary.copy(alpha = .70f)
                    else colors.outlineVariant.copy(alpha = .70f),
                ),
                shadowElevation = if (active) 4.dp else 1.dp,
                tonalElevation = if (active) 2.dp else 0.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .padding(horizontal = if (options.size >= 5) 1.dp else 3.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        label,
                        style = LedgerTypographyStandard.style(
                            if (options.size >= 5) LedgerTextRole.HELPER else LedgerTextRole.BUTTON,
                            if (en) "en" else "my",
                        ),
                        color = if (active) colors.onPrimaryContainer else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        Modifier.width(if (active) 23.dp else 8.dp).height(3.dp)
                            .background(
                                if (active) colors.primary else Color.Transparent,
                                RoundedCornerShape(3.dp),
                            ),
                    )
                }
            }
        }
    }
}
