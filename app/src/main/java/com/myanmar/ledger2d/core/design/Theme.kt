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
    val Primary = Color(0xFF145C52); val Secondary = Color(0xFF8A5A13); val Success = Color(0xFF26734D)
    val Warning = Color(0xFF9B6700); val Closed = Color(0xFF9A3B3B); val Limit = Color(0xFF6750A4)
    val MoneyPositive = Color(0xFF18794E); val MoneyNegative = Color(0xFFB3261E)
}
object AppDimens { val screen = 20.dp; val section = 24.dp; val card = 16.dp; val compact = 8.dp }
object AppShapes { val shapes = Shapes(extraSmall=androidx.compose.foundation.shape.RoundedCornerShape(8.dp), small=androidx.compose.foundation.shape.RoundedCornerShape(12.dp), medium=androidx.compose.foundation.shape.RoundedCornerShape(18.dp), large=androidx.compose.foundation.shape.RoundedCornerShape(28.dp)) }
object AppMotion { const val Short = 180; const val Medium = 320 }
private val Light = lightColorScheme(primary=AppColors.Primary,onPrimary=Color.White,secondary=AppColors.Secondary,background=Color(0xFFF7FAF8),surface=Color(0xFFF7FAF8),surfaceVariant=Color(0xFFE1EBE7),error=AppColors.MoneyNegative)
private val Dark = darkColorScheme(primary=Color(0xFF82D4C5),secondary=Color(0xFFF1C27D),background=Color(0xFF0E1513),surface=Color(0xFF0E1513),surfaceVariant=Color(0xFF26332F),error=Color(0xFFFFB4AB))
private val AppFont = FontFamily.SansSerif
val AppTypography = Typography(
    headlineLarge=Typography().headlineLarge.copy(fontFamily=AppFont,fontSize=32.sp,lineHeight=40.sp,fontWeight=FontWeight.Bold),
    headlineSmall=Typography().headlineSmall.copy(fontFamily=AppFont,fontSize=25.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold),
    titleLarge=Typography().titleLarge.copy(fontFamily=AppFont,fontSize=20.sp,lineHeight=27.sp,fontWeight=FontWeight.SemiBold),
    titleMedium=Typography().titleMedium.copy(fontFamily=AppFont,fontSize=17.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold),
    bodyLarge=Typography().bodyLarge.copy(fontFamily=AppFont,fontSize=16.sp,lineHeight=24.sp),
    bodyMedium=Typography().bodyMedium.copy(fontFamily=AppFont,fontSize=14.sp,lineHeight=21.sp),
    bodySmall=Typography().bodySmall.copy(fontFamily=AppFont,fontSize=12.sp,lineHeight=18.sp),
    labelLarge=Typography().labelLarge.copy(fontFamily=AppFont,fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium),
    labelMedium=Typography().labelMedium.copy(fontFamily=AppFont,fontSize=12.sp,lineHeight=17.sp)
)
@Composable fun LedgerTheme(darkTheme:Boolean=isSystemInDarkTheme(),content:@Composable () -> Unit){ MaterialTheme(colorScheme=if(darkTheme)Dark else Light,typography=AppTypography,shapes=AppShapes.shapes,content=content) }
