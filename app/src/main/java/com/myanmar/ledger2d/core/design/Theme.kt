package com.myanmar.ledger2d.core.design

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

object AppColors {
    val Primary = Color(0xFFF52B57); val PrimaryDeep = Color(0xFFC8104A); val Secondary = Color(0xFF8D3152); val Success = Color(0xFF26734D)
    val Warning = Color(0xFF9B6700); val Closed = Color(0xFFB3264B); val Limit = Color(0xFF8A3D75)
    val MoneyPositive = Color(0xFF18794E); val MoneyNegative = Color(0xFFB3261E)
}
object AppDimens { val screen = 20.dp; val section = 24.dp; val card = 18.dp; val compact = 8.dp; val buttonHeight = 58.dp }
object AppShapes { val shapes = Shapes(extraSmall=androidx.compose.foundation.shape.RoundedCornerShape(10.dp), small=androidx.compose.foundation.shape.RoundedCornerShape(16.dp), medium=androidx.compose.foundation.shape.RoundedCornerShape(22.dp), large=androidx.compose.foundation.shape.RoundedCornerShape(30.dp)) }
object AppMotion { const val Short = 180; const val Medium = 320 }
private val Light = lightColorScheme(primary=AppColors.Primary,onPrimary=Color.White,primaryContainer=Color(0xFFFFDCE5),onPrimaryContainer=Color(0xFF5E0922),secondary=AppColors.Secondary,onSecondary=Color.White,secondaryContainer=Color(0xFFFFE1EA),background=Color(0xFFFFF7F9),surface=Color.White,surfaceVariant=Color(0xFFF8E8ED),onSurfaceVariant=Color(0xFF6D5B61),error=AppColors.MoneyNegative)
private val Dark = darkColorScheme(primary=Color(0xFFFFB1C2),onPrimary=Color(0xFF650022),primaryContainer=Color(0xFF8E1744),onPrimaryContainer=Color(0xFFFFD9E2),secondary=Color(0xFFFFB0C6),background=Color(0xFF1D1115),surface=Color(0xFF1D1115),surfaceVariant=Color(0xFF392329),error=Color(0xFFFFB4AB))
private val AppFont = FontFamily.SansSerif
val AppTypography = Typography(
    headlineLarge=Typography().headlineLarge.copy(fontFamily=AppFont,fontSize=32.sp,lineHeight=43.sp,fontWeight=FontWeight.Bold),
    headlineSmall=Typography().headlineSmall.copy(fontFamily=AppFont,fontSize=25.sp,lineHeight=35.sp,fontWeight=FontWeight.Bold),
    titleLarge=Typography().titleLarge.copy(fontFamily=AppFont,fontSize=21.sp,lineHeight=30.sp,fontWeight=FontWeight.SemiBold),
    titleMedium=Typography().titleMedium.copy(fontFamily=AppFont,fontSize=18.sp,lineHeight=27.sp,fontWeight=FontWeight.SemiBold),
    bodyLarge=Typography().bodyLarge.copy(fontFamily=AppFont,fontSize=17.sp,lineHeight=27.sp),
    bodyMedium=Typography().bodyMedium.copy(fontFamily=AppFont,fontSize=15.sp,lineHeight=24.sp),
    bodySmall=Typography().bodySmall.copy(fontFamily=AppFont,fontSize=13.sp,lineHeight=20.sp),
    labelLarge=Typography().labelLarge.copy(fontFamily=AppFont,fontSize=15.sp,lineHeight=22.sp,fontWeight=FontWeight.Medium),
    labelMedium=Typography().labelMedium.copy(fontFamily=AppFont,fontSize=13.sp,lineHeight=19.sp)
)
@Composable fun LedgerTheme(darkTheme:Boolean=isSystemInDarkTheme(),content:@Composable () -> Unit){ MaterialTheme(colorScheme=if(darkTheme)Dark else Light,typography=AppTypography,shapes=AppShapes.shapes,content=content) }
