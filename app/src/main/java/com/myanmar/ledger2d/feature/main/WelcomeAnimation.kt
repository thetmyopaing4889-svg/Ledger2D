package com.myanmar.ledger2d.feature.main

import android.content.Context
import android.media.AudioManager
import android.view.SoundEffectConstants
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.R
import com.myanmar.ledger2d.core.design.LocalLanguage
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================================
// Cherry 2D — Welcome (reference poster, static art + gentle blinks only)
// The screen IS the reference poster: welcome_poster_bg.png fills the screen,
// the cherry hero art sits on its baked seat, and the metallic "Cherry 2D"
// logo, LEDGER line, four feature tiles and the gold-ringed CTA sit on top.
// The ONLY motion is gentle blinking glows (sparkles, halo shimmer, CTA glow)
// plus a soft press flash; tapping the CTA navigates straight to Home.
// Public API stays WelcomeScreen(onContinue: () -> Unit).
// ============================================================================

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
    val density = LocalDensity.current

    // Blink clock: one shared infinite transition drives every gentle blink.
    val blink = rememberInfiniteTransition(label = "welcome-blink")
    val sparkBlink = blink.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse), label = "spark")
    val haloBlink = blink.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Reverse), label = "halo")
    val ctaGlow = blink.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse), label = "ctaGlow")

    var pressedOut by remember { mutableStateOf(false) }
    // Soft white flash that covers the screen on the way to Home.
    val exitFlash = remember { androidx.compose.animation.core.Animatable(0f) }

    androidx.compose.runtime.LaunchedEffect(pressedOut) {
        if (pressedOut) {
            launch { exitFlash.animateTo(1f, tween(360, easing = LinearEasing)) }
            delay(340)
            onContinue()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1A050D))
    ) {
        // Layer 1 — the reference poster art (everything baked in: vines,
        // blossoms, glow dome, bubbles, petals, floor rings).
        Image(
            painter = painterResource(R.drawable.welcome_poster_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2 — the cherry hero on its baked seat, with a gentle halo blink.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp)
                .size(320.dp)
                .graphicsLayer { alpha = 1f }
        ) {
            // Halo shimmer (very soft; the poster already has its own dome)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.30f * haloBlink.value }
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0x30FF4D79), Color(0x14FF7BA3), Color.Transparent)
                        )
                    )
            )
            Image(
                painter = painterResource(R.drawable.cherry_hero_art),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.96f + 0.04f * sparkBlink.value }
            )
        }

        // Layer 3 — content: logo, tiles, CTA, footer.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WelcomeLogo(shimmer = sparkBlink.value)
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                val tiles = listOf(
                    Triple(Icons.Default.FormatListNumbered, l.translate("စာရင်းထိုးသွင်း"), l.translate("စတင်ခြင်း")),
                    Triple(Icons.Default.People, l.translate("ဝန်ထမ်း"), l.translate("ကိုယ်စားထိုးသားများ")),
                    Triple(Icons.Default.BarChart, l.translate("စာရင်း"), l.translate("စာရင်းဇယား")),
                    Triple(Icons.Default.Settings, l.translate("စနစ်ထည့်သွင်း"), l.translate("ထိန်းချုပ်မှုများ"))
                )
                tiles.forEach { (icon, line1, line2) ->
                    WelcomeFeatureTile(
                        icon = icon,
                        line1 = line1,
                        line2 = line2,
                        shimmer = sparkBlink.value,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            WelcomeCtaButton(
                label = l.translate("စတင်အသုံးပြုမည်"),
                glow = ctaGlow.value,
                enabled = !pressedOut,
                onPress = {
                    playWelcomeClick(view)
                    if (!pressedOut) pressedOut = true
                }
            )
            Spacer(Modifier.height(12.dp))
            WelcomeFooter(shimmer = sparkBlink.value)
        }

        // Exit flash — a soft pink/white light that covers the screen to Home.
        if (pressedOut) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = exitFlash.value.coerceIn(0f, 1f) }
                    .background(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.96f), Color(0xFFFFC9D9).copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
            )
        }
    }
}

