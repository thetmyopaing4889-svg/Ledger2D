package com.myanmar.ledger2d.core.design

import android.animation.ValueAnimator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cherry Obsidian Premium: decorative surfaces never determine accounting states. */
object AppColors {
    val Primary = Color(0xFFFF2D55); val PrimaryDeep = Color(0xFF9F1239); val Secondary = Color(0xFF7A2948)
    val Accent = Color(0xFFE11D48); val Gold = Color(0xFFC49345); val GoldSoft = Color(0xFFFFEFF3); val Ink = Color(0xFF24191E)
    val Wine = Color(0xFF9F1239); val Blush = Color(0xFFFFF3F7); val Champagne = Color.White; val Stone = Color(0xFFFFCFE0)
    val Success = Color(0xFF18794E); val Warning = Color(0xFF936500); val Closed = Color(0xFFB3264B); val Limit = Color(0xFF7C3F70)
    val MoneyPositive = Color(0xFF18794E); val MoneyNegative = Color(0xFFB3261E)
    val Obsidian = Color(0xFF1B1121)
    val Plum = Color(0xFF49304E)
    val AuroraRose = Color(0xFFDC547F)
    val AuroraViolet = Color(0xFF8167B1)
    val Pearl = Color(0xFFF9F7FB)
    val PanelBorder = Color(0xFFE8DFEA)
    val MutedInk = Color(0xFF6B606E)
    val ChampagneGold = Color(0xFFF0D2A5)
}
object AppDimens {
    val screen = 16.dp; val section = 24.dp; val card = 16.dp
    val compact = 8.dp; val buttonHeight = 56.dp; val row = 76.dp
    val cardElevation = 2.dp; val featuredElevation = 5.dp
    val space4 = 4.dp; val space8 = 8.dp; val space12 = 12.dp; val space16 = 16.dp; val space20 = 20.dp; val space24 = 24.dp
}
object AppShapes {
    val shapes = Shapes(extraSmall=RoundedCornerShape(8.dp), small=RoundedCornerShape(12.dp), medium=RoundedCornerShape(16.dp), large=RoundedCornerShape(20.dp), extraLarge=RoundedCornerShape(26.dp))
}
object AppMotion { const val Short = 180; const val Medium = 320; const val Long = 500 }

private val Light = lightColorScheme(
    primary=AppColors.PrimaryDeep, onPrimary=Color.White,
    primaryContainer=Color(0xFFFBE5EF), onPrimaryContainer=AppColors.Obsidian,
    secondary=AppColors.Secondary, onSecondary=Color.White,
    secondaryContainer=Color(0xFFF4E8F2), onSecondaryContainer=AppColors.Obsidian,
    tertiary=AppColors.Gold, onTertiary=AppColors.Obsidian,
    tertiaryContainer=Color(0xFFF8EEDD), onTertiaryContainer=Color(0xFF3B2A0D),
    background=AppColors.Pearl, surface=Color.White, surfaceVariant=Color(0xFFF6F0F7),
    surfaceTint=AppColors.PrimaryDeep, onBackground=AppColors.Ink,
    onSurface=AppColors.Ink, onSurfaceVariant=AppColors.MutedInk,
    outline=AppColors.PanelBorder, error=AppColors.MoneyNegative
)
private val Dark = darkColorScheme(
    primary=Color(0xFFFFA4C2), onPrimary=AppColors.Obsidian,
    primaryContainer=Color(0xFF673450), onPrimaryContainer=Color(0xFFFFE3EC),
    secondary=Color(0xFFE0B6CF), tertiary=AppColors.ChampagneGold,
    tertiaryContainer=Color(0xFF574318), background=Color(0xFF140F19),
    surface=AppColors.Obsidian, surfaceVariant=AppColors.Plum,
    onSurfaceVariant=Color(0xFFE5D6E6), error=Color(0xFFFFB4AB)
)

