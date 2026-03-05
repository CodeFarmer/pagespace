package io.gluth.pagespace.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4682C8),
    secondary = Color(0xFFE6781E),
    background = Color(0xFF121423),
    surface = Color(0xFF1A1C30),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFDCE1F5),
    onSurface = Color(0xFFDCE1F5)
)

@Composable
fun PageSpaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
