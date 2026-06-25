package com.resolveprogramming.pocketcounter.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resolveprogramming.pocketcounter.domain.model.AutomationStat
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.automationPercent
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.PocketBadge
import com.resolveprogramming.pocketcounter.ui.components.PocketBadgeVariant
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.components.PocketSegmented
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.SegmentOption
import com.resolveprogramming.pocketcounter.ui.components.SegmentTone
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.theme.LocalReducedMotion
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import com.resolveprogramming.pocketcounter.ui.wizard.label
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeScreen(
    onNotificationTap: (String) -> Unit,
    onNavigate: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var historyKind by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PocketTheme.colors.accent)
        }
        return
    }

    Scaffold(
        containerColor = PocketTheme.colors.bg,
        bottomBar = {
            PocketTabBar(
                active = TabId.INICIO,
                onNav = { tab ->
                    when (tab) {
                        TabId.TRANSACOES -> onNavigate("transacoes")
                        TabId.CARTOES -> onNavigate("cartoes")
                        TabId.INICIO -> Unit
                        TabId.MAIS -> Unit
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }

            item { HeaderSection(state.userName, onAssistant = { onNavigate("assistente") }) }

            item { BalanceCard(state) }

            item {
                AutomationCard(
                    stat = state.automation ?: AutomationStat(monthTotal = 0, autoDone = 0),
                    monthLabel = state.monthLabel,
                    pendingCount = state.pendingReview.size,
                )
            }

            item {
                ResumoStrip(monthLabel = state.monthLabel, onClick = { onNavigate("resumo") })
            }

            val openBillsTotal = state.openBillsTotal
            if (openBillsTotal != null) {
                item {
                    OpenBillsStrip(
                        total = openBillsTotal,
                        cardCount = state.openBillsCount,
                        onClick = { onNavigate("cartoes") },
                    )
                }
            }

            item { PendingReviewHeader(count = state.pendingReview.size) }

            if (state.pendingReview.isEmpty()) {
                item { ReviewEmptyState() }
            }
            if (state.pendingReview.isNotEmpty()) {
                items(state.pendingReview, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onClick = { onNotificationTap(notification.id) },
                    )
                }
            }

            if (state.history.isNotEmpty()) {
                val expenses = state.history.filter { it.type == TransactionType.EXPENSE }
                val incomes = state.history.filter { it.type == TransactionType.INCOME }
                val shown = expenses.takeIf { historyKind == TransactionType.EXPENSE } ?: incomes

                item {
                    Text(
                        text = "HISTÓRICO",
                        style = PocketTheme.typography.sectionHeader,
                        color = PocketTheme.colors.text3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                item {
                    PocketSegmented(
                        options = listOf(
                            SegmentOption("Despesas · ${expenses.size}", SegmentTone.EXPENSE),
                            SegmentOption("Receitas · ${incomes.size}", SegmentTone.INCOME),
                        ),
                        selectedIndex = 1.takeUnless { historyKind == TransactionType.EXPENSE } ?: 0,
                        onSelect = {
                            historyKind = TransactionType.EXPENSE.takeIf { _ -> it == 0 } ?: TransactionType.INCOME
                        },
                    )
                }

                if (shown.isEmpty()) {
                    item {
                        Text(
                            text = "Nenhuma despesa recente".takeIf {
                                historyKind == TransactionType.EXPENSE
                            } ?: "Nenhuma receita recente",
                            style = PocketTheme.typography.bodySm,
                            color = PocketTheme.colors.text3,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                if (shown.isNotEmpty()) {
                    items(shown, key = { it.id }) { item ->
                        HistoryRow(item = item, state = state)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HeaderSection(userName: String, onAssistant: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Olá",
                style = PocketTheme.typography.bodySm,
                color = PocketTheme.colors.text3,
            )
            Text(
                text = userName.ifBlank { "Bem-vindo" },
                style = PocketTheme.typography.body.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = PocketTheme.colors.text,
            )
        }
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(onClick = onAssistant),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(PocketTheme.shapes.labelPicker)
                    .background(PocketTheme.colors.surface)
                    .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.labelPicker),
                contentAlignment = Alignment.Center,
            ) {
                Text("✦", color = PocketTheme.colors.accent)
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(PocketTheme.colors.accent, PocketTheme.colors.accent2),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName.firstOrNull()?.uppercase() ?: "?",
                style = PocketTheme.typography.button,
                color = PocketTheme.colors.accentInk,
            )
        }
    }
}

@Composable
private fun BalanceCard(state: HomeUiState) {
    val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
    val isDark = PocketTheme.isDark
    val cardBg = PocketTheme.colors.surface.takeIf { isDark } ?: PocketTheme.colors.text
    val ink = PocketTheme.colors.text.takeIf { isDark } ?: PocketTheme.colors.bg
    val accent = PocketTheme.colors.accent
    val reducedMotion = LocalReducedMotion.current

    val target = state.balance.toFloat()
    val animated = remember { Animatable(target.takeIf { reducedMotion } ?: 0f) }
    LaunchedEffect(target) {
        if (reducedMotion) {
            animated.snapTo(target)
        }
        if (!reducedMotion) {
            animated.snapTo(0f)
            animated.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            )
        }
    }

    PocketCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PocketTheme.shapes.card),
        backgroundColor = cardBg,
    ) {
        // Decorative accent glow, top-right, clipped to the card shape. Drawn via drawBehind so
        // it never participates in layout (a sized Box would inflate the card height).
        Column(
            modifier = Modifier.drawBehind {
                drawCircle(
                    color = accent.copy(alpha = 0.14f),
                    radius = 90.dp.toPx(),
                    center = Offset(x = size.width + 40.dp.toPx(), y = -40.dp.toPx()),
                )
            },
        ) {
            Text(
                text = "SALDO DO MÊS · ${state.monthLabel}",
                style = PocketTheme.typography.sectionHeader,
                color = ink.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatter.format(animated.value.toDouble()),
                style = PocketTheme.typography.monoBalance,
                color = ink,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                BalanceSubColumn(
                    label = "Receitas",
                    value = formatter.format(state.totalIncome),
                    ink = ink,
                    modifier = Modifier.weight(1f),
                )
                BalanceSubColumn(
                    label = "Despesas",
                    value = formatter.format(state.totalExpense),
                    ink = ink,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BalanceSubColumn(
    label: String,
    value: String,
    ink: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = PocketTheme.typography.bodyXs,
            color = ink.copy(alpha = 0.65f),
        )
        Text(
            text = value,
            style = PocketTheme.typography.body.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = ink,
        )
    }
}

@Composable
private fun PendingReviewHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "PARA REVISAR · $count",
            style = PocketTheme.typography.sectionHeader,
            color = PocketTheme.colors.text3,
        )
        Text(
            text = "ENSINAR",
            style = PocketTheme.typography.sectionHeader.copy(fontWeight = FontWeight.Bold),
            color = PocketTheme.colors.accent,
        )
    }
}

@Composable
private fun NotificationCard(
    notification: NotificationItem,
    onClick: () -> Unit,
) {
    val barColor = when (notification.status) {
        NotificationStatus.NEEDS_REVIEW -> PocketTheme.colors.warn
        NotificationStatus.NEEDS_TAGS -> PocketTheme.colors.accent
        NotificationStatus.AUTO -> PocketTheme.colors.income
    }
    val badgeText = when (notification.status) {
        NotificationStatus.NEEDS_REVIEW -> "Revisar"
        NotificationStatus.NEEDS_TAGS -> "Faltam tags"
        NotificationStatus.AUTO -> "Classificada"
    }
    val badgeVariant = when (notification.status) {
        NotificationStatus.NEEDS_REVIEW -> PocketBadgeVariant.WARN
        NotificationStatus.NEEDS_TAGS -> PocketBadgeVariant.ACCENT
        NotificationStatus.AUTO -> PocketBadgeVariant.INCOME
    }

    PocketCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .padding(vertical = 14.dp)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(PocketTheme.shapes.pill)
                    .background(barColor),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PocketBadge(text = badgeText, variant = badgeVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = notification.app,
                            style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                            color = PocketTheme.colors.text,
                        )
                    }
                    Text(
                        text = notification.time,
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.text3,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = notification.text,
                    style = PocketTheme.typography.bodySm,
                    color = PocketTheme.colors.text2,
                    maxLines = 3,
                )

                if (notification.parsed.amount != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "VALOR IDENTIFICADO",
                                style = PocketTheme.typography.bodyXs,
                                color = PocketTheme.colors.text3,
                            )
                            AmountText(
                                amount = notification.parsed.amount,
                                style = PocketTheme.typography.monoBody.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                type = notification.parsed.type,
                            )
                        }
                        val ctaText = when (notification.status) {
                            NotificationStatus.NEEDS_REVIEW -> "Ensinar →"
                            NotificationStatus.NEEDS_TAGS -> "Adicionar tags →"
                            NotificationStatus.AUTO -> ""
                        }
                        if (ctaText.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(PocketTheme.colors.accent, PocketTheme.shapes.chip)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = ctaText,
                                    style = PocketTheme.typography.button,
                                    color = PocketTheme.colors.accentInk,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewEmptyState() {
    PocketCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(PocketTheme.colors.incomeBg, PocketTheme.shapes.chip),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    fontSize = 18.sp,
                    color = PocketTheme.colors.income,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tudo revisado",
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                Text(
                    text = "Sem notificações esperando você",
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: HistoryItem,
    state: HomeUiState,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayTitle(),
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.Medium),
                    color = PocketTheme.colors.text,
                )
                Text(
                    text = historyMeta(item, state),
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
                )
            }
            AmountText(
                amount = item.amount,
                type = item.type,
                showSign = true,
                style = PocketTheme.typography.monoSm,
            )
        }
        HorizontalDivider(color = PocketTheme.colors.line)
    }
}

