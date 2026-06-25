package com.resolveprogramming.pocketcounter.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.platform.capture.CapturePermissions
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.onGrantsChanged(
            notificationAccessGranted = CapturePermissions.isNotificationAccessGranted(context),
        )
        onPauseOrDispose { }
    }

    val finishAndGo: () -> Unit = {
        viewModel.completeOnboarding()
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketTheme.colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))

        Box(
            modifier = Modifier
                .size(64.dp)
                .background(PocketTheme.colors.accent, PocketTheme.shapes.card),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "P",
                style = PocketTheme.typography.display,
                color = PocketTheme.colors.accentInk,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Captura automática",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "O PocketCounter lê as notificações dos apps dos seus bancos para detectar " +
                "transações. Apenas mensagens financeiras com um valor identificado são enviadas.",
            style = PocketTheme.typography.body,
            color = PocketTheme.colors.text3,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        PermissionRow(
            title = "Acesso a notificações",
            subtitle = "Detecta avisos de compra e Pix dos apps dos bancos.",
            granted = state.notificationAccessGranted,
            actionLabel = "Abrir ajustes",
            onAction = {
                context.startActivity(CapturePermissions.notificationAccessSettingsIntent())
            },
        )

        Spacer(Modifier.height(12.dp))

        PocketCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "Recebe avisos por SMS?",
                    style = PocketTheme.typography.button,
                    color = PocketTheme.colors.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Encaminhe a mensagem do banco e escolha o PocketCounter — a transação " +
                        "aparece em “Para revisar”.",
                    style = PocketTheme.typography.bodySm,
                    color = PocketTheme.colors.text3,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        PrimaryButton(text = "Continuar", onClick = finishAndGo)

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Pular",
            style = PocketTheme.typography.button,
            color = PocketTheme.colors.text3,
            modifier = Modifier
                .clickable(onClick = finishAndGo)
                .padding(vertical = 8.dp),
        )

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    PocketCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = PocketTheme.typography.button,
                    color = PocketTheme.colors.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = PocketTheme.typography.bodySm,
                    color = PocketTheme.colors.text3,
                )
            }
            if (granted) {
                Text(
                    text = "Ativo",
                    style = PocketTheme.typography.bodySm,
                    color = PocketTheme.colors.income,
                )
            }
            if (!granted) {
                Text(
                    text = actionLabel,
                    style = PocketTheme.typography.bodySm,
                    color = PocketTheme.colors.accent,
                    modifier = Modifier
                        .clickable(onClick = onAction)
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(PocketTheme.colors.accent, PocketTheme.shapes.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = PocketTheme.typography.button,
            color = PocketTheme.colors.accentInk,
        )
    }
}
