package com.myanmar.ledger2d.feature.main

import android.content.Context
import android.media.AudioManager
import android.view.SoundEffectConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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
// Cherry 2D — Welcome (poster-faithful cinematic brand experience)
// A single ~5.2s entrance story: darkness -> blossoms + light -> floating 2D
// glass digit bubbles -> digits stream into the living cherry along light
// trails -> metallic "Cherry 2D" logo light-sweep reveal -> LEDGER -> four
// feature tiles -> glossy CTA. Pressing Start replays the absorption, zooms
// the cherry into the camera and covers the screen in light before Home.
// Pure Compose + Canvas. No 3D engine, no bundled audio, no business-logic
// changes; the public API stays WelcomeScreen(onContinue: () -> Unit).
// ============================================================================

private val WelcomeEasingOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val WelcomeEasingIn = CubicBezierEasing(0.4f, 0f, 0.8f, 0.2f)
private val WelcomeEasingInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

// easeOutBack — springy overshoot used by the hero rise.
private fun welcomeBackOut(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

// 80% -> 105% -> 100% pop for tiles and digit bubbles.
private fun welcomePopScale(t: Float): Float {
    val p1 = (t / 0.55f).coerceIn(0f, 1f)
    val p2 = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
    val outCubic = 1f - (1f - p1).let { v -> v * v * v }
    val inOutSine = 0.5f - 0.5f * cos(PI.toFloat() * p2)
    return 0.8f + 0.25f * outCubic - 0.05f * inOutSine
}

private fun welcomeEaseInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { v -> v * v * v } / 2f

private fun welcomeQuadBezier(a: Offset, c: Offset, b: Offset, t: Float): Offset {
    val mt = 1f - t
    return a * (mt * mt) + c * (2f * mt * t) + b * (t * t)
}

private fun welcomeRotateVec(v: Offset, degrees: Float): Offset {
    val r = degrees * PI.toFloat() / 180f
    val c = cos(r)
    val s = sin(r)
    return Offset(v.x * c - v.y * s, v.x * s + v.y * c)
}

// Fractional placement around the cherry, echoing the poster: bubbles crowd
// the upper field, a few sit low beside the logo. depth 0 = far, 1 = near.
private data class WelcomeDigitSpec(val value: String, val dx: Float, val dy: Float, val depth: Float, val phase: Float)
private val WelcomeDigitSpecs = listOf(
    WelcomeDigitSpec("53", -0.36f, -0.40f, 0.35f, 0.0f),
    WelcomeDigitSpec("12", -0.13f, -0.44f, 0.55f, 1.1f),
    WelcomeDigitSpec("07", 0.14f, -0.41f, 0.45f, 2.3f),
    WelcomeDigitSpec("96", 0.40f, -0.33f, 0.25f, 3.4f),
    WelcomeDigitSpec("27", -0.40f, -0.16f, 0.50f, 4.6f),
    WelcomeDigitSpec("35", 0.40f, -0.10f, 0.40f, 5.5f),
    WelcomeDigitSpec("19", 0.44f, 0.08f, 0.30f, 0.7f),
    WelcomeDigitSpec("61", -0.43f, 0.05f, 0.60f, 1.9f),
    WelcomeDigitSpec("42", 0.36f, 0.22f, 0.55f, 3.1f),
    WelcomeDigitSpec("84", -0.36f, 0.24f, 0.45f, 4.2f),
    WelcomeDigitSpec("70", 0.28f, 0.30f, 0.35f, 5.0f)
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

// Entrance timeline (seconds): the spec's cinematic sequence.
// 0.0 darkness lifts | 0.4-1.5 bubbles pop in | 1.0-2.5 absorption cycles
// 2.0+ cherry comes alive | 3.0 logo sweep | 3.5 LEDGER | 4.0 tiles
// 4.4 CTA | 4.7 footer. Outro: converge -> zoom -> light burst -> Home.
private const val WELCOME_ENTRANCE_SEC = 5.2f

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val l = LocalLanguage.current
    val view = LocalView.current
    WelcomeDarkBars()

    // --- State machine: entrance -> idle loops -> outro (burst) -> navigate ---
    var transitioningIn by rememberSaveable { mutableStateOf(false) }
    var transitioningOut by remember { mutableStateOf(false) }
    val entranceTime = remember { Animatable(0f) }
    val convergeAll = remember { Animatable(0f) }
    val outroZoom = remember { Animatable(1f) }
    val outroGlow = remember { Animatable(0f) }
    val outroContentAlpha = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    val btnFlash = remember { Animatable(0f) }
    val attract = remember { Animatable(0f) }
    val absorbGlow = remember { Animatable(0f) }
    var idlePhase by remember { mutableFloatStateOf(0f) }
    // Unbounded frame clock (seconds) driving all continuous Canvas motion.
    val clock = remember { mutableFloatStateOf(0f) }
    // Read during composition, BEFORE any effect mutates it, so a re-entry into
    // this screen skips the entrance while a first launch still plays it.
    val resumedEntrance = transitioningIn

    LaunchedEffect(Unit) {
        if (!transitioningIn) {
            transitioningIn = true
            entranceTime.animateTo(WELCOME_ENTRANCE_SEC, tween((WELCOME_ENTRANCE_SEC * 1000).toInt(), easing = LinearEasing))
        } else entranceTime.snapTo(WELCOME_ENTRANCE_SEC)
    }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (isActive) withFrameNanos { clock.floatValue = (it - start) / 1_000_000_000f }
    }
    // Absorption cycles: a rotating group of digits periodically travels to the
    // cherry along curved light trails, orbits, and is absorbed with a flash.
    LaunchedEffect(Unit) {
        if (!resumedEntrance) delay(1000)
        while (isActive) {
            if (!transitioningOut) {
                idlePhase += 1f
                // Glow builds through the approach and peaks when digits reach
                // the core, then cools to 0 at the end of the 2600ms cycle.
                absorbGlow.animateTo(1f, keyframes {
                    durationMillis = 2600
                    0f at 0 using LinearEasing
                    0.55f at 1600 using FastOutSlowInEasing
                    1f at 2000 using WelcomeEasingOut
                    0f at 2600 using LinearEasing
                })
            } else delay(250)
            attract.snapTo(0f)
            attract.animateTo(1f, keyframes {
                durationMillis = 2600
                0f at 0 using LinearEasing
                0.40f at 1150 using FastOutSlowInEasing
                0.80f at 1800 using WelcomeEasingIn
                1f at 2600 using LinearEasing
            })
            delay(600)
        }
    }
    // Outro: button flash -> digits converge fast -> cherry zooms into the
    // camera -> pink/white light burst covers the screen -> navigate (the
    // persistence + navigation happen in the nav graph).
    LaunchedEffect(transitioningOut) {
        if (!transitioningOut) return@LaunchedEffect
        attract.snapTo(0f)
        absorbGlow.snapTo(0f)
        launch { btnFlash.animateTo(1f, tween(260, easing = WelcomeEasingOut)) }
        launch { convergeAll.animateTo(1f, tween(800, easing = WelcomeEasingInOut)) }
        launch { outroGlow.animateTo(1f, tween(600, delayMillis = 100, easing = WelcomeEasingOut)) }
        launch { outroContentAlpha.animateTo(0f, tween(350, delayMillis = 150, easing = LinearEasing)) }
        launch {
            burst.animateTo(1f, keyframes {
                durationMillis = 950
                0f at 0 using LinearEasing
                0.55f at 420 using WelcomeEasingOut
                1f at 900 using WelcomeEasingIn
            })
        }
        outroZoom.animateTo(1.55f, keyframes {
            durationMillis = 900
            1.15f at 300 using WelcomeEasingOut
            1.30f at 600 using FastOutSlowInEasing
            1.55f at 900 using WelcomeEasingIn
        })
        onContinue()
    }

    // --- Touch parallax + interactive 3D camera tilt (drag-driven) ---
    var parallaxX by remember { mutableFloatStateOf(0f) }
    var parallaxY by remember { mutableFloatStateOf(0f) }
    val smoothX by animateFloatAsState(parallaxX, tween(180, easing = LinearEasing), label = "welcomePx")
    val smoothY by animateFloatAsState(parallaxY, tween(180, easing = LinearEasing), label = "welcomePy")

    // --- Ambient loops (amplitudes are scaled by "alive" once the story starts) ---
    val floatY = rememberInfiniteTransition(label = "cherry-float").animateFloat(
        initialValue = -4.5f, targetValue = 4.5f,
        animationSpec = infiniteRepeatable(tween(3800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "floatY")
    val breathe = rememberInfiniteTransition(label = "cherry-breathe").animateFloat(
        initialValue = 0.978f, targetValue = 1.022f,
        animationSpec = infiniteRepeatable(tween(4600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    val sheen = rememberInfiniteTransition(label = "cherry-sheen").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4600, easing = LinearEasing)), label = "sheen")
    val ctaSweep = rememberInfiniteTransition(label = "cta-sweep").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing)), label = "ctaSweep")

    // --- Entrance helpers (entranceTime is in seconds 0..5.2) ---
    fun enterFade(startSec: Float, durSec: Float = 0.5f): Float =
        ((entranceTime.value - startSec) / durSec).coerceIn(0f, 1f)

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
        val bgIn = enterFade(0.05f, 0.85f)
        val glowIn = enterFade(0.15f, 0.75f)
        val heroIn = welcomeBackOut(enterFade(0.45f, 0.9f).coerceIn(0f, 1f))
        val alive = enterFade(2.0f, 0.9f)

        // Layer 1 — burgundy depth backdrop
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = smoothX * 4f; translationY = smoothY * 3f
            alpha = bgIn
        }.background(Brush.verticalGradient(
            0f to Color(0xFF1A050D), 0.38f to Color(0xFF2E0816), 0.72f to Color(0xFF3D0B1E), 1f to Color(0xFF1C060F))))

        // Layer 2 — blossom branches (slow sway) + soft blurred corner blossoms
        Box(Modifier.fillMaxSize().graphicsLayer {
            translationX = smoothX * 8f; translationY = smoothY * 5f
            alpha = bgIn
        }.drawBehind {
            drawWelcomeBlossomBranches(clock.floatValue, size)
            // Blurred foreground flower silhouettes, bottom corners (poster bokeh)
            drawWelcomeBlossom(Offset(size.width * 0.06f, size.height * 0.86f), 52.dp.toPx(), 0.06f)
            drawWelcomeBlossom(Offset(size.width * 0.94f, size.height * 0.80f), 44.dp.toPx(), 0.05f)
            drawWelcomeBlossom(Offset(size.width * 0.88f, size.height * 0.94f), 60.dp.toPx(), 0.06f)
        })

        // Layer 3 — center pink light rising from darkness
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset { IntOffset(0, (cherryCenter.y - 170.dp.toPx()).roundToInt()) }
                .size(340.dp)
                .graphicsLayer { alpha = glowIn * (0.75f + 0.25f * sin(clock.floatValue * 0.8f)) }
                .drawBehind {
                    drawCircle(brush = Brush.radialGradient(listOf(Color(0x59FF4D79), Color(0x24FF7BA3), Color.Transparent)))
                }
        )

        // Layer 4 — environment: twinkle, bokeh, drifting petals, gold swirls
        WelcomeEnvironmentLayer(
            clock = clock.floatValue,
            parallax = Offset(smoothX, smoothY),
            envAlpha = enterFade(0.3f, 0.8f)
        )

        // Layer 5 — glass digit bubbles with curved absorption into the cherry
        WelcomeDigitBubblesLayer(
            entranceSec = entranceTime.value,
            parallax = Offset(smoothX, smoothY),
            cherryCenter = cherryCenter,
            attract = attract.value,
            absorbGlow = absorbGlow.value,
            idlePhase = idlePhase,
            converge = convergeAll.value,
            clock = clock.floatValue,
            outro = transitioningOut
        )

        // Layer 6 — the living Cherry hero with a real perspective camera.
        val heroGlow = maxOf(absorbGlow.value * 0.9f, outroGlow.value)
        val heroAlpha = if (transitioningOut) 1f else heroIn.coerceIn(0f, 1f)
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset { IntOffset(0, (cherryCenter.y - 108.dp.toPx()).roundToInt()) }
                .size(216.dp, 216.dp)
                .graphicsLayer {
                    cameraDistance = 8f * density
                    transformOrigin = TransformOrigin(0.5f, 0.55f)
                    val zoom = outroZoom.value
                    scaleX = heroIn * breathe.value * zoom
                    scaleY = heroIn * breathe.value * zoom
                    translationY = floatY.value * alive + smoothY * 9f
                    translationX = smoothX * 11f
                    rotationZ = sin(clock.floatValue * 0.42f) * 2.4f * alive + smoothX * 2f
                    rotationY = sin(clock.floatValue * 0.31f) * 2.0f * alive + smoothX * 7f
                    rotationX = sin(clock.floatValue * 0.26f) * 1.6f * alive - smoothY * 6f
                    alpha = heroAlpha
                }
        ) {
            WelcomeCherryHero(
                clock = clock.floatValue,
                glow = heroGlow,
                sheen = sheen.value,
                alive = alive
            )
        }

        // Layer 7 — content: logo, tiles, CTA, footer
        val contentAlpha = outroContentAlpha.value
        if (contentAlpha > 0.01f) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp)
                    .graphicsLayer {
                        alpha = contentAlpha.coerceIn(0f, 1f)
                        translationY = smoothY * 2f
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WelcomeLogo(
                    reveal = enterFade(3.0f, 0.75f),
                    flourish = enterFade(3.3f, 0.7f),
                    ledgerIn = enterFade(3.55f, 0.5f)
                )
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
                    tiles.forEachIndexed { i, (icon, line1, line2) ->
                        WelcomeFeatureTile(
                            icon = icon,
                            line1 = line1,
                            line2 = line2,
                            appear = enterFade(4.0f + i * 0.1f, 0.55f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                WelcomeCtaButton(
                    label = l.translate("စတင်အသုံးပြုမည်"),
                    visible = if (transitioningOut) outroContentAlpha.value else enterFade(4.4f, 0.55f),
                    enabled = !transitioningOut,
                    clock = clock.floatValue,
                    sweep = ctaSweep.value,
                    flash = btnFlash.value,
                    onPress = {
                        playWelcomeClick(view)
                        if (!transitioningOut) transitioningOut = true
                    }
                )
                Spacer(Modifier.height(12.dp))
                WelcomeFooter(
                    appear = if (transitioningOut) outroContentAlpha.value else enterFade(4.7f, 0.5f),
                    clock = clock.floatValue
                )
            }
        }

        // Layer 8 — darkness veil lifting at the very start
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = (1f - enterFade(0.05f, 0.9f)).coerceIn(0f, 1f)
            }.background(Color(0xFF0B0206))
        )

        // Layer 9 — outro light burst expanding from the cherry core
        if (transitioningOut && burst.value > 0.001f) {
            Canvas(Modifier.fillMaxSize()) {
                val t = burst.value
                val cover = (t / 0.85f).coerceIn(0f, 1f)
                val radius = kotlin.math.sqrt(size.width * size.width + size.height * size.height) * 0.78f
                // Soft pink light front, then a white core that swallows the screen.
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.95f * cover),
                        0.55f to Color(0xFFFFC9D9).copy(alpha = 0.85f * cover),
                        0.85f to Color(0xFFFF6E9C).copy(alpha = 0.45f * cover),
                        1f to Color.Transparent
                    ),
                    radius = radius * (0.25f + 0.85f * cover),
                    center = cherryCenter
                )
                drawCircle(
                    color = Color.White.copy(alpha = (cover * cover).coerceIn(0f, 1f)),
                    radius = radius * cover,
                    center = cherryCenter
                )
            }
        }
    }
}

