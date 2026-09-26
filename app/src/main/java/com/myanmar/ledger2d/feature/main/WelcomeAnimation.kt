package com.myanmar.ledger2d.feature.main

import android.content.Context
import android.media.AudioManager
import android.view.SoundEffectConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myanmar.ledger2d.R
import com.myanmar.ledger2d.core.design.LocalLanguage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ============================================================================
// Cherry 2D — Welcome animation experience
// Cinematic layered front door: continuously floating depth-layered digits ->
// curved absorption into the living Cherry -> staggered entrance -> cherry-zoom
// transition into Home. Compose animation APIs + a single Canvas; no 3D engine.
// ============================================================================

private val WelcomeEasingOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val WelcomeEasingIn = CubicBezierEasing(0.4f, 0f, 0.8f, 0.2f)

// Fractional placement around the cherry; depth: 0 = background, 1 = foreground.
private data class WelcomeDigitSpec(val value: String, val dx: Float, val dy: Float, val depth: Float, val phase: Float)
private val WelcomeDigitSpecs = listOf(
    WelcomeDigitSpec("27", -0.40f, -0.30f, 0.15f, 0.0f), WelcomeDigitSpec("12", -0.16f, -0.36f, 0.45f, 1.1f),
    WelcomeDigitSpec("53", 0.13f, -0.33f, 0.80f, 2.3f), WelcomeDigitSpec("84", 0.39f, -0.26f, 0.20f, 3.4f),
    WelcomeDigitSpec("42", -0.44f, -0.08f, 0.55f, 4.6f), WelcomeDigitSpec("19", 0.44f, -0.05f, 0.35f, 5.5f),
    WelcomeDigitSpec("61", -0.34f, 0.16f, 0.85f, 0.7f), WelcomeDigitSpec("35", 0.35f, 0.20f, 0.60f, 1.9f),
    WelcomeDigitSpec("96", -0.14f, 0.34f, 0.25f, 3.1f), WelcomeDigitSpec("70", 0.17f, 0.37f, 0.70f, 4.2f),
    WelcomeDigitSpec("00", -0.42f, 0.40f, 0.40f, 5.0f), WelcomeDigitSpec("07", 0.43f, 0.42f, 0.10f, 2.8f)
)

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

