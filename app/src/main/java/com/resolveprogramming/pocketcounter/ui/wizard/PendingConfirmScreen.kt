package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@Composable
fun PendingConfirmScreen(
    notification: NotificationItem,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketTheme.colors.bg)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Achei essa transação pendente — confirma como paga?",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        PocketCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = notification.app,
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = notification.text,
                    style = PocketTheme.typography.body,
                    color = PocketTheme.colors.text2,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PocketTheme.spacing.gap),
        ) {
            WizardButton(
                text = "Agora não",
                isPrimary = false,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            WizardButton(
                text = "Confirmando...".takeIf { isSaving } ?: "Confirmar pagamento",
                isPrimary = true,
                enabled = !isSaving,
                onClick = onConfirm,
                modifier = Modifier.weight(1.5f),
            )
        }
    }
}

@Composable
fun PendingConfirmedScreen(
    onBackToApp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketTheme.colors.bg)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Pagamento confirmado",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "A transação pendente foi marcada como paga.",
            style = PocketTheme.typography.body,
            color = PocketTheme.colors.text3,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        WizardButton(
            text = "Voltar ao app",
            isPrimary = true,
            onClick = onBackToApp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
