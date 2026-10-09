package com.meetnotes.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Mokwa Meeting Note brand colours. */
object Brand {
    val Green = Color(0xFF0B6B4F)
    val GreenDark = Color(0xFF064532)
    val GreenLight = Color(0xFF2E9C74)
    val Gold = Color(0xFFE0A100)
    val Amber = Color(0xFFF2A900)
    val Red = Color(0xFFD93025)
    val Slate = Color(0xFF5F6B7A)
    val Blue = Color(0xFF2F6FDB)      // Word
    val ExcelGreen = Color(0xFF1E7E45) // Excel
    val PdfRed = Color(0xFFC62828)    // PDF

    /** Header gradient used on the main screens. */
    val headerGradient = Brush.linearGradient(listOf(GreenDark, Green, GreenLight))
}

private val LightColors = lightColorScheme(
    primary = Brand.Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EFE3),
    onPrimaryContainer = Color(0xFF002116),
    secondary = Color(0xFF4F6359),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2ECE6),
    onSecondaryContainer = Color(0xFF0C1F16),
    tertiary = Color(0xFF9A6A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7B0),
    onTertiaryContainer = Color(0xFF2E1F00),
    background = Color(0xFFF4F7F5),
    onBackground = Color(0xFF161D1A),
    surface = Color(0xFFF4F7F5),
    onSurface = Color(0xFF161D1A),
    surfaceVariant = Color(0xFFE1E8E4),
    onSurfaceVariant = Color(0xFF414945),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAF9),
    surfaceContainer = Color(0xFFEEF2F0),
    surfaceContainerHigh = Color(0xFFE8EDEA),
    surfaceContainerHighest = Color(0xFFE2E8E5),
    outline = Color(0xFF717975),
    outlineVariant = Color(0xFFDCE3DF),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FD8B3),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFF9BF5CE),
    secondary = Color(0xFFB4CCBF),
    secondaryContainer = Color(0xFF364B41),
    onSecondaryContainer = Color(0xFFD0E8DA),
    tertiary = Color(0xFFF5BF48),
    tertiaryContainer = Color(0xFF5C4000),
    onTertiaryContainer = Color(0xFFFFDEA0),
    background = Color(0xFF0F1513),
    surface = Color(0xFF0F1513),
    surfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1B),
    surfaceContainer = Color(0xFF1B211F),
    surfaceContainerHigh = Color(0xFF252B29),
    surfaceContainerHighest = Color(0xFF303634),
    outlineVariant = Color(0xFF3F4945),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        displayMedium = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
    )
}

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun MeetNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Off by default so the app keeps its own brand colours on every phone. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
