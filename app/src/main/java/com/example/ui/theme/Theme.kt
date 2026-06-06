package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val CosmicColorScheme = darkColorScheme(
    primary = CosmicPrimaryHex,
    secondary = CosmicSecondaryHex,
    tertiary = CosmicTertiaryHex,
    background = CosmicSlateBg,
    surface = CosmicSurface,
    surfaceVariant = CosmicSurfaceVariant,
    onBackground = CosmicTextNormal,
    onSurface = CosmicTextNormal,
    onPrimary = CosmicTextOnPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force gorgeous dark mode design for optimal viewing
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CosmicColorScheme,
        typography = Typography,
        content = content
    )
}
