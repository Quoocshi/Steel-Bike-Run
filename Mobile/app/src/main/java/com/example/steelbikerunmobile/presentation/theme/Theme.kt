package com.example.steelbikerunmobile.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ── Customer: Light mode — safe green + trust blue ────────────────────────────
private val CustomerColorScheme = lightColorScheme(
    primary          = CustomerPrimary,
    onPrimary        = CustomerOnPrimary,
    primaryContainer = CustomerPrimary.copy(alpha = 0.15f),
    onPrimaryContainer = CustomerPrimaryDark,
    secondary        = CustomerSecondary,
    onSecondary      = CustomerOnSecondary,
    secondaryContainer = CustomerSecondary.copy(alpha = 0.15f),
    onSecondaryContainer = CustomerSecondaryDark,
    background       = CustomerBackground,
    onBackground     = Neutral900,
    surface          = CustomerSurface,
    onSurface        = Neutral900,
    surfaceVariant   = Neutral100,
    onSurfaceVariant = Neutral600,
    outline          = CustomerOutline,
    error            = ErrorRed,
    onError          = CustomerOnPrimary,
)

// ── Driver: Dark mode — dynamic orange on near-black ─────────────────────────
private val DriverColorScheme = darkColorScheme(
    primary          = DriverPrimary,
    onPrimary        = DriverOnPrimary,
    primaryContainer = DriverPrimary.copy(alpha = 0.20f),
    onPrimaryContainer = DriverPrimaryDark,
    secondary        = CustomerSecondary,
    onSecondary      = DriverOnPrimary,
    background       = DriverBackground,
    onBackground     = DriverOnBackground,
    surface          = DriverSurface,
    onSurface        = DriverOnSurface,
    surfaceVariant   = DriverSurfaceVariant,
    onSurfaceVariant = Neutral400,
    outline          = DriverOutline,
    error            = ErrorRed,
    onError          = DriverOnPrimary,
)

enum class AppThemeMode { CUSTOMER, DRIVER }

@Composable
fun SteelBikeTheme(
    mode: AppThemeMode = AppThemeMode.CUSTOMER,
    content: @Composable () -> Unit
) {
    val colorScheme = when (mode) {
        AppThemeMode.CUSTOMER -> CustomerColorScheme
        AppThemeMode.DRIVER   -> DriverColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = SteelBikeTypography,
        shapes      = SteelBikeShapes,
        content     = content
    )
}
