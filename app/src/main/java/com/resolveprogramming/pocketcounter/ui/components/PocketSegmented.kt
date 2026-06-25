package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.theme.pocketCardShadow

/** Optional accent for the selected segment's label (mirrors the prototype `.gseg.tone`). */
enum class SegmentTone { NEUTRAL, INCOME, EXPENSE, WARN }

data class SegmentOption(
    val label: String,
    val tone: SegmentTone = SegmentTone.NEUTRAL,
)

/**
 * Inline toggle matching the prototype `.gseg` — a surface-2 track holding equal-width
 * segments; the selected one lifts onto a surface chip with a card shadow.
 * Used for Mês/Trimestre/Ano, Tabela/Cartões, Despesas/Receitas, etc.
 */
@Composable
fun PocketSegmented(
    options: List<SegmentOption>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PocketTheme.colors
    val trackShape = RoundedCornerShape(14.dp)
    val segShape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .background(colors.surface2, trackShape)
            .border(1.dp, colors.line, trackShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val labelColor = run {
                if (!selected) return@run colors.text3
                if (option.tone == SegmentTone.INCOME) return@run colors.income
                if (option.tone == SegmentTone.EXPENSE) return@run colors.expense
                if (option.tone == SegmentTone.WARN) return@run colors.warn
                colors.text
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .then(run { if (selected) return@run Modifier.pocketCardShadow(segShape); Modifier })
                    .background(colors.surface.takeIf { selected } ?: Color.Transparent, segShape)
                    .clickable(
                        interactionSource = remember(index) { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.label,
                    style = PocketTheme.typography.bodySm.copy(
                        fontWeight = PocketTheme.typography.button.fontWeight,
                    ),
                    color = labelColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
