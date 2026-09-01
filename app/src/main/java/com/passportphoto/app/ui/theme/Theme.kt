package com.passportphoto.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = PassportWhite,
    primaryContainer = PrimaryLight,
    secondary = AccentGreen,
    onSecondary = PassportWhite,
    background = SurfaceLight,
    onBackground = OnSurfaceDark,
    surface = PassportWhite,
    onSurface = OnSurfaceDark,
    error = AccentRed,
    onError = PassportWhite
)

@Composable
fun PassportPhotoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AppTypography,
        content = content
    )
}
