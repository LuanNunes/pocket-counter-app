package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.theme.pressScale

enum class PocketButtonVariant { PRIMARY, GHOST, SOFT }

enum class PocketButtonSize { DEFAULT, SMALL }

/**
 * Shared button matching the prototype `.btn` family.
 * Primary = accent fill, Ghost = transparent, Soft = surface-2 with border.
 */
@Composable
fun PocketButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PocketButtonVariant = PocketButtonVariant.PRIMARY,
    size: PocketButtonSize = PocketButtonSize.DEFAULT,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
) {
    val colors = PocketTheme.colors
    val shape = PocketTheme.shapes.chip
    // Disabled uses a muted-but-legible surface/text pair instead of a low alpha, which on the
    // dark theme would fade the accent fill + near-black ink into the background (illegible CTA).
    val container = when {
        variant == PocketButtonVariant.GHOST -> Color.Transparent
        !enabled -> colors.surface2
        variant == PocketButtonVariant.PRIMARY -> colors.accent
        else -> colors.surface2
    }
    val contentColor = when {
        !enabled -> colors.text3
        variant == PocketButtonVariant.PRIMARY -> colors.accentInk
        else -> colors.text
    }
    val border: BorderStroke? = BorderStroke(1.dp, colors.line)
        .takeIf { variant == PocketButtonVariant.SOFT }
    val minHeight = 48.dp.takeIf { size != PocketButtonSize.SMALL } ?: 36.dp
    val hPad = 18.dp.takeIf { size != PocketButtonSize.SMALL } ?: 14.dp
    val textStyle = run {
        if (size == PocketButtonSize.SMALL) {
            return@run PocketTheme.typography.bodySm.copy(
                fontWeight = PocketTheme.typography.button.fontWeight,
            )
        }
        PocketTheme.typography.button
    }

    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .then(run { if (fillMaxWidth) return@run Modifier.fillMaxWidth(); Modifier })
            .heightIn(min = minHeight)
            .pressScale(interaction)
            .clip(shape)
            .then(run { if (border != null) return@run Modifier.border(border, shape); Modifier })
            .background(container, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = hPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            leading?.invoke()
            ProvideTextStyle(textStyle) {
                Text(text = text, color = contentColor)
            }
        }
    }
}
