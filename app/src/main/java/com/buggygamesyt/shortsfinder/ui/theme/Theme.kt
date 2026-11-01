package com.buggygamesyt.shortsfinder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BugGreen = Color(0xFF8BE36B)
val Amber = Color(0xFFFFC857)
val Sky = Color(0xFF6EC8FF)

private val DarkScheme = darkColorScheme(
    primary = BugGreen,
    onPrimary = Color(0xFF0B2000),
    primaryContainer = Color(0xFF1F3A16),
    onPrimaryContainer = Color(0xFFCBF5B8),
    secondary = Amber,
    onSecondary = Color(0xFF2A1E00),
    tertiary = Sky,
    onTertiary = Color(0xFF00243A),
    background = Color(0xFF0F1113),
    onBackground = Color(0xFFE6E8E3),
    surface = Color(0xFF15181B),
    onSurface = Color(0xFFE6E8E3),
    surfaceVariant = Color(0xFF23282C),
    onSurfaceVariant = Color(0xFFB9C0B5),
    surfaceContainer = Color(0xFF1A1E21),
    surfaceContainerHigh = Color(0xFF1F2427),
    outline = Color(0xFF5A635A),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0000),
)

@Composable
fun ShortsFinderTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
