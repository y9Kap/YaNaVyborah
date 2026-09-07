package org.yanavybori.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1D5D4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2DA),
    onPrimaryContainer = Color(0xFF002019),
    secondary = Color(0xFF4B635B),
    secondaryContainer = Color(0xFFCDE9DE),
    error = Color(0xFFBA1A1A),
    background = Color(0xFFF7FBF7),
    surface = Color(0xFFF7FBF7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD6BE),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005142),
    secondary = Color(0xFFB1CCC2),
    secondaryContainer = Color(0xFF334B43),
)

@Composable
fun YaNaVyborahTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
