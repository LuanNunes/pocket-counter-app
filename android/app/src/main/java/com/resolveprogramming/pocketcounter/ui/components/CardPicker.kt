package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/** Vertical list of the user's cards, one tap to pick. Empty state says so instead of rendering nothing. */
@Composable
fun CardPicker(
    cards: List<CreditCard>,
    selectedCardId: String?,
    onSelect: (String) -> Unit,
) {
    if (cards.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.card)
                .padding(16.dp),
        ) {
            Text(
                text = "Nenhum cartão ainda",
                style = PocketTheme.typography.body,
                color = PocketTheme.colors.text3,
            )
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.forEach { card ->
            val isSelected = selectedCardId == card.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = PocketTheme.colors.accent.takeIf { isSelected } ?: PocketTheme.colors.line,
                        shape = PocketTheme.shapes.card,
                    )
                    .background(
                        PocketTheme.colors.accentBg.takeIf { isSelected } ?: PocketTheme.colors.surface,
                        PocketTheme.shapes.card,
                    )
                    .clickable(role = Role.Button, onClick = { onSelect(card.id) })
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.name,
                        style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = PocketTheme.colors.text,
                    )
                    Text(
                        text = "fecha dia ${card.billDay}",
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.text3,
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = PocketTheme.colors.accent,
                    )
                }
            }
        }
    }
}
