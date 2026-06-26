package com.resolveprogramming.pocketcounter.ui.contextos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.ui.components.ManageTopBar
import com.resolveprogramming.pocketcounter.ui.components.PocketBadge
import com.resolveprogramming.pocketcounter.ui.components.PocketBadgeVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketChip
import com.resolveprogramming.pocketcounter.ui.components.PocketChipVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.SquareIconButton
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContextosTagsScreen(
    onBack: () -> Unit,
    onNav: (TabId) -> Unit,
    viewModel: ContextosTagsViewModel = hiltViewModel(),
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
                ManageTopBar(title = "Contextos & Tags", onBack = onBack, onAdd = viewModel::openAddContext)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        SectionLabel("Contextos de despesa")
                    }
                    items(state.sections.size, key = { "ctx_${state.sections[it].context?.id ?: "__sem_contexto__"}" }) { i ->
                        ContextSectionCard(
                            section = state.sections[i],
                            onEditContext = { id -> viewModel.openEditContext(id) },
                            onDeleteContext = { id -> viewModel.requestDeleteContext(id) },
                            onAddTag = { idCtx -> viewModel.openAddTag(idCtx) },
                            onEditTag = { id -> viewModel.openEditTag(id) },
                            onDeleteTag = { id -> viewModel.requestDeleteTag(id) },
                        )
                    }
                    item { SectionLabel("Categorias de renda") }
                    item {
                        IncomeCategoriesCard(
                            categories = state.incomeCategories,
                            onAddCategory = viewModel::openAddIncomeCategory,
                            onEditCategory = { id -> viewModel.openEditTag(id) },
                            onDeleteCategory = { id -> viewModel.requestDeleteTag(id) },
                        )
                    }
                    item { Box(Modifier.height(80.dp)) }
                }
            }
        }
        PocketToastHost(state = toastState)
    }

    // ── Sheets ───────────────────────────────────────────────────
    state.contextForm?.let { mode ->
        ContextFormSheet(
            mode = mode,
            editing = state.editingContext,
            palette = state.palette,
            onSave = viewModel::saveContext,
            onDelete = {
                val id = (mode as? ContextFormMode.Edit)?.id
                viewModel.closeContextForm()
                if (id != null) viewModel.requestDeleteContext(id)
            },
            onDismiss = viewModel::closeContextForm,
        )
    }

    state.tagForm?.let { mode ->
        TagFormSheet(
            mode = mode,
            editing = state.editingTag,
            contexts = state.contexts,
            palette = state.palette,
            onSave = viewModel::saveTag,
            onDelete = {
                val id = (mode as? TagFormMode.Edit)?.id
                viewModel.closeTagForm()
                if (id != null) viewModel.requestDeleteTag(id)
            },
            onDismiss = viewModel::closeTagForm,
        )
    }

    // ── Confirm dialogs ──────────────────────────────────────────
    state.confirmDeleteContext?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteContext,
            title = { Text("Excluir contexto?", color = PocketTheme.colors.text) },
            text = {
                Text(
                    "“${target.name}” será removido. Suas ${target.tagCount} tag(s) ficam sem contexto; os lançamentos continuam funcionando.",
                    color = PocketTheme.colors.text2,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteContext) {
                    Text("Excluir", color = PocketTheme.colors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDeleteContext) {
                    Text("Cancelar", color = PocketTheme.colors.text2)
                }
            },
            containerColor = PocketTheme.colors.surface,
        )
    }

    state.confirmDeleteTag?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteTag,
            title = { Text("Excluir tag?", color = PocketTheme.colors.text) },
            text = { Text("“${target.name}” será removida das transações que a usam.", color = PocketTheme.colors.text2) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteTag) {
                    Text("Excluir", color = PocketTheme.colors.expense)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDeleteTag) {
                    Text("Cancelar", color = PocketTheme.colors.text2)
                }
            },
            containerColor = PocketTheme.colors.surface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextSectionCard(
    section: ContextSection,
    onEditContext: (String) -> Unit,
    onDeleteContext: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onEditTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
) {
    val ctx = section.context
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ctx != null) {
                Box(Modifier.size(12.dp).background(Color(ctx.color), PocketTheme.shapes.pill))
            }
            Text(
                section.title,
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
                modifier = Modifier.weight(1f),
            )
            PocketBadge(text = "${section.tags.size}", variant = PocketBadgeVariant.SOFT)
            if (ctx != null) {
                Spacer(Modifier.size(4.dp))
                SquareIconButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = "Editar",
                    onClick = { onEditContext(ctx.id) },
                )
                SquareIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Excluir",
                    onClick = { onDeleteContext(ctx.id) },
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            section.tags.forEach { tag ->
                TagChip(
                    tag = tag,
                    dotColor = ctx?.color?.let { Color(it) } ?: PocketTheme.colors.text3,
                    onClick = { onEditTag(tag.id) },
                    onRemove = { onDeleteTag(tag.id) },
                )
            }
            if (ctx != null) {
                PocketChip(label = "+ Tag", variant = PocketChipVariant.ADD, onClick = { onAddTag(ctx.id) })
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = PocketTheme.typography.sectionHeader,
        color = PocketTheme.colors.text3,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IncomeCategoriesCard(
    categories: List<Tag>,
    onAddCategory: () -> Unit,
    onEditCategory: (String) -> Unit,
    onDeleteCategory: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            categories.forEach { category ->
                TagChip(
                    tag = category,
                    dotColor = category.color?.let { Color(it) } ?: PocketTheme.colors.text3,
                    onClick = { onEditCategory(category.id) },
                    onRemove = { onDeleteCategory(category.id) },
                )
            }
            PocketChip(label = "+ Categoria", variant = PocketChipVariant.ADD, onClick = onAddCategory)
        }
    }
}

@Composable
private fun TagChip(tag: Tag, dotColor: Color, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .background(PocketTheme.colors.surface2, PocketTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(dotColor, PocketTheme.shapes.pill))
        Text(tag.name, style = PocketTheme.typography.bodySm, color = PocketTheme.colors.text2)
        Box(
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remover",
                modifier = Modifier.size(14.dp),
                tint = PocketTheme.colors.text3,
            )
        }
    }
}
