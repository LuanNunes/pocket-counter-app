package com.resolveprogramming.pocketcounter.ui.home.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resolveprogramming.pocketcounter.domain.model.ConfirmReadyItem
import com.resolveprogramming.pocketcounter.domain.model.HomeKpis
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.AutoSizeText
import com.resolveprogramming.pocketcounter.ui.components.PocketButton
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonSize
import com.resolveprogramming.pocketcounter.ui.components.PocketButtonVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
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

@Composable
fun HomeQuickTiles(
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
            caption = "Faturas · $openBillsCount cartões",
            value = currency().format(openBillsTotal),
            valueMono = true,
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
                    color = PocketTheme.colors.text,
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
 * A pending notification the classifier recognized confidently enough to confirm in one tap. Shows the
 * pre-filled title/amount + a payment/tag preview, a primary "Confirmar" (per-card spinner while its
 * confirm is in flight) and a "Revisar" fallback that opens the wizard.
 */
@Composable
fun ConfirmReadyCard(
    item: ConfirmReadyItem,
    isConfirming: Boolean,
    tagName: (String) -> String?,
    cardName: (String) -> String?,
    onConfirm: () -> Unit,
    onReview: () -> Unit,
) {
    val colors = PocketTheme.colors
    val draft = item.draft
    val title = draft.name?.takeIf { it.isNotBlank() }
        ?: item.notification.parsed.merchantRaw?.takeIf { it.isNotBlank() }
        ?: "Lançamento"
    val meta = buildList {
        draft.paymentMethod?.let { method ->
            val card = draft.cardId?.let(cardName)
            add(if (card != null) "${method.label()} · $card" else method.label())
        }
        draft.tagIds.mapNotNull(tagName).takeIf { it.isNotEmpty() }?.let { add(it.joinToString(", ")) }
    }.joinToString("  ·  ")

    PocketCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = colors.accent,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Reconhecido automaticamente",
                    style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.text,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false),
                )
                draft.amount?.let { amount ->
                    Spacer(Modifier.width(12.dp))
                    AmountText(
                        amount = amount,
                        type = draft.type,
                        showSign = true,
                        style = PocketTheme.typography.monoBody,
                    )
                }
            }
            if (meta.isNotEmpty()) {
                Text(text = meta, style = PocketTheme.typography.bodyXs, color = colors.text3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PocketButton(
                    text = "Confirmar".takeUnless { isConfirming } ?: "Confirmando…",
                    onClick = onConfirm,
                    variant = PocketButtonVariant.PRIMARY,
                    size = PocketButtonSize.SMALL,
                    enabled = !isConfirming,
                    modifier = Modifier.weight(1f),
                    leading = if (isConfirming) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = colors.accentInk,
                            )
                        }
                    } else {
                        null
                    },
                )
                PocketButton(
                    text = "Revisar",
                    onClick = onReview,
                    variant = PocketButtonVariant.SOFT,
                    size = PocketButtonSize.SMALL,
                    enabled = !isConfirming,
                )
            }
        }
    }
}
