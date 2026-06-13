package com.resolveprogramming.pocketcounter.ui.contextos

/** The curated context-color swatches (client convention; the backend stores free hex). */
object CuratedPalette {
    val argb: List<Long> = listOf(
        0xFF7B5BF5, 0xFF0E9BB0, 0xFF23A268, 0xFFDD9627, 0xFFE25555,
        0xFFE0529C, 0xFFB558D6, 0xFF5566EF, 0xFF8A9B2A, 0xFFE07B3C,
    )

    val default: Long get() = argb.first()
}
