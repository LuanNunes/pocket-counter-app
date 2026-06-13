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

@Composable
fun PocketBadge(
    text: String,
    variant: PocketBadgeVariant,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = PocketTheme.colors
    val (backgroundColor, contentColor) = when (variant) {
        PocketBadgeVariant.WARN -> colors.warnBg to colors.warn
        PocketBadgeVariant.INCOME -> colors.incomeBg to colors.income
        PocketBadgeVariant.EXPENSE -> colors.expenseBg to colors.expense
        PocketBadgeVariant.ACCENT -> colors.accentBg to colors.accent
        PocketBadgeVariant.SOFT -> colors.surface2 to colors.text2
    }

    Row(
        modifier = modifier
            .height(22.dp)
            .background(backgroundColor, PocketTheme.shapes.pill)
            .padding(horizontal = 8.dp),
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
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.02f.em,
            textAlign = TextAlign.Center,
        )
    }
}