// ============================================================================
// Metallic "Cherry 2D" logo: gold + pink gradient lettering, blossom accent,
// a light sweep gliding across the metal, an underline flourish that draws
// itself, and the letterspaced LEDGER line fading in behind it.
// ============================================================================
@Composable
private fun WelcomeLogo(reveal: Float, flourish: Float, ledgerIn: Float) {
    if (reveal <= 0.01f && flourish <= 0.01f) return
    val density = LocalDensity.current
    val shY = with(density) { 3.sp.toPx() }
    val shBlur = with(density) { 8.sp.toPx() }
    val sweep = rememberInfiniteTransition(label = "logo-sweep").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "logoSweep")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            Canvas(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 84.dp, y = (-9).dp)
                    .size(26.dp)
                    .graphicsLayer { alpha = reveal.coerceIn(0f, 1f) }
            ) { drawWelcomeBlossom(Offset(size.width / 2f, size.height / 2f), size.width * 0.46f, 0.95f) }
            // Continuous metallic light sweep, clipped to the wordmark row
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = reveal.coerceIn(0f, 1f) }
                    .drawBehind {
                        val x = size.width * (sweep.value * 1.7f - 0.35f)
                        drawRect(
                            brush = Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.32f),
                                1f to Color.Transparent,
                                start = Offset(x, 0f),
                                end = Offset(x + size.width * 0.30f, size.height)
                            ),
                            blendMode = BlendMode.Screen
                        )
                    }
            )
        }
        // Underline flourish drawing itself left -> right
        Canvas(
            Modifier
                .padding(top = 2.dp)
                .size(210.dp, 14.dp)
                .graphicsLayer { alpha = flourish.coerceIn(0f, 1f) }
        ) {
            val path = Path().apply {
                moveTo(8.dp.toPx(), 5.dp.toPx())
                quadraticBezierTo(size.width / 2f, size.height * 1.15f, size.width - 8.dp.toPx(), 3.dp.toPx())
            }
            clipRect(left = 0f, top = 0f, right = size.width * flourish.coerceIn(0f, 1f), bottom = size.height) {
                drawPath(
                    path,
                    brush = Brush.horizontalGradient(listOf(Color(0x00FF87AB), Color(0xFFFFE3EC), Color(0xFFFF87AB))),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Text(
            "LEDGER",
            color = Color.White.copy(alpha = 0.88f * ledgerIn.coerceIn(0f, 1f)),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 12.sp,
            modifier = Modifier.padding(top = 4.dp).graphicsLayer {
                translationY = (1f - ledgerIn.coerceIn(0f, 1f)) * 6.dp.toPx()
            }
        )
    }
}

// ============================================================================
// Four feature tiles — glass rounded squares, icon + two Burmese label lines,
// staggered ~0.1s apart with a back-out spring scale 80% -> 105% -> 100%.
// ============================================================================
@Composable
private fun WelcomeFeatureTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    line1: String,
    line2: String,
    appear: Float,
    modifier: Modifier = Modifier
) {
    if (appear <= 0.01f) {
        Box(modifier)
        return
    }
    val popAlpha = (appear * 1.8f).coerceIn(0f, 1f)
    val popScale = welcomePopScale(appear.coerceIn(0f, 1f))
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .height(96.dp)
            .graphicsLayer {
                alpha = popAlpha
                scaleX = popScale
                scaleY = popScale
            }
            .clip(shape)
            .background(Brush.verticalGradient(0f to Color(0x1AFFFFFF), 1f to Color(0x08FFFFFF)))
            .border(1.dp, Brush.verticalGradient(0f to Color.White.copy(alpha = 0.22f), 1f to Color.White.copy(alpha = 0.06f)), shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color(0xFFFFB3C6), modifier = Modifier.size(22.dp))
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
// Glossy pink CTA pill: cherry icon, label, deep-red circular arrow chip with
// a forward nudge, outer glow pulse, travelling highlight sweep, press scale.
// ============================================================================
@Composable
private fun WelcomeCtaButton(
    label: String,
    visible: Float,
    enabled: Boolean,
    clock: Float,
    sweep: Float,
    flash: Float,
    onPress: () -> Unit
) {
    if (visible <= 0.01f) return
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "welcomeCtaPress"
    )
    val ctaShape = RoundedCornerShape(31.dp)
    val visibleCoerced = visible.coerceIn(0f, 1f)
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
                .graphicsLayer { alpha = visibleCoerced }
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(Color(0x66FF4D79), Color(0x40FF87AB))),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
                .graphicsLayer {
                    val pulse = 0.55f + 0.45f * sin(clock * 1.5f)
                    alpha = visibleCoerced * 0.45f * pulse + 0.5f * flash
                    scaleX = 1.05f + 0.02f * pulse + 0.08f * flash
                    scaleY = 1.25f + 0.08f * pulse + 0.2f * flash
                }
        )
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleX = pressScale; scaleY = pressScale
                    alpha = visibleCoerced
                    translationY = (1f - visibleCoerced) * 16.dp.toPx()
                }
                .clip(ctaShape)
                .background(Brush.horizontalGradient(0f to Color(0xFFFF4D79), 0.55f to Color(0xFFEF2860), 1f to Color(0xFFC40E4A)))
                .border(1.dp, Brush.horizontalGradient(listOf(Color(0x80FFD9E4), Color(0x40FF87AB), Color(0x80FFD9E4))), ctaShape)
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
                        val x = size.width * (sweep * 1.5f - 0.25f)
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
            // Press flash
            if (flash > 0.01f) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.55f * flash.coerceIn(0f, 1f)), ctaShape)
                )
            }
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
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(label, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(10.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x998F0E2E))
                        .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.ArrowForward, null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(17.dp)
                            .graphicsLayer { translationX = (sin(clock * 2.4f) * 1.5f).dp.toPx() }
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
private fun WelcomeFooter(appear: Float, clock: Float) {
    val a = appear.coerceIn(0f, 1f)
    if (a <= 0.01f) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(14.dp).graphicsLayer { alpha = a }) {
            drawWelcomeBlossom(Offset(size.width / 2f, size.height / 2f), size.width * 0.42f, 0.9f)
        }
        Text(
            "MYANMAR 2D · SIMPLE · FAST · RELIABLE",
            color = Color.White.copy(alpha = 0.52f * a),
            fontSize = 10.sp,
            letterSpacing = 2.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.graphicsLayer {
                alpha = a * (0.85f + 0.15f * sin(clock * 0.9f))
            }
        )
    }
}

