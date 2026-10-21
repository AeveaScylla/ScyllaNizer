package com.better.scyllanizer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GlossyDarkColors = darkColorScheme(
    primary = Color(0xFF06B6D4), 
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF164E63), 
    onPrimaryContainer = Color(0xFFCFFAFE), 
    background = Color(0xFF09090B), 
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF18181B), 
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF27272A), 
    onSurfaceVariant = Color(0xFFA1A1AA), 
    outline = Color(0xFF3F3F46), 
    outlineVariant = Color(0xFF27272A), 
    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun FileFlowTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GlossyDarkColors, content = content)
}
