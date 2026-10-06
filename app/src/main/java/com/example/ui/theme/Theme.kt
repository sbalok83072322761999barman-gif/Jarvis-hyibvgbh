package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisDarkColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisObsidian,
    primaryContainer = JarvisElevatedCard,
    onPrimaryContainer = JarvisCyan,
    secondary = JarvisViolet,
    onSecondary = Color.White,
    secondaryContainer = JarvisSurfaceVariant,
    onSecondaryContainer = JarvisTextPrimary,
    tertiary = JarvisEmerald,
    onTertiary = JarvisObsidian,
    tertiaryContainer = Color(0xFF0A2E24),
    onTertiaryContainer = JarvisEmerald,
    error = JarvisCrimson,
    onError = Color.White,
    errorContainer = Color(0xFF3B0A18),
    onErrorContainer = Color(0xFFFF8A9F),
    background = JarvisObsidian,
    onBackground = JarvisTextPrimary,
    surface = JarvisGlassSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisElevatedCard,
    onSurfaceVariant = JarvisTextSecondary,
    outline = JarvisBorderCyan
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = Typography,
        content = content
    )
}