// Lightweight, mute-safe native UI click (no bundled audio assets required).
private fun playWelcomeClick(view: android.view.View) {
    val audio = view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL) view.playSoundEffect(SoundEffectConstants.CLICK)
}

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val l = LocalLanguage.current
    val view = LocalView.current
    WelcomeDarkBars()

    // --- State machine: entrance -> idle loops -> outro (cherry zoom) -> navigate ---
    var transitioningIn by rememberSaveable { mutableStateOf(false) }
    var transitioningOut by remember { mutableStateOf(false) }
    val entranceTime = remember { Animatable(0f) }
    val convergeAll = remember { Animatable(0f) }
    val outroScale = remember { Animatable(1f) }
    val outroGlow = remember { Animatable(0f) }
    val outroContentAlpha = remember { Animatable(1f) }
    val ripple = remember { Animatable(0f) }
    val attract = remember { Animatable(0f) }
    val absorbGlow = remember { Animatable(0f) }
    var idlePhase by remember { mutableFloatStateOf(0f) }
    // Unbounded frame clock (seconds) driving all continuous Canvas motion.
    val clock = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        if (!transitioningIn) {
            transitioningIn = true
            entranceTime.animateTo(2f, tween(2000, easing = LinearEasing))
        } else entranceTime.snapTo(2f)
    }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (isActive) withFrameNanos { clock.floatValue = (it - start) / 1_000_000_000f }
    }
    // Absorption cycles: a rotating group of digits periodically travels to the cherry.
    LaunchedEffect(Unit) {
        while (isActive) {
            if (!transitioningOut) {
                idlePhase += 1f
                // Glow builds through the approach and peaks exactly when digits
                // reach the core (o ~ 1 near 1950ms), then cools to 0 at 2400ms.
                absorbGlow.animateTo(1f, keyframes {
                    durationMillis = 2400
                    0f at 0 using LinearEasing
                    0.55f at 1500 using FastOutSlowInEasing
                    1f at 1950 using WelcomeEasingOut
                    0f at 2400 using LinearEasing
                })
            } else delay(250)
            attract.snapTo(0f)
            attract.animateTo(1f, keyframes {
                durationMillis = 2400
                0f at 0 using LinearEasing
                0.42f at 1050 using FastOutSlowInEasing
                0.78f at 1650 using WelcomeEasingIn
                1f at 2400 using LinearEasing
            })
            delay(700)
        }
    }
    // Outro: ripple -> digits converge -> cherry swells -> navigate (persistence happens in nav).
    LaunchedEffect(transitioningOut) {
        if (!transitioningOut) return@LaunchedEffect
        launch { ripple.animateTo(1f, tween(700, easing = WelcomeEasingOut)) }
        launch { convergeAll.animateTo(1f, tween(900, easing = WelcomeEasingIn)) }
        launch { outroGlow.animateTo(1f, tween(650, delayMillis = 120, easing = WelcomeEasingOut)) }
        launch { outroContentAlpha.animateTo(0f, tween(450, delayMillis = 250, easing = LinearEasing)) }
        outroScale.animateTo(2.6f, tween(950, delayMillis = 150, easing = WelcomeEasingIn))
        onContinue()
    }

    // --- Touch parallax (lightweight drag-driven; no sensors) ---
    var parallaxX by remember { mutableFloatStateOf(0f) }
    var parallaxY by remember { mutableFloatStateOf(0f) }
    val smoothX by animateFloatAsState(parallaxX, tween(160, easing = LinearEasing), label = "welcomePx")
    val smoothY by animateFloatAsState(parallaxY, tween(160, easing = LinearEasing), label = "welcomePy")

    // --- Cherry hero continuous idle motion ---
    val floatY = rememberInfiniteTransition(label = "cherry-float").animateFloat(
        initialValue = -5f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "floatY")
    val tilt = rememberInfiniteTransition(label = "cherry-tilt").animateFloat(
        initialValue = -2.2f, targetValue = 2.2f,
        animationSpec = infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
    val breathe = rememberInfiniteTransition(label = "cherry-breathe").animateFloat(
        initialValue = 0.975f, targetValue = 1.025f,
        animationSpec = infiniteRepeatable(tween(4400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")

    // --- Staggered entrance helpers ---
    fun enterFade(startSec: Float, durSec: Float = 0.5f): Float =
        ((entranceTime.value - startSec) / durSec).coerceIn(0f, 1f)
    val heroIn = remember { Animatable(0f) }
    LaunchedEffect(Unit) { heroIn.animateTo(1f, tween(700, delayMillis = 500, easing = WelcomeEasingOut)) }
    val logoIn = enterFade(1.2f)
    val subtitleIn = enterFade(1.45f)
    val chipsIn = enterFade(1.6f)
    val ctaIn = enterFade(1.7f)

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Color(0xFF14030A))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { parallaxX = 0f; parallaxY = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        parallaxX = (parallaxX + amount.x / 60f).coerceIn(-1f, 1f)
                        parallaxY = (parallaxY + amount.y / 90f).coerceIn(-1f, 1f)
                    },
                    onDragEnd = { parallaxX = 0f; parallaxY = 0f }
                )
            }
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val cherryCenter = Offset(w / 2f, h * 0.40f)
        val voidRadius = min(w, h) * 0.30f

        // Layer 1 — atmospheric background
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = smoothX * 4f; translationY = smoothY * 3f
        }.background(Brush.verticalGradient(
            0f to Color(0xFF2B0712), 0.42f to Color(0xFF3A0A1A), 0.78f to Color(0xFF240611), 1f to Color(0xFF14030A))))
        // Layer 2 — soft blossom depth accents
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = smoothX * 7f; translationY = smoothY * 5f
        }.drawBehind {
            drawCircle(brush = Brush.radialGradient(listOf(Color(0x26FF7FA5), Color.Transparent)), radius = size.width * 0.55f, center = Offset(size.width * 0.08f, size.height * 0.06f))
            drawCircle(brush = Brush.radialGradient(listOf(Color(0x1FFFAEC6), Color.Transparent)), radius = size.width * 0.50f, center = Offset(size.width * 0.95f, size.height * 0.30f))
            drawCircle(brush = Brush.radialGradient(listOf(Color(0x17FF9BB8), Color.Transparent)), radius = size.width * 0.45f, center = Offset(size.width * 0.90f, size.height * 0.86f))
        })

        // Layers 3 + 4 — animated digits, bokeh particles, absorption orbits
        WelcomeDigitsLayer(
            entrance = entranceTime.value,
            parallax = Offset(smoothX, smoothY),
            cherryCenter = cherryCenter,
            voidRadius = voidRadius,
            attract = attract.value,
            absorbGlow = absorbGlow.value,
            idlePhase = idlePhase,
            converge = convergeAll.value,
            clock = clock.floatValue,
            outro = transitioningOut
        )

        // Layer 5 — living Cherry hero (entrance + float + tilt + breathe + absorption response)
        val heroGlow = maxOf(absorbGlow.value * 0.9f, outroGlow.value)
        val heroAlpha = if (transitioningOut) 1f else enterFade(0.5f, 0.45f)
        val heroEntranceScale = 0.72f + 0.28f * heroIn.value
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset { IntOffset(0, (h * 0.40f - 76.dp.toPx()).roundToInt()) }
                .graphicsLayer {
                    scaleX = heroEntranceScale; scaleY = heroEntranceScale
                    translationY = floatY.value + smoothY * 9f
                    translationX = smoothX * 11f
                    rotationZ = tilt.value
                    alpha = heroAlpha
                }
        ) {
            WelcomeCherryHero(
                modifier = Modifier.size(152.dp).scale(breathe.value * (1f + 0.05f * heroGlow) * outroScale.value),
                glow = heroGlow,
                shadowRise = heroIn.value
            )
        }

        // Layer 6 — logo / title / subtitle / chips (staggered)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer { translationY = smoothY * 3f }) {
            WelcomeLogoBlock(
                alpha = if (transitioningOut) outroContentAlpha.value else min(logoIn, 1f),
                subtitleAlpha = if (transitioningOut) outroContentAlpha.value else min(subtitleIn, 1f),
                chipsAlpha = if (transitioningOut) outroContentAlpha.value else min(chipsIn, 1f),
                title = "Cherry 2D",
                subtitle = l.translate("မြန်မာ 2D ဒိုင်များအတွက် ပရော်ဖက်ရှင်နယ် စာရင်းစနစ်"),
                chips = listOf(
                    Icons.Default.GridView to l.translate("အကွက်စာရင်း"),
                    Icons.Default.Lock to l.translate("ပိတ်ဂဏန်း"),
                    Icons.Default.Assessment to l.translate("အစီရင်ခံစာ")
                )
            )
        }

        // Layer 7 — CTA with press feedback + transition trigger
        val ctaInteraction = remember { MutableInteractionSource() }
        val pressed by ctaInteraction.collectIsPressedAsState()
        val ctaScale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(120), label = "welcomeCta")
        val ctaAlpha = if (transitioningOut) outroContentAlpha.value else ctaIn
        if (ctaAlpha > 0.01f) {
            Button(
                onClick = {
                    if (!transitioningOut) {
                        playWelcomeClick(view)
                        transitioningOut = true
                    }
                },
                interactionSource = ctaInteraction,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp)
                    .navigationBarsPadding().padding(bottom = 22.dp).height(58.dp)
                    .scale(ctaScale).alpha(ctaAlpha.coerceIn(0f, 1f)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2D55), contentColor = Color.White),
                shape = RoundedCornerShape(28.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp, pressedElevation = 4.dp)
            ) {
                Text(l.translate("စတင်အသုံးပြုမည်"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }

        // Transition light ripple expanding from the CTA area
        if (transitioningOut) {
            Canvas(Modifier.fillMaxSize()) {
                val t = ripple.value
                if (t <= 0f || t >= 1f) return@Canvas
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color(0x00FFFFFF), 0.86f to Color(0x00FFFFFF), 0.94f to Color(0x59FFFFFF), 1f to Color(0x00FFFFFF)
                    ),
                    radius = t * size.height * 0.85f,
                    center = Offset(size.width / 2f, size.height - 110.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun WelcomeCherryHero(modifier: Modifier = Modifier, glow: Float, shadowRise: Float) {
    Box(modifier, contentAlignment = Alignment.Center) {
        // Absorption glow response
        if (glow > 0.01f) {
            Box(Modifier.size((152 + 90 * glow).dp).graphicsLayer { alpha = 0.55f * glow }
                .background(Brush.radialGradient(listOf(Color(0x80FF2D55), Color(0x33FF5C8A), Color.Transparent)), CircleShape))
        }
        // Soft suspended shadow
        Box(Modifier.size(150.dp).offset(y = 10.dp).graphicsLayer { alpha = 0.5f * shadowRise }
            .background(Brush.radialGradient(listOf(Color(0x66000000), Color.Transparent)), CircleShape))
        // Glossy cherry sphere
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.34f)),
            modifier = Modifier.size(152.dp).shadow(18.dp, CircleShape, ambientColor = Color(0xFFFF2D55), spotColor = Color(0xFF7A0F2E))
        ) {
            Box(Modifier.fillMaxSize().clip(CircleShape)
                .background(Brush.radialGradient(
                    0.0f to Color(0xFFFF7C9C), 0.45f to Color(0xFFE11D48), 0.8f to Color(0xFF8D123A), 1f to Color(0xFF4A0A20)))) {
                Image(
                    painterResource(R.drawable.ic_cherry_mark), "Cherry 2D",
                    Modifier.size(94.dp).align(Alignment.Center).graphicsLayer { alpha = 0.96f },
                    contentScale = ContentScale.Fit
                )
                // Specular highlight + inner rim shade
                Box(Modifier.matchParentSize().drawBehind {
                    drawArc(
                        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.05f))),
                        startAngle = -160f, sweepAngle = 70f, useCenter = false,
                        style = Stroke(width = size.minDimension * 0.10f, cap = StrokeCap.Round),
                        topLeft = Offset(size.width * 0.14f, size.height * 0.14f),
                        size = Size(size.width * 0.72f, size.height * 0.72f)
                    )
                    drawCircle(brush = Brush.radialGradient(0.68f to Color.Transparent, 1f to Color(0x59000000)))
                })
            }
        }
        // Absorption pulse ring
        if (glow > 0.02f) {
            Box(Modifier.size(152.dp).graphicsLayer { alpha = glow }.drawBehind {
                drawCircle(color = Color.White.copy(alpha = 0.55f * glow), radius = size.minDimension / 2f, style = Stroke(width = 2.dp.toPx() + 3.dp.toPx() * glow))
            })
        }
    }
}

