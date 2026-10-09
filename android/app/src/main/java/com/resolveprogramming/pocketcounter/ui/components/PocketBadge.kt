package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.resolveprogramming.pocketcounter.ui.theme.DmSans
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

enum class PocketBadgeVariant { WARN, INCOME, EXPENSE, ACCENT, SOFT }

/** [MICRO] is `.qa-rev-badge`: the quick-add summary rows, where a 22dp badge crowds the value. */
enum class PocketBadgeSize { DEFAULT, MICRO }

@Composable
fun PocketBadge(
    text: String,
    variant: PocketBadgeVariant,
    modifier: Modifier = Modifier,
    size: PocketBadgeSize = PocketBadgeSize.DEFAULT,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = PocketTheme.colors
    val (backgroundColor, contentColor) = when (variant) {
        // warn on warnBg is 2.6:1 in light, sub-AA for an 11sp badge; text2 reaches 7.4.
        PocketBadgeVariant.WARN -> colors.warnBg to colors.text2
        PocketBadgeVariant.INCOME -> colors.incomeBg to colors.income
        PocketBadgeVariant.EXPENSE -> colors.expenseBg to colors.expense
        // accent on accentBg is 4.06:1 in light, under AA; text2 reaches 7.39.
        PocketBadgeVariant.ACCENT -> colors.accentBg to colors.text2
        PocketBadgeVariant.SOFT -> colors.surface2 to colors.text2
    }
    val micro = size == PocketBadgeSize.MICRO

    Row(
        modifier = modifier
            .height(16.dp.takeIf { micro } ?: 22.dp)
            .background(backgroundColor, PocketTheme.shapes.pill)
            .padding(horizontal = 6.dp.takeIf { micro } ?: 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        }
        Text(
            text = text.uppercase(),
            color = contentColor,
            fontFamily = DmSans,
            fontWeight = FontWeight.Bold.takeIf { micro } ?: FontWeight.SemiBold,
            fontSize = 9.5.sp.takeIf { micro } ?: 11.sp,
            letterSpacing = 0.05f.em.takeIf { micro } ?: 0.02f.em,
            textAlign = TextAlign.Center,
        )
    }
}
