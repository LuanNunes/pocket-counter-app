package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.ui.components.AmountText
import com.resolveprogramming.pocketcounter.ui.components.PocketCard
import com.resolveprogramming.pocketcounter.ui.theme.PocketTheme
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuccessScreen(
    draft: WizardDraft,
    cards: List<CreditCard>,
    allTags: List<Tag>,
    contexts: List<TagContext>,
    onViewTransaction: () -> Unit,
    onBackToApp: () -> Unit,
) {
    val circleProgress = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        circleProgress.animateTo(1f, tween(600))
        checkProgress.animateTo(1f, tween(350))
    }

    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    val accentColor = PocketTheme.colors.accent
    val cardName = draft.cardId?.let { id -> cards.find { it.id == id }?.name }
    val paymentLabel = draft.paymentMethod?.let { method ->
        val base = method.label()
        if (cardName != null) "$base · $cardName" else base
    } ?: "—"
    val selectedTags = allTags.filter { it.id in draft.tagIds }
    val contextMap = contexts.associateBy { it.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(60.dp))

        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(80.dp)) {
                val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = 360f * circleProgress.value,
                    useCenter = false,
                    style = stroke,
                )
            }
            if (checkProgress.value > 0f) {
                Canvas(modifier = Modifier.size(40.dp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    val mid = Offset(w * 0.35f, h * 0.65f)
                    val start = Offset(w * 0.15f, h * 0.45f)
                    val end = Offset(w * 0.85f, h * 0.25f)

                    if (checkProgress.value <= 0.5f) {
                        val t = checkProgress.value / 0.5f
                        drawLine(accentColor, start, Offset(start.x + (mid.x - start.x) * t, start.y + (mid.y - start.y) * t), stroke.width, StrokeCap.Round)
                    } else {
                        drawLine(accentColor, start, mid, stroke.width, StrokeCap.Round)
                        val t = (checkProgress.value - 0.5f) / 0.5f
                        drawLine(accentColor, mid, Offset(mid.x + (end.x - mid.x) * t, mid.y + (end.y - mid.y) * t), stroke.width, StrokeCap.Round)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        val title = if (draft.statusPayment == PaymentStatus.PAID) "Transação salva" else "Transação agendada"
        Text(
            text = title,
            style = PocketTheme.typography.stepQuestion,
            color = PocketTheme.colors.text,
        )

        Spacer(Modifier.height(8.dp))

        val amount = draft.amount ?: return
        AmountText(
            amount = amount,
            type = draft.type,
            showSign = true,
            style = PocketTheme.typography.monoDisplay,
        )

        Spacer(Modifier.height(24.dp))

        PocketCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SummaryRow("Tipo", if (draft.type == TransactionType.EXPENSE) "Despesa ↑" else "Receita ↓")
                SummaryDivider()
                SummaryRow("Status", if (draft.statusPayment == PaymentStatus.PAID) "Efetuada" else "Pendente")
                SummaryDivider()
                SummaryRow("Data", draft.date?.format(dateFormatter) ?: "—")
                SummaryDivider()
                SummaryRow("Pagamento", paymentLabel)
                if (draft.isFixo) {
                    SummaryDivider()
                    SummaryRow(
                        "Repete todo mês",
                        draft.recurrenceDay?.let { "dia $it" } ?: "Sim",
                    )
                }

                if (selectedTags.isNotEmpty()) {
                    SummaryDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text = "Tags",
                            style = PocketTheme.typography.bodySm,
                            color = PocketTheme.colors.text3,
                            modifier = Modifier.width(60.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            selectedTags.forEach { tag ->
                                val ctx = tag.idContext?.let { contextMap[it] }
                                val ctxColor = (ctx?.color ?: tag.color)?.let { Color(it) }
                                    ?: PocketTheme.colors.text3
                                Row(
                                    modifier = Modifier
                                        .background(PocketTheme.colors.surface2, PocketTheme.shapes.chip)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .background(ctxColor, CircleShape)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = tag.name,
                                        style = PocketTheme.typography.bodyXs,
                                        color = PocketTheme.colors.text,
                                    )
                                }
                            }
                        }
                    }
                }

                if (draft.installments != null) {
                    SummaryDivider()
                    SummaryRow("Parcelado", "${draft.installments}x")
                }
            }
        }

        if (draft.learnRule) {
            Spacer(Modifier.height(16.dp))
            PocketCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = PocketTheme.colors.accentBg,
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✦", color = PocketTheme.colors.accent)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Regra aprendida",
                            style = PocketTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = PocketTheme.colors.text,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val pattern = draft.merchant ?: "..."
                    Text(
                        text = "$pattern → $paymentLabel + ${selectedTags.size} tags",
                        style = PocketTheme.typography.monoSm,
                        color = PocketTheme.colors.text2,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Próximas notificações iguais serão classificadas automaticamente.",
                        style = PocketTheme.typography.bodyXs,
                        color = PocketTheme.colors.text3,
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WizardButton(
                text = "Ver transação",
                isPrimary = false,
                onClick = onViewTransaction,
                modifier = Modifier.weight(1f),
            )
            WizardButton(
                text = "Voltar ao app",
                isPrimary = true,
                onClick = onBackToApp,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = PocketTheme.typography.bodySm,
            color = PocketTheme.colors.text3,
        )
        Text(
            text = value,
            style = PocketTheme.typography.bodySm.copy(fontWeight = FontWeight.SemiBold),
            color = PocketTheme.colors.text,
        )
    }
}

@Composable
private fun SummaryDivider() {
    HorizontalDivider(color = PocketTheme.colors.line, thickness = 0.5.dp)
}

@Composable
fun WizardButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val bg = when {
        !enabled -> PocketTheme.colors.accent.copy(alpha = 0.4f)
        isPrimary -> PocketTheme.colors.accent
        else -> PocketTheme.colors.surface
    }
    val textColor = when {
        isPrimary -> PocketTheme.colors.accentInk
        else -> PocketTheme.colors.text
    }
    // The non-primary ("Ver transação") button reads as a soft surface tile with a line border.
    val surfaceMod = if (!isPrimary) {
        Modifier
            .border(1.dp, PocketTheme.colors.line, PocketTheme.shapes.chip)
            .background(bg, PocketTheme.shapes.chip)
    } else {
        Modifier.background(bg, PocketTheme.shapes.chip)
    }

    Box(
        modifier = modifier
            .then(surfaceMod)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = PocketTheme.typography.button,
            color = textColor,
        )
    }
}
