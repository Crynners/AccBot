package com.accbot.dca.presentation.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// CompositionLocal for sandbox mode - accessible throughout the app
val LocalSandboxMode = staticCompositionLocalOf { false }

// AccBot color palette
val Primary = Color(0xFF4ECCA3)
val PrimaryVariant = Color(0xFF3BA67D)
val Secondary = Color(0xFF0F3460)
val Background = Color(0xFF16213E)
val Surface = Color(0xFF1A1A2E)
val SurfaceVariant = Color(0xFF0F3460)
val OnPrimary = Color(0xFF1A1A2E)
val OnSecondary = Color(0xFFFFFFFF)
val OnBackground = Color(0xFFFFFFFF)
val OnSurface = Color(0xFFFFFFFF)
val OnSurfaceVariant = Color(0xFFA0A0A0)
val Error = Color(0xFFE94560)
val Success = Color(0xFF4ECCA3)
val Warning = Color(0xFFFFA726) // Orange for sandbox mode indicators

// Sandbox color palette (orange instead of green)
val SandboxPrimary = Color(0xFFFFA726)
val SandboxPrimaryVariant = Color(0xFFE65100)
val SandboxSuccess = Color(0xFFFFA726)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A2E),
    surface = Color.White,
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF666666),
    error = Error,
    onError = Color.White
)

// Sandbox dark color scheme (orange theme)
private val SandboxDarkColorScheme = darkColorScheme(
    primary = SandboxPrimary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = Color.White
)

// Sandbox light color scheme (orange theme)
private val SandboxLightColorScheme = lightColorScheme(
    primary = SandboxPrimary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = OnSecondary,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1A1A2E),
    surface = Color.White,
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = Color(0xFF666666),
    error = Error,
    onError = Color.White
)

@Composable
fun AccBotTheme(
    darkTheme: Boolean = true, // Default to dark theme for AccBot
    isSandboxMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isSandboxMode && darkTheme -> SandboxDarkColorScheme
        isSandboxMode && !darkTheme -> SandboxLightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSandboxMode provides isSandboxMode) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}

// ============================================
// Theme-aware color helpers for sandbox mode
// ============================================

/**
 * Returns the appropriate accent/primary color based on sandbox mode.
 * Use this instead of hardcoded `Primary` constant.
 */
@Composable
fun accentColor(): Color = if (LocalSandboxMode.current) SandboxPrimary else Primary

/**
 * Returns the appropriate success color based on sandbox mode.
 * Use this instead of hardcoded `Success` constant.
 */
@Composable
fun successColor(): Color = if (LocalSandboxMode.current) SandboxSuccess else Success
