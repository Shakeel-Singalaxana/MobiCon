package io.rojx.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ClayBg,
    primaryContainer = ClaySurfaceElevated,
    onPrimaryContainer = TextPrimary,
    secondary = RacingGreen,
    onSecondary = ClayBg,
    secondaryContainer = ClaySurfaceHigh,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberWarning,
    onTertiary = ClayBg,
    background = ClayBg,
    onBackground = TextPrimary,
    surface = ClaySurface,
    onSurface = TextPrimary,
    surfaceVariant = ClaySurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = ClayBorder,
    outlineVariant = ClayBorderLight
)

@Composable
fun RojxDashboardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
