package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonSize
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * An invoice-payment push that did not resolve to one pending invoice. Never reuses
 * [ConfirmReadyCard]: the absence of a Confirmar button is the whole message. There is deliberately
 * no Revisar either — it opens the wizard on a draft whose only terminal action is "save a new
 * transaction", which is the duplicate this feature exists to kill.
 */
@Composable
fun InvoicePaymentChoiceCard(
    presentation: InvoicePromptPresentation,
    onChoose: () -> Unit,
    onIgnore: () -> Unit,
) {
    val colors = PocketTheme.colors
    PocketCard(
        elevated = true,
        contentPadding = PaddingValues(
            horizontal = PocketTheme.spacing.pad,
            vertical = PocketTheme.spacing.gap,
        ),
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = presentation.contentDescription
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pagamento de fatura",
                            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                        )
                        Text(
                            text = presentation.metaLine,
                            style = PocketTheme.typography.bodyXs,
                            color = colors.text3,
                        )
                    }
                    // The amount sits on the title row, like every other ledger card — it is what the
                    // user matches against the invoices in the picker. No sign and no type colour: the
                    // type is deliberately unknown here, and asserting "expense" is what produced the
                    // duplicate this feature removes.
                    presentation.amount?.let { amount ->
                        Spacer(Modifier.width(8.dp))
                        AmountText(
                            amount = amount,
                            style = PocketTheme.typography.monoBody.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.text,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    text = reasonText(presentation.reason),
                    style = PocketTheme.typography.bodySm,
                    color = colors.text,
                )
            }
            Spacer(Modifier.size(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!presentation.canChoose) {
                    // A picker that can only open empty is a dead end; promote the dismissal instead.
                    PocketButton(
                        text = "Ignorar",
                        onClick = onIgnore,
                        variant = PocketButtonVariant.SOFT,
                        size = PocketButtonSize.SMALL,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    )
                    return@Row
                }
                PocketButton(
                    text = "Escolher a fatura",
                    onClick = onChoose,
                    variant = PocketButtonVariant.PRIMARY,
                    size = PocketButtonSize.SMALL,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                )
                IconButton(onClick = onIgnore) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Ignorar sugestão",
                        modifier = Modifier.size(18.dp),
                        tint = colors.text3,
                    )
                }
            }
        }
    }
}

private fun reasonText(reason: InvoiceReasonLine): AnnotatedString = when (reason) {
    is InvoiceReasonLine.Plain -> AnnotatedString(reason.text)
    is InvoiceReasonLine.Conflict -> buildAnnotatedString {
        append("O valor bate com a ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(reason.amountCard) }
        append(", mas a notificação é do ")
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(reason.issuer) }
        append(".")
    }
}
