package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ElectricNeonBlue,
    onPrimary = OnPrimaryText,
    primaryContainer = SecondaryCyan,
    onPrimaryContainer = OnPrimaryText,
    secondary = SecondaryCyan,
    onSecondary = OnPrimaryText,
    background = DarkBackground,
    onBackground = TextWhite,
    surface = SlateSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkContrastDivider,
    onSurfaceVariant = TextMuted,
    error = LiveRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Forced dark theme for the cinematic, movie-theater IPTV feel!
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
