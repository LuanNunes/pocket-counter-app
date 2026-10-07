package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.theme.pocketCardShadow

@Composable
fun PocketCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = PocketTheme.colors.surface,
    borderColor: Color? = null,
    elevated: Boolean = true,
    shape: Shape = PocketTheme.shapes.card,
    contentPadding: PaddingValues = PaddingValues(PocketTheme.spacing.pad),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .then(run { if (elevated) return@run Modifier.pocketCardShadow(shape); Modifier })
            .then(
                run {
                    if (borderColor != null) return@run Modifier.border(1.dp, borderColor, shape)
                    Modifier
                }
            )
            .background(backgroundColor, shape)
            // `overflow: hidden`: a row background has to stop at the card's own corners.
            .clip(shape)
            .padding(contentPadding),
    ) {
        content()
    }
}
