package com.resolveprogramming.pocketcounter.ui.transacoes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.LedgerGroup
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.effectiveTagIds
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonSize
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketSegmented
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.SegmentOption
import com.resolveprogramming.pocketcounter.ui.components.SquareIconButton
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.theme.pocketCardShadow
import com.resolveprogramming.pocketcounter.ui.wizard.label
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TransacoesContent(
    padding: PaddingValues,
    onBack: () -> Unit,
    viewModel: TransacoesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = remember { PocketToastState() }

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        toastState.show(message)
        viewModel.consumeToast()
    }

    val isIncome = state.typeFilter == TransactionType.INCOME
    var confirmCopyData by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TransacoesTopBar(
                    searchOpen = state.searchOpen,
                    onBack = onBack,
                    onToggleSearch = viewModel::toggleSearch,
                )

                MonthStepperRow(
                    label = state.monthLabel,
                    onPrev = { viewModel.stepMonth(-1) },
                    onNext = { viewModel.stepMonth(1) },
                )

                TypeAndViewBar(
                    typeFilter = state.typeFilter,
                    groupMode = state.groupMode,
                    onSelectType = viewModel::setTypeFilter,
                    onSelectMode = viewModel::setGroupMode,
                )

                SummaryLine(
                    total = state.typeTotal,
                    count = state.typeCount,
                    isIncome = isIncome,
                    onlyFixo = state.ledgerFilter == LedgerFilter.FIXOS,
                    onToggleFixo = {
                        val next = LedgerFilter.TODOS
                            .takeIf { state.ledgerFilter == LedgerFilter.FIXOS }
                            ?: LedgerFilter.FIXOS
                        viewModel.setLedgerFilter(next)
                    },
                    onGenerateBalance = { confirmCopyData = true },
                )

                if (state.searchOpen) {
                    SearchField(query = state.query, onQueryChange = viewModel::setQuery)
                }

                val isLista = state.groupMode == GroupMode.LISTA
                val emptyForMode = state.listItems.isEmpty().takeIf { isLista }
                    ?: state.ledgerGroups.isEmpty()
                val onToggleStatus: (HistoryItem) -> Unit = { item ->
                    if (item.statusPayment == PaymentStatus.PAID) viewModel.markPending(item.id)
                    else viewModel.markPaid(item.id)
                }
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = PocketTheme.colors.accent)
                    }

                    emptyForMode -> EmptyState(
                        message = emptyMessage(
                            isIncome = isIncome,
                            searching = state.query.isNotBlank(),
                            onlyFixo = state.ledgerFilter == LedgerFilter.FIXOS,
                        ),
                    )

                    isLista -> LedgerCardList(
                        rows = state.listItems,
                        meta = { txMeta(it, state.cards, state.tags, state.contexts) },
                        onRowClick = viewModel::openDetail,
                        onToggleStatus = onToggleStatus,
                        onTogglePin = viewModel::toggleFixo,
                    )

                    else -> CategoryCardList(
                        groups = state.ledgerGroups,
                        meta = { txMeta(it, state.cards, state.tags, state.contexts) },
                        onRowClick = viewModel::openDetail,
                        onToggleStatus = onToggleStatus,
                        onTogglePin = viewModel::toggleFixo,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
            ) {
                ExtendedAddFab(
                    type = state.typeFilter,
                    onClick = { viewModel.openAdd(state.typeFilter) },
                )
            }
        }

        PocketToastHost(state = toastState)
    }

    state.detailTarget?.let { item ->
        TransacaoDetailSheet(
            item = item,
            tagNames = effectiveTagIds(item.tagIds, emptyList())
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
        TagEditSheet(
            type = item.type,
            initialTagIds = effectiveTagIds(item.tagIds, emptyList()),
            tags = state.tags.values.toList(),
            contexts = state.contexts,
            onSave = { selected -> viewModel.saveTags(item, selected) },
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

    if (confirmCopyData) {
        AlertDialog(
            onDismissRequest = { confirmCopyData = false },
            title = { Text("Copiar dados?", color = PocketTheme.colors.text) },
            text = {
                Text(
                    "As contas fixas do mês anterior serão copiadas para este mês.",
                    color = PocketTheme.colors.text2,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmCopyData = false
                        viewModel.generateBalance()
                    },
                ) {
                    Text("Copiar", color = PocketTheme.colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmCopyData = false }) {
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
            initialType = (state.formMode as? FormMode.Add)?.initialType,
            cards = state.cards,
            tags = state.tags.values.toList(),
            contexts = state.contexts,
            onSave = viewModel::saveForm,
            onDismiss = viewModel::closeForm,
        )
    }
}

@Composable
private fun TransacoesTopBar(
    searchOpen: Boolean,
    onBack: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SquareIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar",
                onClick = onBack,
            )
            Box(Modifier.padding(start = 12.dp)) {
                Text(
                    text = "Transações",
                    style = PocketTheme.typography.screenH1,
                    color = PocketTheme.colors.text,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(
                    1.dp,
                    PocketTheme.colors.accent.takeIf { searchOpen } ?: PocketTheme.colors.line,
                    PocketTheme.shapes.icon,
                )
                .background(
                    PocketTheme.colors.accentBg.takeIf { searchOpen } ?: PocketTheme.colors.surface,
                    PocketTheme.shapes.icon,
                )
                .clickable(onClick = onToggleSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Buscar",
                modifier = Modifier.size(20.dp),
                tint = PocketTheme.colors.accent.takeIf { searchOpen } ?: PocketTheme.colors.text2,
            )
        }
    }
}

@Composable
private fun MonthStepperRow(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Mês anterior",
            onClick = onPrev,
        )
        Text(
            text = label.replaceFirstChar { it.uppercase() },
            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.text,
        )
        StepperButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Próximo mês",
            onClick = onNext,
        )
    }
}