/** Android system Myanmar fallback stays available for mixed-script names and entries. */
private val AppFont = FontFamily.SansSerif
val AppTypography = Typography(
    displaySmall=Typography().displaySmall.copy(fontFamily=AppFont,fontSize=30.sp,lineHeight=39.sp,fontWeight=FontWeight.Bold),
    headlineLarge=Typography().headlineLarge.copy(fontFamily=AppFont,fontSize=28.sp,lineHeight=37.sp,fontWeight=FontWeight.Bold),
    headlineSmall=Typography().headlineSmall.copy(fontFamily=AppFont,fontSize=23.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold),
    titleLarge=Typography().titleLarge.copy(fontFamily=AppFont,fontSize=21.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),
    titleMedium=Typography().titleMedium.copy(fontFamily=AppFont,fontSize=17.sp,lineHeight=26.sp,fontWeight=FontWeight.SemiBold),
    titleSmall=Typography().titleSmall.copy(fontFamily=AppFont,fontSize=15.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold),
    bodyLarge=Typography().bodyLarge.copy(fontFamily=AppFont,fontSize=16.sp,lineHeight=26.sp),
    bodyMedium=Typography().bodyMedium.copy(fontFamily=AppFont,fontSize=14.sp,lineHeight=23.sp),
    bodySmall=Typography().bodySmall.copy(fontFamily=AppFont,fontSize=13.sp,lineHeight=21.sp),
    labelLarge=Typography().labelLarge.copy(fontFamily=AppFont,fontSize=14.sp,lineHeight=22.sp,fontWeight=FontWeight.Medium),
    labelMedium=Typography().labelMedium.copy(fontFamily=AppFont,fontSize=12.sp,lineHeight=19.sp),
    labelSmall=Typography().labelSmall.copy(fontFamily=AppFont,fontSize=12.sp,lineHeight=19.sp)
)

@Composable
fun LedgerTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=if(darkTheme) Dark else Light,typography=AppTypography,shapes=AppShapes.shapes,content=content)
}

/** A glass-inspired layered gradient; animation is optional and honors system animations. */
@Composable
fun AuroraGlassCard(
    modifier: Modifier = Modifier,
    animated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val motionAllowed = remember { ValueAnimator.areAnimatorsEnabled() }
    val phase = if (animated && motionAllowed) {
        val transition = rememberInfiniteTransition(label = "aurora-glass")
        val value = transition.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(7200), RepeatMode.Reverse),
            label = "aurora-shift"
        )
        value.value
    } else 0.35f
    val glow = lerp(AppColors.AuroraRose, AppColors.AuroraViolet, phase)
    Surface(
        modifier = modifier, shape = MaterialTheme.shapes.extraLarge,
        color = AppColors.Obsidian,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .22f)),
        shadowElevation = AppDimens.featuredElevation
    ) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(
            AppColors.Obsidian, lerp(AppColors.Plum, AppColors.PrimaryDeep, phase).copy(alpha = .92f), AppColors.Obsidian
        )))) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 42.dp, y = (-44).dp)
                    .size(178.dp).background(
                        Brush.radialGradient(listOf(glow.copy(alpha = .30f), Color.Transparent)),
                        CircleShape
                    )
            )
            Column(
                Modifier.fillMaxWidth().padding(AppDimens.card),
                verticalArrangement = Arrangement.spacedBy(AppDimens.space8),
                content = content
            )
        }
    }
}

/** Quiet presentation for numbers and transaction detail: no moving background. */
@Composable
fun PremiumPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, AppColors.PanelBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(AppDimens.card),
            verticalArrangement = Arrangement.spacedBy(AppDimens.space8),
            content = content
        )
    }
}

@Composable
fun PremiumSettlementPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF4EDF6),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Color(0xFFDCC9DD))
    ) {
        Column(
            Modifier.fillMaxWidth().padding(AppDimens.card),
            verticalArrangement = Arrangement.spacedBy(AppDimens.space8),
            content = content
        )
    }
}
