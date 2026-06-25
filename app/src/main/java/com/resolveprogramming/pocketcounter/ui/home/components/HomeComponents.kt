package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.HomeKpis
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.components.PocketSegmented
import com.resolveprogramming.pocketcounter.ui.components.SegmentOption
import com.resolveprogramming.pocketcounter.ui.components.SegmentTone
import com.resolveprogramming.pocketcounter.ui.home.HomeUiState
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.label
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private val ptBr = Locale("pt", "BR")
private fun currency(): NumberFormat = NumberFormat.getCurrencyInstance(ptBr)

@Composable
fun MonthNavBar(
    monthLabel: String,
    isCurrentMonth: Boolean,
    onStep: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.card)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Chevron(glyph = "‹", onClick = { onStep(-1) })
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Text("📅", fontSize = 15.sp)
            Text(
                text = monthLabel,
                style = PocketTheme.typography.body.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                color = PocketTheme.colors.text,
            )
            if (isCurrentMonth) {
                Box(
                    modifier = Modifier
                        .background(PocketTheme.colors.accentBg, PocketTheme.shapes.pill)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "ATUAL",
                        style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.Bold),
                        color = PocketTheme.colors.accent,
                    )
                }
            }
        }
        Chevron(glyph = "›", onClick = { onStep(1) })
    }
}

@Composable
private fun Chevron(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .clip(PocketTheme.shapes.icon)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.icon)
            .background(PocketTheme.colors.surface, PocketTheme.shapes.icon)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = PocketTheme.typography.stepQuestion, color = PocketTheme.colors.text2)
    }
}

@Composable
fun BalanceHero(
    monthLabel: String,
    kpis: HomeKpis,
    balance: BigDecimal,
    automationPct: Int?,
) {
    // The hero stays dark in both themes; KPI dot/value colors read from the always-dark palette.
    val dark = PocketTheme.darkColors
    val cardBg = dark.surface
    val ink = dark.text
    val formatter = currency()

    PocketCard(
        modifier = Modifier.fillMaxWidth().clip(PocketTheme.shapes.card),
        backgroundColor = cardBg,
    ) {
        Column(
            modifier = Modifier.drawBehind {
                drawCircle(
                    color = dark.accent.copy(alpha = 0.14f),
                    radius = 90.dp.toPx(),
                    center = Offset(x = size.width + 40.dp.toPx(), y = -40.dp.toPx()),
                )
            },
        ) {
            Text(
                text = "SALDO DO MÊS · ${monthLabel.uppercase(ptBr)}",
                style = PocketTheme.typography.sectionHeader,
                color = ink.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatter.format(balance),
                style = PocketTheme.typography.monoBalance,
                color = ink,
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                KpiColumn(
                    label = "Despesas",
                    dotColor = dark.expense,
                    value = formatter.format(kpis.totals.expense),
                    count = "${kpis.expenseCount} lançs.",
                    ink = ink,
                    modifier = Modifier.weight(1f),
                )
                KpiColumn(
                    label = "Receitas",
                    dotColor = dark.income,
                    value = formatter.format(kpis.totals.income),
                    count = "${kpis.incomeCount} lançs.",
                    ink = ink,
                    modifier = Modifier.weight(1f),
                )
                KpiColumn(
                    label = "Pendente",
                    dotColor = dark.warn,
                    value = formatter.format(kpis.pendingTotal),
                    count = "${kpis.pendingCount} em aberto",
                    ink = ink,
                    modifier = Modifier.weight(1f),
                )
            }
            if (automationPct != null) {
                Spacer(Modifier.height(16.dp))
                AutomationBar(pct = automationPct, ink = ink, accent = dark.accent, track = dark.surface2)
            }
        }
    }
}

@Composable
private fun KpiColumn(
    label: String,
    dotColor: Color,
    value: String,
    count: String,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = PocketTheme.typography.bodyXs,
                color = ink.copy(alpha = 0.65f),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.SemiBold),
            color = ink,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = count,
            style = PocketTheme.typography.bodyXs,
            color = ink.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun AutomationBar(pct: Int, ink: Color, accent: Color, track: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Classificado por mim",
                style = PocketTheme.typography.bodyXs,
                color = ink.copy(alpha = 0.65f),
            )
            Text(
                text = "$pct%",
                style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.Bold),
                color = accent,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(track, PocketTheme.shapes.pill),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct / 100f)
                    .height(6.dp)
                    .background(accent, PocketTheme.shapes.pill),
            )
        }
    }
}

