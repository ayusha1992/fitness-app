package com.healthcoach.ircoach.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Green & white brand palette. Fixed (no dynamic color) so the app always looks the same. */
object CoachColors {
    val Green = Color(0xFF2E7D4F)        // primary brand green
    val GreenDark = Color(0xFF1B5E37)    // pressed / headers
    val GreenLight = Color(0xFF4CAF7A)   // accents, illustrations
    val Mint = Color(0xFFDDF3E4)         // tinted containers, chips
    val MintSoft = Color(0xFFF1F9F3)     // page tint, coach bubbles
    val Outline = Color(0xFFCFE5D6)      // card borders, dividers
    val Ink = Color(0xFF14261B)          // body text
    val InkMuted = Color(0xFF5B6E62)     // secondary text
    val Amber = Color(0xFFF2A93B)        // warm accent (sun, warnings)
    val Danger = Color(0xFFC6423A)
}

private val Scheme = lightColorScheme(
    primary = CoachColors.Green,
    onPrimary = Color.White,
    primaryContainer = CoachColors.Mint,
    onPrimaryContainer = CoachColors.GreenDark,
    secondary = CoachColors.GreenLight,
    onSecondary = Color.White,
    secondaryContainer = CoachColors.MintSoft,
    onSecondaryContainer = CoachColors.GreenDark,
    tertiary = CoachColors.Amber,
    onTertiary = Color.White,
    background = Color.White,
    onBackground = CoachColors.Ink,
    surface = Color.White,
    onSurface = CoachColors.Ink,
    surfaceVariant = CoachColors.MintSoft,
    onSurfaceVariant = CoachColors.InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = CoachColors.MintSoft,
    surfaceContainerHigh = CoachColors.MintSoft,
    surfaceContainerHighest = CoachColors.Mint,
    outline = CoachColors.Outline,
    outlineVariant = CoachColors.Outline,
    error = CoachColors.Danger,
)

private val Type = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(lineHeight = 22.sp),
        labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.2.sp),
    )
}

private val ShapeSet = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, shapes = ShapeSet, content = content)
}
