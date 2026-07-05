package com.resolveprogramming.pocketcounter.ui.configuracoes

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
import com.resolveprogramming.pocketcounter.ui.components.ManageTopBar
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.icon
import com.resolveprogramming.pocketcounter.ui.wizard.label

@Composable
fun ConfiguracoesScreen(
    onBack: () -> Unit,
    viewModel: ConfiguracoesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ConfiguracoesEvent.ShowEnrollSheet -> context.startActivity(enrollIntent())
            }
        }
    }

    // Re-check availability when returning to the screen (e.g. after enrolling a biometric).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshLockAvailability()
    }

    Scaffold(containerColor = PocketTheme.colors.bg) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ManageTopBar(title = "Configurações", onBack = onBack)

            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "Segurança",
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                Spacer(Modifier.height(12.dp))

                LockRow(
                    enabled = state.lockEnabled,
                    disabled = state.lockRowState == LockRowState.Disabled,
                    onToggle = { activity?.let { viewModel.onToggleLock(it, !state.lockEnabled) } },
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "Meios de pagamento",
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Formas desabilitadas não aparecem ao classificar.",
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                )
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethod.entries.forEach { method ->
                        val enabled = method in state.enabledMethods
                        val toggleable =
                            !enabled || PaymentMethodPreferences.canDisable(state.enabledMethods, method)
                        PaymentMethodRow(
                            method = method,
                            checked = enabled,
                            toggleable = toggleable,
                            onCheckedChange = { viewModel.setPaymentMethodEnabled(method, it) },
                        )
                    }
                }
            }
        }
    }
}

private fun enrollIntent(): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Intent(Settings.ACTION_BIOMETRIC_ENROLL)
    }
    return Intent(Settings.ACTION_SECURITY_SETTINGS)
}

@Composable
private fun LockRow(
    enabled: Boolean,
    disabled: Boolean,
    onToggle: () -> Unit,
) {
    val sub = "Indisponível neste aparelho".takeIf { disabled }
        ?: "Exija Face ou digital ao abrir o app"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .then(
                run {
                    if (disabled) return@run Modifier
                    Modifier.clickable(onClick = onToggle)
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(PocketTheme.colors.surface2, PocketTheme.shapes.icon),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = PocketTheme.colors.text2,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Bloqueio por biometria",
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            Text(sub, style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
        }
        // Presentational: the whole row is the single toggle target (avoids a double-toggle path).
        Switch(
            checked = enabled,
            onCheckedChange = null,
            enabled = !disabled,
            colors = SwitchDefaults.colors(checkedTrackColor = PocketTheme.colors.accent),
        )
    }
}

@Composable
private fun PaymentMethodRow(
    method: PaymentMethod,
    checked: Boolean,
    toggleable: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(PocketTheme.colors.surface2, PocketTheme.shapes.icon),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = method.icon(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = PocketTheme.colors.text2,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = method.label(),
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            if (checked && !toggleable) {
                Text(
                    text = "Pelo menos um meio precisa ficar ativo.",
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = toggleable,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PocketTheme.colors.accent,
                checkedThumbColor = PocketTheme.colors.accentInk,
            ),
        )
    }
}
