package dev.kevin.trail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Pine = Color(0xFF1B4332)
private val Moss = Color(0xFF2D6A4F)
private val Sand = Color(0xFFF3EDE2)
private val Paper = Color(0xFFFFFBF5)
private val Ink = Color(0xFF1C1917)
private val Clay = Color(0xFF9C6644)
private val Danger = Color(0xFF9B2226)

private val LightColors = lightColorScheme(
    primary = Pine,
    onPrimary = Color(0xFFF8F4EC),
    primaryContainer = Color(0xFFD8F3DC),
    onPrimaryContainer = Color(0xFF081C15),
    secondary = Clay,
    onSecondary = Color.White,
    background = Sand,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E0D6),
    error = Danger,
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF95D5B2),
    onPrimary = Color(0xFF081C15),
    primaryContainer = Moss,
    onPrimaryContainer = Color(0xFFD8F3DC),
    secondary = Color(0xFFE6C7A8),
    background = Color(0xFF10221B),
    onBackground = Color(0xFFF3EDE2),
    surface = Color(0xFF1B3028),
    onSurface = Color(0xFFF3EDE2),
    error = Color(0xFFFFB4AB),
)

@Composable
fun TrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
