package com.forge.workshop.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/** Access the forge palette inside composables: `forgeColors.ember`, `forgeColors.tool`, ... */
val forgeColors: ForgeColors
    @Composable @ReadOnlyComposable
    get() = LocalForgeColors.current

/**
 * Wraps content in the forge visual system: Material3 color scheme derived from the palette plus
 * the extended [ForgeColors] via CompositionLocal. Both themes are first-class.
 */
@Composable
fun ForgeTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (dark) ForgeDarkColors else ForgeLightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.ember,
            background = colors.ground,
            surface = colors.surface1,
            onBackground = colors.ink,
            onSurface = colors.ink,
        )
    } else {
        lightColorScheme(
            primary = colors.ember,
            background = colors.ground,
            surface = colors.surface1,
            onBackground = colors.ink,
            onSurface = colors.ink,
        )
    }
    CompositionLocalProvider(LocalForgeColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