@Composable
private fun StepperButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.icon)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.icon)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = PocketTheme.colors.text2,
        )
    }
}

@Composable
private fun TypeAndViewBar(
    typeFilter: TransactionType,
    groupMode: GroupMode,
    onSelectType: (TransactionType) -> Unit,
    onSelectMode: (GroupMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PocketSegmented(
            options = listOf(SegmentOption("Despesas"), SegmentOption("Receitas")),
            selectedIndex = 0.takeIf { typeFilter == TransactionType.EXPENSE } ?: 1,
            onSelect = { index ->
                onSelectType(TransactionType.EXPENSE.takeIf { index == 0 } ?: TransactionType.INCOME)
            },
            modifier = Modifier.weight(1f),
        )
        ViewToggle(groupMode = groupMode, onSelect = onSelectMode)
    }
}

@Composable
private fun ViewToggle(groupMode: GroupMode, onSelect: (GroupMode) -> Unit) {
    val trackShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .background(PocketTheme.colors.surface2, trackShape)
            .border(1.dp, PocketTheme.colors.line, trackShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        ViewToggleButton(
            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
            contentDescription = "Ver em lista",
            selected = groupMode == GroupMode.LISTA,
            onClick = { onSelect(GroupMode.LISTA) },
        )
        ViewToggleButton(
            icon = Icons.Filled.GridView,
            contentDescription = "Ver por categoria",
            selected = groupMode == GroupMode.CONTEXTO,
            onClick = { onSelect(GroupMode.CONTEXTO) },
        )
    }
}

@Composable
private fun ViewToggleButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val segShape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .size(38.dp)
            .then(run { if (selected) return@run Modifier.pocketCardShadow(segShape); Modifier })
            .background(PocketTheme.colors.surface.takeIf { selected } ?: Color.Transparent, segShape)
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            tint = PocketTheme.colors.text.takeIf { selected } ?: PocketTheme.colors.text3,
        )
    }
}

