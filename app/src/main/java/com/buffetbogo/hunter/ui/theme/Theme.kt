package com.buffetbogo.hunter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The app's palette: deep navy surfaces with a single warm orange accent, matching the design.
 * The screen is dark by design, so there is no light variant.
 */
object BuffetColors {
    val Background = Color(0xFF0A101C)
    val Surface = Color(0xFF121B2A)
    val SurfaceHigh = Color(0xFF182234)
    val Chip = Color(0xFF161F2E)
    val Border = Color(0xFF223049)
    val Accent = Color(0xFFF97316)
    val AccentSoft = Color(0xFF2A1A0C)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF94A3B8)
    val Green = Color(0xFF22C55E)
    val Star = Color(0xFFFFA726)
    val Scrim = Color(0xFF0A101C)
}

private val BuffetScheme = darkColorScheme(
    primary = BuffetColors.Accent,
    onPrimary = Color.White,
    primaryContainer = BuffetColors.AccentSoft,
    onPrimaryContainer = Color(0xFFFFC08A),
    secondary = BuffetColors.TextSecondary,
    onSecondary = BuffetColors.Background,
    secondaryContainer = BuffetColors.SurfaceHigh,
    onSecondaryContainer = BuffetColors.TextPrimary,
    tertiary = BuffetColors.Green,
    onTertiary = Color(0xFF052E16),
    background = BuffetColors.Background,
    onBackground = BuffetColors.TextPrimary,
    surface = BuffetColors.Surface,
    onSurface = BuffetColors.TextPrimary,
    surfaceVariant = BuffetColors.Chip,
    onSurfaceVariant = BuffetColors.TextSecondary,
    outline = Color(0xFF3A4A63),
    outlineVariant = BuffetColors.Border,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
)

private val BuffetTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 29.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)

@Composable
fun BuffetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BuffetScheme,
        typography = BuffetTypography,
        content = content,
    )
}
