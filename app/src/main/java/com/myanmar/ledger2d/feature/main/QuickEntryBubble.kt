package com.myanmar.ledger2d.feature.main

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.R
import com.myanmar.ledger2d.core.design.LocalLanguage
import kotlin.math.roundToInt

internal val QuickEntryBubbleHeaderSlot = 64.dp
private val BubbleSize = 48.dp
private val BubbleMargin = 8.dp
private val HeaderHeight = 64.dp

// New keys intentionally reset the old bottom-right pixel offset once on upgrade.
internal const val QuickEntryBubbleXKey = "quick_entry_bubble_v2_x"
internal const val QuickEntryBubbleYKey = "quick_entry_bubble_v2_y"

internal data class QuickEntryBubblePosition(val x: Float = 1f, val y: Float = 0f)
internal data class QuickEntryBubblePoint(val x: Float, val y: Float)

/** Pure normalized positioning. Header is reserved for the orb's own trailing slot. */
internal class QuickEntryBubbleBounds(
    val maxX: Float,
    val maxY: Float,
    val headerClearance: Float,
    val dockTolerance: Float,
) {
    private fun fraction(value: Float, fallback: Float): Float =
        if (value.isFinite()) value.coerceIn(0f, 1f) else fallback

    fun point(position: QuickEntryBubblePosition): QuickEntryBubblePoint {
        val x = fraction(position.x, 1f) * maxX
        val y = fraction(position.y, 0f) * maxY
        if (y < headerClearance) {
            if (x >= maxX - dockTolerance || maxY < headerClearance) {
                return QuickEntryBubblePoint(maxX, if (maxY < headerClearance) 0f else y)
            }
            return QuickEntryBubblePoint(x, headerClearance)
        }
        return QuickEntryBubblePoint(x, y)
    }

    fun position(x: Float, y: Float): QuickEntryBubblePosition =
        QuickEntryBubblePosition(
            if (maxX > 0f) fraction(x / maxX, 1f) else 1f,
            if (maxY > 0f) fraction(y / maxY, 0f) else 0f,
        )

    fun canonical(value: QuickEntryBubblePosition): QuickEntryBubblePosition {
        val point = point(value)
        return position(point.x, point.y)
    }
}

private fun SharedPreferences.bubblePosition() = QuickEntryBubblePosition(
    getFloat(QuickEntryBubbleXKey, 1f), getFloat(QuickEntryBubbleYKey, 0f),
)

/** A full-screen non-intercepting overlay; only the actual 48dp orb is interactive. */
@Composable
internal fun QuickEntryFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ledger_ui", Context.MODE_PRIVATE) }
    var position by remember(prefs) { mutableStateOf(prefs.bubblePosition()) }
    // A retained screen receives the latest placement when another screen saves it.
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == QuickEntryBubbleXKey || key == QuickEntryBubbleYKey) {
                position = prefs.bubblePosition()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    BoxWithConstraints(
        modifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime)),
    ) {
        val density = LocalDensity.current
        val marginX = minOf(BubbleMargin, ((maxWidth - BubbleSize) / 2).coerceAtLeast(0.dp))
        val marginY = minOf(BubbleMargin, ((maxHeight - BubbleSize) / 2).coerceAtLeast(0.dp))
        val bounds = with(density) {
            QuickEntryBubbleBounds(
                maxX = (maxWidth - BubbleSize - marginX * 2).coerceAtLeast(0.dp).toPx(),
                maxY = (maxHeight - BubbleSize - marginY * 2).coerceAtLeast(0.dp).toPx(),
                headerClearance = (HeaderHeight - marginY).toPx(),
                dockTolerance = 4.dp.toPx(),
            )
        }
        val marginXPx = with(density) { marginX.toPx() }
        val marginYPx = with(density) { marginY.toPx() }
        if (maxWidth >= BubbleSize && maxHeight >= BubbleSize) {
            Box(
                modifier = Modifier
                    .absoluteOffset {
                        val point = bounds.point(position)
                        IntOffset((marginXPx + point.x).roundToInt(), (marginYPx + point.y).roundToInt())
                    }
                    .size(BubbleSize)
                    .pointerInput(bounds.maxX, bounds.maxY, bounds.headerClearance, bounds.dockTolerance) {
                        var rawX = 0f
                        var rawY = 0f
                        fun persistPlacement() {
                            position = bounds.canonical(position)
                            prefs.edit()
                                .putFloat(QuickEntryBubbleXKey, position.x)
                                .putFloat(QuickEntryBubbleYKey, position.y)
                                .apply()
                        }
                        detectDragGestures(
                            onDragStart = {
                                val point = bounds.point(position)
                                rawX = point.x
                                rawY = point.y
                            },
                            onDragEnd = { persistPlacement() },
                            onDragCancel = { persistPlacement() },
                        ) { change, dragAmount ->
                            change.consume()
                            rawX = (rawX + dragAmount.x).coerceIn(0f, bounds.maxX)
                            rawY = (rawY + dragAmount.y).coerceIn(0f, bounds.maxY)
                            position = bounds.position(rawX, rawY)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Only a tiny stationary halo uses blur (Android 12+); shadow/rim
                // remain on older Android. No background-content blur or animation.
                Box(
                    Modifier.size(40.dp).blur(5.dp)
                        .background(Color(0x40D81B60), CircleShape),
                )
                Surface(
                    onClick = onClick,
                    modifier = Modifier.size(BubbleSize).shadow(
                        4.dp, CircleShape,
                        ambientColor = Color(0x66D81B60), spotColor = Color(0x66D81B60),
                    ),
                    shape = CircleShape,
                    color = Color(0xB8FFFFFF),
                    border = BorderStroke(1.dp, Color(0xD9FFFFFF)),
                ) {
                    Box(
                        Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0xE6FFFFFF), Color(0x55F6DCE5))), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.btn_cherry_orb),
                            contentDescription = LocalLanguage.current.translate("အမြန်စာရင်းသွင်းရန်"),
                            modifier = Modifier.size(42.dp).clip(CircleShape)
                                .graphicsLayer { scaleX = 1.13f; scaleY = 1.13f }
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            alpha = .94f,
                        )
                    }
                }
            }
        }
    }
}
