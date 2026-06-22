package com.resolveprogramming.pocketcounter.ui.transacoes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.ExperimentalFoundationApi
import com.resolveprogramming.pocketcounter.domain.model.DayGroup
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.LedgerGroup
import com.resolveprogramming.pocketcounter.domain.model.effectiveTagIds
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.PocketSegmented
import com.resolveprogramming.pocketcounter.ui.components.SegmentOption
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import kotlin.math.roundToInt

@Composable
fun TransacoesScreen(
    onNav: (TabId) -> Unit,
    viewModel: TransacoesViewModel = hiltViewModel(),
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
            bottomBar = { PocketTabBar(active = TabId.TRANSACOES, onNav = onNav) },
            floatingActionButton = { AddFab(onClick = viewModel::openAdd) },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                MonthStepperBar(
                    label = state.monthLabel,
                    searchOpen = state.searchOpen,
                    onPrev = { viewModel.stepMonth(-1) },
                    onNext = { viewModel.stepMonth(1) },
                    onToggleSearch = viewModel::toggleSearch,
                    onGenerateBalance = viewModel::generateBalance,
                )

                TotalsStrip(
                    income = state.totals.income,
                    expense = state.totals.expense,
                    balance = state.totals.balance,
                )

                if (state.searchOpen) {
                    SearchField(query = state.query, onQueryChange = viewModel::setQuery)
                }

                GroupModeBar(mode = state.groupMode, onSelect = viewModel::setGroupMode)

                val emptyForMode = if (state.groupMode == GroupMode.LISTA) {
                    state.dayGroups.isEmpty()
                } else {
                    state.ledgerGroups.isEmpty()
                }
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = PocketTheme.colors.accent)
                    }

                    emptyForMode -> EmptyState(searching = state.query.isNotBlank(), month = state.monthLabel)

                    state.groupMode == GroupMode.LISTA -> LedgerList(
                        groups = state.dayGroups,
                        sourceName = { state.sources[it]?.name ?: "—" },
                        paymentName = { state.paymentSources[it]?.name.orEmpty() },
                        onRowClick = viewModel::openDetail,
                    )

                    else -> GroupedLedgerList(
                        groups = state.ledgerGroups,
                        collapsedIds = state.collapsedGroupIds,
                        canReorder = state.query.isBlank(),
                        sourceName = { state.sources[it]?.name ?: "—" },
                        paymentName = { state.paymentSources[it]?.name.orEmpty() },
                        onToggleCollapse = viewModel::toggleGroupCollapsed,
                        onRowClick = viewModel::openDetail,
                        onMoveTo = viewModel::moveItemTo,
                    )
                }
            }
        }

        PocketToastHost(state = toastState)
    }

    state.detailTarget?.let { item ->
        TransacaoDetailSheet(
            item = item,
            sourceName = state.sources[item.idSource]?.name ?: "—",
            paymentName = state.paymentSources[item.idPaymentSource]?.name.orEmpty(),
            tagNames = effectiveTagIds(item.tagIds, state.sources[item.idSource]?.tags ?: emptyList())
                .mapNotNull { state.tags[it]?.name },
            onDismiss = viewModel::closeDetail,
            onMarkPaid = { viewModel.markPaid(item.id) },
            onMarkPending = { viewModel.markPending(item.id) },
            onEdit = { viewModel.openEdit(item) },
            onEditTags = { viewModel.openTagEdit(item) },
            onDelete = { viewModel.requestDelete(item.id) },
        )
    }

    state.tagEditTarget?.let { item ->
        val source = state.sources[item.idSource]
        TagEditSheet(
            type = item.type,
            initialTagIds = effectiveTagIds(item.tagIds, source?.tags ?: emptyList()),
            inheriting = item.tagIds == null,
            sourceName = source?.name ?: "fonte",
            tags = state.tags.values.toList(),
            contexts = state.contexts,
            onSave = { selected, updateSource -> viewModel.saveTags(item, selected, updateSource) },
            onDismiss = viewModel::closeTagEdit,
        )
    }

    state.confirmDeleteId?.let {
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            title = { Text("Excluir transação?", color = PocketTheme.colors.text) },
            text = {
                Text(
                    "Essa ação não pode ser desfeita.",
                    color = PocketTheme.colors.text2,
                )
            },
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

    if (state.formMode != null) {
        TransacaoFormSheet(
            mode = state.formMode!!,
            initialItem = (state.formMode as? FormMode.Edit)?.let { edit ->
                state.items.firstOrNull { it.id == edit.itemId }
            },
            cards = state.cards,
            tags = state.tags.values.toList(),
            contexts = state.contexts,
            onSave = viewModel::saveForm,
            onDismiss = viewModel::closeForm,
        )
    }
}

