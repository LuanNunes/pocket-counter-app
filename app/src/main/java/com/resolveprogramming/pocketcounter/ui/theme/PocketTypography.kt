package com.resolveprogramming.pocketcounter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.resolveprogramming.pocketcounter.R

val DmSans = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold),
)

val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
    Font(R.font.geist_mono_semibold, FontWeight.SemiBold),
)

@Immutable
data class PocketTypography(
    val display: TextStyle,
    val screenH1: TextStyle,
    val stepQuestion: TextStyle,
    val button: TextStyle,
    val body: TextStyle,
    val bodySm: TextStyle,
    val bodyXs: TextStyle,
    val sectionHeader: TextStyle,
    val label: TextStyle,
    val monoDisplay: TextStyle,
    val monoBalance: TextStyle,
    val monoTotal: TextStyle,
    val monoAmountInput: TextStyle,
    val monoBody: TextStyle,
    val monoSm: TextStyle,
)

val DefaultPocketTypography = PocketTypography(
    display = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.025f).em,
    ),
    // Screen title (.screen-hd .h1) — 24/700.
    screenH1 = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.025f).em,
    ),
    stepQuestion = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.02f).em,
    ),
    button = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    body = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = (-0.01f).em,
    ),
    bodySm = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = (-0.01f).em,
    ),
    bodyXs = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
    ),
    sectionHeader = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
        letterSpacing = 0.06f.em,
    ),
    // Uppercase field/total labels (.fld-label, .mg-tot .k) — 10.5/700, wide tracking.
    // Callers uppercase the string; this style does not transform.
    label = TextStyle(
        fontFamily = DmSans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        letterSpacing = 0.07f.em,
    ),
    monoDisplay = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        letterSpacing = (-0.025f).em,
    ),
    // Hero balance value (.balance .value) — 32/600, mono tabular per the amount convention.
    monoBalance = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        letterSpacing = (-0.02f).em,
    ),
    // Card/fatura total (.cardtile .ct-total) — 26, mono SemiBold (mono has no 700 weight).
    monoTotal = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = (-0.02f).em,
    ),
    // Wizard amount input (.amount-input) — 38, mono SemiBold.
    monoAmountInput = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 38.sp,
        letterSpacing = (-0.025f).em,
    ),
    monoBody = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    monoSm = TextStyle(
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
)

val LocalPocketTypography = staticCompositionLocalOf { DefaultPocketTypography }
