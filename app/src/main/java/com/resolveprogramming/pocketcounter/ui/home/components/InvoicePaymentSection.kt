package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.ui.home.InvoicePaymentPrompt
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * The unresolved invoice-payment stack. Kept out of "Reconhecidos automaticamente" on purpose:
 * these cards have no Confirmar, and counting them there would both mis-sell them and inflate a
 * count that is deliberately truthful. Amber, not red — nothing has gone wrong.
 */
@Composable
fun InvoicePaymentSection(
    prompts: List<InvoicePaymentPrompt>,
    onChoose: (InvoicePaymentPrompt) -> Unit,
    onIgnore: (InvoicePaymentPrompt) -> Unit,
) {
    val colors = PocketTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = colors.warn,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Pagamento de fatura",
                style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                color = colors.warn,
            )
            if (prompts.size > 1) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "· ${prompts.size}",
                    style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text3,
                )
            }
        }

        prompts.forEach { prompt ->
            key(prompt.notificationId) {
                val presentation = remember(prompt) { invoicePromptPresentation(prompt) }
                InvoicePaymentChoiceCard(
                    presentation = presentation,
                    onChoose = { onChoose(prompt) },
                    onIgnore = { onIgnore(prompt) },
                )
            }
        }
    }
}
