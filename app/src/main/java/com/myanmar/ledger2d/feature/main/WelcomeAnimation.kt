package com.myanmar.ledger2d.feature.main

import android.content.Context
import android.media.AudioManager
import android.view.SoundEffectConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.R
import com.myanmar.ledger2d.core.design.LocalLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================================
// Cherry 2D — Welcome
// The screen IS the uploaded poster (welcome_poster_full.png), full-bleed.
// On top of it, ONLY:
//   * gentle "blink blink" light pulses (sparkle shimmer + warm halo)
//   * a slow rose-gold color wave drifting across the poster (screen blend)
//   * a bottom aurora ribbon breathing with the poster's pink floor light
//   * a soft press flash on the way to Home
// The CTA is a real button styled to live inside the poster (glossy pink
// pill, gold ring, cherry mark, Burmese label, gold-ringed arrow chip).
// It is anchored to a poster FRACTION (CTA_FY), not a fixed dp offset, so
// it always sits on the poster's baked floor-ring light. ContentScale.Crop
// fits the poster height on portrait phones, so vertical poster fractions
// map linearly to the screen height. When the poster ships a baked-in
// CTA, set CTA_FY to the pill center measured by tools/measure_cta.py and
// the real button covers it exactly. Press -> flash -> onContinue().
// Public API stays WelcomeScreen(onContinue: () -> Unit).
// ============================================================================

// Vertical center of the CTA as a fraction of the poster height.
// Measured on the current poster's pink floor-ring center (tools/measure_cta.py).
private const val CTA_FY = 0.79f
// Horizontal insets as fractions of screen width (poster pill width ≈ 84%).
private const val CTA_HFRACTION = 0.055f

private fun playWelcomeClick(view: android.view.View) {
    val audio = view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL) view.playSoundEffect(SoundEffectConstants.CLICK)
}

@Composable
private fun WelcomeDarkBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, it.decorView) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = true }
    }
}

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val l = LocalLanguage.current
    val view = LocalView.current
    WelcomeDarkBars()

    // ---- shared blink clock ------------------------------------------------
    val blink = rememberInfiniteTransition(label = "welcome-blink")
    val sparkBlink = blink.animateFloat(
        initialValue = 0.30f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "spark")
    val haloBlink = blink.animateFloat(
        initialValue = 0.55f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
        label = "halo")
    val auroraBlink = blink.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Reverse),
        label = "aurora")
    val wavePhase = blink.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "wavePhase")

    var pressedOut by remember { mutableStateOf(false) }
    val exitFlash = remember { Animatable(0f) }

    LaunchedEffect(pressedOut) {
        if (pressedOut) {
            launch { exitFlash.animateTo(1f, tween(360, easing = LinearEasing)) }
            delay(340)
            onContinue()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF150309))
    ) {
        // Layer 0 — the poster itself, full-bleed, edge to edge.
        Image(
            painter = painterResource(R.drawable.welcome_poster_full),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // Warm rose color-wave drifting across the poster.
                    // Two soft bands ride the wave; pure additive light,
                    // never covering the art (screen-ish via moderate alpha).
                    val t = wavePhase.value
                    val w = size.width
                    val hgt = size.height
                    val band = w * 0.55f
                    val x1 = w * 1.25f - (w * 1.25f + band) * t
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color(0x24FFB27A),   // rose-gold breath
                            1f to Color.Transparent,
                            start = Offset(x1, 0f),
                            end = Offset(x1 + band, hgt * 0.9f)
                        )
                    )
                    val x2 = w * 1.45f - (w * 1.45f + band) * ((t + 0.5f) % 1f)
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color(0x1EFF6E9C),   // pink breath
                            1f to Color.Transparent,
                            start = Offset(x2, 0f),
                            end = Offset(x2 + band, hgt)
                        )
                    )
                }
        )

        // Layer 1 — gentle blink veil: a faint warm light that breathes.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.16f * haloBlink.value }
                .background(
                    Brush.radialGradient(
                        0f to Color(0x40FFC9A8),
                        0.55f to Color(0x20FF8FB4),
                        1f to Color.Transparent
                    )
                )
        )

        // Layer 2 — bottom aurora ribbons echoing the poster's pink floor
        // light (color-wave bands that slowly sway side to side).
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(240.dp)
                .graphicsLayer { alpha = auroraBlink.value }
                .drawBehind {
                    val hgt = size.height
                    val w = size.width
                    val sway = kotlin.math.sin(wavePhase.value * 2f * Math.PI.toFloat()) * w * 0.06f
                    // wide soft ribbon
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color(0x2EFF4D79),
                            1f to Color(0x00FFD1E0)
                        ),
                        topLeft = Offset(sway, 0f),
                        size = androidx.compose.ui.geometry.Size(w - sway, hgt)
                    )
                    // bright core ribbon
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color(0x26FFB27A),
                            1f to Color.Transparent
                        ),
                        topLeft = Offset(-sway * 1.4f, hgt * 0.18f),
                        size = androidx.compose.ui.geometry.Size(w + sway * 1.4f, hgt * 0.82f)
                    )
                }
        )

        // Layer 3 — sparkle dust: tiny glints twinkling over the upper poster.
        WelcomeSparkles(alpha = sparkBlink.value)

        // Layer 4 — CTA (real button) anchored to the poster fraction so it
        // sits on the poster's baked floor-ring light at any screen height.
        BoxWithConstraints(Modifier.matchParentSize()) {
            // Under Crop with height fitting exactly, screen fy == poster fy.
            val centerY = maxHeight * CTA_FY
            WelcomeCtaButton(
                label = l.translate("စတင်အသုံးပြုမည်"),
                glow = ctaGlowValue(),
                enabled = !pressedOut,
                onPress = {
                    playWelcomeClick(view)
                    if (!pressedOut) pressedOut = true
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = centerY)
                    .offset(y = (-31).dp) // own height/2 to center on the anchor
            )
        }

        // Exit flash — soft pink/white light that carries the screen to Home.
        if (pressedOut) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = exitFlash.value.coerceIn(0f, 1f) }
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color.White.copy(alpha = 0.96f),
                                Color(0xFFFFC9D9).copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }
}