// ============================================================================
// The living cherry: two glossy spheres with offset radial lighting, moving
// specular reflection band, stems + swaying leaf, gold spark swirls orbiting
// on tilted ellipses, absorption glow halo, pulse ring and a grounding shadow.
// ============================================================================
@Composable
private fun WelcomeCherryHero(clock: Float, glow: Float, sheen: Float, alive: Float) {
    Canvas(Modifier.size(216.dp)) {
        val cx = size.width / 2f
        val r = 46.dp.toPx()
        val cyl = size.height * 0.60f
        val cyr = size.height * 0.52f
        val cxl = cx - 27.dp.toPx()
        val cxr = cx + 24.dp.toPx()
        val junction = Offset(cx + 4.dp.toPx(), size.height * 0.16f)

        // Grounding shadow — breathes against the float
        val lift = (sin(clock * 0.9f) + 1f) / 2f
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0x78000000), Color.Transparent)),
            radius = 58.dp.toPx() * (1f - 0.06f * lift),
            center = Offset(cx, size.height - 20.dp.toPx() + lift * 4.dp.toPx())
        )

        // Absorption glow halo
        if (glow > 0.01f) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0x80FF2D55), Color(0x33FF7BA3), Color.Transparent)),
                radius = r * (2.2f + 0.9f * glow),
                center = Offset(cx, (cyl + cyr) / 2f)
            )
        }

        // Stems: two curves meeting at the junction knot
        val stemBrush = Brush.verticalGradient(listOf(Color(0xFF8A5A3C), Color(0xFF5C3A24)), startY = junction.y, endY = cyl)
        val stemStyle = Stroke(width = 4.2.dp.toPx(), cap = StrokeCap.Round)
        drawPath(
            Path().apply {
                moveTo(cxl, cyl - r * 0.88f)
                quadraticBezierTo(cxl - 10.dp.toPx(), cyl - r * 1.5f, junction.x - 3.dp.toPx(), junction.y)
            },
            brush = stemBrush, style = stemStyle
        )
        drawPath(
            Path().apply {
                moveTo(cxr, cyr - r * 0.88f)
                quadraticBezierTo(cxr + 12.dp.toPx(), cyr - r * 1.55f, junction.x + 3.dp.toPx(), junction.y)
            },
            brush = stemBrush, style = stemStyle
        )
        drawCircle(color = Color(0xFF9C6B46), radius = 3.2.dp.toPx(), center = junction)

        // Leaf — sways slowly around the junction
        val leafSway = sin(clock * 0.5f) * 5f * alive
        rotate(-24f + leafSway, junction) {
            drawOval(
                brush = Brush.linearGradient(listOf(Color(0xFF7FBE58), Color(0xFF2F6B2C))),
                topLeft = Offset(junction.x + 2.dp.toPx(), junction.y - 8.dp.toPx()),
                size = Size(32.dp.toPx(), 14.dp.toPx())
            )
            drawLine(
                color = Color(0x992F5B24),
                start = Offset(junction.x + 4.dp.toPx(), junction.y - 1.dp.toPx()),
                end = Offset(junction.x + 30.dp.toPx(), junction.y - 4.dp.toPx()),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Two glossy spheres
        listOf(Offset(cxl, cyl), Offset(cxr, cyr)).forEachIndexed { i, c ->
            // Body with top-left lit radial gradient
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xFFFF9DB4), 0.30f to Color(0xFFF5204E), 0.62f to Color(0xFFB40E37),
                    0.85f to Color(0xFF6B0621), 1f to Color(0xFF430418),
                    center = Offset(c.x - r * 0.35f, c.y - r * 0.45f), radius = r * 1.7f
                ),
                radius = r, center = c
            )
            // Rim shade
            drawCircle(
                brush = Brush.radialGradient(0.60f to Color.Transparent, 1f to Color(0x66280009)),
                radius = r, center = c
            )
            // Stem dimple
            drawOval(
                color = Color(0x88300212),
                topLeft = Offset(c.x - 6.dp.toPx() + (if (i == 0) -2.dp.toPx() else 3.dp.toPx()), c.y - r - 1.dp.toPx()),
                size = Size(11.dp.toPx(), 4.5.dp.toPx())
            )
            // Main specular (rotated glossy blob)
            rotate(-32f, c) {
                drawOval(
                    brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.80f), Color.White.copy(alpha = 0.08f))),
                    topLeft = Offset(c.x - r * 0.55f, c.y - r * 0.80f),
                    size = Size(r * 0.46f, r * 0.64f),
                    alpha = 0.92f
                )
            }
            drawCircle(color = Color.White.copy(alpha = 0.75f), radius = r * 0.06f, center = Offset(c.x - r * 0.46f, c.y - r * 0.50f))
            // Moving glossy reflection band, clipped to the sphere
            val band = r * 2.6f * (sheen * 1.6f - 0.3f)
            clipRect(left = c.x - r, top = c.y - r, right = c.x + r, bottom = c.y + r) {
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.20f),
                        1f to Color.Transparent,
                        start = Offset(c.x - r + band, c.y - r),
                        end = Offset(c.x - r + band + r * 0.85f, c.y + r)
                    ),
                    blendMode = BlendMode.Screen
                )
            }
        }

        // Absorption pulse ring
        if (glow > 0.02f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.50f * glow),
                radius = r * 1.18f,
                center = Offset(cx, (cyl + cyr) / 2f),
                style = Stroke(width = 1.6.dp.toPx() + 2.4f * glow.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Gold spark swirls — the poster's light trails, two tilted comet rings
        if (alive > 0.02f) {
            repeat(2) { k ->
                val ringTilt = -16f + 38f * k
                val rx = r * (1.62f + 0.32f * k)
                val ry = rx * 0.40f
                val dir = if (k == 0) 1f else -1f
                val head = clock * (0.55f + 0.16f * k) * 2f * PI.toFloat() * dir + k * 2.4f
                val trail = 40
                val ringCenter = Offset(cx, (cyl + cyr) / 2f)
                for (j in 0 until trail) {
                    val a = head - j * 0.05f * dir
                    val rel = 1f - j.toFloat() / trail
                    val local = welcomeRotateVec(Offset(cos(a) * rx, sin(a) * ry), ringTilt)
                    val p = ringCenter + local
                    val alpha = (rel * rel * 0.55f * alive).coerceIn(0f, 1f)
                    if (alpha > 0.01f) {
                        drawCircle(
                            color = Color(0xFFFFD98A).copy(alpha = alpha),
                            radius = (1.2f + 1.5f * rel).dp.toPx(),
                            center = p
                        )
                    }
                }
                // Bright comet head with a soft gold bloom
                val headLocal = welcomeRotateVec(Offset(cos(head) * rx, sin(head) * ry), ringTilt)
                val hp = ringCenter + headLocal
                drawCircle(color = Color(0x59FFD98A), radius = 5.dp.toPx(), center = hp)
                drawCircle(color = Color.White.copy(alpha = 0.85f * alive), radius = 1.8f.dp.toPx(), center = hp)
            }
        }
    }
}

