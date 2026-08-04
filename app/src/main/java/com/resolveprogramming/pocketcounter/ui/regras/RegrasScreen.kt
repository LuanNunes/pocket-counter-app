package com.resolveprogramming.pocketcounter.ui.regras

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.MergeType
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
import com.resolveprogramming.pocketcounter.domain.model.RuleAction
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.wizard.label
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
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme

@Composable
fun RegrasScreen(
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
                    actions = {
                        if (state.rules.isNotEmpty()) {
                            SquareIconButton(
                                icon = Icons.AutoMirrored.Filled.MergeType,
                                contentDescription = "Mesclar e limpar regras",
                                enabled = !state.isMerging,
                                onClick = viewModel::previewDedupe,
                            )
                        }
                    },
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
                                cardsById = state.cardsById,
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

    state.dedupePreview?.let { preview ->
        if (preview.isEmpty) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDedupe,
                title = { Text("Mesclar e limpar regras", color = PocketTheme.colors.text) },
                text = {
                    Text(
                        dedupeMessage(preview),
                        color = PocketTheme.colors.text2,
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissDedupe) {
                        Text("Entendi", color = PocketTheme.colors.accent)
                    }
                },
                containerColor = PocketTheme.colors.surface,
            )
            return@let
        }

        AlertDialog(
            onDismissRequest = viewModel::dismissDedupe,
            title = { Text("Mesclar e limpar regras", color = PocketTheme.colors.text) },
            text = {
                Text(
                    dedupeMessage(preview),
                    color = PocketTheme.colors.text2,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::applyDedupe, enabled = !state.isMerging) {
                    Text("Mesclar", color = PocketTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDedupe) {
                    Text("Cancelar", color = PocketTheme.colors.text2)
                }
            },
            containerColor = PocketTheme.colors.surface,
        )
    }

    state.editTarget?.let { rule ->
        RegraEditSheet(
            rule = rule,
            cards = state.cardsById.values.toList(),
            enabledMethods = state.enabledMethods,
            saving = state.savingEdit,
            onSave = { method, cardId -> viewModel.saveEdit(method, cardId) },
            onDismiss = viewModel::cancelEdit,
        )
    }
}

/**
 * Body copy for the merge dialog. A pass can delete duplicates, only compact redundant patterns on
 * rules that stay, or find nothing to do — the three outcomes read very differently to the user.
 *
 * The first case has to spell out that tags and payment method travel with the merge (newest wins),
 * not just the patterns: that is the part that can silently change how future notifications get
 * categorised, so it can't be left to the word "consolidando".
 */
private fun dedupeMessage(preview: DedupePreview): String {
    if (preview.deletedRules > 0 && preview.mergedRules > 0) {
        val removed = countLabel(preview.deletedRules, "regra duplicada", "regras duplicadas")
        val kept = countLabel(preview.mergedRules, "regra", "regras")
        val removedRules = "da regra removida".takeIf { preview.deletedRules == 1 } ?: "das regras removidas"
        return "Isto vai remover $removed, consolidando os padrões em $kept. " +
            "As tags e o meio de pagamento $removedRules também são consolidados: " +
            "prevalece o valor mais recente."
    }
    if (preview.deletedRules > 0) {
        return "Isto vai remover ${countLabel(preview.deletedRules, "regra duplicada", "regras duplicadas")}. " +
            "Os padrões já estão consolidados nas regras que permanecem."
    }
    if (preview.mergedRules > 0) {
        return "Nenhuma regra duplicada. Isto vai limpar os padrões redundantes de " +
            "${countLabel(preview.mergedRules, "regra", "regras")}."
    }
    return "Nenhuma regra duplicada nem padrão redundante para limpar."
}

