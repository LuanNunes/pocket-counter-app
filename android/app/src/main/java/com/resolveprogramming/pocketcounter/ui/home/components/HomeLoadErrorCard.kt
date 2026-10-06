package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonSize
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * Takes the hero's place when the month failed to load, so the figures are never replaced by a
 * plausible-looking zero.
 */
@Composable
fun HomeLoadErrorCard(onRetry: () -> Unit) {
    val colors = PocketTheme.colors
    PocketCard {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Não foi possível carregar o mês"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(colors.warn.copy(alpha = 0.18f), PocketTheme.shapes.icon),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.warn,
                    )
                }
                Spacer(Modifier.width(11.dp))
                Text(
                    text = "Não foi possível carregar o mês.",
                    style = PocketTheme.typography.bodySm,
                    color = colors.text,
                )
            }
            Spacer(Modifier.height(10.dp))
            PocketButton(
                text = "Tentar de novo",
                onClick = onRetry,
                variant = PocketButtonVariant.SOFT,
                size = PocketButtonSize.DEFAULT,
            )
        }
    }
}
