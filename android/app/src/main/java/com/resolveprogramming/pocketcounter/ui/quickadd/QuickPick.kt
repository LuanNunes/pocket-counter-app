package com.resolveprogramming.pocketcounter.ui.quickadd

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.theme.dashedBorder
import com.resolveprogramming.pocketcounter.ui.theme.pressScale

private val PickHeight = 38.dp

/**
 * `.qa-pick`: the quick-fill pill inside an expanded summary row; [dashed] is `.qa-pick-new`. The
 * 38dp visual sits in a 48dp target box, the split
 * [com.resolveprogramming.pocketcounter.ui.components.PocketChip] already uses.
 */
@Composable
fun QuickPick(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    dashed: Boolean = false,
) {
    val colors = PocketTheme.colors
    val reducedMotion = LocalReducedMotion.current
    val interaction = remember { MutableInteractionSource() }
    val edge = colors.accent.takeIf { selected } ?: colors.line
    val border = run {
        if (dashed) return@run Modifier.dashedBorder(edge, PickHeight / 2)
        Modifier.border(1.dp, edge, PocketTheme.shapes.pill)
    }
    val fill = run {
        if (selected) return@run colors.accentBg
        // `.qa-pick-new` is transparent: the create pill is an outline, not another filled option.
        Color.Transparent.takeIf { dashed } ?: colors.surface
    }
    // accent on accentBg is 4.06:1 in light, under AA; text2 reaches 7.39. Same override as
    // PocketBadge's ACCENT. The dashed pill's accent sits on `surface` (4.62:1) and stays.
    val ink = colors.text2.takeIf { selected }
        ?: colors.accent.takeIf { dashed }
        ?: colors.text

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .pressScale(interaction)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                // Without the scale there must be something left to show the press.
                indication = LocalIndication.current.takeIf { reducedMotion },
                onClick = onClick,
            )
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = PickHeight)
                .then(border)
                .background(fill, PocketTheme.shapes.pill)
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dotColor != null) {
                Box(Modifier.size(7.dp).background(dotColor, CircleShape))
            }
            Text(
                text = label,
                style = PocketTheme.typography.bodySm,
                fontWeight = FontWeight.SemiBold.takeIf { selected } ?: FontWeight.Medium,
                color = ink,
            )
        }
    }
}
