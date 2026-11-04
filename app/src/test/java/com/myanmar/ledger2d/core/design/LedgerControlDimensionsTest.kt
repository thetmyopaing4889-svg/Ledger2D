package com.myanmar.ledger2d.core.design

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerControlDimensionsTest {
    @Test fun sharedControlsHaveAccessibleMinimumAndCompactTypography() {
        assertEquals(68.dp, LedgerControlDimensions.selectorMinHeight)
        assertEquals(10.dp, LedgerControlDimensions.selectorSpacing)
        assertEquals(96.dp, LedgerControlDimensions.fabSafeBottom)
        assertEquals(14.sp, LedgerControlDimensions.selectorValueSize)
        assertEquals(12.sp, LedgerControlDimensions.selectorLabelSize)
    }
}
