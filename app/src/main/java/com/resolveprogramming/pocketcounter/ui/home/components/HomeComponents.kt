package com.resolveprogramming.pocketcounter.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resolveprogramming.pocketcounter.domain.model.ConfirmReadyItem
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.HomeKpis
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.AutoSizeText
import com.resolveprogramming.pocketcounter.ui.components.LedgerMeta
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonSize
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.components.TagPill
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
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
        Chevron(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Mês anterior",
            onClick = { onStep(-1) },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = PocketTheme.colors.text2,
            )
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
        Chevron(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Próximo mês",
            onClick = { onStep(1) },
        )
    }
}

@Composable
private fun Chevron(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
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
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
            tint = PocketTheme.colors.text2,
        )
    }
}

@Composable
fun BalanceHero(
    monthLabel: String,
    kpis: HomeKpis,
    balance: BigDecimal,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SALDO DO MÊS · ${monthLabel.uppercase(ptBr)}",
                        style = PocketTheme.typography.sectionHeader,
                        color = ink.copy(alpha = 0.65f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = formatter.format(balance),
                        style = PocketTheme.typography.monoBalance,
                        // Net saldo carries sign meaning: green when up, red when down, neutral at zero.
                        color = when (balance.signum()) {
                            1 -> dark.income
                            -1 -> dark.expense
                            else -> ink
                        },
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(ink.copy(alpha = 0.12f), PocketTheme.shapes.icon),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CreditCard,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = ink.copy(alpha = 0.85f),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            KpiStackRow(
                label = "Despesas",
                dotColor = dark.expense,
                value = formatter.format(kpis.totals.expense),
                count = "${kpis.expenseCount} lançs.",
                ink = ink,
                showDivider = false,
            )
            KpiStackRow(
                label = "Receitas",
                dotColor = dark.income,
                value = formatter.format(kpis.totals.income),
                count = "${kpis.incomeCount} lançs.",
                ink = ink,
                showDivider = true,
            )
            if (kpis.pendingCount >= 1) {
                KpiStackRow(
                    label = "Pendente",
                    dotColor = dark.warn,
                    value = formatter.format(kpis.pendingTotal),
                    count = "${kpis.pendingCount} em aberto",
                    ink = ink,
                    showDivider = true,
                )
            }
        }
    }
}

@Composable
private fun KpiStackRow(
    label: String,
    dotColor: Color,
    value: String,
    count: String,
    ink: Color,
    showDivider: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ink.copy(alpha = 0.09f)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(dotColor, CircleShape))
                Spacer(Modifier.width(9.dp))
                Text(
                    text = label,
                    style = PocketTheme.typography.body.copy(fontSize = 13.5.sp),
                    color = ink.copy(alpha = 0.82f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = value,
                    style = PocketTheme.typography.monoSm.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    ),
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
    }
}

/**
 * Shown on Home whenever the notification-listener access is revoked/missing, so capture never fails
 * silently. Tapping it deep-links to the system Notification-access settings. The caller re-evaluates
 * the grant on every resume, so it appears/disappears as the user toggles the setting.
 */
@Composable
fun NotificationAccessBanner(onEnable: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .border(1.dp, PocketTheme.colors.warn.copy(alpha = 0.5f), PocketTheme.shapes.card)
            .clickable(onClick = onEnable)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(PocketTheme.colors.warn.copy(alpha = 0.18f), PocketTheme.shapes.icon),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.NotificationsOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PocketTheme.colors.warn,
                )
            }
            Spacer(Modifier.width(11.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Captura desativada. ") }
                    append("Ative o acesso a notificações para capturar suas compras.")
                },
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text,
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ativar",
                style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.Bold),
                color = PocketTheme.colors.warn,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = PocketTheme.colors.warn,
            )
        }
    }
}

@Composable
fun RevisarBanner(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(PocketTheme.colors.warn.copy(alpha = 0.18f), PocketTheme.shapes.icon),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = PocketTheme.colors.warn,
                )
            }
            Spacer(Modifier.width(11.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$count") }
                    append(" lançamentos para revisar")
                },
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "ensinar",
                style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.Bold),
                color = PocketTheme.colors.warn,
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = PocketTheme.colors.warn,
            )
        }
    }
}

