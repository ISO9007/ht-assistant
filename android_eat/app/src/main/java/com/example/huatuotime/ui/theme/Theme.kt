package com.example.huatuotime.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = CareMint,
    secondary = CareAmber,
    tertiary = CareRed,
    background = CareInk,
    surface = CareGreenDark,
    onPrimary = CareInk,
    onSecondary = CareInk,
    onTertiary = CareSurface,
    onBackground = CareSurface,
    onSurface = CareSurface
)

private val LightColorScheme = lightColorScheme(
    primary = CareGreen,
    secondary = CareAmber,
    tertiary = CareRed,
    background = CareSurface,
    surface = CareSurface,
    onPrimary = CareSurface,
    onSecondary = CareInk,
    onTertiary = CareSurface,
    onBackground = CareInk,
    onSurface = CareInk
)

@Composable
fun HuaTuoTimeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