@Composable
fun RevisarBanner(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.warnBg, PocketTheme.shapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠", color = PocketTheme.colors.warn)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$count lançamentos para revisar",
                style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.warn,
            )
        }
        Text(
            text = "ensinar ›",
            style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.Bold),
            color = PocketTheme.colors.warn,
        )
    }
}

@Composable
fun HomeQuickTiles(
    monthLabel: String,
    openBillsTotal: BigDecimal,
    openBillsCount: Int,
    onResumo: () -> Unit,
    onFaturas: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickTile(
            icon = "◔",
            title = "Resumo",
            subtitle = monthLabel.lowercase(ptBr),
            onClick = onResumo,
            modifier = Modifier.weight(1f),
        )
        QuickTile(
            icon = "▭",
            title = "Faturas · $openBillsCount",
            subtitle = currency().format(openBillsTotal),
            onClick = onFaturas,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickTile(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PocketCard(modifier = modifier.clickable(onClick = onClick)) {
        Column {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(PocketTheme.colors.accentBg, PocketTheme.shapes.chip),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 16.sp, color = PocketTheme.colors.accent)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = PocketTheme.colors.text,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = subtitle,
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
fun GroupToggle(
    groupBy: GroupMode,
    onSelect: (GroupMode) -> Unit,
) {
    // Lista | Categoria, Categoria default-active. CONTEXTO drives "Categoria".
    val categoria = groupBy != GroupMode.LISTA
    Row(
        modifier = Modifier
            .background(PocketTheme.colors.surface2, RoundedCornerShape(10.dp))
            .border(1.dp, PocketTheme.colors.line, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        GroupToggleSeg(label = "Lista", active = !categoria, onClick = { onSelect(GroupMode.LISTA) })
        GroupToggleSeg(label = "Categoria", active = categoria, onClick = { onSelect(GroupMode.CONTEXTO) })
    }
}

@Composable
private fun GroupToggleSeg(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PocketTheme.colors.surface.takeIf { active } ?: Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.text.takeIf { active } ?: PocketTheme.colors.text3,
        )
    }
}

@Composable
fun HomeListSection(
    state: HomeUiState,
    onSelectType: (TransactionType) -> Unit,
    onSelectGroup: (GroupMode) -> Unit,
    onToggleStatus: (HistoryItem) -> Unit,
    onEdit: (HistoryItem) -> Unit,
) {
    val expenseSelected = state.listType == TransactionType.EXPENSE
    Column {
        PocketSegmented(
            options = listOf(
                SegmentOption("Despesas · ${state.kpis.expenseCount}", SegmentTone.EXPENSE),
                SegmentOption("Receitas · ${state.kpis.incomeCount}", SegmentTone.INCOME),
            ),
            selectedIndex = 0.takeIf { expenseSelected } ?: 1,
            onSelect = {
                onSelectType(TransactionType.EXPENSE.takeIf { _ -> it == 0 } ?: TransactionType.INCOME)
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupToggle(groupBy = state.groupBy, onSelect = onSelectGroup)
            Text(
                text = currency().format(state.periodTotal),
                style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.Bold),
                color = PocketTheme.colors.text,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.height(8.dp))

        if (state.isEmptyMonth || state.shownItems.isEmpty()) {
            Text(
                text = "Nenhuma despesa neste mês".takeIf { expenseSelected } ?: "Nenhuma receita neste mês",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            return@Column
        }

        if (state.groupBy == GroupMode.LISTA) {
            state.shownItems.forEach { item ->
                TxRow(
                    item = item,
                    state = state,
                    onToggleStatus = onToggleStatus,
                    onEdit = onEdit,
                )
            }
            return@Column
        }

        state.groupedSections.forEach { group ->
            GroupHeader(
                title = group.title,
                color = group.color,
                count = group.items.size,
                subtotal = group.subtotal,
            )
            group.items.forEach { item ->
                TxRow(
                    item = item,
                    state = state,
                    onToggleStatus = onToggleStatus,
                    onEdit = onEdit,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GroupHeader(title: String, color: Long?, count: Int, subtotal: BigDecimal) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(color?.let { Color(it) } ?: PocketTheme.colors.text3, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${title.uppercase(ptBr)} · $count",
                style = PocketTheme.typography.sectionHeader,
                color = PocketTheme.colors.text3,
            )
        }
        Text(
            text = currency().format(subtotal),
            style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.text2,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun TxRow(
    item: HistoryItem,
    state: HomeUiState,
    onToggleStatus: (HistoryItem) -> Unit,
    onEdit: (HistoryItem) -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val flashing = state.flashId == item.id
    val targetBg = PocketTheme.colors.accentBg.takeIf { flashing } ?: Color.Transparent
    val bg by animateColorAsState(
        targetValue = targetBg,
        label = "txFlash",
    )
    val rowBg = targetBg.takeIf { reducedMotion } ?: bg
    val paid = item.statusPayment == PaymentStatus.PAID

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg, PocketTheme.shapes.chip)
            .clickable { onEdit(item) }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusToggle(paid = paid, onToggle = { onToggleStatus(item) })
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayTitle(),
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                color = PocketTheme.colors.text,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = txMeta(item, state),
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.text3,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(
                amount = item.amount,
                type = item.type,
                showSign = true,
                style = PocketTheme.typography.monoSm,
            )
            Text(
                text = "Paga".takeIf { paid } ?: "Pendente",
                style = PocketTheme.typography.bodyXs,
                color = PocketTheme.colors.income.takeIf { paid } ?: PocketTheme.colors.warn,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .size(32.dp)
                .clickable(onClickLabel = "Editar transação") { onEdit(item) },
            contentAlignment = Alignment.Center,
        ) {
            Text("✎", fontSize = 15.sp, color = PocketTheme.colors.text3)
        }
    }
}

@Composable
private fun StatusToggle(paid: Boolean, onToggle: () -> Unit) {
    // Nested clickable with its own interaction source so the tap is consumed here and never
    // propagates to the row's edit click (Compose equivalent of stopPropagation).
    val interaction = remember { MutableInteractionSource() }
    val a11yLabel = "Paga — marcar como pendente".takeIf { paid } ?: "Pendente — marcar como paga"
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(28.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onToggle,
            )
            .semantics { contentDescription = a11yLabel },
        contentAlignment = Alignment.Center,
    ) {
        if (paid) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(PocketTheme.colors.incomeBg, PocketTheme.shapes.icon),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", fontSize = 13.sp, color = PocketTheme.colors.income)
            }
        }
        if (!paid) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .dashedRing(PocketTheme.colors.warn),
                contentAlignment = Alignment.Center,
            ) {
                Text("◷", fontSize = 13.sp, color = PocketTheme.colors.warn)
            }
        }
    }
}

private fun Modifier.dashedRing(color: Color): Modifier = drawBehind {
    val stroke = Stroke(
        width = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f), 0f),
    )
    val r = 6.dp.toPx()
    drawRoundRect(color = color, cornerRadius = CornerRadius(r, r), style = stroke)
}

private fun txMeta(item: HistoryItem, state: HomeUiState): String {
    val payment = run {
        if (item.paymentMethod == PaymentMethod.CREDIT) {
            val cardName = item.cardId?.let { state.cards[it]?.name }
            return@run cardName?.let { "Cartão $it" } ?: "Crédito"
        }
        item.paymentMethod?.label().orEmpty()
    }
    val tagNames = item.tagIds.orEmpty().mapNotNull { state.tags[it]?.name }
    val tagPreview = run {
        if (tagNames.isEmpty()) return@run ""
        if (tagNames.size == 1) return@run tagNames.first()
        "${tagNames.first()} +${tagNames.size - 1}"
    }
    return listOf(payment, tagPreview).filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
fun HomeFab(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(PocketTheme.colors.accent, PocketTheme.shapes.fab)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", style = PocketTheme.typography.stepQuestion, color = PocketTheme.colors.accentInk)
    }
}

/**
 * Clears [HomeUiState.flashId] ~1.4s after a flash starts, unless reduced motion is on.
 * Keyed on [flashNonce] (bumped on every flash) so re-flashing the SAME row id still fires.
 */
@Composable
fun FlashEffect(flashId: String?, flashNonce: Int, reducedMotion: Boolean, onConsume: () -> Unit) {
    LaunchedEffect(flashNonce, reducedMotion) {
        flashId ?: return@LaunchedEffect
        if (!reducedMotion) {
            kotlinx.coroutines.delay(1400)
        }
        onConsume()
    }
}