@Composable
private fun MonthStepperBar(
    label: String,
    searchOpen: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggleSearch: () -> Unit,
    onGenerateBalance: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton(glyph = "‹", onClick = onPrev)
            Spacer(Modifier.size(4.dp))
            Text(
                text = label.replaceFirstChar { it.uppercase() },
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            Spacer(Modifier.size(4.dp))
            StepperButton(glyph = "›", onClick = onNext)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.icon)
                    .background(PocketTheme.colors.surface, PocketTheme.shapes.icon)
                    .clickable(onClick = onGenerateBalance),
                contentAlignment = Alignment.Center,
            ) {
                Text("↻", color = PocketTheme.colors.text2)
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .border(
                        1.dp,
                        if (searchOpen) PocketTheme.colors.accent else PocketTheme.colors.line,
                        PocketTheme.shapes.icon,
                    )
                    .background(
                        if (searchOpen) PocketTheme.colors.accentBg else PocketTheme.colors.surface,
                        PocketTheme.shapes.icon,
                    )
                    .clickable(onClick = onToggleSearch),
                contentAlignment = Alignment.Center,
            ) {
                Text("⌕", color = if (searchOpen) PocketTheme.colors.accent else PocketTheme.colors.text2)
            }
        }
    }
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.icon)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.icon)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.text,
        )
    }
}

@Composable
private fun TotalsStrip(
    income: java.math.BigDecimal,
    expense: java.math.BigDecimal,
    balance: java.math.BigDecimal,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TotalCell("Receitas", income, PocketTheme.colors.income, Modifier.weight(1f))
        TotalCell("Despesas", expense, PocketTheme.colors.expense, Modifier.weight(1f))
        TotalCell(
            label = "Saldo",
            amount = balance,
            // Saldo is signed: red when negative, green when positive (matches Relatório).
            color = if (balance.signum() < 0) PocketTheme.colors.expense else PocketTheme.colors.income,
            modifier = Modifier.weight(1f),
            showSign = true,
        )
    }
}

@Composable
private fun TotalCell(
    label: String,
    amount: java.math.BigDecimal,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    showSign: Boolean = false,
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = PocketTheme.typography.label,
            color = PocketTheme.colors.text3,
        )
        Spacer(Modifier.size(2.dp))
        AmountText(
            amount = amount,
            color = color,
            showSign = showSign,
            style = PocketTheme.typography.monoSm,
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = PocketTheme.typography.body.copy(color = PocketTheme.colors.text),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, PocketTheme.colors.line2, PocketTheme.shapes.chip)
                        .background(PocketTheme.colors.surface, PocketTheme.shapes.chip)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    if (query.isEmpty()) {
                        Text(
                            "Buscar por fonte, meio, tag ou valor…",
                            style = PocketTheme.typography.body,
                            color = PocketTheme.colors.text3,
                        )
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun LedgerList(
    groups: List<DayGroup>,
    sourceName: (String) -> String,
    paymentName: (String) -> String,
    onRowClick: (HistoryItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        groups.forEach { group ->
            item(key = "h_${group.date}") {
                Text(
                    text = group.label,
                    style = PocketTheme.typography.sectionHeader,
                    color = PocketTheme.colors.text3,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
            }
            items(group.items, key = { it.id }) { item ->
                TxRow(
                    item = item,
                    sourceName = sourceName(item.idSource),
                    paymentName = paymentName(item.idPaymentSource),
                    onClick = { onRowClick(item) },
                )
            }
        }
        item { Spacer(Modifier.size(80.dp)) }
    }
}

@Composable
private fun TxRow(
    item: HistoryItem,
    sourceName: String,
    paymentName: String,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sourceName,
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                    color = PocketTheme.colors.text,
                )
                Text(
                    text = paymentName,
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.statusPayment == PaymentStatus.PENDING) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(PocketTheme.colors.warn, PocketTheme.shapes.pill),
                    )
                    Spacer(Modifier.size(8.dp))
                }
                AmountText(
                    amount = item.amount,
                    type = item.type,
                    showSign = true,
                    style = PocketTheme.typography.monoSm,
                )
            }
        }
        HorizontalDivider(color = PocketTheme.colors.line)
    }
}

@Composable
private fun GroupModeBar(mode: GroupMode, onSelect: (GroupMode) -> Unit) {
    val options = listOf(SegmentOption("Lista"), SegmentOption("Por contexto"), SegmentOption("Por tag"))
    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        PocketSegmented(
            options = options,
            selectedIndex = mode.ordinal,
            onSelect = { onSelect(GroupMode.entries[it]) },
        )
    }
}

