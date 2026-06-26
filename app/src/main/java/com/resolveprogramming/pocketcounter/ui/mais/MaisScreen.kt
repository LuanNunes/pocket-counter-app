package com.resolveprogramming.pocketcounter.ui.mais

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import kotlinx.coroutines.launch

@Composable
fun MaisScreen(
    onNav: (TabId) -> Unit,
    onOpenRoute: (String) -> Unit,
    viewModel: MaisViewModel = hiltViewModel(),
) {
    val toastState = remember { PocketToastState() }
    val scope = rememberCoroutineScope()
    fun soon() = scope.launch { toastState.show("Em breve") }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                MaisEvent.ShowEnrollSheet -> context.startActivity(enrollIntent())
            }
        }
    }

    // Re-check availability when returning to the screen (e.g. after enrolling a biometric).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

    data class Entry(val label: String, val sub: String, val icon: ImageVector, val onClick: () -> Unit)

    val topEntries = listOf(
        Entry("Contas Fixas", "Lançamentos que se repetem", Icons.Filled.Autorenew) { onOpenRoute("contas-fixas") },
        Entry("Relatório", "Tendências por mês, trimestre, ano", Icons.Filled.BarChart) { onOpenRoute("relatorio") },
        Entry("Regras aprendidas", "Classificação automática", Icons.Filled.AutoFixHigh) { onOpenRoute("regras") },
        Entry("Contextos & Tags", "Organize suas análises", Icons.Filled.Sell) { onOpenRoute("contextos") },
        Entry("Assistente", "Tire dúvidas sobre suas finanças", Icons.Filled.AutoAwesome) { onOpenRoute("assistente") },
    )
    val settingsEntry =
        Entry("Configurações", "Tema, densidade, conta", Icons.Filled.Settings) { soon() }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PocketTheme.colors.bg,
            bottomBar = { PocketTabBar(active = TabId.MAIS, onNav = onNav) },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        text = "Mais",
                        style = PocketTheme.typography.screenH1,
                        color = PocketTheme.colors.text,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                }
                items(topEntries) { entry ->
                    MaisRow(label = entry.label, sub = entry.sub, icon = entry.icon, onClick = entry.onClick)
                }
                item {
                    MaisLockRow(
                        enabled = state.lockEnabled,
                        disabled = state.lockRowState == LockRowState.Disabled,
                        onToggle = {
                            val target = !state.lockEnabled
                            activity?.let { viewModel.onToggle(it, target) }
                            scope.launch {
                                val msg = "Bloqueio ativado".takeIf { target } ?: "Bloqueio desativado"
                                toastState.show(msg)
                            }
                        },
                    )
                }
                item {
                    MaisRow(
                        label = settingsEntry.label,
                        sub = settingsEntry.sub,
                        icon = settingsEntry.icon,
                        onClick = settingsEntry.onClick,
                    )
                }
                item {
                    MaisLogoutRow(onClick = { showLogoutDialog = true })
                }
            }
        }
        PocketToastHost(state = toastState)
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

private fun enrollIntent(): Intent {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        return Intent(Settings.ACTION_BIOMETRIC_ENROLL)
    }
    return Intent(Settings.ACTION_SECURITY_SETTINGS)
}

@Composable
private fun MaisLockRow(
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
private fun MaisRow(label: String, sub: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .clickable(onClick = onClick)
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
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = PocketTheme.colors.text2,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            Text(sub, style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = PocketTheme.colors.text3,
        )
    }
}

@Composable
private fun MaisLogoutRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .clickable(onClick = onClick)
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
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = PocketTheme.colors.expense,
            )
        }
        Text(
            "Sair",
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.expense,
            modifier = Modifier.weight(1f),
        )
    }
}