// ============================================================================
// Environment: twinkle stars, blurred bokeh, drifting petal layers (some
// crossing close to the camera near the cherry) with layered parallax.
// ============================================================================
private data class WelcomePetalSpec(
    val x0: Float, val y0: Float, val sizeDp: Float, val speed: Float,
    val swayFreq: Float, val phase: Float, val spin: Float, val depth: Float, val alpha: Float
)
private val WelcomePetals = listOf(
    WelcomePetalSpec(0.08f, 0.00f, 13f, 0.055f, 0.35f, 0.0f, 22f, 0.35f, 0.55f),
    WelcomePetalSpec(0.22f, 0.15f, 10f, 0.040f, 0.28f, 1.4f, 16f, 0.25f, 0.45f),
    WelcomePetalSpec(0.40f, 0.05f, 15f, 0.065f, 0.40f, 2.6f, 28f, 0.45f, 0.60f),
    WelcomePetalSpec(0.63f, 0.10f, 11f, 0.048f, 0.33f, 3.7f, 19f, 0.30f, 0.50f),
    WelcomePetalSpec(0.86f, 0.00f, 14f, 0.060f, 0.37f, 4.8f, 24f, 0.40f, 0.55f),
    WelcomePetalSpec(0.74f, 0.28f, 9f, 0.036f, 0.26f, 5.9f, 14f, 0.22f, 0.42f),
    WelcomePetalSpec(0.30f, 0.32f, 17f, 0.090f, 0.44f, 1.1f, 34f, 0.75f, 0.75f),
    WelcomePetalSpec(0.68f, 0.35f, 20f, 0.110f, 0.50f, 3.3f, 40f, 0.90f, 0.85f),
    WelcomePetalSpec(0.48f, 0.42f, 24f, 0.140f, 0.55f, 5.2f, 48f, 1.00f, 0.90f)
)

