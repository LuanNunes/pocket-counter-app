package com.resolveprogramming.pocketcounter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class PocketColors(
    val bg: Color,
    val bg2: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val line2: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val accent2: Color,
    val accentBg: Color,
    val accentInk: Color,
    val income: Color,
    val incomeBg: Color,
    val expense: Color,
    val expenseBg: Color,
    val warn: Color,
    val warnBg: Color,
)

// Derived from prototype/styles.css :root OKLCH tokens (converted to sRGB).
// Do not hand-tweak — regenerate from the OKLCH source if the spec changes.
val LightPocketColors = PocketColors(
    bg = Color(0xFFFAFAFC),
    bg2 = Color(0xFFF3F3F6),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFF4F5F8),
    line = Color(0xFFE4E4E8),
    line2 = Color(0xFFD6D7DB),
    text = Color(0xFF15161B),
    text2 = Color(0xFF4C4D53),
    text3 = Color(0xFF797A80),
    accent = Color(0xFF735FE9),
    accent2 = Color(0xFF8A81EF),
    accentBg = Color(0xFFEEEFFF),
    accentInk = Color(0xFFFFFFFF),
    income = Color(0xFF00884B),
    incomeBg = Color(0xFFD5F9E0),
    expense = Color(0xFFB33736),
    expenseBg = Color(0xFFFFEBE8),
    warn = Color(0xFFD58300),
    warnBg = Color(0xFFFFEECD),
)

val DarkPocketColors = PocketColors(
    bg = Color(0xFF0D0E14),
    bg2 = Color(0xFF14141A),
    surface = Color(0xFF18191F),
    surface2 = Color(0xFF1F2026),
    line = Color(0xFF28282F),
    line2 = Color(0xFF36373E),
    text = Color(0xFFF3F3F7),
    text2 = Color(0xFFB6B7BE),
    text3 = Color(0xFF7F8086),
    accent = Color(0xFF9B90FF),
    accent2 = Color(0xFFAFA9FF),
    accentBg = Color(0xFF26214E),
    accentInk = Color(0xFF0E0A18),
    income = Color(0xFF35C177),
    incomeBg = Color(0xFF062F19),
    expense = Color(0xFFF07F77),
    expenseBg = Color(0xFF3E1E1C),
    warn = Color(0xFFF2A618),
    warnBg = Color(0xFF422700),
)

val LocalPocketColors = staticCompositionLocalOf { LightPocketColors }

val LocalPocketIsDark = staticCompositionLocalOf { false }
