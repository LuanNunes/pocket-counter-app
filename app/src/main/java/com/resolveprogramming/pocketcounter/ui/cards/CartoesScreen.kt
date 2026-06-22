package com.resolveprogramming.pocketcounter.ui.cards

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.InvoiceItem
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.ui.components.FormLabel
import com.resolveprogramming.pocketcounter.ui.components.FormTextField
import com.resolveprogramming.pocketcounter.ui.components.PocketBottomSheet
import com.resolveprogramming.pocketcounter.ui.components.PocketChip
import com.resolveprogramming.pocketcounter.ui.components.PocketChipVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.PocketToastHost
import com.resolveprogramming.pocketcounter.ui.components.PocketToastState
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CartoesScreen(
    onBack: () -> Unit,
    onNav: (TabId) -> Unit = {},
    viewModel: CartoesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PocketTheme.colors.accent)
        }
        return
    }

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
            CartoesCarousel(
                invoices = state.invoices,
                formatter = formatter,
                onItemClick = { invoice, item ->
                    classifyTarget = ClassifyTarget(invoice.card, item)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
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

@Composable
private fun CartoesCarousel(
    invoices: List<OpenInvoice>,
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
            contentPadding = if (multi) PaddingValues(horizontal = 28.dp) else PaddingValues(horizontal = 20.dp),
            pageSpacing = if (multi) 12.dp else 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) { page ->
            FaturaCard(invoice = invoices[page], formatter = formatter)
        }

        // Invoice list for the currently-visible card.
        val current = invoices[pagerState.currentPage]
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { Spacer(Modifier.height(12.dp)) }

            items(current.items, key = { it.itemId ?: it.transactionId }) { item ->
                InvoiceItemRow(
                    item = item,
                    formatter = formatter,
                    onClick = { onItemClick(current, item) },
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
                    .background(if (isActive) PocketTheme.colors.text else PocketTheme.colors.surface2)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = invoice.card.name,
                    style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isActive) PocketTheme.colors.bg else PocketTheme.colors.text3,
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
    val firstTag = item.tags.firstOrNull()?.name
    val tagLabel = firstTag ?: "classificar"
    val tagColor = if (firstTag == null) PocketTheme.colors.warn else PocketTheme.colors.text3

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
            Text(
                text = "$dateStr · $tagLabel",
                style = PocketTheme.typography.bodyXs,
                color = tagColor,
            )
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
                    variant = if (isSelected) PocketChipVariant.ON else PocketChipVariant.DEFAULT,
                    onClick = {
                        selectedIds = if (isSelected) selectedIds - tag.id else selectedIds + tag.id
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
        val ctaLabel = if (learnRule) "Salvar e criar regra" else "Salvar classificação"

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PocketTheme.shapes.card)
                .background(
                    if (canSave) PocketTheme.colors.accent else PocketTheme.colors.surface2,
                )
                .let { if (canSave) it.clickable { onSave(selectedTags, learnRule) } else it }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = ctaLabel,
                style = PocketTheme.typography.button,
                color = if (canSave) PocketTheme.colors.accentInk else PocketTheme.colors.text3,
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
                .background(if (canSave) PocketTheme.colors.accent else PocketTheme.colors.surface2)
                .let {
                    if (canSave) {
                        it.clickable {
                            onSave(
                                name.trim(),
                                brand.trim().takeIf { b -> b.isNotBlank() },
                                parsedDay,
                                color.trim().takeIf { c -> c.isNotBlank() },
                            )
                        }
                    } else {
                        it
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isSaving) "Salvando…" else "Adicionar cartão",
                style = PocketTheme.typography.button,
                color = if (canSave) PocketTheme.colors.accentInk else PocketTheme.colors.text3,
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
            style = if (mono) PocketTheme.typography.monoSm
            else PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.Medium),
            color = PocketTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
        )
    }
}
