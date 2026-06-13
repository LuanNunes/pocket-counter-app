package com.resolveprogramming.pocketcounter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class PocketSpacing(
    val pad: Dp,
    val gap: Dp,
)

enum class PocketDensity { COMPACT, COMFORTABLE, COZY }

val CompactPocketSpacing = PocketSpacing(pad = 12.dp, gap = 8.dp)
val ComfortablePocketSpacing = PocketSpacing(pad = 16.dp, gap = 12.dp)
val CozyPocketSpacing = PocketSpacing(pad = 20.dp, gap = 16.dp)

fun spacingFor(density: PocketDensity): PocketSpacing = when (density) {
    PocketDensity.COMPACT -> CompactPocketSpacing
    PocketDensity.COMFORTABLE -> ComfortablePocketSpacing
    PocketDensity.COZY -> CozyPocketSpacing
}

val LocalPocketSpacing = staticCompositionLocalOf { ComfortablePocketSpacing }
