package com.buggygamesyt.shortsfinder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Brand colors, used when the phone has no Material You palette (Android 11 and older).
val BugGreen = Color(0xFF8BE36B)
val Amber = Color(0xFFFFC857)
val Sky = Color(0xFF6EC8FF)

private val DarkFallback = darkColorScheme(
    primary = BugGreen,
    onPrimary = Color(0xFF0B2000),
    primaryContainer = Color(0xFF1F3A16),
    onPrimaryContainer = Color(0xFFCBF5B8),
    secondary = Amber,
    onSecondary = Color(0xFF2A1E00),
    secondaryContainer = Color(0xFF3D2F00),
    onSecondaryContainer = Color(0xFFFFE08F),
    tertiary = Sky,
    onTertiary = Color(0xFF00243A),
    tertiaryContainer = Color(0xFF003A5C),
    onTertiaryContainer = Color(0xFFCDE6FF),
    background = Color(0xFF0F1113),
    onBackground = Color(0xFFE6E8E3),
    surface = Color(0xFF0F1113),
    onSurface = Color(0xFFE6E8E3),
    surfaceVariant = Color(0xFF23282C),
    onSurfaceVariant = Color(0xFFB9C0B5),
    surfaceContainerLowest = Color(0xFF0B0D0E),
    surfaceContainerLow = Color(0xFF15181B),
    surfaceContainer = Color(0xFF1A1E21),
    surfaceContainerHigh = Color(0xFF1F2427),
    surfaceContainerHighest = Color(0xFF282E31),
    outline = Color(0xFF5A635A),
    outlineVariant = Color(0xFF3A413A),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0000),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightFallback = lightColorScheme(
    primary = Color(0xFF2E6B1C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F09C),
    onPrimaryContainer = Color(0xFF062100),
    secondary = Color(0xFF735B00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE08F),
    onSecondaryContainer = Color(0xFF241A00),
    tertiary = Color(0xFF00658F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC7E7FF),
    onTertiaryContainer = Color(0xFF001E2E),
    background = Color(0xFFF7F9F2),
    onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFF7F9F2),
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFDFE4D7),
    onSurfaceVariant = Color(0xFF43483F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4EC),
    surfaceContainer = Color(0xFFEBEFE6),
    surfaceContainerHigh = Color(0xFFE6E9E0),
    surfaceContainerHighest = Color(0xFFE0E4DB),
    outline = Color(0xFF73796E),
    outlineVariant = Color(0xFFC3C8BC),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Material You on Android 12+, follows the system dark/light setting. */
@Composable
fun YTShortsMakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkFallback
        else -> LightFallback
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}
