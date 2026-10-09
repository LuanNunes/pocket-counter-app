package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.theme.dashedBorder

enum class PocketChipVariant { DEFAULT, ON, ADD, WARN, INCOME, EXPENSE }

private val ChipHeight = 32.dp

@Composable
fun PocketChip(
    label: String,
    modifier: Modifier = Modifier,
    variant: PocketChipVariant = PocketChipVariant.DEFAULT,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = PocketTheme.colors
    val reducedMotion = LocalReducedMotion.current

    val backgroundColor = when (variant) {
        PocketChipVariant.DEFAULT, PocketChipVariant.ADD -> colors.surface2
        PocketChipVariant.ON -> colors.accent
        PocketChipVariant.WARN -> colors.warnBg
        PocketChipVariant.INCOME -> colors.incomeBg
        PocketChipVariant.EXPENSE -> colors.expenseBg
    }
    val contentColor = when (variant) {
        PocketChipVariant.DEFAULT -> colors.text2
        PocketChipVariant.ON -> colors.accentInk
        PocketChipVariant.ADD -> colors.text3
        PocketChipVariant.WARN -> colors.warn
        // income on incomeBg is 3.99:1 in light; becomes colors.income once the token is regenerated.
        PocketChipVariant.INCOME -> colors.text2
        PocketChipVariant.EXPENSE -> colors.expense
    }
    val borderColor = when (variant) {
        PocketChipVariant.DEFAULT -> colors.line
        PocketChipVariant.ON -> colors.accent
        PocketChipVariant.ADD -> colors.line
        PocketChipVariant.WARN, PocketChipVariant.INCOME, PocketChipVariant.EXPENSE -> Color.Transparent
    }
    val fontWeight = FontWeight.SemiBold
        .takeIf { variant == PocketChipVariant.INCOME || variant == PocketChipVariant.EXPENSE }
    val dashedBorder = variant == PocketChipVariant.ADD

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = 0.985f.takeIf { pressed && !reducedMotion } ?: 1f

    val chipModifier = Modifier
        .scale(scale)
        .height(ChipHeight)
        .then(
            run {
                if (dashedBorder) return@run Modifier.dashedBorder(borderColor, ChipHeight / 2)
                Modifier.border(1.dp, borderColor, PocketTheme.shapes.pill)
            }
        )
        .background(backgroundColor, PocketTheme.shapes.pill)
        .padding(horizontal = 12.dp)

    val content: @Composable () -> Unit = {
        Row(
            modifier = chipModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            }
            Text(
                text = label,
                style = PocketTheme.typography.bodySm,
                color = contentColor,
                fontWeight = fontWeight,
            )
        }
    }

    val pressIndication = LocalIndication.current.takeIf { reducedMotion }

    if (onClick != null) {
        Box(
            modifier = modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .selectable(
                    selected = variant == PocketChipVariant.ON,
                    interactionSource = interactionSource,
                    indication = pressIndication,
                    onClick = onClick,
                )
                .semantics { role = Role.Button },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
    if (onClick == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            content()
        }
    }
}
