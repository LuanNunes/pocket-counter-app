package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SourceTextCard(
    notification: NotificationItem,
    tokens: List<Token>,
    selectedTokenIndex: Int?,
    onTokenTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.surface2, PocketTheme.shapes.card)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.card)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = buildString {
                    append("SMS".takeIf { notification.channel == NotificationChannel.SMS } ?: "Push")
                    append(" • ")
                    append(notification.app.uppercase())
                    append(" • ")
                    append(notification.received)
                },
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
            )
            Text(
                text = "toque para marcar",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
            )
        }

        Spacer(Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tokens.forEachIndexed { index, token ->
                val bg = tokenBackground(token.role)
                val textColor = tokenTextColor(token.role)
                val isSelected = selectedTokenIndex == index
                val borderMod = run {
                    if (isSelected) {
                        return@run Modifier.border(2.dp, PocketTheme.colors.accent, PocketTheme.shapes.icon)
                    }
                    if (token.role != null) {
                        return@run Modifier.border(1.dp, bg.copy(alpha = 0.5f), PocketTheme.shapes.icon)
                    }
                    Modifier
                }

                Text(
                    text = token.text,
                    style = PocketTheme.typography.monoSm.copy(
                        fontWeight = FontWeight.Medium.takeIf { token.role != null } ?: FontWeight.Normal,
                    ),
                    color = textColor,
                    modifier = Modifier
                        .then(borderMod)
                        .background(
                            bg.copy(alpha = 0.15f).takeIf { token.role != null } ?: Color.Transparent,
                            PocketTheme.shapes.icon,
                        )
                        .clickable { onTokenTap(index) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun tokenBackground(role: TokenRole?): Color = when (role) {
    TokenRole.TYPE -> PocketTheme.colors.expense
    TokenRole.AMOUNT -> PocketTheme.colors.income
    TokenRole.PAYMENT -> PocketTheme.colors.accent
    TokenRole.MERCHANT -> PocketTheme.colors.warn
    TokenRole.DATE -> PocketTheme.colors.text3
    TokenRole.INSTALLMENTS -> PocketTheme.colors.accent2
    null -> Color.Transparent
}

@Composable
private fun tokenTextColor(role: TokenRole?): Color = when (role) {
    TokenRole.TYPE -> PocketTheme.colors.expense
    TokenRole.AMOUNT -> PocketTheme.colors.income
    TokenRole.PAYMENT -> PocketTheme.colors.accent
    TokenRole.MERCHANT -> PocketTheme.colors.warn
    TokenRole.DATE -> PocketTheme.colors.text3
    TokenRole.INSTALLMENTS -> PocketTheme.colors.accent2
    null -> PocketTheme.colors.text2
}
