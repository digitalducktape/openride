package dev.digitalducktape.openride.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * App-wide dark theme (PRD P0-7: near-black background, high-contrast text, single red
 * accent). There is deliberately no light variant — the bike tablet always runs this theme.
 */
private val OpenRideColorScheme = darkColorScheme(
    background = OpenRideColors.Background,
    onBackground = OpenRideColors.OnBackground,
    surface = OpenRideColors.Surface,
    onSurface = OpenRideColors.OnBackground,
    surfaceVariant = OpenRideColors.SurfaceVariant,
    onSurfaceVariant = OpenRideColors.OnSurfaceVariant,
    primary = OpenRideColors.Accent,
    onPrimary = OpenRideColors.OnBackground,
    secondary = OpenRideColors.Accent,
    onSecondary = OpenRideColors.OnBackground,
    error = OpenRideColors.Warning,
    onError = OpenRideColors.Background,
)

@Composable
fun OpenRideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OpenRideColorScheme,
        typography = OpenRideTypography,
        content = content,
    )
}
