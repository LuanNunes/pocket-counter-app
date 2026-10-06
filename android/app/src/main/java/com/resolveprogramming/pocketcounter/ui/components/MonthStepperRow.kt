package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/** A centered "‹ Mês Ano ›" stepper used to page the viewed month on month-scoped screens. */
@Composable
fun MonthStepperRow(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Mês anterior",
            onClick = onPrev,
        )
        Text(
            text = label.replaceFirstChar { it.uppercase() },
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.text,
        )
        StepperButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Próximo mês",
            onClick = onNext,
        )
    }
}

@Composable
private fun StepperButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.icon)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.icon)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = PocketTheme.colors.text2,
        )
    }
}
