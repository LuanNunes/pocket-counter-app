package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp

/**
 * Single-line text that shrinks its font size (down to [minFontSize]) until it fits the available
 * width, so long monetary values render in full instead of being clipped. Falls back to the base
 * [style] when the width is unbounded or the style has no concrete font size.
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 11.sp,
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val maxWidthPx = constraints.maxWidth
        val bounded = constraints.hasBoundedWidth
        val fittedStyle = remember(text, style, maxWidthPx, bounded) {
            if (!bounded || !style.fontSize.isSpecified) return@remember style
            var size = style.fontSize
            while (size.value > minFontSize.value) {
                val width = measurer.measure(
                    text = text,
                    style = style.copy(fontSize = size),
                    softWrap = false,
                    maxLines = 1,
                ).size.width
                if (width <= maxWidthPx) break
                size = (size.value - 1f).sp
            }
            style.copy(fontSize = size)
        }
        Text(
            text = text,
            style = fittedStyle,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}