// Small helper so the CTA glow shares the main blink clock without
// threading another parameter through composition.
@Composable
private fun ctaGlowValue(): Float = rememberInfiniteTransition(label = "cta-blink").animateFloat(
    initialValue = 0.45f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
    label = "ctaGlow").value

// Twinkling glints (tiny 4-point stars) drifting over the poster's upper half.
@Composable
private fun WelcomeSparkles(alpha: Float) {
    val glints = remember {
        listOf(
            Glint(0.10f, 0.10f, 22f, 0f), Glint(0.26f, 0.16f, 15f, 1.3f),
            Glint(0.46f, 0.08f, 18f, 2.1f), Glint(0.66f, 0.13f, 22f, 0.7f),
            Glint(0.86f, 0.09f, 15f, 1.9f), Glint(0.94f, 0.22f, 20f, 2.6f),
            Glint(0.06f, 0.30f, 15f, 3.4f), Glint(0.74f, 0.27f, 13f, 4.1f),
            Glint(0.18f, 0.44f, 13f, 5.0f), Glint(0.58f, 0.36f, 15f, 5.6f),
            Glint(0.90f, 0.40f, 13f, 0.4f), Glint(0.38f, 0.52f, 13f, 1.1f)
        )
    }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        glints.forEach { g ->
            // each glint twinkles on its own phase of the shared 1500ms clock
            val tw = (kotlin.math.sin((alpha * 2f + g.phase) * Math.PI.toFloat()) + 1f) / 2f
            val a = alpha * (0.25f + 0.75f * tw)
            val cx = g.xf * w
            val cy = g.yf * h
            val r = g.pxSize * (0.7f + 0.5f * tw)
            drawGlint(cx, cy, r, a)
        }
    }
}

private data class Glint(val xf: Float, val yf: Float, val pxSize: Float, val phase: Float)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlint(x: Float, y: Float, r: Float, a: Float) {
    if (a <= 0.02f) return
    val col = Color(0xFFFFF3DA)
    // 4-point star: two slim crossed rays + bright core
    drawLine(col.copy(alpha = 0.75f * a), Offset(x - r, y), Offset(x + r, y), strokeWidth = r * 0.10f)
    drawLine(col.copy(alpha = 0.75f * a), Offset(x, y - r), Offset(x, y + r), strokeWidth = r * 0.10f)
    drawCircle(col.copy(alpha = 0.9f * a), radius = r * 0.22f, center = Offset(x, y))
    drawCircle(Color.White.copy(alpha = a), radius = r * 0.10f, center = Offset(x, y))
}

// ============================================================================
// The CTA — glossy pink pill with gold rim + cherry mark + Burmese label +
// gold-ringed arrow chip, floating over the poster's floor-ring light.
// Only motion: gentle outer glow pulse + travelling highlight sweep + press.
// ============================================================================
@Composable
private fun WelcomeCtaButton(
    label: String,
    glow: Float,
    enabled: Boolean,
    onPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "welcomeCtaPress"
    )
    val ctaShape = RoundedCornerShape(31.dp)
    val sweep = rememberInfiniteTransition(label = "cta-sweep").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)),
        label = "ctaSweep")

    Box(
        modifier
            // Fraction-based width keeps the pill's side margins proportional
            // to the poster's margins at any screen width; still centered.
            .fillMaxWidth(1f - 2 * CTA_HFRACTION)
            .height(62.dp)
    ) {
        // Outer glow pulse (behind the pill, echoing the floor light)
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = 0.42f * glow
                    scaleX = 1.05f + 0.02f * glow
                    scaleY = 1.22f + 0.06f * glow
                }
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0x66FF4D79), Color(0x40FFB27A))
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            size.height / 2f, size.height / 2f)
                    )
                }
        )
        // Pill body
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(ctaShape)
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xFFFF4D8F), 0.55f to Color(0xFFEF2860), 1f to Color(0xFFC40E4A)
                    )
                )
                .border(
                    2.dp,
                    Brush.horizontalGradient(
                        listOf(Color(0xFFFFE9A8), Color(0xFFC9962E), Color(0xFFFFD98A), Color(0xFFB87F2C))
                    ),
                    ctaShape
                )
        ) {
            // Glass top highlight
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.38f),
                                0.42f to Color.Transparent
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                size.height / 2f, size.height / 2f)
                        )
                    }
            )
            // Travelling highlight sweep (gentle blink)
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        val x = size.width * (sweep.value * 1.5f - 0.25f)
                        drawRect(
                            brush = Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.30f),
                                1f to Color.Transparent,
                                start = Offset(x, 0f),
                                end = Offset(x + size.width * 0.32f, size.height)
                            ),
                            blendMode = BlendMode.Screen
                        )
                    }
            )
            Button(
                onClick = onPress,
                enabled = enabled,
                interactionSource = interaction,
                modifier = Modifier.matchParentSize(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = Color.White
                ),
                shape = ctaShape,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cherry_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.size(10.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x998F0E2E))
                        .border(
                            1.6.dp,
                            Brush.linearGradient(listOf(Color(0xFFFFE9A8), Color(0xFFC9962E))),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ArrowForward, null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
