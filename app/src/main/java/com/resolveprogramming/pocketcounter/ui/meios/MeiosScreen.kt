package com.resolveprogramming.pocketcounter.ui.meios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.ui.components.ManageTopBar
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@Composable
fun MeiosScreen(
    onBack: () -> Unit,
    onNav: (TabId) -> Unit,
    viewModel: MeiosViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = remember { PocketToastState() }

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        toastState.show(message)
        viewModel.consumeToast()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PocketTheme.colors.bg,
            bottomBar = { PocketTabBar(active = TabId.MAIS, onNav = onNav) },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ManageTopBar(title = "Meios de pagamento", onBack = onBack, onAdd = viewModel::openAdd)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    section("Cartões de crédito", state.creditMeios, viewModel::openEdit)
                    section("Contas", state.checkingMeios, viewModel::openEdit)
                    item { Box(Modifier.height(80.dp)) }
                }
            }
        }
        PocketToastHost(state = toastState)
    }

    state.formMode?.let { mode ->
        MeioFormSheet(
            mode = mode,
            editing = state.editing,
            onSave = viewModel::save,
            onDelete = {
                val id = (mode as? MeioFormMode.Edit)?.id
                viewModel.closeForm()
                if (id != null) viewModel.requestDelete(id)
            },
            onDismiss = viewModel::closeForm,
        )
    }

    state.confirmDelete?.let { target ->
        if (target.blocked) {
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text("Não é possível excluir", color = PocketTheme.colors.text) },
                text = {
                    Text(
                        "“${target.name}” tem ${target.sourceCount} fonte(s) e ${target.txCount} transação(ões) vinculada(s).",
                        color = PocketTheme.colors.text2,
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::cancelDelete) {
                        Text("Entendi", color = PocketTheme.colors.accent)
                    }
                },
                containerColor = PocketTheme.colors.surface,
            )
        } else {
            AlertDialog(
                onDismissRequest = viewModel::cancelDelete,
                title = { Text("Excluir meio?", color = PocketTheme.colors.text) },
                text = { Text("“${target.name}” será removido.", color = PocketTheme.colors.text2) },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDelete) {
                        Text("Excluir", color = PocketTheme.colors.expense)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::cancelDelete) {
                        Text("Cancelar", color = PocketTheme.colors.text2)
                    }
                },
                containerColor = PocketTheme.colors.surface,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    meios: List<PaymentSource>,
    onEdit: (String) -> Unit,
) {
    if (meios.isEmpty()) return
    item(key = "h_$title") {
        Text(
            text = title,
            style = PocketTheme.typography.sectionHeader,
            color = PocketTheme.colors.text3,
            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
        )
    }
    items(meios.size, key = { meios[it].id }) { i ->
        MeioRow(meio = meios[i], onClick = { onEdit(meios[i].id) })
    }
}

@Composable
private fun MeioRow(meio: PaymentSource, onClick: () -> Unit) {
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
                .size(32.dp)
                .background(Color(meio.color), PocketTheme.shapes.icon),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                meio.name,
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            val sub = if (meio.refDayBill != null) "${meio.sub} · fecha dia ${meio.refDayBill}" else meio.sub
            Text(sub, style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
        }
        Text("›", color = PocketTheme.colors.text3)
    }
}
