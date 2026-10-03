package com.resolveprogramming.pocketcounter.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.BiometricUnavailable
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@Composable
fun LockScreen(
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? FragmentActivity ?: return

    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.authenticate(activity)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PocketTheme.colors.bg)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(120.dp))

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
            text = "PocketCounter",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        Spacer(Modifier.height(16.dp))

        val statusText = when (val phase = state.phase) {
            LockPhase.Prompting -> "Verificando…"
            LockPhase.Idle -> "Toque para desbloquear"
            is LockPhase.Unavailable -> unavailableStatus(phase.reason)
            LockPhase.Unlocked -> ""
        }
        Text(
            text = statusText,
            style = PocketTheme.typography.body,
            color = PocketTheme.colors.text3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        )

        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.error!!,
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.expense,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))

        LockPrimaryAction(
            phase = state.phase,
            onRetry = { viewModel.authenticate(activity) },
            onContinueAnyway = viewModel::continueAnyway,
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = "Sair",
            style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.expense,
            modifier = Modifier
                .clickable { showLogoutDialog = true }
                .padding(vertical = 12.dp),
        )

        Spacer(Modifier.height(24.dp))
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sair da conta?", color = PocketTheme.colors.text) },
            text = {
                Text(
                    "Você precisará entrar novamente com e-mail e senha.",
                    color = PocketTheme.colors.text2,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout()
                    },
                ) {
                    Text("Sair", color = PocketTheme.colors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancelar", color = PocketTheme.colors.text2)
                }
            },
            containerColor = PocketTheme.colors.surface,
        )
    }
}

@Composable
private fun LockPrimaryAction(
    phase: LockPhase,
    onRetry: () -> Unit,
    onContinueAnyway: () -> Unit,
) {
    val action = when (phase) {
        LockPhase.Idle -> PrimaryAction("Desbloquear", onRetry)
        is LockPhase.Unavailable -> unavailableAction(phase.reason, onRetry, onContinueAnyway)
        LockPhase.Prompting -> null
        LockPhase.Unlocked -> null
    } ?: return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(PocketTheme.colors.accent, PocketTheme.shapes.chip)
            .clickable(onClick = action.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = action.label,
            style = PocketTheme.typography.button,
            color = PocketTheme.colors.accentInk,
        )
    }
}

private data class PrimaryAction(val label: String, val onClick: () -> Unit)

private fun unavailableStatus(reason: BiometricUnavailable): String = when (reason) {
    BiometricUnavailable.LOCKOUT ->
        "Muitas tentativas. Tente mais tarde ou use o PIN do dispositivo."
    BiometricUnavailable.NONE_ENROLLED ->
        "Cadastre uma biometria nas configurações do aparelho, ou continue com sua senha."
    BiometricUnavailable.NO_HARDWARE,
    BiometricUnavailable.UNKNOWN,
    -> "Biometria indisponível no momento"
}

private fun unavailableAction(
    reason: BiometricUnavailable,
    onRetry: () -> Unit,
    onContinueAnyway: () -> Unit,
): PrimaryAction = when (reason) {
    BiometricUnavailable.LOCKOUT -> PrimaryAction("Usar PIN do dispositivo", onRetry)
    BiometricUnavailable.NONE_ENROLLED,
    BiometricUnavailable.NO_HARDWARE,
    BiometricUnavailable.UNKNOWN,
    -> PrimaryAction("Continuar mesmo assim", onContinueAnyway)
}