private fun countLabel(count: Int, singular: String, plural: String): String =
    "$count ${singular.takeIf { count == 1 } ?: plural}"

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegraCard(
    rule: ClassificationRule,
    cardsById: Map<String, CreditCard>,
    tagsById: Map<String, Tag>,
    contextsById: Map<String, TagContext>,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    // Tapping anywhere on the card (except the delete button, which consumes its own click) opens the
    // payment-method editor. IGNORE rules have no payment target, so they stay non-editable.
    val editable = rule.id != null && rule.action == RuleAction.SUGGEST
    val cardModifier = Modifier
        .fillMaxWidth()
        .let { base -> if (editable) base.clickable { onEdit(rule.id!!) } else base }
    PocketCard(modifier = cardModifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.patterns.joinToString(", ").ifBlank { "sem padrão" },
                    style = PocketTheme.typography.monoSm,
                    color = PocketTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                rule.transactionType?.let { type ->
                    val isIncome = type == TransactionType.INCOME
                    PocketBadge(
                        text = "Receita".takeIf { isIncome } ?: "Despesa",
                        variant = PocketBadgeVariant.INCOME.takeIf { isIncome }
                            ?: PocketBadgeVariant.EXPENSE,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                rule.id?.let { id ->
                    SquareIconButton(
                        icon = Icons.Filled.Close,
                        contentDescription = "Excluir",
                        onClick = { onDelete(id) },
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            val outcome = listOfNotNull(
                rule.paymentMethod?.label(),
                rule.cardId?.let { cardsById[it]?.name },
                rule.tags.size.takeIf { it > 0 }?.let { "+ $it tags" },
                rule.appliedCount.takeIf { it > 0 }?.let { "aplicada ${it}×" },
            ).joinToString(" · ")
            Text(
                text = "sem destino".takeIf { outcome.isBlank() } ?: "→ $outcome",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
            )

            if (rule.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rule.tags.forEach { tag ->
                        val name = tagsById[tag.id]?.name ?: return@forEach
                        val dotColor = (tag.idContext?.let { contextsById[it] }?.color ?: tag.color)
                            ?.let { Color(it) }
                            ?: PocketTheme.colors.text3
                        TagDotChip(name = name, dotColor = dotColor)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegraEditSheet(
    rule: ClassificationRule,
    cards: List<CreditCard>,
    enabledMethods: Set<PaymentMethod>,
    saving: Boolean,
    onSave: (PaymentMethod?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var method by remember(rule.id) { mutableStateOf(rule.paymentMethod) }
    var cardId by remember(rule.id) { mutableStateOf(rule.cardId) }
    // Expense rules can be CREDIT; income rules can't (mirrors the wizard's payment invariant).
    val methods = PaymentMethodPreferences.selectable(enabledMethods, rule.paymentMethod, rule.transactionType)

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Forma de pagamento",
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            rule.patterns.joinToString(", ").ifBlank { "sem padrão" },
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.text3,
        )
        Spacer(Modifier.height(16.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            methods.forEach { m ->
                SelectChip(
                    label = m.label(),
                    selected = method == m,
                    onClick = {
                        // Tap the selected method again to clear it (rule ends up with no method).
                        method = m.takeIf { method != m }
                        if (method != PaymentMethod.CREDIT) cardId = null
                    },
                )
            }
        }

        if (method == PaymentMethod.CREDIT && cards.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Cartão", style = PocketTheme.typography.bodySm, color = PocketTheme.colors.text2)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cards.forEach { card ->
                    SelectChip(
                        label = card.name,
                        selected = cardId == card.id,
                        onClick = { cardId = card.id },
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        // A CREDIT method needs a card selected before it can be saved.
        val canSave = !saving && (method != PaymentMethod.CREDIT || cardId != null)
        PocketButton(
            text = "Salvar",
            onClick = { onSave(method, cardId) },
            enabled = canSave,
            fillMaxWidth = true,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = PocketTheme.colors.accentBg.takeIf { selected } ?: PocketTheme.colors.surface
    val border = PocketTheme.colors.accent.takeIf { selected } ?: PocketTheme.colors.line
    Text(
        text = label,
        style = PocketTheme.typography.bodySm,
        color = PocketTheme.colors.text.takeIf { selected } ?: PocketTheme.colors.text2,
        modifier = Modifier
            .border(1.dp, border, PocketTheme.shapes.pill)
            .background(bg, PocketTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
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
