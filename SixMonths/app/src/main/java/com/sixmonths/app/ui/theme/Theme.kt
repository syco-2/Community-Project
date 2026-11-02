package com.sixmonths.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlueDeep,
    onPrimaryContainer = Color.White,
    secondary = AccentOrange,
    onSecondary = BackgroundBase,
    secondaryContainer = Color(0xFF56260F),
    onSecondaryContainer = Color(0xFFFFD9C7),
    tertiary = SuccessGreen,
    onTertiary = BackgroundBase,
    background = BackgroundBase,
    onBackground = TextPrimary,
    surface = BackgroundRaised,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = BackgroundBase,
    surfaceContainerLow = BackgroundRaised,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceElevated,
    surfaceContainerHighest = Color(0xFF222D3B),
    error = DangerRed,
    onError = Color.White,
    errorContainer = Color(0xFF4A1216),
    onErrorContainer = Color(0xFFFFD7D9),
    outline = BorderSubtle,
    outlineVariant = Color(0xFF1E2833),
    scrim = Color.Black,
)

// Squircle radii: nothing in the app uses the default Android corner scale.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * Colors that are not part of the Material 3 color scheme but belong to the visual
 * language (success/warning/danger, card borders, flame gradient).
 */
@Immutable
data class SixMonthsColors(
    val success: Color = SuccessGreen,
    val warning: Color = WarningAmber,
    val danger: Color = DangerRed,
    val cardBorder: Color = BorderSubtle,
    val surfaceElevated: Color = SurfaceElevated,
    val textSecondary: Color = TextSecondary,
    val textMuted: Color = TextMuted,
    val flameStart: Color = FlameOrange,
    val flameEnd: Color = FlameYellow,
)

val LocalSixMonthsColors = staticCompositionLocalOf { SixMonthsColors() }

/** Every must-have animation checks this flag (Settings -> animations on/off). */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

val MaterialTheme.sixMonthsColors: SixMonthsColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSixMonthsColors.current

val animationsEnabled: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalAnimationsEnabled.current

@Composable
fun SixMonthsTheme(
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAnimationsEnabled provides animationsEnabled,
        LocalSixMonthsColors provides SixMonthsColors(),
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
