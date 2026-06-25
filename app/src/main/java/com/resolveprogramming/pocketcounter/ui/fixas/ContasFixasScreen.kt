package com.resolveprogramming.pocketcounter.ui.fixas

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.Series
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.ManageTopBar
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketChip
import com.resolveprogramming.pocketcounter.ui.components.PocketChipVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.SquareIconButton
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@Composable
fun ContasFixasScreen(
    onBack: () -> Unit,
    onNav: (TabId) -> Unit,
    viewModel: ContasFixasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = remember { PocketToastState() }

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        toastState.show(message)
        viewModel.consumeToast()
    }

    val expenses = state.series.filter { it.type == TransactionType.EXPENSE }
    val incomes = state.series.filter { it.type == TransactionType.INCOME }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PocketTheme.colors.bg,
            bottomBar = { PocketTabBar(active = TabId.MAIS, onNav = onNav) },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                ManageTopBar(title = "Contas Fixas", onBack = onBack)
                val showEmpty = !state.isLoading && state.series.isEmpty()
                val showList = !state.isLoading && state.series.isNotEmpty()
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = PocketTheme.colors.accent)
                    }
                }
                if (showEmpty) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            "Nenhuma conta fixa ainda. Marque “Repete todo mês” ao salvar uma transação.",
                            style = PocketTheme.typography.bodySm,
                            color = PocketTheme.colors.text3,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
                if (showList) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (expenses.isNotEmpty()) {
                            item(key = "h_expense") { SectionHeader("Despesas", expenses.size) }
                            items(expenses.size, key = { expenses[it].id }) { i ->
                                SeriesRow(
                                    series = expenses[i],
                                    tagsById = state.tagsById,
                                    onRename = { viewModel.openRename(expenses[i]) },
                                    onTags = { viewModel.openTags(expenses[i]) },
                                    onDelete = { viewModel.requestDelete(expenses[i].id) },
                                )
                            }
                        }
                        if (incomes.isNotEmpty()) {
                            item(key = "h_income") { SectionHeader("Receitas", incomes.size) }
                            items(incomes.size, key = { incomes[it].id }) { i ->
                                SeriesRow(
                                    series = incomes[i],
                                    tagsById = state.tagsById,
                                    onRename = { viewModel.openRename(incomes[i]) },
                                    onTags = { viewModel.openTags(incomes[i]) },
                                    onDelete = { viewModel.requestDelete(incomes[i].id) },
                                )
                            }
                        }
                        item { Box(Modifier.height(80.dp)) }
                    }
                }
            }
        }
        PocketToastHost(state = toastState)
    }

    state.renameTarget?.let { target ->
        RenameDialog(
            initial = target.name,
            onConfirm = { viewModel.rename(target.id, it) },
            onDismiss = viewModel::cancelRename,
        )
    }

    state.confirmDeleteId?.let { id ->
        val name = state.series.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Excluir conta fixa?", color = PocketTheme.colors.text) },
            text = { Text("“$name” será removida. As transações já lançadas não são apagadas.", color = PocketTheme.colors.text2) },
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

    state.tagsTarget?.let { target ->
        SeriesTagSheet(
            series = target,
            allTags = state.tagsById.values.toList(),
            onSave = { viewModel.saveTags(target.id, it) },
            onDismiss = viewModel::closeTags,
        )
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Text(
        "${title.uppercase()} · $count",
        style = PocketTheme.typography.sectionHeader,
        color = PocketTheme.colors.text3,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesRow(
    series: Series,
    tagsById: Map<String, Tag>,
    onRename: () -> Unit,
    onTags: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(32.dp).background(PocketTheme.colors.surface2, PocketTheme.shapes.icon),
                contentAlignment = Alignment.Center,
            ) {
                Text("↻", color = PocketTheme.colors.text2)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    series.name,
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                val sub = series.recurrenceDay?.let { "dia $it" } ?: "sem dia fixo"
                Text(sub, style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
            }
            SquareIconButton(glyph = "✎", onClick = onRename)
            SquareIconButton(glyph = "#", onClick = onTags)
            SquareIconButton(glyph = "🗑", onClick = onDelete)
        }
        if (series.tagIds.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                series.tagIds.take(4).forEach { tagId ->
                    val tagName = tagsById[tagId]?.name ?: return@forEach
                    PocketChip(label = tagName)
                }
                if (series.tagIds.size > 4) {
                    Text(
                        "+${series.tagIds.size - 4}",
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.text3,
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renomear conta fixa", color = PocketTheme.colors.text) },
        text = {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = PocketTheme.typography.body.copy(color = PocketTheme.colors.text),
                cursorBrush = SolidColor(PocketTheme.colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PocketTheme.colors.surface2, PocketTheme.shapes.chip)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) {
                Text("Salvar", color = PocketTheme.colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = PocketTheme.colors.text2)
            }
        },
        containerColor = PocketTheme.colors.surface,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesTagSheet(
    series: Series,
    allTags: List<Tag>,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(series.tagIds.toSet()) }
    val candidates = allTags.filter { it.kind == series.type }
    PocketBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Tags · ${series.name}",
            style = PocketTheme.typography.screenH1,
            color = PocketTheme.colors.text,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (candidates.isEmpty()) {
            Text(
                "Nenhuma tag disponível para este tipo.",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (candidates.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                candidates.forEach { tag ->
                    val on = tag.id in selected
                    PocketChip(
                        label = tag.name,
                        variant = PocketChipVariant.ON.takeIf { on } ?: PocketChipVariant.DEFAULT,
                        onClick = {
                            selected = run {
                                if (on) return@run selected - tag.id
                                selected + tag.id
                            }
                        },
                    )
                }
            }
        }
        PocketButton(
            text = "Salvar tags",
            onClick = { onSave(selected.toList()) },
            fillMaxWidth = true,
        )
    }
}