@Composable
private fun SummaryLine(
    total: java.math.BigDecimal,
    count: Int,
    isIncome: Boolean,
    onlyFixo: Boolean,
    onToggleFixo: () -> Unit,
    onGenerateBalance: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            AmountText(
                amount = total,
                color = PocketTheme.colors.income.takeIf { isIncome } ?: PocketTheme.colors.expense,
                style = PocketTheme.typography.monoSummary,
            )
            val noun = "receita".takeIf { isIncome } ?: "despesa"
            val plural = noun.takeIf { count == 1 } ?: "${noun}s"
            Text(
                text = "$count $plural" + (" · fixos".takeIf { onlyFixo } ?: ""),
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .border(
                    1.dp,
                    PocketTheme.colors.accent.takeIf { onlyFixo } ?: PocketTheme.colors.line,
                    PocketTheme.shapes.icon,
                )
                .background(
                    PocketTheme.colors.accentBg.takeIf { onlyFixo } ?: PocketTheme.colors.surface,
                    PocketTheme.shapes.icon,
                )
                .clickable(
                    role = Role.Switch,
                    onClickLabel = "Mostrar todos".takeIf { onlyFixo } ?: "Mostrar só fixos",
                    onClick = onToggleFixo,
                )
                .semantics { stateDescription = "Só fixos".takeIf { onlyFixo } ?: "Todos" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = "Mostrar só fixos",
                modifier = Modifier.size(18.dp),
                tint = PocketTheme.colors.accent.takeIf { onlyFixo } ?: PocketTheme.colors.text2,
            )
        }
        PocketButton(
            text = "Copiar Dados",
            onClick = onGenerateBalance,
            variant = PocketButtonVariant.SOFT,
            size = PocketButtonSize.SMALL,
            leading = {
                Icon(
                    imageVector = Icons.Filled.Autorenew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PocketTheme.colors.text2,
                )
            },
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
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
                            "Buscar por descrição, tag ou valor…",
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
private fun LedgerCardList(
    rows: List<HistoryItem>,
    meta: (HistoryItem) -> TxMeta,
    onRowClick: (HistoryItem) -> Unit,
    onToggleStatus: (HistoryItem) -> Unit,
    onTogglePin: (HistoryItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        item { Spacer(Modifier.size(8.dp)) }
        items(rows, key = { it.id }) { item ->
            TxCard(
                item = item,
                meta = meta(item),
                onClick = { onRowClick(item) },
                onToggleStatus = { onToggleStatus(item) },
                onTogglePin = { onTogglePin(item) },
            )
        }
        item { Spacer(Modifier.size(80.dp)) }
    }
}

@Composable
private fun CategoryCardList(
    groups: List<LedgerGroup>,
    meta: (HistoryItem) -> TxMeta,
    onRowClick: (HistoryItem) -> Unit,
    onToggleStatus: (HistoryItem) -> Unit,
    onTogglePin: (HistoryItem) -> Unit,
) {
    val sorted = groups.sortedByDescending { it.subtotal }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        sorted.forEach { group ->
            item(key = "h_${group.id}") {
                CategoryHeader(group = group)
            }
            items(group.items, key = { "${group.id}_${it.id}" }) { item ->
                TxCard(
                    item = item,
                    meta = meta(item),
                    onClick = { onRowClick(item) },
                    onToggleStatus = { onToggleStatus(item) },
                    onTogglePin = { onTogglePin(item) },
                )
            }
        }
        item { Spacer(Modifier.size(80.dp)) }
    }
}

@Composable
private fun CategoryHeader(group: LedgerGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(
                    group.color?.let { Color(it) } ?: PocketTheme.colors.text3,
                    PocketTheme.shapes.pill,
                ),
        )
        Text(
            text = group.title.uppercase(),
            style = PocketTheme.typography.sectionHeader,
            color = PocketTheme.colors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        AmountText(
            amount = group.subtotal,
            color = PocketTheme.colors.text2,
            style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.SemiBold),
        )
    }
}

/** Resolved secondary-line data for a row: its date, leading tag chip, +N badge, and a payment label. */
private data class TxMeta(
    val date: String,
    val tagName: String?,
    val tagColor: Long?,
    val extraTags: Int,
    val payment: String,
)

private fun txMeta(
    item: HistoryItem,
    cards: List<CreditCard>,
    tags: Map<String, Tag>,
    contexts: List<TagContext>,
): TxMeta {
    val payment = run {
        if (item.paymentMethod != PaymentMethod.CREDIT) return@run item.paymentMethod?.label().orEmpty()
        val cardName = item.cardId?.let { id -> cards.firstOrNull { it.id == id }?.name }
        "Cartão $cardName".takeIf { cardName != null } ?: "Crédito"
    }
    val tagIds = item.tagIds.orEmpty()
    val first = tagIds.firstOrNull()?.let { tags[it] }
    // Tags rarely carry their own color; fall back to their context (category) color so the
    // chip dot is colored like the design instead of grey.
    val tagColor = first?.color ?: contexts.firstOrNull { it.id == first?.idContext }?.color
    return TxMeta(
        date = formatTxDate(item.date),
        tagName = first?.name,
        tagColor = tagColor,
        extraTags = (tagIds.size - 1).coerceAtLeast(0),
        payment = payment,
    )
}

private val txDateLocale = Locale("pt", "BR")

/** Short row date: "Hoje" / "Ontem" / "04 jun". */
private fun formatTxDate(date: LocalDate): String {
    val today = LocalDate.now()
    if (date == today) return "Hoje"
    if (date == today.minusDays(1)) return "Ontem"
    val month = date.month.getDisplayName(TextStyle.SHORT, txDateLocale).trimEnd('.').lowercase(txDateLocale)
    return "%02d %s".format(date.dayOfMonth, month)
}

private val TX_CARD_SHAPE = RoundedCornerShape(16.dp)