@Composable
private fun WelcomeEnvironmentLayer(
    clock: Float,
    parallax: Offset,
    envAlpha: Float
) {
    Canvas(Modifier.fillMaxSize().graphicsLayer {
        translationX = parallax.x * 10f; translationY = parallax.y * 7f
    }) {
        if (envAlpha <= 0f) return@Canvas

        // Twinkle starfield (far plane)
        repeat(26) { i ->
            val sx = ((i * 73) % 100) / 100f * size.width
            val sy = ((i * 37) % 100) / 100f * size.height * 0.60f
            val tw = 0.5f + 0.5f * sin(clock * (0.7f + (i % 5) * 0.13f) * 2f * PI.toFloat() + i * 2.1f)
            val a = (0.05f + (i % 4) * 0.035f) * tw * envAlpha
            if (a > 0.004f) drawCircle(color = Color.White.copy(alpha = a), radius = 1.0f + (i % 3) * 0.7f, center = Offset(sx, sy))
        }

        // Bokeh glow dots (mid plane, slow orbit)
        repeat(14) { i ->
            val speed = 0.02f + (i % 5) * 0.012f
            val bx = (0.5f + 0.46f * sin(clock * speed * 2f * PI.toFloat() + i * 1.7f)) * size.width
            val by = (0.5f + 0.44f * cos(clock * speed * 1.6f * 2f * PI.toFloat() + i * 2.3f)) * size.height
            val a = 0.10f * envAlpha
            drawCircle(brush = Brush.radialGradient(listOf(Color(0x59FFB3C6), Color.Transparent)), radius = (6f + (i % 4) * 4f), center = Offset(bx, by), alpha = a)
        }

        // Drifting petals (looping fall, layered depth, camera-cross foreground)
        WelcomePetals.forEach { spec ->
            val cycle = 1.2f
            val ny = ((spec.y0 + clock * spec.speed) % cycle + cycle) % cycle - 0.1f
            val px = (spec.x0 + sin(clock * spec.swayFreq + spec.phase) * 0.030f) * size.width + parallax.x * (4f + 18f * spec.depth)
            val py = ny * size.height + parallax.y * (3f + 14f * spec.depth)
            val rot = clock * spec.spin + spec.phase * 60f
            drawPetal(
                center = Offset(px, py),
                sizePx = spec.sizeDp.dp.toPx(),
                rotationDeg = rot,
                alpha = spec.alpha * envAlpha
            )
        }
    }
}

