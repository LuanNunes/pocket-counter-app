package com.resolveprogramming.pocketcounter.ui.fontes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
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
import com.resolveprogramming.pocketcounter.domain.model.Source
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.ui.components.ManageTopBar
import com.resolveprogramming.pocketcounter.ui.components.PocketChip
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.SquareIconButton
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FontesScreen(
    onBack: () -> Unit,
    onNav: (TabId) -> Unit,
    viewModel: FontesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = remember { PocketToastState() }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("pt", "BR")) }

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
                ManageTopBar(title = "Fontes", onBack = onBack, onAdd = { viewModel.openAdd() })
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.sections.forEach { section ->
                        item(key = "h_${section.title}") {
                            SectionHeader(
                                title = section.title,
                                color = section.paymentSource?.color,
                                count = section.sources.size,
                                onAdd = section.paymentSource?.let { ps -> { viewModel.openAdd(ps.id) } },
                            )
                        }
                        items(section.sources.size, key = { section.sources[it].id }) { i ->
                            val src = section.sources[i]
                            FonteRow(
                                source = src,
                                tagsById = state.tags.associateBy { it.id },
                                amountText = src.amount?.let { currency.format(it) },
                                onClick = { viewModel.openEdit(src.id) },
                            )
                        }
                    }
                    item { Box(Modifier.height(80.dp)) }
                }
            }
        }
        PocketToastHost(state = toastState)
    }

    state.formMode?.let { mode ->
        FonteFormSheet(
            mode = mode,
            editing = state.editing,
            preselectMeioId = state.preselectMeioId,
            paymentSources = state.allPaymentSources,
            tags = state.tags,
            contexts = state.contexts,
            onSave = viewModel::save,
            onDelete = {
                val id = (mode as? FonteFormMode.Edit)?.id
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
                        "“${target.name}” tem ${target.txCount} transação(ões) vinculada(s).",
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
                title = { Text("Excluir fonte?", color = PocketTheme.colors.text) },
                text = { Text("“${target.name}” será removida.", color = PocketTheme.colors.text2) },
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

@Composable
private fun SectionHeader(title: String, color: Long?, count: Int, onAdd: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (color != null) {
                Box(Modifier.size(8.dp).background(Color(color), PocketTheme.shapes.pill))
            }
            Text(
                "${title.uppercase()} · $count",
                style = PocketTheme.typography.sectionHeader,
                color = PocketTheme.colors.text3,
            )
        }
        if (onAdd != null) SquareIconButton(glyph = "+", onClick = onAdd)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FonteRow(
    source: Source,
    tagsById: Map<String, Tag>,
    amountText: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(32.dp).background(PocketTheme.colors.surface2, PocketTheme.shapes.icon),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (source.isRecurring) "↻" else "◆", color = PocketTheme.colors.text2)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    source.name,
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                val sub = if (source.isRecurring) {
                    buildString {
                        append("recorrente")
                        source.refDayRecurring?.let { append(" · dia $it") }
                        amountText?.let { append(" · $it") }
                    }
                } else {
                    "ocasional"
                }
                Text(sub, style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
            }
        }
        if (source.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                source.tags.take(3).forEach { tagId ->
                    val tagName = tagsById[tagId]?.name ?: return@forEach
                    PocketChip(label = tagName)
                }
                if (source.tags.size > 3) {
                    Text(
                        "+${source.tags.size - 3}",
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.text3,
                    )
                }
            }
        }
    }
}