private val GROUPED_ROW_HEIGHT = 60.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupedLedgerList(
    groups: List<LedgerGroup>,
    collapsedIds: Set<String>,
    canReorder: Boolean,
    sourceName: (String) -> String,
    paymentName: (String) -> String,
    onToggleCollapse: (String) -> Unit,
    onRowClick: (HistoryItem) -> Unit,
    onMoveTo: (LedgerGroup, HistoryItem, Int) -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragGroupId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var dragOrigin by remember { mutableIntStateOf(0) }
    // Measured from the real rows so the drag-to-target mapping holds at any font scale.
    val fallbackPx = with(LocalDensity.current) { GROUPED_ROW_HEIGHT.toPx() }
    var rowHeightPx by remember { mutableFloatStateOf(fallbackPx) }

    fun targetIndex(size: Int): Int =
        (dragOrigin + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, size - 1)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
    ) {
        groups.forEach { group ->
            val collapsed = group.id in collapsedIds
            stickyHeader(key = group.id) {
                GroupHeader(group = group, collapsed = collapsed, onClick = { onToggleCollapse(group.id) })
            }
            if (!collapsed) {
                itemsIndexed(group.items, key = { _, it -> "${group.id}_${it.id}" }) { index, item ->
                    val dragging = item.id == dragId && group.id == dragGroupId
                    val groupActive = dragGroupId == group.id
                    val target = if (groupActive) targetIndex(group.items.size) else -1
                    Column {
                        if (groupActive && !dragging && index == target) DropLine()
                        Row(
                            modifier = Modifier
                                .onSizeChanged { if (dragId == null && it.height > 0) rowHeightPx = it.height.toFloat() }
                                .then(
                                    if (dragging) {
                                        Modifier.zIndex(1f).graphicsLayer {
                                            translationY = dragOffset
                                            if (!reducedMotion) {
                                                shadowElevation = 10f
                                                scaleX = 1.02f
                                                scaleY = 1.02f
                                            }
                                        }
                                    } else {
                                        Modifier
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) {
                                TxRow(
                                    item = item,
                                    sourceName = sourceName(item.idSource),
                                    paymentName = paymentName(item.idPaymentSource),
                                    onClick = { onRowClick(item) },
                                )
                            }
                            if (canReorder && group.items.size > 1) {
                                DragHandle(
                                    dragKey = "${group.id}_${item.id}_$index",
                                    onStart = { dragId = item.id; dragGroupId = group.id; dragOrigin = index; dragOffset = 0f },
                                    onDrag = { dy -> dragOffset += dy },
                                    onEnd = {
                                        val t = targetIndex(group.items.size)
                                        if (t != dragOrigin) onMoveTo(group, item, t)
                                        dragId = null; dragGroupId = null; dragOffset = 0f
                                    },
                                    onCancel = { dragId = null; dragGroupId = null; dragOffset = 0f },
                                )
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.size(80.dp)) }
    }
}

@Composable
private fun DropLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(PocketTheme.colors.accent, PocketTheme.shapes.pill),
    )
}

@Composable
private fun GroupHeader(group: LedgerGroup, collapsed: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.bg)
            .clickable(onClick = onClick)
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    group.color?.let { androidx.compose.ui.graphics.Color(it) } ?: PocketTheme.colors.text3,
                    PocketTheme.shapes.pill,
                ),
        )
        Text(
            text = group.title,
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.text,
            modifier = Modifier.weight(1f),
        )
        AmountText(
            amount = group.subtotal,
            type = group.type,
            style = PocketTheme.typography.monoSm,
        )
        Spacer(Modifier.size(6.dp))
        Text(if (collapsed) "▸" else "▾", color = PocketTheme.colors.text3)
    }
}

@Composable
private fun DragHandle(
    dragKey: Any,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(40.dp)
            .pointerInput(dragKey) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStart()
                    },
                    onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
                    onDragEnd = { onEnd() },
                    onDragCancel = { onCancel() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("⠿", style = PocketTheme.typography.body, color = PocketTheme.colors.text3)
    }
}

@Composable
private fun EmptyState(searching: Boolean, month: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(
            text = if (searching) "Nada encontrado" else "Nenhuma transação em $month",
            style = PocketTheme.typography.body,
            color = PocketTheme.colors.text3,
        )
    }
}

@Composable
private fun AddFab(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(PocketTheme.colors.accent, PocketTheme.shapes.fab)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "+",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.accentInk,
        )
    }
}
