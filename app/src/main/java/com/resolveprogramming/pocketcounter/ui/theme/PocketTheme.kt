package com.resolveprogramming.pocketcounter.ui.theme

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

private fun lightScheme(colors: PocketColors) = lightColorScheme(
    primary = colors.accent,
    onPrimary = colors.accentInk,
    surface = colors.surface,
    background = colors.bg,
    onBackground = colors.text,
    onSurface = colors.text,
    outline = colors.line,
)

private fun darkScheme(colors: PocketColors) = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.accentInk,
    surface = colors.surface,
    background = colors.bg,
    onBackground = colors.text,
    onSurface = colors.text,
    outline = colors.line,
)

@Composable
fun PocketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    density: PocketDensity = PocketDensity.COMFORTABLE,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkPocketColors else LightPocketColors
    val spacing = spacingFor(density)
    val m3Scheme = if (darkTheme) darkScheme(colors) else lightScheme(colors)

    val context = LocalContext.current
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }

    CompositionLocalProvider(
        LocalPocketColors provides colors,
        LocalPocketTypography provides DefaultPocketTypography,
        LocalPocketShapes provides DefaultPocketShapes,
        LocalPocketSpacing provides spacing,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(colorScheme = m3Scheme) {
            Box(modifier = Modifier.background(PocketTheme.colors.bg)) {
                content()
            }
        }
    }
}

object PocketTheme {
    val colors: PocketColors
        @Composable get() = LocalPocketColors.current

    val typography: PocketTypography
        @Composable get() = LocalPocketTypography.current

    val shapes: PocketShapes
        @Composable get() = LocalPocketShapes.current

    val spacing: PocketSpacing
        @Composable get() = LocalPocketSpacing.current
}
