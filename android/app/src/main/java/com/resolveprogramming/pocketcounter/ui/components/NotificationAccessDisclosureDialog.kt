package com.resolveprogramming.pocketcounter.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

/**
 * Play-policy prominent disclosure for the notification-listener permission. Must be shown —
 * and affirmatively accepted — BEFORE firing the notification-access settings intent, at every
 * request site (Home banner, onboarding). The copy states what is read, what leaves the device
 * (only financial notifications with an identified amount) and that access is revocable.
 */
@Composable
fun NotificationAccessDisclosureDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Acesso às notificações", color = PocketTheme.colors.text) },
        text = {
            Text(
                "Para detectar suas compras automaticamente, o PocketCounter lê as notificações " +
                    "recebidas neste aparelho.\n\n" +
                    "As notificações são analisadas no próprio aparelho. Somente avisos " +
                    "financeiros com um valor identificado (compras, Pix, cobranças) são " +
                    "enviados aos servidores do PocketCounter e associados à sua conta — " +
                    "todas as outras notificações são descartadas e nunca saem do aparelho.\n\n" +
                    "Você pode desativar esse acesso a qualquer momento nas configurações " +
                    "do Android.",
                color = PocketTheme.colors.text2,
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Concordar e ativar", color = PocketTheme.colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Agora não", color = PocketTheme.colors.text2)
            }
        },
        containerColor = PocketTheme.colors.surface,
    )
}
