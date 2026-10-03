package com.resolveprogramming.pocketcounter.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

@Immutable
data class PocketShapes(
    val icon: RoundedCornerShape,
    val chip: RoundedCornerShape,
    val cta: RoundedCornerShape,
    val card: RoundedCornerShape,
    val notification: RoundedCornerShape,
    val fab: RoundedCornerShape,
    val labelPicker: RoundedCornerShape,
    val sheet: RoundedCornerShape,
    val pill: RoundedCornerShape,
)

val DefaultPocketShapes = PocketShapes(
    icon = RoundedCornerShape(10.dp),
    chip = RoundedCornerShape(12.dp),
    cta = RoundedCornerShape(16.dp),
    card = RoundedCornerShape(20.dp),
    notification = RoundedCornerShape(18.dp),
    fab = RoundedCornerShape(19.dp),
    labelPicker = RoundedCornerShape(14.dp),
    // Bottom sheets round only the top corners.
    sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    pill = RoundedCornerShape(999.dp),
)

val LocalPocketShapes = staticCompositionLocalOf { DefaultPocketShapes }
