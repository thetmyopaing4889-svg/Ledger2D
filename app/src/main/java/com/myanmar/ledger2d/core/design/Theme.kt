package com.myanmar.ledger2d.core.design

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppColors {
    val Primary = Color(0xFFFF2D55); val PrimaryDeep = Color(0xFF9F1239); val Secondary = Color(0xFF7A2948)
    val Accent = Color(0xFFE11D48); val Gold = Color(0xFFC49345); val GoldSoft = Color(0xFFFFEFF3); val Ink = Color(0xFF24191E)
    val Wine = Color(0xFF9F1239); val Blush = Color(0xFFFFF3F7); val Champagne = Color.White; val Stone = Color(0xFFFFCFE0)
    val Success = Color(0xFF18794E); val Warning = Color(0xFF936500); val Closed = Color(0xFFB3264B); val Limit = Color(0xFF7C3F70)
    val MoneyPositive = Color(0xFF18794E); val MoneyNegative = Color(0xFFB3261E)
}
object AppDimens { val screen = 18.dp; val section = 24.dp; val card = 18.dp; val compact = 8.dp; val buttonHeight = 56.dp; val row = 76.dp; val cardElevation = 3.dp; val featuredElevation = 10.dp }
object AppShapes { val shapes = Shapes(extraSmall=RoundedCornerShape(8.dp), small=RoundedCornerShape(14.dp), medium=RoundedCornerShape(18.dp), large=RoundedCornerShape(22.dp), extraLarge=RoundedCornerShape(26.dp)) }
object AppMotion { const val Short = 180; const val Medium = 320; const val Long = 500 }
private val Light = lightColorScheme(primary=AppColors.Primary,onPrimary=Color.White,primaryContainer=Color(0xFFFFD7E3),onPrimaryContainer=Color(0xFF650022),secondary=AppColors.Secondary,onSecondary=Color.White,secondaryContainer=Color(0xFFFFE7EE),onSecondaryContainer=Color(0xFF35111F),tertiary=AppColors.Gold,onTertiary=Color.White,tertiaryContainer=AppColors.GoldSoft,onTertiaryContainer=Color(0xFF3B2A0D),background=Color(0xFFFFF6F8),surface=Color.White,surfaceVariant=AppColors.Blush,surfaceTint=AppColors.Primary,onSurface=AppColors.Ink,onSurfaceVariant=Color(0xFF665C60),outline=AppColors.Stone,error=AppColors.MoneyNegative)
private val Dark = darkColorScheme(primary=Color(0xFFFF9CB3),onPrimary=Color(0xFF650022),primaryContainer=Color(0xFF7F1640),onPrimaryContainer=Color(0xFFFFD9E2),secondary=Color(0xFFFFB0C6),tertiary=Color(0xFFE4BE72),tertiaryContainer=Color(0xFF574318),background=Color(0xFF181114),surface=Color(0xFF24191E),surfaceVariant=Color(0xFF34242A),onSurfaceVariant=Color(0xFFD1BFC5),error=Color(0xFFFFB4AB))
private val AppFont = FontFamily.SansSerif
val AppTypography = Typography(
    displaySmall=Typography().displaySmall.copy(fontFamily=AppFont,fontSize=30.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold),
    headlineLarge=Typography().headlineLarge.copy(fontFamily=AppFont,fontSize=28.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold),
    headlineSmall=Typography().headlineSmall.copy(fontFamily=AppFont,fontSize=23.sp,lineHeight=29.sp,fontWeight=FontWeight.Bold),
    titleLarge=Typography().titleLarge.copy(fontFamily=AppFont,fontSize=19.sp,lineHeight=25.sp,fontWeight=FontWeight.SemiBold),
    titleMedium=Typography().titleMedium.copy(fontFamily=AppFont,fontSize=17.sp,lineHeight=23.sp,fontWeight=FontWeight.SemiBold),
    bodyLarge=Typography().bodyLarge.copy(fontFamily=AppFont,fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=Typography().bodyMedium.copy(fontFamily=AppFont,fontSize=14.sp,lineHeight=21.sp),
    bodySmall=Typography().bodySmall.copy(fontFamily=AppFont,fontSize=13.sp,lineHeight=19.sp),
    labelLarge=Typography().labelLarge.copy(fontFamily=AppFont,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium),
    labelMedium=Typography().labelMedium.copy(fontFamily=AppFont,fontSize=12.sp,lineHeight=17.sp)
)
@Composable fun LedgerTheme(darkTheme:Boolean=false,content:@Composable () -> Unit){ MaterialTheme(colorScheme=if(darkTheme)Dark else Light,typography=AppTypography,shapes=AppShapes.shapes,content=content) }