@Composable
private fun WelcomeLogoBlock(
    alpha: Float,
    subtitleAlpha: Float,
    chipsAlpha: Float,
    title: String,
    subtitle: String,
    chips: List<Pair<androidx.compose.ui.graphics.vector.ImageVector, String>>
) {
    if (alpha <= 0.01f) return
    Column(
        Modifier.fillMaxWidth().alpha(alpha.coerceIn(0f, 1f)).padding(bottom = 116.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(Color(0xFFFF5C8A), CircleShape))
            Text("ခင်ဗျားကို ကြိုဆိုပါသည်", Modifier.padding(start = 8.dp), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.labelMedium, letterSpacing = 3.sp)
            Box(Modifier.size(5.dp).background(Color(0xFFFF5C8A), CircleShape))
        }
        Text(title, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, lineHeight = 50.sp, modifier = Modifier.padding(top = 8.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.82f * subtitleAlpha.coerceIn(0f, 1f)), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp).padding(horizontal = 6.dp))
        Row(Modifier.padding(top = 20.dp).alpha(chipsAlpha.coerceIn(0f, 1f)), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { (icon, label) -> WelcomeChip(icon, label) }
        }
    }
}

@Composable
private fun WelcomeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.09f), border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = Color(0xFFFFB3C6), modifier = Modifier.size(15.dp))
            Text(label, color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.labelSmall, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}

