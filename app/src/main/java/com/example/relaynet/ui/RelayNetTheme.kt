package com.example.relaynet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00D4AA),
    onPrimary = Color(0xFF001F18),
    primaryContainer = Color(0xFF005140),
    onPrimaryContainer = Color(0xFF9CF6DF),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFFF3B30),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9E0B00),
    onTertiaryContainer = Color(0xFFFFDAD4),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF0),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF0),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFFC9D1D9)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006A55),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF6DF),
    onPrimaryContainer = Color(0xFF002018),
    secondary = Color(0xFF00695C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFFC00000),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD4),
    onTertiaryContainer = Color(0xFF410000),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF43474E)
)

@Composable
fun RelayNetTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
