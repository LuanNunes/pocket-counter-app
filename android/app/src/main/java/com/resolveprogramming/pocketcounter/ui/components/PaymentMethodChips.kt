package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.icon
import com.resolveprogramming.pocketcounter.ui.wizard.label

/** One payment-method option: icon + label, accent-outlined when picked. */
@Composable
fun MethodChip(
    method: PaymentMethod,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = PocketTheme.colors.accent.takeIf { isSelected } ?: PocketTheme.colors.line
    val bgColor = PocketTheme.colors.accentBg.takeIf { isSelected } ?: PocketTheme.colors.surface
    val inkColor = PocketTheme.colors.accent.takeIf { isSelected } ?: PocketTheme.colors.text

    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .border(1.5.dp, borderColor, PocketTheme.shapes.chip)
            .background(bgColor, PocketTheme.shapes.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = method.icon(),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = inkColor,
        )
        Text(
            text = method.label(),
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = inkColor,
        )
    }
}
