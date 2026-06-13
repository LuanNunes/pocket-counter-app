package com.resolveprogramming.pocketcounter.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class PocketThemeController {
    // TODO: persist via DataStore (M4 Configurações)
    var darkTheme: Boolean? by mutableStateOf(null)
    var density: PocketDensity by mutableStateOf(PocketDensity.COMFORTABLE)
}

@Composable
fun rememberPocketThemeController(): PocketThemeController = remember { PocketThemeController() }

val LocalPocketThemeController = staticCompositionLocalOf { PocketThemeController() }

val LocalReducedMotion = staticCompositionLocalOf { false }
