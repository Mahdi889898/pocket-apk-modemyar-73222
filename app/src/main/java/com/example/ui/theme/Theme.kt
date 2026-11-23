package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ColorElevatedHighest = androidx.compose.ui.graphics.Color(0xFF243752)

private val ModemYarDarkColorScheme = darkColorScheme(
    primary = SignalEmerald,
    onPrimary = OnSignalEmerald,
    primaryContainer = SignalEmeraldContainer,
    onPrimaryContainer = SignalEmerald,
    secondary = CyberCyan,
    onSecondary = OnCyberCyan,
    secondaryContainer = CyberCyanContainer,
    onSecondaryContainer = CyberCyan,
    tertiary = StatusWarning,
    onTertiary = ObsidianBg,
    tertiaryContainer = StatusWarningContainer,
    onTertiaryContainer = StatusWarning,
    error = StatusFault,
    onError = TextPrimary,
    errorContainer = StatusFaultContainer,
    onErrorContainer = StatusFault,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateSurfaceVariant,
    surfaceContainerHigh = SlateSurfaceElevated,
    surfaceContainerHighest = ColorElevatedHighest,
    outline = SlateOutline,
    outlineVariant = SlateOutlineSubtle
)

val ModemYarShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun ModemYarTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ModemYarDarkColorScheme,
        typography = ModemYarTypography,
        shapes = ModemYarShapes,
        content = content
    )
}
