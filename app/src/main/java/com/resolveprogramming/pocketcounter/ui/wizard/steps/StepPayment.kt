package com.resolveprogramming.pocketcounter.ui.wizard.steps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.label

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepPayment(
    type: TransactionType?,
    cards: List<CreditCard>,
    selectedMethod: PaymentMethod?,
    selectedCardId: String?,
    onSelectMethod: (PaymentMethod) -> Unit,
    onSelectCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val methods = PaymentMethod.entries.filterNot {
        it == PaymentMethod.CREDIT && type == TransactionType.INCOME
    }

    Column(modifier = modifier) {
        Text(
            text = "Como foi pago?",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Opcional — só o crédito precisa de cartão.",
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text3,
        )

        Spacer(Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            methods.forEach { method ->
                MethodChip(
                    method = method,
                    isSelected = selectedMethod == method,
                    onClick = { onSelectMethod(method) },
                )
            }
        }

        AnimatedVisibility(
            visible = selectedMethod == PaymentMethod.CREDIT,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Qual cartão?",
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                Spacer(Modifier.height(12.dp))

                if (cards.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.card)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Nenhum cartão ainda",
                            style = PocketTheme.typography.body,
                            color = PocketTheme.colors.text3,
                        )
                    }
                }
                if (cards.isNotEmpty()) {
                    cards.forEach { card ->
                        CardRow(
                            card = card,
                            isSelected = selectedCardId == card.id,
                            onClick = { onSelectCard(card.id) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MethodChip(
    method: PaymentMethod,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = PocketTheme.colors.accent.takeIf { isSelected } ?: PocketTheme.colors.line
    val bgColor = PocketTheme.colors.accentBg.takeIf { isSelected } ?: PocketTheme.colors.surface
    val inkColor = PocketTheme.colors.accent.takeIf { isSelected } ?: PocketTheme.colors.text

    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .border(1.5.dp, borderColor, PocketTheme.shapes.chip)
            .background(bgColor, PocketTheme.shapes.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = method.icon(),
            style = PocketTheme.typography.body,
            color = inkColor,
        )
        Text(
            text = method.label(),
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = inkColor,
        )
    }
}

@Composable
private fun CardRow(
    card: CreditCard,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = PocketTheme.colors.accent.takeIf { isSelected } ?: PocketTheme.colors.line
    val bgColor = PocketTheme.colors.accentBg.takeIf { isSelected } ?: PocketTheme.colors.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, PocketTheme.shapes.card)
            .background(bgColor, PocketTheme.shapes.card)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(card.gradientStart), Color(card.gradientEnd)),
                    ),
                    PocketTheme.shapes.icon,
                ),
        )

        Spacer(Modifier.width(12.dp))

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
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(PocketTheme.colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.accentInk,
                )
            }
        }
    }
}

private fun PaymentMethod.icon(): String = when (this) {
    PaymentMethod.CREDIT -> "▢"
    PaymentMethod.DEBIT -> "◇"
    PaymentMethod.PIX -> "⚡"
    PaymentMethod.CASH -> "$"
    PaymentMethod.CRYPTO -> "₿"
}
