package com.babydatalog.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Static light color scheme — warm amber/golden seed
private val LightColorScheme: ColorScheme = lightColorScheme(
    primary = Amber40,
    onPrimary = Amber99,
    primaryContainer = Amber90,
    onPrimaryContainer = Amber10,
    secondary = Peach40,
    onSecondary = Peach99,
    secondaryContainer = Peach90,
    onSecondaryContainer = Peach10,
    tertiary = Sage40,
    onTertiary = Sage99,
    tertiaryContainer = Sage90,
    onTertiaryContainer = Sage10,
    error = Error40,
    onError = Error99,
    errorContainer = Error90,
    onErrorContainer = Error10,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = NeutralVariant90,
    onSurfaceVariant = NeutralVariant30,
    outline = NeutralVariant50,
    outlineVariant = NeutralVariant80,
    scrim = Neutral10,
    inverseSurface = Neutral20,
    inverseOnSurface = Neutral95,
    inversePrimary = Amber80,
)

// Static dark color scheme
private val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = Amber80,
    onPrimary = Amber20,
    primaryContainer = Amber30,
    onPrimaryContainer = Amber90,
    secondary = Peach80,
    onSecondary = Peach20,
    secondaryContainer = Peach30,
    onSecondaryContainer = Peach90,
    tertiary = Sage80,
    onTertiary = Sage20,
    tertiaryContainer = Sage30,
    onTertiaryContainer = Sage90,
    error = Error80,
    onError = Error20,
    errorContainer = Error30,
    onErrorContainer = Error90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = NeutralVariant30,
    onSurfaceVariant = NeutralVariant80,
    outline = NeutralVariant60,
    outlineVariant = NeutralVariant30,
    scrim = Neutral10,
    inverseSurface = Neutral90,
    inverseOnSurface = Neutral20,
    inversePrimary = Amber40,
)

// Derives on/container roles for an arbitrary user-picked primary colour. Not a full
// Material-You tonal palette (that needs the material-color-utilities algorithm) — just
// enough contrast-safe roles to keep the rest of the app's static scheme looking coherent.
private fun customColorScheme(seed: Color, darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) DarkColorScheme else LightColorScheme
    val onSeed = if (seed.luminance() > 0.45f) Color.Black else Color.White

    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val containerHsv = hsv.copyOf().also { it[1] = (it[1] * 0.35f).coerceIn(0f, 1f) }
    containerHsv[2] = if (darkTheme) (containerHsv[2] * 0.45f).coerceIn(0.1f, 1f) else 0.92f
    val container = Color(android.graphics.Color.HSVToColor(containerHsv))
    val onContainer = if (container.luminance() > 0.45f) Color.Black else Color.White

    return base.copy(
        primary = seed,
        onPrimary = onSeed,
        primaryContainer = container,
        onPrimaryContainer = onContainer,
        inversePrimary = seed
    )
}

@Composable
fun BabyDataLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ (API 31+)
    dynamicColor: Boolean = true,
    // When non-null, overrides both the static and dynamic schemes with a user-chosen colour
    customPrimaryColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme: ColorScheme = when {
        customPrimaryColor != null -> customColorScheme(customPrimaryColor, darkTheme)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BabyDataLogTypography,
        shapes = BabyDataLogShapes,
        content = content
    )
}
