package com.resolveprogramming.pocketcounter.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.components.PocketTabBar
import com.resolveprogramming.pocketcounter.ui.components.TabId
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
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
                        else -> Unit
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

            val automation = state.automation
            if (automation != null) {
                item {
                    AutomationCard(
                        stat = automation,
                        monthLabel = state.monthLabel,
                        pendingCount = state.pendingReview.size,
                    )
                }
            }

            item {
                ResumoStrip(monthLabel = state.monthLabel, onClick = { onNavigate("resumo") })
            }

            item {
                ReportStrip(onClick = { onNavigate("relatorio") })
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

            if (state.pendingReview.isNotEmpty()) {
                item { PendingReviewHeader(count = state.pendingReview.size) }

                items(state.pendingReview, key = { it.id }) { notification ->
                    NotificationCard(
                        notification = notification,
                        onClick = { onNotificationTap(notification.id) },
                    )
                }
            }

            if (state.history.isNotEmpty()) {
                item {
                    Text(
                        text = "HISTÓRICO",
                        style = PocketTheme.typography.sectionHeader,
                        color = PocketTheme.colors.text3,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(state.history, key = { it.id }) { item ->
                    HistoryRow(
                        item = item,
                        sourceName = state.sources[item.idSource]?.name ?: item.idSource,
                        paymentName = state.paymentSources[item.idPaymentSource]?.name ?: "",
                    )
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
                style = PocketTheme.typography.stepQuestion,
                color = PocketTheme.colors.text,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(PocketTheme.shapes.labelPicker)
                .background(PocketTheme.colors.surface)
                .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.labelPicker)
                .clickable(onClick = onAssistant),
            contentAlignment = Alignment.Center,
        ) {
            Text("✦", color = PocketTheme.colors.accent)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(PocketTheme.colors.accent),
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

    PocketCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = PocketTheme.colors.accent,
    ) {
        Column {
            Text(
                text = "SALDO DO MÊS · ${state.monthLabel}",
                style = PocketTheme.typography.sectionHeader,
                color = PocketTheme.colors.accentInk.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatter.format(state.balance),
                style = PocketTheme.typography.monoDisplay,
                color = PocketTheme.colors.accentInk,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Receitas",
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.accentInk.copy(alpha = 0.7f),
                    )
                    Text(
                        text = formatter.format(state.totalIncome),
                        style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.SemiBold),
                        color = PocketTheme.colors.accentInk,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Despesas",
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.accentInk.copy(alpha = 0.7f),
                    )
                    Text(
                        text = formatter.format(state.totalExpense),
                        style = PocketTheme.typography.monoSm.copy(fontWeight = FontWeight.SemiBold),
                        color = PocketTheme.colors.accentInk,
                    )
                }
            }
        }
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
    val borderColor = when (notification.status) {
        NotificationStatus.NEEDS_REVIEW -> PocketTheme.colors.warn
        NotificationStatus.NEEDS_TAGS -> PocketTheme.colors.accent
        NotificationStatus.AUTO -> PocketTheme.colors.income
    }
    val badgeText = when (notification.status) {
        NotificationStatus.NEEDS_REVIEW -> "REVISAR"
        NotificationStatus.NEEDS_TAGS -> "FALTA CATEGORIA"
        NotificationStatus.AUTO -> "CLASSIFICADA"
    }
    val badgeBg = when (notification.status) {
        NotificationStatus.NEEDS_REVIEW -> PocketTheme.colors.warnBg
        NotificationStatus.NEEDS_TAGS -> PocketTheme.colors.accentBg
        NotificationStatus.AUTO -> PocketTheme.colors.incomeBg
    }
    val badgeColor = when (notification.status) {
        NotificationStatus.NEEDS_REVIEW -> PocketTheme.colors.warn
        NotificationStatus.NEEDS_TAGS -> PocketTheme.colors.accent
        NotificationStatus.AUTO -> PocketTheme.colors.income
    }

    PocketCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        borderColor = borderColor.copy(alpha = 0.3f),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(badgeBg, PocketTheme.shapes.pill)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = badgeText,
                            style = PocketTheme.typography.bodyXs.copy(fontWeight = FontWeight.Bold),
                            color = badgeColor,
                        )
                    }
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
                            style = PocketTheme.typography.monoBody.copy(fontWeight = FontWeight.SemiBold),
                            type = notification.parsed.type,
                        )
                    }
                    val ctaText = when (notification.status) {
                        NotificationStatus.NEEDS_REVIEW -> "Ensinar →"
                        NotificationStatus.NEEDS_TAGS -> "Categorizar →"
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

@Composable
private fun HistoryRow(
    item: HistoryItem,
    sourceName: String,
    paymentName: String,
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

@Composable
private fun AutomationCard(
    stat: AutomationStat,
    monthLabel: String,
    pendingCount: Int,
) {
    val pct = if (stat.monthTotal > 0) {
        (stat.autoDone.toFloat() / stat.monthTotal * 100).toInt()
    } else {
        0
    }

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
                    text = "$pct%",
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
                    .background(PocketTheme.colors.accentBg, PocketTheme.shapes.pill),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct / 100f)
                        .height(8.dp)
                        .background(PocketTheme.colors.accent, PocketTheme.shapes.pill),
                )
            }

            Spacer(Modifier.height(8.dp))

            // Caption with bold autoDone + warn pending
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
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                Text(
                    text = "Para onde foi seu dinheiro",
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
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
private fun ReportStrip(onClick: () -> Unit) {
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
                    text = "▦",
                    fontSize = 18.sp,
                    color = PocketTheme.colors.accent,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Relatório",
                    style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = PocketTheme.colors.text,
                )
                Text(
                    text = "Tendências por mês, trimestre, ano",
                    style = PocketTheme.typography.bodyXs,
                    color = PocketTheme.colors.text3,
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
