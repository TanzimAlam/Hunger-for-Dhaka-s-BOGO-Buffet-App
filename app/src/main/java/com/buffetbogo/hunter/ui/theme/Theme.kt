package com.buffetbogo.hunter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The palette is sampled from the app's own emblem and from the table it advertises: the cream
 * paper of the ring is the page, white is every card, the steel-blue disc is the primary colour,
 * and the clay of a curry pot marks meal times and anything running out. Bottle green — the green
 * of the national flag — means live and still-available. The look is deliberately light: a Dhaka
 * buffet list is read outdoors in daylight, so most of the screen is white paper, ink and a
 * single blue accent rather than a dark theme.
 */
object BuffetColors {
    /** Warm paper white behind every card. */
    val Background = Color(0xFFFCF8F0)
    /** Cards, sheets and fields are pure white. */
    val Surface = Color(0xFFFFFFFF)
    /** Cream inset rows inside a white card. */
    val SurfaceHigh = Color(0xFFF8F2E5)
    val Chip = Color(0xFFFFFFFF)
    val Border = Color(0xFFE8DDC9)
    /** The steel-blue disc behind the emblem's host. */
    val Accent = Color(0xFF2E6391)
    val OnAccent = Color(0xFFFFFFFF)
    val AccentSoft = Color(0xFFE8F0F8)
    /** Ink, as in the emblem's outlines. */
    val TextPrimary = Color(0xFF1B2430)
    val TextSecondary = Color(0xFF6E6A5F)
    /** Bottle green of the national flag: live offers, days remaining. */
    val Green = Color(0xFF0E6B4E)
    val OnGreen = Color(0xFFFFFFFF)
    val GreenSoft = Color(0xFFE7F2EC)
    /** Terracotta of the curry bowls and the chillies: meal times, ending soon. */
    val Clay = Color(0xFFB4532F)
    val OnClay = Color(0xFFFFFFFF)
    val ClaySoft = Color(0xFFFBEDE5)
    /** Saffron of the biryani: star ratings and "typical" prices. */
    val Star = Color(0xFFE0B05C)
    val Amber = Color(0xFF8A5C10)
    val AmberSoft = Color(0xFFFDF3DE)
    val Slate = Color(0xFF5D7486)
    /** Ink scrim used under text laid over a photo. */
    val Scrim = Color(0xFF111A24)
}

/**
 * Temporary eye-catcher: a scatter of colourful food doodles behind the feed and Settings.
 * Flip to false for the plain cream page again — nothing else in the app depends on it.
 */
const val PlayfulFoodBackdrop = true

private val BuffetScheme = lightColorScheme(
    primary = BuffetColors.Accent,
    onPrimary = BuffetColors.OnAccent,
    primaryContainer = BuffetColors.AccentSoft,
    onPrimaryContainer = BuffetColors.Accent,
    secondary = BuffetColors.Clay,
    onSecondary = BuffetColors.OnClay,
    secondaryContainer = BuffetColors.ClaySoft,
    onSecondaryContainer = BuffetColors.Clay,
    tertiary = BuffetColors.Green,
    onTertiary = BuffetColors.OnGreen,
    background = BuffetColors.Background,
    onBackground = BuffetColors.TextPrimary,
    surface = BuffetColors.Surface,
    onSurface = BuffetColors.TextPrimary,
    surfaceVariant = BuffetColors.SurfaceHigh,
    onSurfaceVariant = BuffetColors.TextSecondary,
    outline = Color(0xFFC9BDA6),
    outlineVariant = BuffetColors.Border,
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
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