// ============================================================================
// Layers 3+4: continuously floating digits across depth bands, periodic curved
// absorption into the cherry (accelerate -> orbit -> absorbed), slow bokeh.
// Single Canvas, single draw pass per frame.
// ============================================================================
@Composable
private fun WelcomeDigitsLayer(
    entrance: Float,
    parallax: Offset,
    cherryCenter: Offset,
    voidRadius: Float,
    attract: Float,
    absorbGlow: Float,
    idlePhase: Float,
    converge: Float,
    clock: Float,
    outro: Boolean
) {
    val paint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
    }

    Canvas(Modifier.fillMaxSize().graphicsLayer {
        translationX = parallax.x * 10f; translationY = parallax.y * 7f
    }) {
        val envAlpha = if (outro) 1f else (entrance / 0.35f).coerceIn(0f, 1f)
        if (envAlpha <= 0f) return@Canvas

        // Layer 4 — slow bokeh light particles
        repeat(22) { i ->
            val speed = 0.02f + (i % 5) * 0.012f
            val px = (0.5f + 0.46f * sin(clock * speed * 2f * PI.toFloat() + i * 1.7f)) * size.width
            val py = (0.5f + 0.44f * cos(clock * speed * 1.6f * 2f * PI.toFloat() + i * 2.3f)) * size.height
            val pr = (2f + (i % 4) * 1.6f) * (if (outro) 1f else ((entrance - 0.2f) / 0.4f).coerceIn(0f, 1f))
            if (pr > 0.4f) drawCircle(color = Color.White.copy(alpha = 0.05f + (i % 3) * 0.02f), radius = pr, center = Offset(px, py))
        }

        // Layer 3 — floating digits with depth + curved absorption
        WelcomeDigitSpecs.forEachIndexed { index, spec ->
            val depth = spec.depth
            val digitIn = if (outro) 1f else ((entrance - (0.35f + index * 0.04f)) / 0.45f).coerceIn(0f, 1f)
            if (digitIn <= 0f) return@forEachIndexed

            val basePx = size.width * 0.5f + spec.dx * size.width
            val basePy = size.height * 0.47f + spec.dy * size.height
            // Continuous idle floating drift (slow, elegant, never chaotic)
            val amp = 6f + 10f * depth
            val driftX = sin(clock * (0.06f + 0.02f * depth) * 2f * PI.toFloat() + spec.phase) * amp
            val driftY = cos(clock * (0.05f + 0.016f * depth) * 2f * PI.toFloat() + spec.phase * 1.3f) * amp * 0.8f
            var px = basePx + driftX + parallax.x * (6f + 16f * depth)
            var py = basePy + driftY + parallax.y * (5f + 12f * depth)
            var alpha = digitIn * (0.30f - 0.13f * depth)
            var scale = 0.85f + 0.45f * depth
            var rotation = sin(clock * 0.05f * 2f * PI.toFloat() + spec.phase) * 10f

            if (outro) {
                // Convergence: every visible digit pulls into the cherry.
                val c = converge.coerceIn(0f, 1f)
                val warped = c * c * (3f - 2f * c)
                px += (cherryCenter.x - px) * warped
                py += (cherryCenter.y - py) * warped
                alpha *= (1f - c * c).coerceIn(0f, 1f)
                scale *= 1f + 0.3f * c
            } else if (attract > 0f && index % 3 == idlePhase.toInt() % 3) {
                // Attraction: curved approach (accelerating) -> brief orbit -> absorbed.
                val p = ((attract - index * 0.05f) / 0.85f).coerceIn(0f, 1f)
                val approach = (p / 0.78f).coerceIn(0f, 1f)
                val warped = approach * approach // acceleration near the cherry
                val start = Offset(px, py)
                val side = if ((index + idlePhase.toInt()) % 2 == 0) 1f else -1f
                val dir = cherryCenter - start
                val dist = dir.getDistance()
                val n = if (dist > 1f) Offset(-dir.y, dir.x) / dist else Offset(0f, 1f)
                val control = (start + cherryCenter) / 2f + n * dist * 0.28f * side
                val land = cherryCenter + Offset(
                    cos(spec.phase * 1.7f + idlePhase * 0.9f),
                    sin(spec.phase * 1.7f + idlePhase * 0.9f)
                ) * voidRadius * 0.72f
                val q = quadBezier(start, control, land, warped)
                px = q.x; py = q.y
                if (p > 0.78f) {
                    // Orbit/spiral around the cherry, then absorbed (fade at the core).
                    val o = ((p - 0.78f) / 0.22f).coerceIn(0f, 1f)
                    val ang = spec.phase * 1.7f + idlePhase * 0.9f + o * 0.9f * PI.toFloat() * side
                    val r = voidRadius * (0.72f - 0.52f * o)
                    px = cherryCenter.x + cos(ang) * r
                    py = cherryCenter.y + sin(ang) * r
                    alpha *= (1f - o * o).coerceIn(0f, 1f)
                    scale *= 1f - 0.35f * o
                }
                alpha = (alpha * digitIn).coerceIn(0f, 1f)
            }

            if (alpha <= 0.004f) return@forEachIndexed
            paint.textSize = (10.sp.toPx() + 9.sp.toPx() * depth) * scale
            paint.color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
            drawContext.canvas.nativeCanvas.save()
            drawContext.canvas.nativeCanvas.rotate(rotation, px, py)
            drawContext.canvas.nativeCanvas.drawText(spec.value, px, py, paint)
            drawContext.canvas.nativeCanvas.restore()
        }

        // Cherry absorption halo feedback drawn on the canvas
        if (absorbGlow > 0.02f && !outro) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0x00FF5C8A), Color(0x33FF5C8A), Color.Transparent)),
                radius = voidRadius * (1.1f + 0.25f * absorbGlow),
                center = cherryCenter
            )
        }
    }
}

private fun quadBezier(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val mt = 1f - t
    return a * (mt * mt) + c * (2f * mt * t) + b * (t * t)
}
