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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
// The full-bleed backdrop is one generated poster art (welcome_poster_bg.png):
// burgundy gradient, glow pool, halftone grain, hanging vines + blossoms,
// gold comet swirls, halo ring, glass digit bubbles, sparks, bokeh, petals
// and floor glow — matching the reference poster pixel-for-pixel. The living
// cherry (cherry_hero_art.png) sits on top. No 3D engine, no bundled audio,
// no business-logic
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
    WelcomeDigitSpec("42", 0.36f, 0.10f, 0.55f, 3.1f),
    WelcomeDigitSpec("84", -0.36f, 0.12f, 0.45f, 4.2f),
    WelcomeDigitSpec("70", 0.28f, 0.16f, 0.35f, 5.0f)
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

    // --- Ambient loops ---
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
        // The poster's fruit seat (swirls + halo) sits at 712/2340 of the art;
        // with top-aligned Crop that lands at ~0.66 * screen width on phones.
        val cherryCenter = Offset(w / 2f, min(w * 0.66f, h * 0.42f))
        val bgIn = enterFade(0.05f, 0.85f)
        val glowIn = enterFade(0.15f, 0.75f)
        val heroIn = welcomeBackOut(enterFade(0.45f, 0.9f).coerceIn(0f, 1f))
        val alive = enterFade(2.0f, 0.9f)

        // Layer 1 — generated poster backdrop (every ref element baked in):
        // vines + blossoms, glow pool, halftone grain, gold comet swirls,
        // halo ring, glass bubbles, sparks, bokeh, petals, floor glow.
        Image(
            painter = painterResource(R.drawable.welcome_poster_bg),
            contentDescription = null,
            alignment = Alignment.TopCenter,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Gentle counter-parallax; slight zoom keeps edges covered
                    translationX = smoothX * 5f
                    translationY = smoothY * 4f
                    val zoom = 1f + smoothX * smoothX * 0.012f + smoothY * smoothY * 0.009f
                    scaleX = zoom
                    scaleY = zoom
                    alpha = bgIn
                }
        )

        // Layer 2 — center pink light rising from darkness (story beat)
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset { IntOffset(0, (cherryCenter.y - 170.dp.toPx()).roundToInt()) }
                .size(340.dp)
                .graphicsLayer { alpha = glowIn * (0.75f + 0.25f * sin(clock.floatValue * 0.8f)) }
                .drawBehind {
                    drawCircle(brush = Brush.radialGradient(listOf(Color(0x59FF4D79), Color(0x24FF7BA3), Color.Transparent)))
                }
        )

        // Layer 3 — glass digit bubbles with curved absorption into the cherry
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

        // Layer 4 — the living Cherry hero seated on the poster's swirls.
        val heroGlow = maxOf(absorbGlow.value * 0.9f, outroGlow.value)
        val heroAlpha = if (transitioningOut) 1f else heroIn.coerceIn(0f, 1f)
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset { IntOffset(0, (cherryCenter.y - 174.dp.toPx()).roundToInt()) }
                .size(280.dp, 280.dp)
                .graphicsLayer {
                    cameraDistance = 8f * density
                    transformOrigin = TransformOrigin(0.5f, 0.55f)
                    val zoom = outroZoom.value
                    // Breathe + float are baked into the hero composable's clock math
                    val breathe = 1f + 0.012f * sin(clock.floatValue * 0.9f) * alive
                    scaleX = heroIn * breathe * zoom
                    scaleY = heroIn * breathe * zoom
                    translationY = (sin(clock.floatValue * 0.9f) * 4.5f) * alive + smoothY * 9f
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
                alive = alive
            )
        }

        // Layer 5 — content: logo, tiles, CTA, footer
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

        // Layer 6 — darkness veil lifting at the very start
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = (1f - enterFade(0.05f, 0.9f)).coerceIn(0f, 1f)
            }.background(Color(0xFF0B0206))
        )

        // Layer 7 — outro light burst expanding from the cherry core
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    // Offscreen buffer so the sweep only meets the glyph pixels
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = reveal.coerceIn(0f, 1f)
                    }
                    .drawWithContent {
                        drawContent()
                        val x = size.width * (sweep.value * 1.7f - 0.35f)
                        drawRect(
                            brush = Brush.linearGradient(
                                0f to Color.Transparent,
                                0.5f to Color.White.copy(alpha = 0.55f),
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
            Canvas(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 84.dp, y = (-9).dp)
                    .size(26.dp)
                    .graphicsLayer { alpha = reveal.coerceIn(0f, 1f) }
            ) { drawWelcomeBlossom(Offset(size.width / 2f, size.height / 2f), size.width * 0.46f, 0.95f) }
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
// The living cherry: the generated photoreal hero sprite (volumetric shading,
// dual speculars, stems, veined leaf, gold comet swirls, sparks and bokeh are
// baked into cherry_hero_art.png) wrapped in story-reactive light — absorption
// halo, a breathing highlight veil and a pulse ring driven by the clock.
// ============================================================================
@Composable
private fun WelcomeCherryHero(clock: Float, glow: Float, alive: Float) {
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            // Offscreen buffer so the veil's SrcAtop only meets art pixels
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        // Absorption glow halo behind the sprite
        if (glow > 0.01f) {
            Canvas(Modifier.matchParentSize()) {
                val c = Offset(size.width / 2f, size.height * 0.58f)
                drawCircle(
                    brush = Brush.radialGradient(listOf(Color(0x80FF2D55), Color(0x33FF7BA3), Color.Transparent)),
                    radius = size.minDimension * (0.46f + 0.18f * glow),
                    center = c
                )
            }
        }
        // Photoreal cherry art — baked volumetric lighting and gold swirls
        Image(
            painter = painterResource(R.drawable.cherry_hero_art),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    rotationZ = sin(clock * 0.45f) * 1.6f * alive
                    val breathe = 1f + 0.014f * sin(clock * 1.4f) * alive
                    scaleX = breathe
                    scaleY = breathe
                }
        )
        // Living light veil: a soft diagonal sheen gliding across the artwork
        Canvas(Modifier.matchParentSize()) {
            val t = (clock * 0.35f) % 1f
            val x = size.width * (t * 1.6f - 0.3f)
            clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.10f + 0.10f * alive),
                        1f to Color.Transparent,
                        start = Offset(x, 0f),
                        end = Offset(x + size.width * 0.45f, size.height)
                    ),
                    blendMode = BlendMode.SrcAtop
                )
            }
        }
        // Absorption pulse ring
        if (glow > 0.02f) {
            Canvas(Modifier.matchParentSize()) {
                val c = Offset(size.width / 2f, size.height * 0.58f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.50f * glow),
                    radius = size.minDimension * 0.34f,
                    center = c,
                    style = Stroke(width = 1.4.dp.toPx() + 2.2f * glow.dp.toPx(), cap = StrokeCap.Round)
                )
            }
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
            // Anchored to the poster's fruit seat so the swarm surrounds the cherry
            // (0.85 keeps the original spread; top bubbles graze the top edge like the ref)
            val basePy = cherryCenter.y + (0.02f + spec.dy) * size.height * 0.85f
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


