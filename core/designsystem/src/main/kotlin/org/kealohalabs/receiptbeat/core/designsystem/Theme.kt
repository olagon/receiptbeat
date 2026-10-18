package org.kealohalabs.receiptbeat.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Brand colors, sampled from the intro video. */
object Brand {
    val Teal = Color(0xFF093A40)
    val TealSurface = Color(0xFF0D4B54)
    val TealHigh = Color(0xFF145C66)
    val Coral = Color(0xFFFE6B49)
    val CoralDeep = Color(0xFFB83A1E) // coral that passes AA as text on light backgrounds
    val Paper = Color(0xFFFEF7EC)
    val Ink = Color(0xFF0B2226)
}

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }

private val Light = lightColorScheme(
    primary = Brand.TealSurface,
    onPrimary = Brand.Paper,
    primaryContainer = Color(0xFFCDE8EA),
    onPrimaryContainer = Brand.Teal,
    secondary = Brand.CoralDeep,
    onSecondary = Color.White,
    secondaryContainer = Brand.Coral,
    onSecondaryContainer = Brand.Teal,
    tertiary = Color(0xFF3B6470),
    background = Color(0xFFF7F4EE),
    onBackground = Brand.Ink,
    surface = Color(0xFFF7F4EE),
    onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFE6E2DA),
    onSurfaceVariant = Color(0xFF41484A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFCF7),
    surfaceContainer = Color(0xFFF1EDE6),
    surfaceContainerHigh = Color(0xFFEBE7E0),
    surfaceContainerHighest = Color(0xFFE5E1DA),
    outline = Color(0xFF71787A),
    outlineVariant = Color(0xFFC6C8C4),
    error = Color(0xFFB3261E)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD3DA),
    onPrimary = Brand.Teal,
    primaryContainer = Brand.TealHigh,
    onPrimaryContainer = Brand.Paper,
    secondary = Brand.Coral,
    onSecondary = Brand.Teal,
    secondaryContainer = Brand.Coral,
    onSecondaryContainer = Brand.Teal,
    tertiary = Color(0xFFA9CBD3),
    background = Brand.Teal,
    onBackground = Brand.Paper,
    surface = Brand.Teal,
    onSurface = Brand.Paper,
    surfaceVariant = Brand.TealSurface,
    onSurfaceVariant = Color(0xFFBFD3D5),
    surfaceContainerLowest = Color(0xFF062E33),
    surfaceContainerLow = Color(0xFF0A4148),
    surfaceContainer = Brand.TealSurface,
    surfaceContainerHigh = Color(0xFF115560),
    surfaceContainerHighest = Brand.TealHigh,
    outline = Color(0xFF7FA0A4),
    outlineVariant = Color(0xFF2A6770),
    error = Color(0xFFFFB4AB)
)

private val Black = Dark.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0F10),
    surfaceContainer = Color(0xFF101718),
    surfaceContainerHigh = Color(0xFF162021),
    surfaceContainerHighest = Color(0xFF1C2829),
    surfaceVariant = Color(0xFF162021)
)

private val Poppins = FontFamily(
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold)
)
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_medium, FontWeight.SemiBold)
)

private val Base = Typography()
private fun TextStyle.display() = copy(fontFamily = Poppins, fontWeight = FontWeight.Bold)
private fun TextStyle.body() = copy(fontFamily = Inter)

val AppTypography = Typography(
    displayLarge = Base.displayLarge.display(),
    displayMedium = Base.displayMedium.display(),
    displaySmall = Base.displaySmall.display(),
    headlineLarge = Base.headlineLarge.display(),
    headlineMedium = Base.headlineMedium.display(),
    headlineSmall = Base.headlineSmall.display(),
    titleLarge = Base.titleLarge.copy(fontFamily = Poppins, fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.body().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.body().copy(fontWeight = FontWeight.Medium),
    bodyLarge = Base.bodyLarge.body(),
    bodyMedium = Base.bodyMedium.body(),
    bodySmall = Base.bodySmall.body(),
    labelLarge = Base.labelLarge.body().copy(fontWeight = FontWeight.Medium),
    labelMedium = Base.labelMedium.body().copy(fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.body().copy(fontWeight = FontWeight.Medium)
)

/** Amounts: tabular numbers so columns line up. */
object MoneyStyle {
    val Hero = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 32.sp, fontFeatureSettings = "tnum")
    val Large = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, fontFeatureSettings = "tnum")
    val Row = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, fontFeatureSettings = "tnum")
    val Small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 14.sp, fontFeatureSettings = "tnum")
}

val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Immutable
data class ExtraColors(val capture: Color, val onCapture: Color, val money: Color, val glow: Color, val isDark: Boolean)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(Brand.Coral, Brand.Teal, Brand.Ink, Color(0x33FE6B49), false)
}

@Composable
fun ReceiptBeatTheme(mode: ThemeMode = ThemeMode.SYSTEM, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.BLACK -> true
    }
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        mode == ThemeMode.BLACK -> Black
        dark -> Dark
        else -> Light
    }
    val extra = ExtraColors(
        capture = Brand.Coral,
        onCapture = Brand.Teal,
        money = if (dark) Brand.Paper else Brand.Ink,
        glow = if (dark) Color(0x55FE6B49) else Color(0x40FE6B49),
        isDark = dark
    )
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
