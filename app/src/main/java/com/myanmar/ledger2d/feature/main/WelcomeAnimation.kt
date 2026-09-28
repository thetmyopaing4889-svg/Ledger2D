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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myanmar.ledger2d.R
import com.myanmar.ledger2d.core.design.LocalLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val CTA_CENTER_Y = 0.785f
private const val CTA_SIDE_FRACTION = 0.085f

private fun playWelcomeClick(view: android.view.View) {
    val audio = view.context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
        view.playSoundEffect(SoundEffectConstants.CLICK)
    }
}

@Composable
private fun WelcomeDarkBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
        }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = true }
    }
}

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    val language = LocalLanguage.current
    val view = LocalView.current
    WelcomeDarkBars()

    val motion = rememberInfiniteTransition(label = "welcome-premium-motion")
    val phase by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "light-wave"
    )
    val pulse by motion.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "cherry-glow"
    )
    val twinkle by motion.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1350, easing = LinearEasing), RepeatMode.Reverse),
        label = "sparkle"
    )

    var pressedOut by remember { mutableStateOf(false) }
    val exitFlash = remember { Animatable(0f) }
    LaunchedEffect(pressedOut) {
        if (pressedOut) {
            launch { exitFlash.animateTo(1f, tween(380, easing = LinearEasing)) }
            delay(350)
            onContinue()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF08020B))
    ) {
        Image(
            painter = painterResource(R.drawable.welcome_premium_poster),
            contentDescription = "Cherry 2D Ledger",
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val band = size.width * 0.42f
                    val x = size.width * 1.35f - (size.width * 1.75f) * phase
                    drawRect(
                        brush = Brush.linearGradient(
                            0f to Color.Transparent,
                            0.5f to Color(0x20FF77B5),
                            1f to Color.Transparent,
                            start = Offset(x, size.height * 0.08f),
                            end = Offset(x + band, size.height * 0.92f)
                        )
                    )
                }
        )

        // A restrained cinematic bloom over the cherry hero; it never hides the poster.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.12f * pulse }
                .background(
                    Brush.radialGradient(
                        0f to Color(0x66FF9DC8),
                        0.32f to Color(0x25FF3E86),
                        0.72f to Color.Transparent,
                        radius = 560f
                    )
                )
        )

        WelcomeLightEffects(phase = phase, pulse = pulse, twinkle = twinkle)

        BoxWithConstraints(Modifier.fillMaxSize()) {
            WelcomeCtaButton(
                label = language.translate("စတင်အသုံးပြုမည်"),
                glow = pulse,
                enabled = !pressedOut,
                onPress = {
                    playWelcomeClick(view)
                    if (!pressedOut) pressedOut = true
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = maxHeight * CTA_CENTER_Y - 31.dp)
                    .fillMaxWidth(1f - CTA_SIDE_FRACTION * 2f)
            )
        }

        if (pressedOut) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = exitFlash.value }
                    .background(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.92f), Color(0xFFFF7CB4).copy(alpha = 0.65f), Color.Transparent)
                        )
                    )
            )
        }
    }
}

@Composable
private fun WelcomeLightEffects(phase: Float, pulse: Float, twinkle: Float) {
    val sparks = remember {
        listOf(
            Spark(0.12f, 0.10f, 11f, 0.0f), Spark(0.28f, 0.17f, 8f, 1.2f),
            Spark(0.54f, 0.08f, 10f, 2.0f), Spark(0.76f, 0.15f, 9f, 0.8f),
            Spark(0.91f, 0.28f, 8f, 2.8f), Spark(0.16f, 0.36f, 7f, 3.4f),
            Spark(0.68f, 0.33f, 8f, 4.1f), Spark(0.84f, 0.52f, 7f, 5.0f),
            Spark(0.35f, 0.61f, 6f, 5.7f)
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Slow concentric light ripples echo the artwork's glowing floor.
        val ripple = (phase * 2f * PI.toFloat())
        repeat(3) { index ->
            val travel = ((phase + index * 0.23f) % 1f)
            val radius = w * (0.16f + travel * 0.42f)
            val alpha = (0.16f * (1f - travel) * pulse).coerceAtLeast(0f)
            drawOval(
                color = Color(0xFFFF6DAA).copy(alpha = alpha),
                topLeft = Offset(w * 0.5f - radius, h * 0.685f - radius * 0.22f),
                size = Size(radius * 2f, radius * 0.44f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f)
            )
        }

        sparks.forEach { spark ->
            val shimmer = ((sin((twinkle * 2f + spark.phase) * PI.toFloat()) + 1f) / 2f)
            val alpha = 0.18f + shimmer * 0.72f
            val x = spark.x * w + sin((phase + spark.phase) * 2f * PI.toFloat()) * 5f
            val y = spark.y * h + cos((phase + spark.phase) * 2f * PI.toFloat()) * 4f
            val r = spark.radius * (0.72f + shimmer * 0.50f)
            drawLine(Color(0xFFFFD9E8).copy(alpha = alpha), Offset(x - r, y), Offset(x + r, y), strokeWidth = 1.3f)
            drawLine(Color.White.copy(alpha = alpha * 0.72f), Offset(x, y - r), Offset(x, y + r), strokeWidth = 1.3f)
            drawCircle(Color.White.copy(alpha = alpha), radius = r * 0.18f, center = Offset(x, y))
        }
    }
}

private data class Spark(val x: Float, val y: Float, val radius: Float, val phase: Float)

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
        animationSpec = spring(),
        label = "cta-press-scale"
    )
    val sweep by rememberInfiniteTransition(label = "cta-shimmer").animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "cta-shimmer-position"
    )
    val shape = RoundedCornerShape(30.dp)

    Box(
        modifier
            .height(62.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .drawBehind {
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(Color(0x66FF2D8A), Color(0x66FFB34D), Color(0x55FF2D8A))),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                )
            }
            .padding(2.dp)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    0f to Color(0xFFB80E58),
                    0.48f to Color(0xFFFF2D82),
                    1f to Color(0xFF7F104E)
                )
            )
            .border(
                1.6.dp,
                Brush.horizontalGradient(listOf(Color(0xFFFFD98C), Color(0xFFFFF0C1), Color(0xFFC88A32))),
                shape
            )
            .drawBehind {
                val x = size.width * sweep
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.26f * glow),
                        1f to Color.Transparent,
                        start = Offset(x, 0f),
                        end = Offset(x + size.width * 0.22f, size.height)
                    )
                )
            }
    ) {
        Button(
            onClick = onPress,
            enabled = enabled,
            interactionSource = interaction,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = Color.White,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = Color.White
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_cherry_mark),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.size(9.dp))
                Text(label, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(12.dp))
                Box(
                    Modifier
                        .size(37.dp)
                        .clip(CircleShape)
                        .background(Color(0x665E0A3D))
                        .border(1.5.dp, Color(0xFFFFE8A5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
