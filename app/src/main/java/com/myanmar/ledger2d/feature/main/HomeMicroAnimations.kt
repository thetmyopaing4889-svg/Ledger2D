package com.myanmar.ledger2d.feature.main

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

// All effects live in Home composables; they cancel immediately when Home leaves composition.
// Respect Android's system-wide reduced/off animation setting.
@Composable
private fun homeMotionEnabled(): Boolean = remember { ValueAnimator.areAnimatorsEnabled() }

/** Uses the caller's existing click handler/interaction source. Never creates a second click. */
@Composable
internal fun rememberHomePressScale(label: String): Pair<MutableInteractionSource, Float> {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val enabled = homeMotionEnabled()
    LaunchedEffect(pressed) {
        if (pressed) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) .94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = label,
    )
    return interaction to scale
}

/** Five tiny drifting petals, drawn over the existing header asset only. */
@Composable
internal fun HomeDriftingPetals(modifier: Modifier = Modifier) {
    if (!homeMotionEnabled()) return
    val phase = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            phase.snapTo(0f)
            phase.animateTo(1f, tween(6500, easing = LinearEasing))
        }
    }
    Canvas(modifier.clipToBounds()) {
        val w = size.width
        val h = size.height
        val petalWidth = 6.dp.toPx()
        val petalHeight = 10.dp.toPx()
        val progress = phase.value
        repeat(5) { index ->
            val speed = 6500f / (4000f + index * 520f)
            val p = (progress * speed + index * .21f) % 1f
            val x = w * (.13f + index * .17f) +
                sin(p * (2f * PI.toFloat()) + index * .8f) * w * .018f
            val y = h * (p * 1.12f - .08f)
            val fadeIn = (p / .14f).coerceIn(0f, 1f)
            val fadeOut = ((1f - p) / .18f).coerceIn(0f, 1f)
            val alpha = .56f * minOf(fadeIn, fadeOut)
            if (alpha > 0f) {
                rotate(degrees = p * 115f + index * 28f, pivot = Offset(x, y)) {
                    drawOval(
                        color = Color(0xFFFFD9E8).copy(alpha = alpha),
                        topLeft = Offset(x - petalWidth / 2f, y - petalHeight / 2f),
                        size = Size(petalWidth, petalHeight),
                    )
                }
            }
        }
    }
}

/** One quiet light ribbon over the silk image, followed by a real idle pause. */
@Composable
internal fun HomeLiveLightSweep(modifier: Modifier = Modifier) {
    if (!homeMotionEnabled()) return
    val progress = remember { Animatable(-1f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.snapTo(-1f)
            progress.animateTo(2f, tween(1150, easing = LinearEasing))
            delay(4800)
        }
    }
    Canvas(modifier.clipToBounds()) {
        val x = progress.value * size.width
        val band = size.width * .22f
        drawRect(
            brush = Brush.linearGradient(
                listOf(Color.Transparent, Color(0x20FFFFFF), Color.Transparent),
                start = Offset(x - band, -size.height * .12f),
                end = Offset(x + band, size.height * 1.12f),
            ),
            size = size,
        )
    }
}

/** Only the three tiny translucent highlights move; the existing cherry image stays intact. */
@Composable
internal fun HomeOrbBubbles(modifier: Modifier = Modifier) {
    if (!homeMotionEnabled()) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1700, easing = LinearEasing))
            delay(3500)
        }
    }
    Canvas(modifier.clipToBounds()) {
        val t = progress.value
        repeat(3) { i ->
            val p = (t + i * .27f) % 1f
            val fade = minOf((p / .22f).coerceIn(0f, 1f), ((1f - p) / .28f).coerceIn(0f, 1f))
            val x = size.width * (.24f + i * .27f) +
                sin(p * 6.28f + i) * 2.dp.toPx()
            val y = size.height * (.76f - p * .50f)
            drawCircle(
                color = Color.White.copy(alpha = .23f * fade),
                radius = (1.4f + .35f * i).dp.toPx(),
                center = Offset(x, y),
            )
        }
    }
}

/** A diagonal sheen for ONLY the cumulative commission pink box. */
@Composable
internal fun HomeCommissionShimmer(modifier: Modifier = Modifier) {
    if (!homeMotionEnabled()) return
    val progress = remember { Animatable(-1f) }
    LaunchedEffect(Unit) {
        while (true) {
            progress.snapTo(-1f)
            progress.animateTo(2f, tween(1000, easing = LinearEasing))
            delay(4500) // 5.5-second cycle without repainting throughout the pause
        }
    }
    Canvas(modifier.clipToBounds()) {
        val x = progress.value * size.width
        val width = size.width * .24f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0x00FFFFFF), Color(0x55FFFFFF), Color(0x00FFFFFF)),
                start = Offset(x - width, 0f),
                end = Offset(x + width, size.height),
            ),
            size = size,
        )
    }
}

@Composable
internal fun rememberHomeRollingCount(value: Int): Int {
    val enabled = homeMotionEnabled() && value in 0..999_999
    val animated = remember { Animatable(0f) }
    LaunchedEffect(value, enabled) {
        if (enabled) {
            animated.animateTo(value.toFloat(), tween(600, easing = FastOutSlowInEasing))
        } else {
            animated.snapTo(0f)
        }
    }
    return if (enabled) {
        if (animated.value == value.toFloat()) value else animated.value.roundToInt()
    } else value
}

/** Float is exact for every integer in this bounded range; larger/negative money is always shown exactly. */
internal fun homeMoneyRollEligible(amount: Long): Boolean = amount in 0L..9_999_999L

@Composable
internal fun rememberHomeRollingAmount(amount: Long): Long {
    val enabled = homeMotionEnabled() && homeMoneyRollEligible(amount)
    val animated = remember { Animatable(0f) }
    LaunchedEffect(amount, enabled) {
        if (enabled) {
            animated.animateTo(amount.toFloat(), tween(600, easing = FastOutSlowInEasing))
        } else {
            animated.snapTo(0f)
        }
    }
    // The last frame is always the original Long; the animation never changes ledger totals.
    return if (enabled) {
        if (animated.value == amount.toFloat()) amount else animated.value.roundToLong()
    } else amount
}
