package com.resolveprogramming.pocketcounter.ui.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.InvoiceItem
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.SummaryGroup
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.UNCATEGORIZED_GROUP_IDS
import com.resolveprogramming.pocketcounter.domain.model.faturaDonutSlices
import com.resolveprogramming.pocketcounter.ui.components.FormLabel
import com.resolveprogramming.pocketcounter.ui.components.MonthStepperRow
import com.resolveprogramming.pocketcounter.ui.components.FormTextField
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.components.PocketChip
import com.resolveprogramming.pocketcounter.ui.components.PocketChipVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketDonutChart
import com.resolveprogramming.pocketcounter.ui.components.PocketSegmented
import com.resolveprogramming.pocketcounter.ui.components.SegmentOption
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.relatorio.ProportionBar
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun CartoesScreen(
    onBack: () -> Unit,
    onNav: (TabId) -> Unit = {},
    viewModel: CartoesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val toastState = remember { PocketToastState() }

    var classifyTarget by remember { mutableStateOf<ClassifyTarget?>(null) }

    LaunchedEffect(state.toastMessage) {
        val message = state.toastMessage ?: return@LaunchedEffect
        toastState.show(message)
        viewModel.consumeToast()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PocketTheme.colors.bg,
            topBar = {
                CartoesHeader(
                    cardCount = state.invoices.size,
                    grandTotal = formatter.format(state.grandTotal),
                    onBack = onBack,
                    onAddCard = viewModel::openAddCard,
                )
            },
            bottomBar = {
                PocketTabBar(active = TabId.CARTOES, onNav = onNav)
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                MonthStepperRow(
                    label = state.monthLabel,
                    onPrev = { viewModel.stepMonth(-1) },
                    onNext = { viewModel.stepMonth(1) },
                )
                if (state.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = PocketTheme.colors.accent)
                    }
                } else {
                    CartoesCarousel(
                        invoices = state.invoices,
                        contexts = state.allContexts,
                        categoriesByCardId = state.categoriesByCardId,
                        categoryA11yByCardId = state.categoryA11yByCardId,
                        formatter = formatter,
                        onItemClick = { invoice, item ->
                            classifyTarget = ClassifyTarget(invoice.card, item)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        PocketToastHost(state = toastState)
    }

    val target = classifyTarget
    if (target != null) {
        ClassifyPurchaseSheet(
            card = target.card,
            item = target.item,
            allTags = state.allTags,
            formatter = formatter,
            onDismiss = { classifyTarget = null },
            onSave = { selectedTags, learnRule ->
                viewModel.classifyPurchase(target.item, target.card, selectedTags, learnRule)
                classifyTarget = null
            },
        )
    }

    if (state.showAddCard) {
        AddCardSheet(
            isSaving = state.isSavingCard,
            onDismiss = viewModel::dismissAddCard,
            onSave = { name, brand, closingDay, color ->
                viewModel.addCard(name, brand, closingDay, color)
            },
        )
    }
}

private data class ClassifyTarget(
    val card: CreditCard,
    val item: InvoiceItem,
)

private enum class FaturaViewMode { ITENS, CATEGORIAS }

@Composable
private fun CartoesCarousel(
    invoices: List<OpenInvoice>,
    contexts: List<TagContext>,
    categoriesByCardId: Map<String, List<SummaryGroup>>,
    categoryA11yByCardId: Map<String, String>,
    formatter: NumberFormat,
    onItemClick: (OpenInvoice, InvoiceItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (invoices.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Nenhuma fatura aberta.",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { invoices.size })
    val scope = rememberCoroutineScope()
    val multi = invoices.size > 1
    // View mode is ephemeral, per-card; ITENS keeps today's behaviour as the default.
    val viewModes = remember { mutableStateMapOf<String, FaturaViewMode>() }
    val reducedMotion = LocalReducedMotion.current

    Column(modifier) {
        // Tappable card-name pills double as the page indicator — far more discoverable
        // than dots, and they name what's on each page.
        if (multi) {
            CardPillRow(
                invoices = invoices,
                active = pagerState.currentPage,
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            )
        }

        // The pager wraps ONLY the gradient card tile + meta row. With horizontal
        // contentPadding the neighbouring cards peek at the edges, making the swipe
        // affordance obvious. The invoice list lives below, full-width.
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 28.dp).takeIf { multi } ?: PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp.takeIf { multi } ?: 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) { page ->
            FaturaCard(invoice = invoices[page], formatter = formatter)
        }

        val current = invoices[pagerState.currentPage]
        val mode = viewModes[current.card.id] ?: FaturaViewMode.ITENS

        PocketSegmented(
            options = listOf(SegmentOption("Itens"), SegmentOption("Categorias")),
            selectedIndex = mode.ordinal,
            onSelect = { viewModes[current.card.id] = FaturaViewMode.entries[it] },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )

        Crossfade(
            targetState = mode,
            animationSpec = tween(durationMillis = if (reducedMotion) 0 else 180),
            label = "faturaBody",
            modifier = Modifier.fillMaxSize(),
        ) { activeMode ->
            when (activeMode) {
                FaturaViewMode.ITENS -> FaturaItensBody(
                    invoice = current,
                    contexts = contexts,
                    formatter = formatter,
                    onItemClick = onItemClick,
                )

                FaturaViewMode.CATEGORIAS -> FaturaCategoriasBody(
                    invoice = current,
                    groups = categoriesByCardId[current.card.id].orEmpty(),
                    a11y = categoryA11yByCardId[current.card.id].orEmpty(),
                    formatter = formatter,
                )
            }
        }
    }
}

@Composable
private fun FaturaItensBody(
    invoice: OpenInvoice,
    contexts: List<TagContext>,
    formatter: NumberFormat,
    onItemClick: (OpenInvoice, InvoiceItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(invoice.items, key = { it.itemId ?: it.transactionId }) { item ->
            InvoiceItemRow(
                item = item,
                contexts = contexts,
                formatter = formatter,
                onClick = { onItemClick(invoice, item) },
            )
        }

        item {
            Text(
                text = "As compras chegam como notificações soltas e o PocketCounter junta tudo na fatura do cartão certo.",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun FaturaCategoriasBody(
    invoice: OpenInvoice,
    groups: List<SummaryGroup>,
    a11y: String,
    formatter: NumberFormat,
) {
    if (groups.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Sem compras para categorizar.",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val allUncategorized = groups.all { it.id in UNCATEGORIZED_GROUP_IDS }
    val slices = faturaDonutSlices(groups)
    // Show only real (positive) shares in the list, matching the donut: an over-itemized fatura's
    // signed "Sem categoria" remainder is negative and must not render as a "-20%" row.
    val rankGroups = groups.filter { it.pct > 0f }
    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            PocketCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PocketDonutChart(
                        segments = slices.map { it.color to it.pct },
                        modifier = Modifier
                            .size(168.dp)
                            .semantics { contentDescription = a11y },
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "FATURA",
                                style = PocketTheme.typography.sectionHeader,
                                color = PocketTheme.colors.text3,
                            )
                            Text(
                                text = formatter.format(invoice.total),
                                style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.Bold),
                                color = PocketTheme.colors.expense,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        if (allUncategorized) {
            item {
                Text(
                    text = "Classifique as compras para ver a divisão por categoria.",
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }

        item { Spacer(Modifier.height(4.dp)) }

        items(rankGroups, key = { it.id }) { group ->
            CategoryRankRow(
                group = group,
                expanded = expandedCategories[group.id] == true,
                onToggle = { expandedCategories[group.id] = expandedCategories[group.id] != true },
                formatter = formatter,
            )
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun CategoryRankRow(
    group: SummaryGroup,
    expanded: Boolean,
    onToggle: () -> Unit,
    formatter: NumberFormat,
) {
    val expandable = group.tags.isNotEmpty()
    val reducedMotion = LocalReducedMotion.current
    val chevronRotation by animateFloatAsState(
        targetValue = 180f.takeIf { expanded } ?: 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 180),
        label = "categoryChevron",
    )

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .let { base ->
                    if (!expandable) return@let base
                    val action = if (expanded) "Recolher" else "Expandir"
                    base.clickable(onClickLabel = "$action ${group.name}", onClick = onToggle)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(PocketTheme.shapes.pill)
                    .background(Color(group.color)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = group.name,
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = PocketTheme.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${(group.pct * 100).roundToInt()}%",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatter.format(group.total),
                style = PocketTheme.typography.monoSm,
                color = PocketTheme.colors.text,
                maxLines = 1,
                softWrap = false,
            )
            if (expandable) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = PocketTheme.colors.text3,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ProportionBar(fraction = group.pct, color = Color(group.color))

        AnimatedVisibility(
            visible = expanded && expandable,
            enter = EnterTransition.None.takeIf { reducedMotion } ?: (expandVertically() + fadeIn()),
            exit = ExitTransition.None.takeIf { reducedMotion } ?: (shrinkVertically() + fadeOut()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                group.tags.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(PocketTheme.shapes.pill)
                                .background(Color(group.color)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tag.name,
                            style = PocketTheme.typography.bodySm,
                            color = PocketTheme.colors.text2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatter.format(tag.total),
                            style = PocketTheme.typography.monoSm,
                            color = PocketTheme.colors.text2,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardPillRow(
    invoices: List<OpenInvoice>,
    active: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        invoices.forEachIndexed { index, invoice ->
            val isActive = index == active
            Box(
                modifier = Modifier
                    .clip(PocketTheme.shapes.pill)
                    .background(PocketTheme.colors.text.takeIf { isActive } ?: PocketTheme.colors.surface2)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = invoice.card.name,
                    style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.bg.takeIf { isActive } ?: PocketTheme.colors.text3,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CartoesHeader(
    cardCount: Int,
    grandTotal: String,
    onBack: () -> Unit,
    onAddCard: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.bg)
            // topBar slot isn't inset by Scaffold; clear the status bar so the header isn't
            // crammed under the clock/wifi. Background fills behind the bar (edge-to-edge).
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = PocketTheme.colors.text,
                    )
                }
                Column {
                    Text(
                        text = "Cartões",
                        style = PocketTheme.typography.stepQuestion,
                        color = PocketTheme.colors.text,
                    )
                    Text(
                        text = "Faturas abertas · $cardCount cartões",
                        style = PocketTheme.typography.bodySm,
                        color = PocketTheme.colors.text3,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "A PAGAR",
                    style = PocketTheme.typography.sectionHeader,
                    color = PocketTheme.colors.text3,
                )
                Text(
                    text = grandTotal,
                    style = PocketTheme.typography.monoSm.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = PocketTheme.colors.text,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(PocketTheme.shapes.pill)
                .background(PocketTheme.colors.surface2)
                .clickable(onClick = onAddCard)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = "+ Adicionar cartão",
                style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text2,
            )
        }
    }
}

@Composable
private fun FaturaCard(
    invoice: OpenInvoice,
    formatter: NumberFormat,
) {
    val card = invoice.card
    val usagePct = (invoice.usage * 100).toInt()
    val gradientColors = listOf(
        Color(card.gradientStart),
        Color(card.gradientEnd),
    )

    Column {
        // Gradient card tile
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PocketTheme.shapes.card)
                .background(Brush.linearGradient(gradientColors))
                .padding(18.dp),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = card.name,
                        style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    if (card.brand.isNotBlank()) {
                        Text(
                            text = card.brand,
                            style = PocketTheme.typography.bodySm,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
                if (card.last4.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "•••• •••• •••• ${card.last4}",
                        style = PocketTheme.typography.monoSm,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text(
                            text = "FATURA ABERTA",
                            style = PocketTheme.typography.sectionHeader,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        Text(
                            text = formatter.format(invoice.total),
                            style = PocketTheme.typography.monoSm.copy(
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "VENCIMENTO",
                            style = PocketTheme.typography.sectionHeader,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        Text(
                            text = invoice.dueLabel,
                            style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Meta row: closes in N days + usage badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Fecha em ",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
            )
            Text(
                text = "${invoice.closesInDays} dias",
                style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text,
            )
            Spacer(Modifier.weight(1f))
            // Limit usage is only shown when the card limit is known (the backend
            // doesn't store it yet); otherwise the badge + bar are hidden.
            if (card.limit.signum() > 0) {
                Box(
                    modifier = Modifier
                        .background(PocketTheme.colors.surface2, PocketTheme.shapes.pill)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "$usagePct% do limite",
                        style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.Medium),
                        color = PocketTheme.colors.text2,
                    )
                }
            }
        }

        if (card.limit.signum() > 0) {
            Spacer(Modifier.height(6.dp))

            // Limit bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(PocketTheme.shapes.pill)
                    .background(PocketTheme.colors.line),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(invoice.usage.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(PocketTheme.colors.text.copy(alpha = 0.55f)),
                )
            }
        }
    }
}

@Composable
private fun InvoiceItemRow(
    item: InvoiceItem,
    contexts: List<TagContext>,
    formatter: NumberFormat,
    onClick: () -> Unit,
) {
    val initial = item.name
        .filter { it.isLetter() }
        .firstOrNull()
        ?.uppercaseChar()
        ?.toString() ?: "?"

    val dayFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale("pt", "BR"))
    val dateStr = item.date.format(dayFormatter).lowercase(Locale("pt", "BR"))
    val tag = item.tags.firstOrNull()
    val tagLabel = tag?.name ?: "classificar"
    // Tags rarely carry their own color; fall back to their context (category) color so the chip
    // dot is colored like the design instead of grey, then to the accent token as a last resort.
    val dotColor = run {
        if (tag == null) return@run PocketTheme.colors.warn
        val argb = tag.color ?: contexts.firstOrNull { it.id == tag.idContext }?.color
        argb?.let { Color(it) } ?: PocketTheme.colors.accent
    }
    val labelColor = PocketTheme.colors.warn.takeIf { tag == null } ?: PocketTheme.colors.text2

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Initial chip 34×34
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(PocketTheme.shapes.chip)
                .background(PocketTheme.colors.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initial,
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = PocketTheme.colors.text2,
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                    color = PocketTheme.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.installmentLabel != null) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(PocketTheme.colors.accentBg, PocketTheme.shapes.pill)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = item.installmentLabel,
                            style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                            color = PocketTheme.colors.accent,
                        )
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = dateStr,
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(PocketTheme.shapes.pill)
                        .background(dotColor),
                )
                Text(
                    text = tagLabel,
                    style = PocketTheme.typography.bodyXs,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = formatter.format(item.amount),
            style = PocketTheme.typography.monoSm,
            color = PocketTheme.colors.text,
            maxLines = 1,
            softWrap = false,
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = PocketTheme.colors.text3,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassifyPurchaseSheet(
    card: CreditCard,
    item: InvoiceItem,
    allTags: List<Tag>,
    formatter: NumberFormat,
    onDismiss: () -> Unit,
    onSave: (selectedTags: List<Tag>, learnRule: Boolean) -> Unit,
) {
    var selectedIds by remember { mutableStateOf(item.tags.map { it.id }.toSet()) }
    var learnRule by remember { mutableStateOf(item.tags.isEmpty()) }

    val dayFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale("pt", "BR"))
    val dateStr = item.date.format(dayFormatter).lowercase(Locale("pt", "BR"))

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Classificar compra",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        Spacer(Modifier.height(16.dp))

        SummaryRow(
            label = "Compra",
            value = item.name + (item.installmentLabel?.let { " · $it" } ?: ""),
        )
        Spacer(Modifier.height(8.dp))
        SummaryRow(
            label = "Valor",
            value = "${formatter.format(item.amount)} · $dateStr",
            mono = true,
        )
        Spacer(Modifier.height(8.dp))
        SummaryRow(label = "Cartão", value = card.name)

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Tags",
            style = PocketTheme.typography.sectionHeader,
            color = PocketTheme.colors.text3,
        )
        Spacer(Modifier.height(10.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allTags.forEach { tag ->
                val isSelected = tag.id in selectedIds
                PocketChip(
                    label = tag.name,
                    variant = PocketChipVariant.ON.takeIf { isSelected } ?: PocketChipVariant.DEFAULT,
                    onClick = {
                        selectedIds = (selectedIds - tag.id).takeIf { isSelected } ?: (selectedIds + tag.id)
                    },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PocketTheme.colors.accentBg, PocketTheme.shapes.card)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aprender este padrão",
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Próximas compras contendo \"${item.name}\" no ${card.name} serão classificadas sozinhas.",
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text2,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = learnRule,
                onCheckedChange = { learnRule = it },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = PocketTheme.colors.accent,
                    checkedThumbColor = PocketTheme.colors.accentInk,
                ),
            )
        }

        Spacer(Modifier.height(16.dp))

        val selectedTags = allTags.filter { it.id in selectedIds }
        val canSave = selectedTags.isNotEmpty()
        val ctaLabel = "Salvar e criar regra".takeIf { learnRule } ?: "Salvar classificação"

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PocketTheme.shapes.card)
                .background(
                    PocketTheme.colors.accent.takeIf { canSave } ?: PocketTheme.colors.surface2,
                )
                .let { base -> run { if (canSave) return@run base.clickable { onSave(selectedTags, learnRule) }; base } }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ctaLabel,
                style = PocketTheme.typography.button,
                color = PocketTheme.colors.accentInk.takeIf { canSave } ?: PocketTheme.colors.text3,
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun AddCardSheet(
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, brand: String?, closingDay: Int?, color: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var closingDay by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }

    val parsedDay = closingDay.toIntOrNull()
    val dayValid = parsedDay != null && parsedDay in 1..31
    val canSave = !isSaving && name.isNotBlank() && dayValid

    PocketBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Adicionar cartão",
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        Spacer(Modifier.height(16.dp))

        FormLabel("Nome")
        Spacer(Modifier.height(6.dp))
        FormTextField(value = name, onValueChange = { name = it }, placeholder = "Ex.: Nubank")

        Spacer(Modifier.height(14.dp))

        FormLabel("Dia de fechamento")
        Spacer(Modifier.height(6.dp))
        FormTextField(
            value = closingDay,
            onValueChange = { closingDay = it.filter { ch -> ch.isDigit() }.take(2) },
            placeholder = "1 a 31",
            keyboardType = KeyboardType.Number,
        )

        Spacer(Modifier.height(14.dp))

        FormLabel("Bandeira (opcional)")
        Spacer(Modifier.height(6.dp))
        FormTextField(value = brand, onValueChange = { brand = it }, placeholder = "Ex.: Mastercard")

        Spacer(Modifier.height(14.dp))

        FormLabel("Cor (opcional)")
        Spacer(Modifier.height(6.dp))
        FormTextField(value = color, onValueChange = { color = it }, placeholder = "#8B00FF")

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PocketTheme.shapes.card)
                .background(PocketTheme.colors.accent.takeIf { canSave } ?: PocketTheme.colors.surface2)
                .let { base ->
                    run {
                        if (canSave) {
                            return@run base.clickable {
                                onSave(
                                    name.trim(),
                                    brand.trim().takeIf { b -> b.isNotBlank() },
                                    parsedDay,
                                    color.trim().takeIf { c -> c.isNotBlank() },
                                )
                            }
                        }
                        base
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Salvando…".takeIf { isSaving } ?: "Adicionar cartão",
                style = PocketTheme.typography.button,
                color = PocketTheme.colors.accentInk.takeIf { canSave } ?: PocketTheme.colors.text3,
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    mono: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text3,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = PocketTheme.typography.monoSm.takeIf { mono }
                ?: PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
            color = PocketTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}
