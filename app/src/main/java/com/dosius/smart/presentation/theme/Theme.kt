package com.dosius.smart.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = DosiusPurple,
    onPrimary = Color.White,
    primaryContainer = DosiusPurpleContainer,
    onPrimaryContainer = OnPurpleContainer,
    secondary = DosiusPurpleLight,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = OnLight,
    surface = LightSurface,
    onSurface = OnLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightSurfaceVariant,
)

@Composable
fun DosiusSmartTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
