package de.isbacar.yolcu.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkFallback = darkColorScheme(
    primary = Color(0xFF6FD8CC),
    onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFF8DF5E8),
    tertiary = Color(0xFFFFB86E),
    background = Color(0xFF0E1415),
    surface = Color(0xFF0E1415),
)

private val LightFallback = lightColorScheme(
    primary = Color(0xFF006A62),
    primaryContainer = Color(0xFF8DF5E8),
    onPrimaryContainer = Color(0xFF00201D),
    tertiary = Color(0xFF8B5000),
)

@Composable
fun YolcuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> DarkFallback
        else -> LightFallback
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
