package com.resolveprogramming.pocketcounter.ui.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.domain.model.PaymentSourceKind
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@Composable
fun StepPayment(
    paymentSources: List<PaymentSource>,
    selectedId: String?,
    suggestedId: String?,
    paymentHint: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (paymentHint != null && suggestedId != null) {
            val suggestedName = paymentSources.find { it.id == suggestedId }?.name ?: suggestedId
            Text(
                text = buildAnnotatedString {
                    append("Vi ")
                    withStyle(SpanStyle(fontFamily = com.resolveprogramming.pocketcounter.ui.theme.GeistMono, fontWeight = FontWeight.Medium)) {
                        append(paymentHint)
                    }
                    append(" no texto. Bate com ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("$suggestedName?")
                    }
                },
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text2,
            )
            Spacer(Modifier.height(4.dp))
        }

        Text(
            text = "De qual cartão ou conta?",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        Spacer(Modifier.height(16.dp))

        paymentSources.forEach { source ->
            PaymentOptionCard(
                source = source,
                isSelected = selectedId == source.id,
                isSuggested = suggestedId == source.id,
                onClick = { onSelect(source.id) },
            )
            Spacer(Modifier.height(10.dp))
        }

        NewPaymentCard()
    }
}

@Composable
private fun PaymentOptionCard(
    source: PaymentSource,
    isSelected: Boolean,
    isSuggested: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (isSelected) PocketTheme.colors.accent else PocketTheme.colors.line
    val bgColor = if (isSelected) PocketTheme.colors.accentBg else PocketTheme.colors.surface

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
                .background(Color(source.color), PocketTheme.shapes.icon),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (source.kind == PaymentSourceKind.CREDIT) "▢" else "◇",
                style = PocketTheme.typography.body,
                color = Color.White,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.name,
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            Text(
                text = source.sub,
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
            )
        }

        if (isSuggested) {
            Text(
                text = "SUGESTÃO",
                style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.Bold),
                color = PocketTheme.colors.accent,
                modifier = Modifier.padding(end = 8.dp),
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(PocketTheme.colors.accent, androidx.compose.foundation.shape.CircleShape),
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

@Composable
private fun NewPaymentCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = PocketTheme.colors.line,
                shape = PocketTheme.shapes.card,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.icon),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = PocketTheme.typography.body, color = PocketTheme.colors.text3)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "+ Novo cartão / conta",
            style = PocketTheme.typography.body,
            color = PocketTheme.colors.text3,
        )
    }
}
