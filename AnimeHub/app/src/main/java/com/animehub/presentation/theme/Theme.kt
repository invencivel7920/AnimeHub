package com.animehub.presentation.theme

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
import com.animehub.domain.model.AppSettings
import com.animehub.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    secondary = Color(0xFF8A5CF6),
    tertiary = Color(0xFFD6468A),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B0FF),
    secondary = Color(0xFFD0BCFF),
    tertiary = Color(0xFFFFA8D0),
)

@Composable
fun AnimeHubTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
