package com.myanmar.ledger2d.core.design

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
val AppTypography = Typography(headlineLarge=Typography().headlineLarge.copy(fontSize=34.sp,lineHeight=42.sp),headlineSmall=Typography().headlineSmall.copy(fontSize=25.sp,lineHeight=32.sp),titleLarge=Typography().titleLarge.copy(fontSize=21.sp),bodyLarge=Typography().bodyLarge.copy(lineHeight=24.sp),labelLarge=Typography().labelLarge.copy(fontSize=15.sp))
@Composable fun LedgerTheme(darkTheme:Boolean=isSystemInDarkTheme(),content:@Composable () -> Unit){ MaterialTheme(colorScheme=if(darkTheme)Dark else Light,typography=AppTypography,shapes=AppShapes.shapes,content=content) }
