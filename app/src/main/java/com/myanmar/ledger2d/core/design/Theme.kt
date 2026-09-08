package com.myanmar.ledger2d.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppColors {
    val Primary = Color(0xFFE92F5B); val PrimaryDeep = Color(0xFFB8174A); val Secondary = Color(0xFF7D3551)
    val Success = Color(0xFF26734D); val Warning = Color(0xFF9B6700); val Closed = Color(0xFFB3264B); val Limit = Color(0xFF8A3D75)
    val MoneyPositive = Color(0xFF18794E); val MoneyNegative = Color(0xFFB3261E)
}
object AppDimens { val screen = 16.dp; val section = 20.dp; val card = 14.dp; val compact = 6.dp; val buttonHeight = 52.dp; val row = 68.dp }
object AppShapes { val shapes = Shapes(extraSmall=8.dp, small=12.dp, medium=16.dp, large=22.dp) }
object AppMotion { const val Short = 180; const val Medium = 320 }
private val Light = lightColorScheme(primary=AppColors.Primary,onPrimary=Color.White,primaryContainer=Color(0xFFFFDCE5),onPrimaryContainer=Color(0xFF5E0922),secondary=AppColors.Secondary,onSecondary=Color.White,secondaryContainer=Color(0xFFFFE1EA),background=Color(0xFFFFF8FA),surface=Color.White,surfaceVariant=Color(0xFFF7ECEF),onSurfaceVariant=Color(0xFF6D5B61),error=AppColors.MoneyNegative)
private val Dark = darkColorScheme(primary=Color(0xFFFF9CB3),onPrimary=Color(0xFF650022),primaryContainer=Color(0xFF7F1640),onPrimaryContainer=Color(0xFFFFD9E2),secondary=Color(0xFFFFB0C6),background=Color(0xFF1C1115),surface=Color(0xFF24191E),surfaceVariant=Color(0xFF34242A),onSurfaceVariant=Color(0xFFD1BFC5),error=Color(0xFFFFB4AB))
private val AppFont = FontFamily.SansSerif
val AppTypography = Typography(
    headlineLarge=Typography().headlineLarge.copy(fontFamily=AppFont,fontSize=27.sp,lineHeight=33.sp,fontWeight=FontWeight.Bold),
    headlineSmall=Typography().headlineSmall.copy(fontFamily=AppFont,fontSize=22.sp,lineHeight=28.sp,fontWeight=FontWeight.Bold),
    titleLarge=Typography().titleLarge.copy(fontFamily=AppFont,fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold),
    titleMedium=Typography().titleMedium.copy(fontFamily=AppFont,fontSize=16.sp,lineHeight=22.sp,fontWeight=FontWeight.SemiBold),
    bodyLarge=Typography().bodyLarge.copy(fontFamily=AppFont,fontSize=16.sp,lineHeight=23.sp),
    bodyMedium=Typography().bodyMedium.copy(fontFamily=AppFont,fontSize=14.sp,lineHeight=20.sp),
    bodySmall=Typography().bodySmall.copy(fontFamily=AppFont,fontSize=12.sp,lineHeight=17.sp),
    labelLarge=Typography().labelLarge.copy(fontFamily=AppFont,fontSize=13.sp,lineHeight=18.sp,fontWeight=FontWeight.Medium),
    labelMedium=Typography().labelMedium.copy(fontFamily=AppFont,fontSize=11.sp,lineHeight=15.sp)
)
@Composable fun LedgerTheme(darkTheme:Boolean=isSystemInDarkTheme(),content:@Composable () -> Unit){ MaterialTheme(colorScheme=if(darkTheme)Dark else Light,typography=AppTypography,shapes=AppShapes.shapes,content=content) }
