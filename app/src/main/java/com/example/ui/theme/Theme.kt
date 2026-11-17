package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val JarvisColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = VoidBlack,
    primaryContainer = HoloPanelElevated,
    onPrimaryContainer = CoreWhiteCyan,
    secondary = ArcBlue,
    onSecondary = IceWhite,
    secondaryContainer = HoloPanel,
    onSecondaryContainer = ElectricAzure,
    tertiary = HudAmber,
    onTertiary = VoidBlack,
    background = VoidBlack,
    onBackground = IceWhite,
    surface = DeepObsidian,
    onSurface = IceWhite,
    surfaceVariant = HoloPanel,
    onSurfaceVariant = MutedSteel,
    surfaceContainerLowest = VoidBlack,
    surfaceContainerLow = DeepObsidian,
    surfaceContainer = HoloPanel,
    surfaceContainerHigh = HoloPanelElevated,
    surfaceContainerHighest = ColorTokens.PanelBright,
    outline = HoloBorder,
    outlineVariant = DimSlate,
    error = HudCrimson,
    onError = IceWhite
)

private object ColorTokens {
    val PanelBright = androidx.compose.ui.graphics.Color(0xFF152A4E)
}

val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
