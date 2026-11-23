package com.myanmar.ledger2d.feature.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.core.profile.ProfileKind

/** One identity header for selectors' companion cards, reports and grouping labels.
 * Photo is only rendered for a real positive profile ID; aggregate scopes remain text-only.
 * Never use this inside a transaction or amount row.
 */
@Composable
internal fun CherryProfileIdentity(
    vm: LedgerViewModel,
    kind: ProfileKind,
    id: Long,
    name: String,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 38.dp,
    textStyle: TextStyle? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (id > 0L) CherryProfileAvatar(vm.profilePhotos, kind, id, Modifier.size(avatarSize))
        Text(
            name,
            modifier = Modifier.weight(1f),
            style = textStyle ?: MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
