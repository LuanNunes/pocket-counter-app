package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * Single-line [Text] that shrinks its font until the content fits the available width, so long
 * values (e.g. currency) scale down instead of clipping. Floors at ~12sp.
 *
 * The shrink state is keyed on the measured width as well as the text/style, so widening the
 * container (rotation, multi-window) resets to the base size and re-fits — without the key it would
 * stay permanently shrunk after the first narrow layout.
 */
@Composable
fun AutoSizeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        var scaled by remember(text, style, maxWidth) { mutableStateOf(style) }
        Text(
            text = text,
            style = scaled,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier.clipToBounds(),
            onTextLayout = { result ->
                if (result.didOverflowWidth && scaled.fontSize > 12.sp) {
                    scaled = scaled.copy(fontSize = scaled.fontSize * 0.92f)
                }
            },
        )
    }
}
