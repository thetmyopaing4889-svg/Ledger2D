package com.myanmar.ledger2d.core.design

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared, bilingual form geometry. Size is a minimum rather than a hard cap:
 * accessibility font scaling can expand fields without hiding user data.
 */
object LedgerControlDimensions {
    val selectorMinHeight = 68.dp
    val selectorSpacing = 10.dp
    val fabSafeBottom = 96.dp
    val selectorValueSize = 14.sp
    val selectorLabelSize = 12.sp
}
