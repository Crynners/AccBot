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
import androidx.compose.ui.graphics.luminance
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
    primaryContainer = Color(0xFF1F4E40),       // dark teal - matches AccBot palette
    onPrimaryContainer = Color(0xFFA8E5CD),
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = Color(0xFF142B4D),     // slightly darker variant of Secondary
    onSecondaryContainer = Color(0xFFB0CCEE),
    tertiary = Warning,
    onTertiary = Color(0xFF1A1A2E),
    tertiaryContainer = Color(0xFF553300),      // dark amber for warning surfaces
    onTertiaryContainer = Color(0xFFFFD194),
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFF601824),
    onErrorContainer = Color(0xFFFFB4B4)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E9B7B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC2EAD9),
    onPrimaryContainer = Color(0xFF0A3A28),
    secondary = Color(0xFF4A6E62),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE8DD),
    onSecondaryContainer = Color(0xFF06201A),
    background = Color(0xFFF8FBF9),
    onBackground = Color(0xFF1A1A2E),
    surface = Color(0xFFEDF5F0),
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFD4E5DE),
    onSurfaceVariant = Color(0xFF4A5D55),
    surfaceTint = Color(0xFF2E9B7B),
    outline = Color(0xFF8FA99E),
    outlineVariant = Color(0xFFC0D5CB),
    surfaceContainerLowest = Color(0xFFFAFCFB),
    surfaceContainerLow = Color(0xFFF5FAF7),
    surfaceContainer = Color(0xFFF0F6F2),
    surfaceContainerHigh = Color(0xFFEAF2ED),
    surfaceContainerHighest = Color(0xFFE4EDE8),
    error = Error,
    onError = Color.White
)

// Sandbox dark color scheme (orange theme)
private val SandboxDarkColorScheme = darkColorScheme(
    primary = SandboxPrimary,
    onPrimary = OnPrimary,
    primaryContainer = Color(0xFF553300),       // dark amber for sandbox primary
    onPrimaryContainer = Color(0xFFFFD194),
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = Color(0xFF142B4D),
    onSecondaryContainer = Color(0xFFB0CCEE),
    tertiary = Warning,
    onTertiary = Color(0xFF1A1A2E),
    tertiaryContainer = Color(0xFF553300),
    onTertiaryContainer = Color(0xFFFFD194),
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = Color.White,
    errorContainer = Color(0xFF601824),
    onErrorContainer = Color(0xFFFFB4B4)
)

// Sandbox light color scheme (orange theme)
private val SandboxLightColorScheme = lightColorScheme(
    primary = Color(0xFFE68A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB0),
    onPrimaryContainer = Color(0xFF3D2600),
    secondary = Color(0xFF6E5E50),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E0CC),
    onSecondaryContainer = Color(0xFF2A1B0C),
    background = Color(0xFFFFFCF8),
    onBackground = Color(0xFF1A1A2E),
    surface = Color(0xFFF5EDE2),
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFF0DCC8),
    onSurfaceVariant = Color(0xFF5C4E42),
    surfaceTint = Color(0xFFE68A00),
    outline = Color(0xFFA89585),
    outlineVariant = Color(0xFFD4C4B4),
    surfaceContainerLowest = Color(0xFFFFFCF8),
    surfaceContainerLow = Color(0xFFFFF9F2),
    surfaceContainer = Color(0xFFFFF4E8),
    surfaceContainerHigh = Color(0xFFFFF0E0),
    surfaceContainerHighest = Color(0xFFFFEBD8),
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
            // Note: window.statusBarColor / navigationBarColor are deprecated in API 35
            // and ignored when targeting Android 15+. Edge-to-edge is enabled in
            // MainActivity via enableEdgeToEdge(); we only adjust the icon appearance
            // (light/dark) here to match the active theme.
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
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
 * Returns the appropriate accent/primary color based on sandbox mode and light/dark theme.
 * Use this instead of hardcoded `Primary` constant.
 */
@Composable
fun accentColor(): Color = MaterialTheme.colorScheme.primary

/**
 * Returns the appropriate success color based on sandbox mode and light/dark theme.
 * Distinct from accentColor() so success states (positive ROI, goals) are visually
 * distinguishable from primary UI accents.
 */
@Composable
fun successColor(): Color = if (LocalSandboxMode.current) {
    MaterialTheme.colorScheme.primary // Orange in sandbox
} else {
    if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
        Color(0xFF2E7D32) // Material Green 800 for light theme
    else
        Success // Original green for dark theme
}
