package com.resolveprogramming.pocketcounter.ui.regras

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.FormErrorNote
import com.resolveprogramming.pocketcounter.ui.components.FormLabel
import com.resolveprogramming.pocketcounter.ui.components.FormSwitchRow
import com.resolveprogramming.pocketcounter.ui.components.FormTextField
import com.resolveprogramming.pocketcounter.ui.components.ManageTopBar
import com.resolveprogramming.pocketcounter.ui.components.PocketBadge
import com.resolveprogramming.pocketcounter.ui.components.PocketBadgeVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.SquareIconButton
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.components.TagPicker
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

private const val SHEET_MAX_HEIGHT_FRACTION = 0.86f

@Composable
fun RulesScreen(
    onBack: () -> Unit,
    onNav: (TabId) -> Unit,
    viewModel: RegrasViewModel = hiltViewModel(),
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
                ManageTopBar(
                    title = "Regras aprendidas",
                    onBack = onBack,
                )
                val isEmpty = !state.isLoading && state.rules.isEmpty()
                if (isEmpty) {
                    EmptyState()
                }
                if (!isEmpty) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.rules.size, key = { state.rules[it].id ?: "idx_$it" }) { i ->
                            RegraCard(
                                rule = state.rules[i],
                                tagsById = state.tagsById,
                                contextsById = state.contextsById,
                                onEdit = { id -> viewModel.openEdit(id) },
                                onDelete = { id -> viewModel.requestDelete(id) },
                            )
                        }
                        item { Box(Modifier.height(80.dp)) }
                    }
                }
            }
        }
        PocketToastHost(state = toastState)
    }

    state.confirmDelete?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Excluir regra?", color = PocketTheme.colors.text) },
            text = { Text("A regra “${target.patternLabel}” será removida.", color = PocketTheme.colors.text2) },
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

    state.editTarget?.let { rule ->
        RegraEditSheet(
            rule = rule,
            tags = remember(state.tagsById) { state.tagsById.values.toList() },
            contexts = remember(state.contextsById) { state.contextsById.values.toList() },
            saving = state.savingEdit,
            errorMessage = state.editError,
            onClearError = viewModel::clearEditError,
            onSave = { pattern, idTag, active -> viewModel.saveEdit(pattern, idTag, active) },
            onDismiss = viewModel::cancelEdit,
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nenhuma regra aprendida ainda",
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "As regras são criadas quando você ativa “Aprender este padrão” no assistente de classificação ou ao classificar uma fatura.",
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.text3,
        )
    }
}

@Composable
internal fun RegraCard(
    rule: ClassificationRule,
    tagsById: Map<String, Tag>,
    contextsById: Map<String, TagContext>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val editId = rule.id
    // The merge sits on the clickable node so the row is one labelled, actionable target; the two
    // touch targets inside carry their own merge, which is what keeps them separately focusable.
    val cardModifier = Modifier
        .fillMaxWidth()
        .let { base -> editId?.let { id -> base.clickable { onEdit(id) } } ?: base }
        .semantics(mergeDescendants = true) { }
    val tag = rule.idTag?.let { tagsById[it] }
    val tagLabel = tag?.name ?: "tag removida".takeIf { rule.idTag != null }

    PocketCard(modifier = cardModifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.pattern.ifBlank { "sem padrão" },
                    style = PocketTheme.typography.monoSm,
                    color = PocketTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                if (rule.active == false) {
                    PocketBadge(text = "inativa", variant = PocketBadgeVariant.SOFT)
                    Spacer(Modifier.width(8.dp))
                }
                rule.id?.let { id ->
                    Box(Modifier.semantics(mergeDescendants = true) { }) {
                        SquareIconButton(
                            icon = Icons.Filled.Close,
                            contentDescription = "Excluir",
                            onClick = { onDelete(id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = ruleOutcomeLabel(rule),
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
            )

            if (tagLabel != null) {
                Spacer(Modifier.height(8.dp))
                val dotColor = tag
                    ?.let { it.idContext?.let { id -> contextsById[id] }?.color ?: it.color }
                    ?.let { Color(it) }
                    ?: PocketTheme.colors.text3
                TagDotChip(name = tagLabel, dotColor = dotColor)
            }
        }
    }
}

private fun ruleOutcomeLabel(rule: ClassificationRule): String {
    val applied = "aplicada ${rule.appliedCount}×".takeIf { rule.appliedCount > 0 } ?: "ainda não aplicada"
    if (rule.action == RuleAction.IGNORE) return "→ ignorar" + " · $applied".takeIf { rule.appliedCount > 0 }.orEmpty()
    return applied
}

@Composable
internal fun RegraEditSheet(
    rule: ClassificationRule,
    tags: List<Tag>,
    contexts: List<TagContext>,
    saving: Boolean,
    errorMessage: String?,
    onClearError: () -> Unit,
    onSave: (pattern: String, idTag: String?, active: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val isSuggest = rule.action == RuleAction.SUGGEST
    var pattern by remember(rule.id) { mutableStateOf(rule.pattern) }
    var idTag by remember(rule.id) { mutableStateOf(rule.idTag.takeIf { isSuggest }) }
    // `active` is a tri-state on the wire and the list reads "on" as `!= false`; the editor
    // always writes a plain Boolean, so the null goes away on the first save.
    var active by remember(rule.id) { mutableStateOf(rule.active != false) }

    val trimmed = pattern.trim()
    val tagKind = idTag?.let { id -> tags.firstOrNull { it.id == id }?.kind }
    val blocker = rule.copy(pattern = trimmed, idTag = idTag).writeBlocker(tagKind)

    // Same cap and pinned-CTA shape as ClassifyPurchaseSheet: unbounded, the tag picker pushes the
    // sheet flush to the top and scrolls "Salvar" out of reach.
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * SHEET_MAX_HEIGHT_FRACTION

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = maxSheetHeight)) {
            Text(
                "Editar regra",
                style = PocketTheme.typography.stepQuestion,
                color = PocketTheme.colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                FormLabel("Padrão")
                Spacer(Modifier.height(8.dp))
                FormTextField(
                    value = pattern,
                    onValueChange = {
                        pattern = it.take(ClassificationRule.MAX_PATTERN_LENGTH)
                        if (errorMessage != null) onClearError()
                    },
                    placeholder = "parte do texto da notificação",
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(8.dp))
                    FormErrorNote(errorMessage)
                }

                if (isSuggest) {
                    Spacer(Modifier.height(20.dp))
                    FormLabel("Tag")
                    Spacer(Modifier.height(8.dp))
                    TagPicker(
                        type = TransactionType.EXPENSE,
                        tags = tags,
                        contexts = contexts,
                        selectedTagIds = listOfNotNull(idTag),
                        onToggleTag = { id -> idTag = id.takeIf { it != idTag } },
                    )
                }

                FormSwitchRow(label = "Ativa", checked = active, onCheckedChange = { active = it })
            }

            Spacer(Modifier.height(20.dp))
            PocketButton(
                text = "Salvar",
                onClick = { onSave(trimmed, idTag, active) },
                enabled = !saving && blocker == null,
                fillMaxWidth = true,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TagDotChip(name: String, dotColor: Color) {
    Row(
        modifier = Modifier
            .background(PocketTheme.colors.surface2, PocketTheme.shapes.chip)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(dotColor, PocketTheme.shapes.pill))
        Text(name, style = PocketTheme.typography.bodySm, color = PocketTheme.colors.text2)
    }
}