private fun historyMeta(item: HistoryItem, state: HomeUiState): String {
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
private fun AutomationCard(
    stat: AutomationStat,
    monthLabel: String,
    pendingCount: Int,
) {
    val isEmpty = stat.monthTotal == 0
    val pct = automationPercent(stat.autoDone, stat.monthTotal)
    val reducedMotion = LocalReducedMotion.current

    var pctTarget by remember { mutableStateOf(pct.takeIf { reducedMotion } ?: 0) }
    LaunchedEffect(pct) {
        pctTarget = pct
    }
    val animatedPct by animateIntAsState(
        targetValue = pctTarget,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "automationPct",
    )
    val displayPct = pct.takeIf { reducedMotion } ?: animatedPct

    val fillFraction by animateFloatAsState(
        targetValue = pct / 100f,
        label = "automationFill",
    )

    PocketCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Lightning bolt chip
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(PocketTheme.colors.accentBg, PocketTheme.shapes.icon),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⚡",
                            fontSize = 13.sp,
                            color = PocketTheme.colors.accent,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Classificado por mim · ${monthLabel.lowercase(java.util.Locale("pt", "BR"))}",
                        style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
                        color = PocketTheme.colors.text,
                    )
                }
                Text(
                    text = "$displayPct%",
                    style = PocketTheme.typography.monoSm.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = PocketTheme.colors.accent,
                    maxLines = 1,
                    softWrap = false,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(PocketTheme.colors.surface2, PocketTheme.shapes.pill),
            ) {
                if (!isEmpty) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillFraction)
                            .height(8.dp)
                            .background(PocketTheme.colors.accent, PocketTheme.shapes.pill),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isEmpty) {
                Text(
                    text = "Assim que eu classificar algo, você acompanha aqui.",
                    style = PocketTheme.typography.bodySm,
                    color = PocketTheme.colors.text3,
                )
            }
            if (!isEmpty) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = PocketTheme.colors.text)) {
                            append("${stat.autoDone}")
                        }
                        append(" lançamentos sem você  •  ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = PocketTheme.colors.warn)) {
                            append("$pendingCount")
                        }
                        append(" esperando revisão")
                    },
                    style = PocketTheme.typography.bodySm,
                    color = PocketTheme.colors.text2,
                )
            }
        }
    }
}

