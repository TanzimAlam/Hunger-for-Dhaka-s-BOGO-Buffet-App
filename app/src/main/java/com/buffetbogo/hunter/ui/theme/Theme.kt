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
 * The palette is sampled from the app's own emblem: warm charcoal paper, a steel-blue disc, ink
 * outlines and cream lettering. Cream is the accent, so text on it is ink — never white.
 * The screen is dark by design, so there is no light variant.
 */
object BuffetColors {
    val Background = Color(0xFF191714)
    val Surface = Color(0xFF23201C)
    val SurfaceHigh = Color(0xFF2D2924)
    val Chip = Color(0xFF272320)
    val Border = Color(0xFF3E3830)
    val Accent = Color(0xFFD9C3A0)
    val OnAccent = Color(0xFF241F18)
    val AccentSoft = Color(0xFF3A3227)
    val TextPrimary = Color(0xFFF1E9DC)
    val TextSecondary = Color(0xFFA79C8C)
    val Green = Color(0xFF9BB386)
    val OnGreen = Color(0xFF1B2416)
    val Star = Color(0xFFE3B770)
    val Slate = Color(0xFF5D7486)
    val Scrim = Color(0xFF191714)
}

private val BuffetScheme = darkColorScheme(
    primary = BuffetColors.Accent,
    onPrimary = BuffetColors.OnAccent,
    primaryContainer = BuffetColors.AccentSoft,
    onPrimaryContainer = BuffetColors.Accent,
    secondary = BuffetColors.TextSecondary,
    onSecondary = BuffetColors.Background,
    secondaryContainer = BuffetColors.SurfaceHigh,
    onSecondaryContainer = BuffetColors.TextPrimary,
    tertiary = BuffetColors.Green,
    onTertiary = BuffetColors.OnGreen,
    background = BuffetColors.Background,
    onBackground = BuffetColors.TextPrimary,
    surface = BuffetColors.Surface,
    onSurface = BuffetColors.TextPrimary,
    surfaceVariant = BuffetColors.Chip,
    onSurfaceVariant = BuffetColors.TextSecondary,
    outline = Color(0xFF574E43),
    outlineVariant = BuffetColors.Border,
    error = Color(0xFFE08B7A),
    onError = Color(0xFF2A120E),
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
