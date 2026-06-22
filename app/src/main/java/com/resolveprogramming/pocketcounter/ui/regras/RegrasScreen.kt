package com.resolveprogramming.pocketcounter.ui.regras

import androidx.compose.foundation.background
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
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.domain.model.Source
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.ManageTopBar
import com.resolveprogramming.pocketcounter.ui.components.PocketBadge
import com.resolveprogramming.pocketcounter.ui.components.PocketBadgeVariant
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
                ManageTopBar(title = "Regras aprendidas", onBack = onBack)
                if (!state.isLoading && state.rules.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.rules.size, key = { state.rules[it].id ?: "idx_$it" }) { i ->
                            RegraCard(
                                rule = state.rules[i],
                                sourcesById = state.sourcesById,
                                paymentsById = state.paymentSourcesById,
                                tagsById = state.tagsById,
                                contextsById = state.contextsById,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RegraCard(
    rule: ClassificationRule,
    sourcesById: Map<String, Source>,
    paymentsById: Map<String, PaymentSource>,
    tagsById: Map<String, Tag>,
    contextsById: Map<String, TagContext>,
    onDelete: (String) -> Unit,
) {
    PocketCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.pattern,
                    style = PocketTheme.typography.monoSm,
                    color = PocketTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                rule.transactionType?.let { type ->
                    PocketBadge(
                        text = if (type == TransactionType.INCOME) "Receita" else "Despesa",
                        variant = if (type == TransactionType.INCOME) {
                            PocketBadgeVariant.INCOME
                        } else {
                            PocketBadgeVariant.EXPENSE
                        },
                    )
                    Spacer(Modifier.size(8.dp))
                }
                rule.id?.let { id -> SquareIconButton(glyph = "×", onClick = { onDelete(id) }) }
            }

            Spacer(Modifier.height(6.dp))
            val outcome = listOfNotNull(
                rule.idPaymentSource?.let { paymentsById[it]?.name },
                rule.idSource?.let { sourcesById[it]?.name },
                rule.tags.size.takeIf { it > 0 }?.let { "+ $it tags" },
            ).joinToString(" · ")
            Text(
                text = if (outcome.isBlank()) "sem destino" else "→ $outcome",
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