@Composable
private fun ResumoStrip(monthLabel: String, onClick: () -> Unit) {
    PocketCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(PocketTheme.colors.accentBg, PocketTheme.shapes.chip),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "◔",
                    fontSize = 18.sp,
                    color = PocketTheme.colors.accent,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Resumo do mês · ${monthLabel.lowercase(java.util.Locale("pt", "BR"))}",
                    style = PocketTheme.typography.bodyXs.copy(fontSize = 12.sp),
                    color = PocketTheme.colors.text3,
                )
                Text(
                    text = "Para onde foi seu dinheiro",
                    style = PocketTheme.typography.body.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = PocketTheme.colors.text,
                )
            }
            Text(
                text = "›",
                style = PocketTheme.typography.stepQuestion,
                color = PocketTheme.colors.text3,
            )
        }
    }
}

@Composable
private fun OpenBillsStrip(
    total: BigDecimal,
    cardCount: Int,
    onClick: () -> Unit,
) {
    val formatter = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    PocketCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(PocketTheme.colors.surface2, PocketTheme.shapes.chip),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "▭",
                    fontSize = 18.sp,
                    color = PocketTheme.colors.text2,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Faturas em aberto · $cardCount cartões",
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                Text(
                    text = formatter.format(total),
                    style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.Bold),
                    color = PocketTheme.colors.text,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = "›",
                style = PocketTheme.typography.stepQuestion,
                color = PocketTheme.colors.text3,
            )
        }
    }
}
