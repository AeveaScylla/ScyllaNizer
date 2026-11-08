package com.better.scyllanizer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { DARK, LIGHT }

private val DarkColors = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF04191D),
    primaryContainer = Color(0xFF0E3B44),
    onPrimaryContainer = Color(0xFFB6F3FB),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF04121F),
    background = Color(0xFF0A0A0C),
    onBackground = Color(0xFFF2F2F4),
    surface = Color(0xFF131316),
    onSurface = Color(0xFFF2F2F4),
    surfaceVariant = Color(0xFF1C1C21),
    onSurfaceVariant = Color(0xFF8A8A93),
    outline = Color(0xFF3A3A44),
    outlineVariant = Color(0xFF26262C),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0808),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFECACA),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFBE3D5),
    onPrimaryContainer = Color(0xFF7C2D12),
    secondary = Color(0xFF2563EB),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F2EC),
    onBackground = Color(0xFF211E18),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF211E18),
    surfaceVariant = Color(0xFFEFEAE0),
    onSurfaceVariant = Color(0xFF7B7266),
    outline = Color(0xFFC9C0B0),
    outlineVariant = Color(0xFFE3DCD0),
    error = Color(0xFFB91C1C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
)

@Composable
fun FileFlowTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            val light = mode == ThemeMode.LIGHT
            controller.isAppearanceLightStatusBars = light
            controller.isAppearanceLightNavigationBars = light
        }
    }
    MaterialTheme(
        colorScheme = if (mode == ThemeMode.DARK) DarkColors else LightColors,
        content = content
    )
}