// ============================================================================
// Glass digit bubbles: nativeCanvas text inside code-drawn glass circles with
// specular arcs, pink glow, per-bubble float/pulse, curved absorption with an
// orbit tail + spark flash, and the outro convergence into the cherry.
// ============================================================================
@Composable
private fun WelcomeDigitBubblesLayer(
    entranceSec: Float,
    parallax: Offset,
    cherryCenter: Offset,
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
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
        }
    }
    val glowPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            maskFilter = android.graphics.BlurMaskFilter(12f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }

    Canvas(Modifier.fillMaxSize().graphicsLayer {
        translationX = parallax.x * 12f; translationY = parallax.y * 8f
    }) {
        val envAlpha = if (outro) 1f else ((entranceSec - 0.35f) / 0.6f).coerceIn(0f, 1f)
        if (envAlpha <= 0f) return@Canvas
        val voidRadius = min(size.width, size.height) * 0.26f

        // Absorption halo feedback around the cherry
        if (absorbGlow > 0.02f && !outro) {
            drawCircle(
                brush = Brush.radialGradient(listOf(Color(0x00FF5C8A), Color(0x30FF5C8A), Color.Transparent)),
                radius = voidRadius * (1.15f + 0.25f * absorbGlow),
                center = cherryCenter
            )
        }

        WelcomeDigitSpecs.forEachIndexed { index, spec ->
            val depth = spec.depth
            val popT = ((entranceSec - (0.4f + index * 0.09f)) / 0.55f).coerceIn(0f, 1f)
            if (popT <= 0f && !outro) return@forEachIndexed

            val basePx = size.width * 0.5f + spec.dx * size.width
            val basePy = size.height * 0.42f + spec.dy * size.height
            val amp = 5f + 9f * depth
            val driftX = sin(clock * (0.06f + 0.022f * depth) * 2f * PI.toFloat() + spec.phase) * amp
            val driftY = cos(clock * (0.05f + 0.017f * depth) * 2f * PI.toFloat() + spec.phase * 1.3f) * amp * 0.85f
            val ux = basePx + driftX + parallax.x * (6f + 16f * depth)
            val uy = basePy + driftY + parallax.y * (5f + 12f * depth)
            var alpha = envAlpha * (popT * 2f).coerceIn(0f, 1f) * (0.58f - 0.10f * depth)
            var scale = (0.85f + 0.30f * depth) * welcomePopScale(popT)
            var z = depth
            val rotation = sin(clock * 0.05f * 2f * PI.toFloat() + spec.phase) * 8f
            val pulse = 0.5f + 0.5f * sin(clock * 1.3f + spec.phase * 2f)
            var px = ux
            var py = uy
            var flash = 0f

            if (outro) {
                // Fast convergence into the cherry, swelling toward the camera.
                val c = welcomeEaseInOutCubic(converge.coerceIn(0f, 1f))
                z = depth + (1.15f - depth) * c
                px = ux + (cherryCenter.x - ux) * c
                py = uy + (cherryCenter.y - uy) * c
                alpha *= (1f - c * c).coerceIn(0f, 1f)
                scale *= 1f + 0.35f * c
                val zScale = 1f + z * 0.55f
                val fx = size.width / 2f + (px - size.width / 2f) * zScale
                val fy = size.height / 2f + (py - size.height / 2f) * zScale
                if (alpha > 0.004f) drawGlassBubble(Offset(fx, fy), (11.dp + 12.dp * depth).toPx() * scale * zScale, spec.value, alpha, pulse, rotation, depth, paint, glowPaint)
                return@forEachIndexed
            }

            if (attract > 0f && index % 3 == idlePhase.toInt() % 3) {
                // Curved approach -> brief orbit -> absorbed with a spark flash.
                val p = ((attract - index * 0.045f) / 0.88f).coerceIn(0f, 1f)
                if (p > 0f) {
                    val start = Offset(ux, uy)
                    val side = if ((index + idlePhase.toInt()) % 2 == 0) 1f else -1f
                    val dir = cherryCenter - start
                    val dist = dir.getDistance()
                    val n = if (dist > 1f) Offset(-dir.y, dir.x) / dist else Offset(0f, 1f)
                    val control = (start + cherryCenter) / 2f + n * dist * 0.28f * side
                    val land = cherryCenter + Offset(cos(spec.phase * 1.7f + idlePhase * 0.9f), sin(spec.phase * 1.7f + idlePhase * 0.9f)) * voidRadius * 0.30f
                    val warped = welcomeEaseInOutCubic((p / 0.80f).coerceIn(0f, 1f))
                    val q = welcomeQuadBezier(start, control, land, warped)
                    z = depth + (0.95f - depth) * warped
                    px = q.x
                    py = q.y
                    scale *= 1f - 0.15f * warped
                    if (p > 0.80f) {
                        val o = ((p - 0.80f) / 0.20f).coerceIn(0f, 1f)
                        val ang = spec.phase * 1.7f + idlePhase * 0.9f + o * 1.1f * PI.toFloat() * side
                        val rr = voidRadius * 0.30f * (1f - 0.85f * o)
                        px = cherryCenter.x + cos(ang) * rr
                        py = cherryCenter.y + sin(ang) * rr
                        alpha *= (1f - o * o).coerceIn(0f, 1f)
                        flash = sin(o * PI.toFloat())
                    }
                }
            }

            val zScale = 1f + z * 0.55f
            val fx = size.width / 2f + (px - size.width / 2f) * zScale
            val fy = size.height / 2f + (py - size.height / 2f) * zScale
            val radius = (11.dp + 12.dp * depth).toPx() * scale * zScale * (1f + 0.05f * pulse)
            if (alpha > 0.004f) {
                drawGlassBubble(Offset(fx, fy), radius, spec.value, alpha, pulse, rotation, depth, paint, glowPaint)
            }
            // Absorption spark flash at the landing point
            if (flash > 0.03f) {
                val fl = flash * envAlpha
                drawCircle(color = Color.White.copy(alpha = 0.55f * fl), radius = radius * (0.5f + 0.7f * (1f - flash)), center = Offset(px, py))
                repeat(6) { k ->
                    val ang = k * 60f * PI.toFloat() / 180f + 0.35f
                    drawLine(
                        color = Color(0xFFFFD98A).copy(alpha = 0.75f * fl),
                        start = Offset(px + cos(ang) * 6f, py + sin(ang) * 6f),
                        end = Offset(px + cos(ang) * (10f + 20f * (1f - flash)), py + sin(ang) * (10f + 20f * (1f - flash))),
                        strokeWidth = 1.4f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

// Glass bubble: soft radial glass fill, rim ring, top-left specular arc, and
// the pink glowing 2D digit drawn via nativeCanvas.
private fun DrawScope.drawGlassBubble(
    center: Offset,
    radius: Float,
    label: String,
    alpha: Float,
    pulse: Float,
    rotation: Float,
    depth: Float,
    paint: android.graphics.Paint,
    glowPaint: android.graphics.Paint
) {
    val a = alpha.coerceIn(0f, 1f)
    if (a <= 0.004f || radius <= 1f) return
    // Glass fill (light from top-left)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.17f * a),
            0.7f to Color.White.copy(alpha = 0.05f * a),
            1f to Color.White.copy(alpha = 0.12f * a),
            center = Offset(center.x - radius * 0.30f, center.y - radius * 0.40f),
            radius = radius * 1.9f
        ),
        radius = radius, center = center
    )
    // Inner pink ambience at the bottom of the bubble
    drawCircle(
        brush = Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to Color(0xFFFF6E93).copy(alpha = 0.22f * a),
            center = center, radius = radius
        ),
        radius = radius, center = center
    )
    // Rim ring
    drawCircle(
        color = Color.White.copy(alpha = 0.30f * a),
        radius = radius, center = center,
        style = Stroke(width = radius * 0.075f)
    )
    // Top-left specular arc
    drawArc(
        brush = Brush.horizontalGradient(listOf(Color.White.copy(0.08f * a), Color.White.copy(0.55f * a))),
        startAngle = -215f, sweepAngle = 55f, useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = radius * 0.13f, cap = StrokeCap.Round)
    )
    // Digit text (glow underlay + crisp pink glyph)
    val textPx = radius * 0.92f
    paint.textSize = textPx
    paint.color = Color(0xFFFFA5BE).copy(alpha = a).toArgb()
    glowPaint.textSize = textPx
    glowPaint.color = Color(0xFFFF4D79).copy(alpha = a * (0.30f + 0.25f * pulse) * (0.5f + depth)).toArgb()
    val canvas = drawContext.canvas.nativeCanvas
    canvas.save()
    canvas.rotate(rotation, center.x, center.y)
    canvas.drawText(label, center.x, center.y + textPx * 0.35f, glowPaint)
    canvas.drawText(label, center.x, center.y + textPx * 0.35f, paint)
    canvas.restore()
}

// A small five-petal blossom puff (used by branches, logo accent, footer).
private fun DrawScope.drawWelcomeBlossom(center: Offset, radius: Float, alpha: Float) {
    if (alpha <= 0.004f || radius <= 1f) return
    repeat(5) { k ->
        val ang = k * 72f * PI.toFloat() / 180f - 90f * PI.toFloat() / 180f
        drawCircle(
            color = Color(0xFFFF9FC0).copy(alpha = 0.92f * alpha),
            radius = radius * 0.52f,
            center = center + Offset(cos(ang) * radius * 0.52f, sin(ang) * radius * 0.52f)
        )
    }
    drawCircle(color = Color(0xFFFFD9E4).copy(alpha = alpha), radius = radius * 0.34f, center = center)
    drawCircle(color = Color(0xFFFFF1F5).copy(alpha = 0.9f * alpha), radius = radius * 0.15f, center = center)
}

// Blossom branches in the top corners: dark arms, blossom clusters, slow sway.
private fun DrawScope.drawWelcomeBlossomBranches(clock: Float, size: Size) {
    val sway = sin(clock * 0.5f) * 1.8f
    val branchStyle = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
    rotate(sway, pivot = Offset(0f, 0f)) {
        // Top-left main branch
        drawPath(
            Path().apply {
                moveTo(-20f, -10f)
                quadraticBezierTo(size.width * 0.14f, size.height * 0.09f, size.width * 0.34f, size.height * 0.15f)
            },
            color = Color(0xFF55202F), style = branchStyle
        )
        drawPath(
            Path().apply {
                moveTo(size.width * 0.10f, size.height * 0.055f)
                quadraticBezierTo(size.width * 0.12f, size.height * 0.14f, size.width * 0.06f, size.height * 0.20f)
            },
            color = Color(0xFF4A1B29), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        drawWelcomeBlossom(Offset(size.width * 0.10f, size.height * 0.055f), 13.dp.toPx(), 0.92f)
        drawWelcomeBlossom(Offset(size.width * 0.20f, size.height * 0.10f), 16.dp.toPx(), 0.95f)
        drawWelcomeBlossom(Offset(size.width * 0.30f, size.height * 0.135f), 12.dp.toPx(), 0.90f)
        drawWelcomeBlossom(Offset(size.width * 0.06f, size.height * 0.155f), 11.dp.toPx(), 0.85f)
        drawWelcomeBlossom(Offset(size.width * 0.16f, size.height * 0.195f), 13.dp.toPx(), 0.88f)
        drawWelcomeBlossom(Offset(size.width * 0.345f, size.height * 0.155f), 9.dp.toPx(), 0.80f)
    }
    rotate(-sway * 0.8f, pivot = Offset(size.width, 0f)) {
        // Small top-right cluster
        drawPath(
            Path().apply {
                moveTo(size.width + 20f, -10f)
                quadraticBezierTo(size.width * 0.90f, size.height * 0.05f, size.width * 0.80f, size.height * 0.08f)
            },
            color = Color(0xFF4A1B29), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        drawWelcomeBlossom(Offset(size.width * 0.90f, size.height * 0.045f), 12.dp.toPx(), 0.85f)
        drawWelcomeBlossom(Offset(size.width * 0.81f, size.height * 0.075f), 10.dp.toPx(), 0.80f)
    }
}

// Single petal: rotated soft pink oval.
private fun DrawScope.drawPetal(center: Offset, sizePx: Float, rotationDeg: Float, alpha: Float) {
    if (alpha <= 0.004f) return
    rotate(rotationDeg, center) {
        drawOval(
            brush = Brush.verticalGradient(
                listOf(Color(0xFFFFC9D9), Color(0xFFFF7BA3)),
                startY = center.y - sizePx / 2f,
                endY = center.y + sizePx / 2f
            ),
            topLeft = Offset(center.x - sizePx * 0.32f, center.y - sizePx / 2f),
            size = Size(sizePx * 0.64f, sizePx),
            alpha = alpha.coerceIn(0f, 1f)
        )
    }
}