/**
 * Shown on the current month while the classifier's first pass runs, before any recognized card or the
 * "para revisar" banner has settled — so the section reads as "working" instead of empty or wrong.
 */
@Composable
fun ClassifyingSkeleton() {
    PocketCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = PocketTheme.colors.accent,
            )
            Spacer(Modifier.width(11.dp))
            Text(
                text = "Analisando notificações…",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
            )
        }
    }
}

@Composable
fun HomeQuickTiles(
    openBillsTotal: BigDecimal,
    openBillsCount: Int,
    openBillsLoading: Boolean,
    onResumo: () -> Unit,
    onFaturas: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuickTile(
            icon = Icons.Filled.PieChart,
            badgeBg = PocketTheme.colors.accentBg,
            badgeTint = PocketTheme.colors.accent,
            caption = "Resumo do mês",
            value = "Para onde foi",
            valueMono = false,
            onClick = onResumo,
            modifier = Modifier.weight(1f),
        )
        QuickTile(
            icon = Icons.Filled.CreditCard,
            badgeBg = PocketTheme.colors.surface2,
            badgeTint = PocketTheme.colors.text2,
            // While the fatura reloads across a month flip, drop the "· N cartões" suffix so it never
            // reads "0 cartões", and show an em-dash instead of a premature R$ 0.
            caption = if (openBillsLoading) "Faturas" else "Faturas · $openBillsCount cartões",
            value = if (openBillsLoading) "—" else currency().format(openBillsTotal),
            valueMono = true,
            valueColor = if (openBillsLoading) PocketTheme.colors.text3 else PocketTheme.colors.text,
            onClick = onFaturas,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickTile(
    icon: ImageVector,
    badgeBg: Color,
    badgeTint: Color,
    caption: String,
    value: String,
    valueMono: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = PocketTheme.colors.text,
) {
    val valueStyle = when {
        valueMono -> PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        else -> PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
    PocketCard(
        modifier = modifier.clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(badgeBg, PocketTheme.shapes.chip),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = badgeTint,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = caption,
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                    maxLines = 1,
                    softWrap = false,
                )
                AutoSizeText(
                    text = value,
                    style = valueStyle,
                    color = valueColor,
                )
            }
        }
    }
}

@Composable
fun SwipeCue(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PocketTheme.colors.surface, PocketTheme.shapes.card)
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.card)
            .clickable(onClickLabel = "Abrir lançamentos", role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Lançamentos",
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                color = PocketTheme.colors.text,
            )
            Text(
                text = "$count no mês".takeIf { count > 0 } ?: "deslize para ver",
                style = PocketTheme.typography.bodyXs.copy(fontSize = 12.sp),
                color = PocketTheme.colors.text3,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "deslize",
                style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp),
                color = PocketTheme.colors.accent,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = PocketTheme.colors.accent,
            )
        }
    }
}

/**
 * Clears [com.resolveprogramming.pocketcounter.ui.home.HomeUiState.flashId] ~1.4s after a flash
 * starts, unless reduced motion is on. Keyed on [flashNonce] (bumped on every flash) so re-flashing
 * the SAME row id still fires.
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

/**
 * How many recognized cards Home shows inline before the rest go behind the expander.
 *
 * This is a VIEW cap and is deliberately NOT coupled to HomeViewModel.CONFIRM_READY_CLASSIFY_CAP,
 * which bounds how many pending notifications get classified. Lowering that one to match this would
 * shrink `ready`, and since pendingReviewCount = (pending.size - ready.size), it would silently
 * inflate the "para revisar" banner with items that were never actually reviewed.
 */
private const val CONFIRM_READY_VISIBLE_CAP = 3

/**
 * The "Reconhecidos automaticamente" stack: pending notifications the classifier matched
 * confidently enough to write to the ledger in one tap.
 *
 * Spec deviation, recorded here because `handoff/` is gitignored and cannot hold the rationale:
 * `handoff/design_handoff_android_final/specs/core-app/README.md:355` says AUTO notifications never
 * appear in "Para revisar" at all, so this component has no spec surface to follow. It also departs
 * from the redesign's decluttering direction on purpose — the review banner only has to answer "how
 * much is left to teach?", whereas each card here authorizes one specific, un-undoable ledger
 * write, so it has to show enough (valor, nome, data, tag) to be checked before it is authorized.
 */
