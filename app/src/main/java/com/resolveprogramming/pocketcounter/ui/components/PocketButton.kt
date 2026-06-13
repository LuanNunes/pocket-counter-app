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
import androidx.compose.ui.draw.alpha
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
    val container = when (variant) {
        PocketButtonVariant.PRIMARY -> colors.accent
        PocketButtonVariant.GHOST -> Color.Transparent
        PocketButtonVariant.SOFT -> colors.surface2
    }
    val contentColor = when (variant) {
        PocketButtonVariant.PRIMARY -> colors.accentInk
        PocketButtonVariant.GHOST, PocketButtonVariant.SOFT -> colors.text
    }
    val border: BorderStroke? = if (variant == PocketButtonVariant.SOFT) {
        BorderStroke(1.dp, colors.line)
    } else {
        null
    }
    val minHeight = if (size == PocketButtonSize.SMALL) 36.dp else 48.dp
    val hPad = if (size == PocketButtonSize.SMALL) 14.dp else 18.dp
    val textStyle = if (size == PocketButtonSize.SMALL) {
        PocketTheme.typography.bodySm.copy(fontWeight = PocketTheme.typography.button.fontWeight)
    } else {
        PocketTheme.typography.button
    }

    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = minHeight)
            .pressScale(interaction)
            .clip(shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .background(container, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.35f)
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
