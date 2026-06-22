package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CustomDarkColorScheme = darkColorScheme(
    primary = ElegantButtonBg,
    onPrimary = ElegantButtonText,
    primaryContainer = ElegantDarkCardBg,
    onPrimaryContainer = ElegantDarkText,
    secondary = ElegantStatusTextBlue,
    onSecondary = ElegantStatusBlue,
    background = ElegantDarkBg,
    onBackground = ElegantDarkText,
    surface = ElegantDarkCardBg,
    onSurface = ElegantDarkText,
    surfaceVariant = ElegantStatusBlue,
    onSurfaceVariant = ElegantStatusTextBlue,
    outline = ElegantBorderLine,
    error = HotPink,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default for premium cyber experience
    dynamicColor: Boolean = false, // Disable dynamic colors to enforce branding
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CustomDarkColorScheme,
        typography = Typography,
        content = content
    )
}