@Composable
fun ConfirmReadySection(
    items: List<ConfirmReadyItem>,
    confirmingIds: Set<String>,
    cards: Map<String, CreditCard>,
    tags: Map<String, Tag>,
    contextsById: Map<String, TagContext>,
    onConfirm: (ConfirmReadyItem) -> Unit,
    onReview: (ConfirmReadyItem) -> Unit,
    onIgnore: (ConfirmReadyItem) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val hidden = items.drop(CONFIRM_READY_VISIBLE_CAP)
    val reducedMotion = LocalReducedMotion.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = PocketTheme.colors.accent,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Reconhecidos automaticamente",
                style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.accent,
            )
            Spacer(Modifier.width(6.dp))
            // The total, not the visible count — collapsing the stack must not look like the
            // classifier recognized fewer things than it did.
            Text(
                text = "· ${items.size}",
                style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                color = PocketTheme.colors.text3,
            )
        }

        items.take(CONFIRM_READY_VISIBLE_CAP).forEach { item ->
            key(item.notificationId) {
                ConfirmReadyRow(item, confirmingIds, cards, tags, contextsById, onConfirm, onReview, onIgnore)
            }
        }

        if (hidden.isNotEmpty()) {
            AnimatedVisibility(
                visible = expanded,
                enter = EnterTransition.None.takeIf { reducedMotion } ?: (expandVertically() + fadeIn()),
                exit = ExitTransition.None.takeIf { reducedMotion } ?: (shrinkVertically() + fadeOut()),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    hidden.forEach { item ->
                        key(item.notificationId) {
                            ConfirmReadyRow(item, confirmingIds, cards, tags, contextsById, onConfirm, onReview, onIgnore)
                        }
                    }
                }
            }
            ConfirmReadyExpander(
                hiddenCount = hidden.size,
                expanded = expanded,
                onToggle = { expanded = !expanded },
            )
        }
    }
}

@Composable
private fun ConfirmReadyRow(
    item: ConfirmReadyItem,
    confirmingIds: Set<String>,
    cards: Map<String, CreditCard>,
    tags: Map<String, Tag>,
    contextsById: Map<String, TagContext>,
    onConfirm: (ConfirmReadyItem) -> Unit,
    onReview: (ConfirmReadyItem) -> Unit,
    onIgnore: (ConfirmReadyItem) -> Unit,
) {
    // Memoized: the builder allocates a NumberFormat and reads LocalDate.now() through its default
    // argument, so calling it straight from the composable body would make both happen on every
    // recomposition — and ConfirmReadySection recomposes on each confirmingIds change.
    val presentation = remember(item, cards, tags, contextsById) {
        confirmReadyPresentation(item, cards, tags, contextsById)
    }
    ConfirmReadyCard(
        item = item,
        presentation = presentation,
        isConfirming = item.notificationId in confirmingIds,
        onConfirm = { onConfirm(item) },
        onReview = { onReview(item) },
        onIgnore = { onIgnore(item) },
    )
}

@Composable
private fun ConfirmReadyExpander(hiddenCount: Int, expanded: Boolean, onToggle: () -> Unit) {
    val label = "Ver menos".takeIf { expanded } ?: "Ver mais $hiddenCount reconhecidos"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PocketTheme.shapes.chip)
            .clickable(onClick = onToggle)
            .semantics {
                role = Role.Button
                stateDescription = "Expandido".takeIf { expanded } ?: "Recolhido"
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.accent,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowUp.takeIf { expanded } ?: Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = PocketTheme.colors.accent,
        )
    }
}

/**
 * One recognized notification, confirmable in a single un-undoable tap — so it has to show the four
 * fields the user would check on a receipt: valor, nome, data, tag.
 *
 * Render-only: everything it draws is already resolved in [presentation]. Two ambiguity guards are
 * load-bearing here — the card body carries NO `clickable` (only the two buttons and the dismiss ✕
 * commit anything), and the elevated/borderless/20dp-radius container stays visually distinct from
 * the bordered/flat/16dp Transações row, because that row navigates while this one writes.
 */
