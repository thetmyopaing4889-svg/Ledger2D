package com.myanmar.ledger2d.feature.main

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A softly blurred colored silhouette behind Home glass, not a second card.
 * Draws just the shadow, then the caller clips and paints the one glass surface.
 * The small native paint is cached per size so scrolling does not recreate it.
 * Compose elevation provides a fallback for devices with weak mask filters.
 */
internal fun Modifier.homeRubyShadow(
    radius: Dp,
    blur: Dp = 14.dp,
    offsetY: Dp = 6.dp,
    tint: Color = Color(0x2E880E4F),
): Modifier = this
    .shadow(
        6.dp, RoundedCornerShape(radius),
        ambientColor = tint, spotColor = tint,
    )
    .drawWithCache {
        val cornerPx = radius.toPx()
        val offsetPx = offsetY.toPx()
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint.toArgb()
            maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        onDrawBehind {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRoundRect(
                    0f, offsetPx,
                    size.width, size.height + offsetPx,
                    cornerPx, cornerPx, shadowPaint
                )
            }
        }
    }