// ============================================================================
// Metallic "Cherry 2D" logo + underline flourish + LEDGER line.
// Only motion: a slow light sweep gliding across the glyphs (gentle blink).
// ============================================================================
@Composable
private fun WelcomeLogo(shimmer: Float) {
    val density = LocalDensity.current
    val shY = with(density) { 3.sp.toPx() }
    val shBlur = with(density) { 8.sp.toPx() }
    val sweep = rememberInfiniteTransition(label = "logo-sweep").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "logoSweep")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    // Offscreen buffer so the sweep only meets the glyph pixels
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        val x = size.width * (sweep.value * 1.7f - 0.35f)
                        drawRect(
                            brush = Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.40f * shimmer),
                                1f to Color.Transparent,
                                start = Offset(x, 0f),
                                end = Offset(x + size.width * 0.30f, size.height)
                            ),
                            blendMode = BlendMode.SrcAtop
                        )
                    }
            ) {
                Text(
                    "Cherry",
                    style = TextStyle(
                        brush = Brush.verticalGradient(
                            0f to Color(0xFFF8E3A1), 0.38f to Color(0xFFE7B457), 0.62f to Color(0xFFB87F2C), 0.85f to Color(0xFFF3D98B), 1f to Color(0xFFD9A84E)
                        ),
                        fontSize = 44.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                        shadow = Shadow(color = Color(0x995A3200), offset = Offset(0f, shY), blurRadius = shBlur)
                    )
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    "2D",
                    style = TextStyle(
                        brush = Brush.verticalGradient(
                            0f to Color(0xFFFFD3E0), 0.4f to Color(0xFFFF6E9C), 0.75f to Color(0xFFE11D48), 1f to Color(0xFFFF87AB)
                        ),
                        fontSize = 44.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
                        shadow = Shadow(color = Color(0x8C7A0F2E), offset = Offset(0f, shY), blurRadius = shBlur)
                    )
                )
            }
            // Blossom accent perched on the wordmark
            androidx.compose.foundation.Canvas(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 0.dp)
                    .offset(x = 84.dp, y = (-9).dp)
                    .size(26.dp)
            ) {
                drawWelcomeBlossom(Offset(size.width / 2f, size.height / 2f), size.width * 0.46f, 0.95f)
            }
        }
        // Underline flourish (static)
        androidx.compose.foundation.Canvas(
            Modifier
                .padding(top = 2.dp)
                .size(210.dp, 14.dp)
        ) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(8.dp.toPx(), 5.dp.toPx())
                quadraticBezierTo(size.width / 2f, size.height * 1.15f, size.width - 8.dp.toPx(), 3.dp.toPx())
            }
            drawPath(
                path,
                brush = Brush.horizontalGradient(listOf(Color(0x00FF87AB), Color(0xFFFFE3EC), Color(0xFFFF87AB))),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        Text(
            "LEDGER",
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 12.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ============================================================================
// Four feature tiles — glass rounded squares, icon + two Burmese label lines.
// Only motion: the icon tint breathes very gently.
// ============================================================================
@Composable
private fun WelcomeFeatureTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    line1: String,
    line2: String,
    shimmer: Float,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .height(96.dp)
            .clip(shape)
            .background(Brush.verticalGradient(0f to Color(0x1AFFFFFF), 1f to Color(0x08FFFFFF)))
            .border(1.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 1f to Color.White.copy(alpha = 0.06f)), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color(0xFFFFB3C6).copy(alpha = 0.75f + 0.25f * shimmer), modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            line1, textAlign = TextAlign.Center, maxLines = 2,
            color = Color.White.copy(alpha = 0.92f),
            fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold
        )
        Text(
            line2, textAlign = TextAlign.Center, maxLines = 2,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 9.sp, lineHeight = 12.sp
        )
    }
}

// ============================================================================
// Glossy pink CTA pill with gold ring: cherry icon, label, gold arrow chip.
// Only motion: outer glow pulse + travelling highlight sweep (gentle blinks),
// press scale, soft white press flash, then navigate.
// ============================================================================
@Composable
private fun WelcomeCtaButton(
    label: String,
    glow: Float,
    enabled: Boolean,
    onPress: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
        label = "welcomeCtaPress"
    )
    val ctaShape = RoundedCornerShape(31.dp)
    val sweep = rememberInfiniteTransition(label = "cta-sweep").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "ctaSweep")
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .padding(top = 18.dp)
            .height(62.dp)
    ) {
        // Outer glow pulse (behind the pill)
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val pulse = glow
                    alpha = 0.40f * pulse
                    scaleX = 1.04f + 0.02f * pulse
                    scaleY = 1.20f + 0.06f * pulse
                }
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(Color(0x66FF4D79), Color(0x40FF87AB))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(ctaShape)
                .background(Brush.horizontalGradient(0f to Color(0xFFFF4D8F), 0.55f to Color(0xFFEF2860), 1f to Color(0xFFC40E4A)))
                // Gold ring — the ref CTA's metallic rim
                .border(
                    2.dp,
                    Brush.horizontalGradient(listOf(Color(0xFFFFE9A8), Color(0xFFC9962E), Color(0xFFFFD98A), Color(0xFFB87F2C))),
                    ctaShape
                )
        ) {
            // Glass top highlight
            Box(
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawRoundRect(
                            brush = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.38f), 0.42f to Color.Transparent),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
                        )
                    }
            )
            // Travelling highlight sweep
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
                Text(label, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(10.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x998F0E2E))
                        // Gold-ringed arrow chip (ref)
                        .border(1.6.dp, Brush.linearGradient(listOf(Color(0xFFFFE9A8), Color(0xFFC9962E))), CircleShape),
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

// ============================================================================
// Footer: tiny blossom + letterspaced brand tagline.
// ============================================================================
@Composable
private fun WelcomeFooter(shimmer: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
            drawWelcomeBlossom(Offset(size.width / 2f, size.height / 2f), size.width * 0.42f, 0.9f)
        }
        Text(
            "MYANMAR 2D · SIMPLE · FAST · RELIABLE",
            color = Color.White.copy(alpha = 0.45f + 0.12f * shimmer),
            fontSize = 10.sp,
            letterSpacing = 2.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// A small five-petal blossom puff (logo accent + footer).
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWelcomeBlossom(center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0.004f || radius <= 1f) return
    repeat(5) { k ->
        val ang = k * 72f * Math.PI.toFloat() / 180f - 90f * Math.PI.toFloat() / 180f
        drawCircle(
            color = Color(0xFFFF9FC0).copy(alpha = 0.92f * alpha),
            radius = radius * 0.52f,
            center = center + Offset(cos(ang) * radius * 0.52f, sin(ang) * radius * 0.52f)
        )
    }
    drawCircle(color = Color(0xFFFFD9E4).copy(alpha = alpha), radius = radius * 0.34f, center = center)
    drawCircle(color = Color(0xFFFFF1F5).copy(alpha = 0.9f * alpha), radius = radius * 0.15f, center = center)
}
