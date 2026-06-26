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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    selectionStart: Int?,
    selectionEnd: Int?,
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
                val inSelection = selectionStart != null &&
                    selectionEnd != null &&
                    index in selectionStart..selectionEnd

                val accent = PocketTheme.colors.accent
                val accentBg = PocketTheme.colors.accentBg
                val roleBg = tokenBackground(token.role)

                val fillColor = run {
                    if (inSelection) return@run accentBg
                    if (token.role != null) return@run roleBg.copy(alpha = 0.15f)
                    Color.Transparent
                }
                val textColor = if (inSelection) accent else tokenTextColor(token.role)

                val shape = run {
                    if (inSelection) {
                        return@run spanShape(
                            isFirst = index == selectionStart,
                            isLast = index == selectionEnd,
                        )
                    }
                    if (token.role != null) {
                        val isFirst = index == 0 || tokens[index - 1].role != token.role
                        val isLast = index == tokens.lastIndex || tokens[index + 1].role != token.role
                        return@run spanShape(isFirst = isFirst, isLast = isLast)
                    }
                    PocketTheme.shapes.icon
                }

                val borderMod = run {
                    if (inSelection) return@run Modifier.border(2.dp, accent, shape)
                    if (token.role != null) return@run Modifier.border(1.dp, roleBg.copy(alpha = 0.5f), shape)
                    Modifier
                }

                val stateDesc = run {
                    if (inSelection) return@run "selecionado"
                    val role = token.role ?: return@run ""
                    "marcado como ${roleLabel(role)}"
                }

                Text(
                    text = token.text,
                    style = PocketTheme.typography.monoSm.copy(
                        fontWeight = FontWeight.Medium.takeIf { token.role != null || inSelection }
                            ?: FontWeight.Normal,
                    ),
                    color = textColor,
                    modifier = Modifier
                        .then(borderMod)
                        .background(fillColor, shape)
                        .minimumInteractiveComponentSize()
                        .clickable { onTokenTap(index) }
                        .padding(horizontal = 4.dp, vertical = 6.dp)
                        .semantics {
                            contentDescription = token.text
                            if (stateDesc.isNotEmpty()) stateDescription = stateDesc
                        },
                )
            }
        }
    }
}

/**
 * Rounds only the outer corners of a multi-word run (10dp end caps, ~2dp interior seams) so a span
 * of consecutive tokens reads as a single connected pill. A length-1 run is fully rounded.
 */
private fun spanShape(isFirst: Boolean, isLast: Boolean): RoundedCornerShape {
    val cap = 10.dp
    val seam = 2.dp
    return RoundedCornerShape(
        topStart = cap.takeIf { isFirst } ?: seam,
        bottomStart = cap.takeIf { isFirst } ?: seam,
        topEnd = cap.takeIf { isLast } ?: seam,
        bottomEnd = cap.takeIf { isLast } ?: seam,
    )
}

private fun roleLabel(role: TokenRole): String = when (role) {
    TokenRole.TYPE -> "Tipo"
    TokenRole.AMOUNT -> "Valor"
    TokenRole.PAYMENT -> "Meio de pgto."
    TokenRole.MERCHANT -> "Estabelecimento"
    TokenRole.DATE -> "Data"
    TokenRole.INSTALLMENTS -> "Parcelas"
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