@Composable
fun ConfirmReadyCard(
    item: ConfirmReadyItem,
    presentation: ConfirmReadyPresentation,
    isConfirming: Boolean,
    onConfirm: () -> Unit,
    onReview: () -> Unit,
    onIgnore: () -> Unit,
) {
    val colors = PocketTheme.colors
    PocketCard(
        elevated = true,
        // spacing.pad, not a literal 16.dp: it tracks the user's density preference
        // (COMPACT 12 / COMFORTABLE 16 / COZY 20) and hard-coding would opt this card out.
        contentPadding = PaddingValues(
            horizontal = PocketTheme.spacing.pad,
            vertical = PocketTheme.spacing.gap,
        ),
    ) {
        Column {
            // Merged on the info block ONLY. Merging the whole card would swallow Confirmar,
            // Revisar and Ignorar into a single unactionable node.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = presentation.contentDescription
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = presentation.title,
                        style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    presentation.signedAmount?.let { amount ->
                        Spacer(Modifier.width(8.dp))
                        AmountText(
                            amount = amount,
                            type = item.draft.type,
                            showSign = true,
                            style = PocketTheme.typography.monoBody.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }
                Spacer(Modifier.size(3.dp))
                ConfirmReadyMetaRow(
                    meta = presentation.meta,
                    installmentsLabel = presentation.installmentsLabel,
                )
            }
            Spacer(Modifier.size(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // heightIn from the CALLER: PocketButton's SMALL sets heightIn(min = 36.dp), and a
                // size modifier coerces into the incoming constraints, so 36 inside [48, ∞) yields
                // 48. Raising it inside PocketButton would silently resize Transações too.
                PocketButton(
                    text = presentation.confirmLabel.takeUnless { isConfirming } ?: "Confirmando…",
                    onClick = onConfirm,
                    variant = PocketButtonVariant.PRIMARY,
                    size = PocketButtonSize.SMALL,
                    enabled = !isConfirming,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    leading = confirmSpinner(colors.accentInk).takeIf { isConfirming },
                )
                PocketButton(
                    text = "Revisar",
                    onClick = onReview,
                    variant = PocketButtonVariant.SOFT,
                    size = PocketButtonSize.SMALL,
                    enabled = !isConfirming,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                Spacer(Modifier.width(4.dp))
                // The learned "skip this suggestion" affordance, muted so it stays subordinate to
                // Confirmar/Revisar. 48dp target comes from IconButton.
                IconButton(
                    onClick = onIgnore,
                    enabled = !isConfirming,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Ignorar sugestão",
                        modifier = Modifier.size(18.dp),
                        tint = colors.text3.copy(alpha = 0.4f.takeIf { isConfirming } ?: 1f),
                    )
                }
            }
        }
    }
}

private fun confirmSpinner(color: Color): @Composable () -> Unit = {
    CircularProgressIndicator(
        modifier = Modifier.size(14.dp),
        strokeWidth = 2.dp,
        color = color,
    )
}

/**
 * `14 jun · ● Farmácia · Crédito Nubank`, on one line that must never wrap.
 *
 * Drop priority is enforced by modifier placement rather than by branching: the date takes no
 * weight and no ellipsis so it is always whole, the pill is intrinsically sized and ellipsizes
 * internally, and only the payment label carries `weight(1f, fill = false)` — so it is the one
 * thing that can shrink.
 */
@Composable
private fun ConfirmReadyMetaRow(meta: LedgerMeta, installmentsLabel: String?) {
    val colors = PocketTheme.colors
    val bodyXs = PocketTheme.typography.bodyXs
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(text = meta.date, style = bodyXs, color = colors.text3, maxLines = 1)
        installmentsLabel?.let {
            Text(
                text = it,
                style = bodyXs.copy(fontWeight = FontWeight.SemiBold),
                color = colors.text3,
                maxLines = 1,
            )
        }
        Text(text = "·", style = bodyXs, color = colors.text3)
        meta.tagName?.let { tag ->
            TagPill(name = tag, color = meta.tagColor)
            if (meta.extraTags > 0) {
                Text(
                    text = "+${meta.extraTags}",
                    style = bodyXs.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text3,
                )
            }
        }
        // Plain text, never a TagPill: a pill with a grey dot reads as a real category.
        if (meta.tagName == null) {
            Text(text = "sem categoria", style = bodyXs, color = colors.text3, maxLines = 1)
        }
        if (meta.payment.isNotBlank()) {
            Text(text = "·", style = bodyXs, color = colors.text3)
            Text(
                text = meta.payment,
                style = bodyXs,
                color = colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