@Composable
private fun TxCard(
    item: HistoryItem,
    meta: TxMeta,
    onClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onTogglePin: () -> Unit,
) {
    val paid = item.statusPayment == PaymentStatus.PAID
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .height(IntrinsicSize.Min)
            .clip(TX_CARD_SHAPE)
            .background(PocketTheme.colors.surface, TX_CARD_SHAPE)
            .border(1.dp, PocketTheme.colors.line, TX_CARD_SHAPE),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusCell(paid = paid, onClick = onToggleStatus)
        VerticalDivider(color = PocketTheme.colors.line)
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayTitle(),
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(3.dp))
                TxMetaRow(meta = meta)
            }
            Spacer(Modifier.size(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                AmountText(
                    amount = item.amount,
                    type = item.type,
                    showSign = true,
                    style = PocketTheme.typography.monoSm.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    ),
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = "Paga".takeIf { paid }?.uppercase() ?: "Pendente".uppercase(),
                    style = PocketTheme.typography.label.copy(fontSize = 9.5.sp),
                    color = PocketTheme.colors.income.takeIf { paid } ?: PocketTheme.colors.warn,
                )
            }
        }
        VerticalDivider(color = PocketTheme.colors.line)
        PinCell(pinned = item.isFixo, onClick = onTogglePin)
    }
}

@Composable
private fun StatusCell(paid: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(46.dp)
            .clickable(
                role = Role.Button,
                onClickLabel = "Paga — marcar como pendente".takeIf { paid }
                    ?: "Pendente — marcar como paga",
                onClick = onClick,
            )
            .semantics { stateDescription = "Paga".takeIf { paid } ?: "Pendente" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle.takeIf { paid } ?: Icons.Outlined.Schedule,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = PocketTheme.colors.income.takeIf { paid } ?: PocketTheme.colors.warn,
        )
    }
}

@Composable
private fun PinCell(pinned: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(42.dp)
            .clickable(
                role = Role.Switch,
                onClickLabel = "Remover dos fixos".takeIf { pinned } ?: "Marcar como fixo",
                onClick = onClick,
            )
            .semantics { stateDescription = "Fixo".takeIf { pinned } ?: "Avulso" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PushPin,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .alpha(1f.takeIf { pinned } ?: 0.42f),
            tint = PocketTheme.colors.accent.takeIf { pinned } ?: PocketTheme.colors.text3,
        )
    }
}

@Composable
private fun TxMetaRow(meta: TxMeta) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = meta.date,
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.text3,
            maxLines = 1,
        )
        meta.tagName?.let { tag ->
            Text(text = "·", style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
            TagChip(name = tag, color = meta.tagColor)
            if (meta.extraTags > 0) {
                Text(
                    text = "+${meta.extraTags}",
                    style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text3,
                )
            }
        }
        if (meta.payment.isNotBlank()) {
            Text(text = "·", style = PocketTheme.typography.bodyXs, color = PocketTheme.colors.text3)
            Text(
                text = meta.payment,
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun TagChip(name: String, color: Long?) {
    Row(
        modifier = Modifier
            .clip(PocketTheme.shapes.pill)
            .background(PocketTheme.colors.surface2, PocketTheme.shapes.pill)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.pill)
            .padding(start = 7.dp, end = 9.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    color?.let { Color(it) } ?: PocketTheme.colors.text3,
                    PocketTheme.shapes.pill,
                ),
        )
        Text(
            text = name,
            style = PocketTheme.typography.bodyXs,
            color = PocketTheme.colors.text2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun emptyMessage(isIncome: Boolean, searching: Boolean, onlyFixo: Boolean): String {
    if (searching) return "Nenhuma transação encontrada."
    val noun = "receita".takeIf { isIncome } ?: "despesa"
    if (onlyFixo) return "Nenhuma $noun fixa neste mês."
    return "Nenhuma $noun neste mês."
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Text(
            text = message,
            style = PocketTheme.typography.body,
            color = PocketTheme.colors.text3,
        )
    }
}

@Composable
private fun ExtendedAddFab(type: TransactionType, onClick: () -> Unit) {
    val isIncome = type == TransactionType.INCOME
    val label = "Receita".takeIf { isIncome } ?: "Despesa"
    Row(
        modifier = Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PocketTheme.colors.accent, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Adicionar $label",
            modifier = Modifier.size(22.dp),
            tint = PocketTheme.colors.accentInk,
        )
        Text(
            text = label,
            style = PocketTheme.typography.button,
            color = PocketTheme.colors.accentInk,
        )
    }
}
